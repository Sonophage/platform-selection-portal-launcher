# Council review remediation

**Status:** Not started · **Branch:** none yet · **Written:** 2026-09-20

## Goal

Fix what a six-lens review of the whole tree found, in the order of what it costs when it goes
wrong: first the things that destroy data or leave the launcher unusable, then the things that
tell the user something false, then the XMB's own motion and identity, then the cheap
performance wins.

## The council

Six reviewers read the tree in parallel, one lens each. Every finding below carries its
verification state, because a reviewer's confidence is not evidence.

| Lens | Brief |
|---|---|
| **XMB purist** | A PSP/PS3 owner asking where the tribute drifted into "a launcher with a cross-shaped menu" |
| Emulation daily-driver | Hundreds of ROMs, RetroArch plus standalone, scraping, save states, multi-disc |
| PC gaming on Android | Winlator/Box64 wrappers, Steam/GOG sideloads, package-backed entries |
| Controller & accessibility | Dead ends, focus visibility, touch/controller parity, the 462 dp screen |
| Handheld performance | Cold start, Compose recomposition, image decode, decoder lifetime, battery |
| Codebase health | Structural risk, unguarded mirrors, tests that cannot fail |

**Verification key:** ✅ confirmed by reading the source · ⚠️ reported, not independently
verified · 📱 needs the device to settle.

One claim was retracted during review: the missing `42.json` schema is **not** a gap.
`Migration41To42Test.kt:17` documents it — Room only exports the declared version's schema, and
41→42→43 landed together, so the test validates at 43 and asserts on what 41→42 did. Correct as
written; leave it alone.

## Architecture

Nothing here is a feature. Almost every task adds a **guard** to a pair that must agree, or
deletes something that no longer runs. The repo's own rule is the organising principle: when you
find a guard, ask what its mirror image would be, and guard that too.

Three tasks turn a hand-written list into a derived one. That is the shape to prefer throughout:
a check someone must remember to update is correct for the case it was born in and blind outside
it.

## Tech stack

Kotlin, Jetpack Compose, MVVM, Hilt, Room (v46), DataStore, SAF, Coil3, Media3, WorkManager.
Tests are pure-JVM JUnit4 + MockK, Robolectric where a resource or a real DB is needed.

## Non-goals

TalkBack coverage (real, but this is a personal handheld — logged at the bottom, not scheduled).
Decomposing `XMBViewModel` beyond the first proof-of-pattern extraction. Re-slugging existing
artwork folders. New features of any kind.

---

# Batch 1 — data loss and lockouts

Four small guards. Each one closes a way the launcher can currently hurt the user without
telling them.

## Task 1.1 — A failed wallpaper apply must not lock the photo viewer forever ✅

**Files:** Modify `feature/feature-xmb/.../detail/PhotoViewerViewModel.kt`

`handleGamepadAction:131` swallows every action, including BACK, while `applyingWallpaper` is
true. Both on-screen buttons are `enabled = !state.applyingWallpaper`. The flag is set at `:252`,
and three operations after the guarded import are bare: `WallpaperLuminanceProbe.survey` (decodes
a bitmap — OOM is realistic on a handheld photo), `pfpDataStore.edit {}` (IOException), and the
orphan sweep's `listFiles()/delete()` (SecurityException). Any throw leaves the flag true: pad
input swallowed, both buttons dead, no `BackHandler` anywhere in the repo, system Back neutered in
`MainActivity`. **The only exit is killing the app.**

- [ ] Write the failing test: make the luma survey throw, assert `applyingWallpaper` is false afterwards
- [ ] Run it — confirm it fails (RED)
- [ ] Wrap the `viewModelScope.launch` body at `:253` in `try { … } finally { clear the flag }`
- [ ] Run it — confirm it passes (GREEN)
- [ ] Falsify: delete the `finally`, watch the named assertion go red, restore
- [ ] `./gradlew :feature:feature-xmb:testDebugUnitTest --rerun-tasks`

## Task 1.2 — Pinning a shortcut must not delete playtime and collections ✅

**Files:** Modify `core/core-data/.../dao/GameDao.kt`, the `GameRepository` interface + impl,
`feature/feature-launcher/.../PcShortcutImporter.kt`

`PcShortcutImporter.kt:148` and `:179` call `upsert` on an **already matched existing row**.
`GameDao.upsert` is `@Insert(onConflict = REPLACE)`; `PlaySessionEntity` and `CollectionGameEntity`
both declare `onDelete = CASCADE`. REPLACE on an existing primary key is DELETE-then-INSERT, so
the cascade fires.

`GameUpsertCascadeTest` already proves the mechanism, on a `platformId = "windows"` fixture.
`PcGameScanner.applyFill` already avoids it by writing one column at a time, and its doc comment
names the hazard. These two callers are the unguarded siblings of a guard that exists twice.

Pin reconcile runs at **every app start** and on every PC scan.

- [ ] Write the failing test: a matched row with sessions + collection membership survives a pin reconcile
- [ ] Run it — confirm it fails (RED)
- [ ] Add `@Query("UPDATE games SET package_name = :pkg, shortcut_id = :shortcutId WHERE id = :id")`, mirroring the existing `updateStorefrontIdentity`
- [ ] Add the repository passthrough; change both `PcShortcutImporter` call sites
- [ ] Run it — confirm it passes (GREEN)
- [ ] Falsify: restore one `upsert` call, watch it go red, restore the fix
- [ ] `./gradlew :feature:feature-launcher:testDebugUnitTest :core:core-data:testDebugUnitTest --rerun-tasks`

## Task 1.3 — A forgotten migration registration must fail a test, not every launch ✅

**Files:** Create `core/core-data/src/test/.../MigrationRegistrationTest.kt`

`PFPDatabase` declares 45 `MIGRATION_n_m` objects. `DatabaseModule` lists all 45 **by hand**, and
appears in **zero** test files — its only occurrence in the repo is its own declaration.

Failure mode: write `MIGRATION_46_47`, write `Migration46To47Test` (it passes — it invokes the
object directly), bump `version = 47`, forget the one line in `DatabaseModule`. Every existing
user gets `IllegalStateException: A migration from 46 to 47 was required but not found` on launch,
and **all 2,120 tests stay green**. `fallbackToDestructiveMigration` is correctly refused, so the
app is simply unusable until a hotfix.

- [ ] Reflect `PFPDatabase.Companion`'s `Migration` fields; collect what the builder registers
- [ ] Assert the registered set covers every declared `(startVersion, endVersion)` pair
- [ ] Assert the chain is unbroken from the lowest start version up to `version`
- [ ] Falsify: comment one migration out of `DatabaseModule`, confirm the named assertion fails, restore
- [ ] `./gradlew :core:core-data:testDebugUnitTest --rerun-tasks`

## Task 1.4 — "Save as Theme…" must not leave the controller dead ✅

**Files:** Modify `feature/feature-xmb/.../viewmodel/XMBViewModel.kt`

`saveThemeNameDialog` has exactly five references in 8,133 lines: the field `:716`, the
`hasBlockingOverlay` entry `:801`, the opener `:7316`, and two clears (`:7321`, `:7327`) reachable
only from the dialog's own touch buttons. **No gamepad branch exists**, so every press falls to
`if (state.hasBlockingOverlay) return` at `:4837` and dies. Controller dead until the user touches
Cancel.

The comment on that line reads: *"this guards against a future overlay being added without its own
branch."* It has been silently catching a present one.

- [ ] Add a `saveThemeNameDialog != null` branch above the `customIconSession` branch, handling BACK
- [ ] Clear the dialog in `closeCustomIcons()` at `:7221`
- [ ] Make the sink at `:4837` log a warning naming the state before returning
- [ ] Test: with the dialog open, BACK closes it and the next press reaches the page
- [ ] `./gradlew :feature:feature-xmb:testDebugUnitTest --rerun-tasks`

## Task 1.5 — Pin the disc-tag divergence before more artwork accumulates ✅

**Files:** Create a test in `feature/feature-artwork/src/test/.../ArtworkNamingDiscTagTest.kt`

`DiscTag` (scanner) matches parens **and brackets**, `of N` totals, and a trailing `- Disc 2` form.
`ArtworkNaming.DISC_TAG` is `\((?:disc|disk|cd)\s*(\d+)[^)]*\)` — **parens only**. So
`"Panzer Dragoon [Disc 2]"` and `"Final Fantasy VII - Disc 2"` get `discNumber = 2` from the
scanner and **no disc suffix** from the slug: disc 2's box art silently overwrites disc 1's.

`NORMALIZATION_VERSION = 1` is documented as unchangeable once shipped, so a fix needs a version
bump plus a re-slug of existing folders. **This is cheaper today than it will ever be again.**

- [ ] Write a test asserting the two title forms produce **distinct** slugs
- [ ] Run it — confirm it fails (RED), and leave it failing with `@Ignore` plus a comment pointing here
- [ ] Do **not** edit the regex in this batch — the version bump is its own conversation
- [ ] Record the decision in this plan when it is made

**Verify batch 1:** `./gradlew test --rerun-tasks` · commit each task separately.

---

# Batch 2 — the XMB, for the XMB fan

The purist's verdict was that **the geometry is faithful and the behaviour mostly is**; what is
missing is **motion** and **identity**. Independently, that reviewer worked the arithmetic for the
462 dp screen and got the selected row's centre at 48.6 % of screen height — exactly what
`XMBShell.kt:790` claims. The cross itself is right. These are the four things a PSP owner would
notice.

## Task 2.1 — A game with a logo but no artwork has no name on screen ✅

**Files:** Modify `feature/feature-xmb/.../ui/XMBItemList.kt`, `.../ui/XMBShell.kt`

```
XMBItemList.kt:698   showGameText = … || (isSelected && item.logoUri == null)   // label hidden when a logo exists
XMBShell.kt:614      ?.takeIf { it.artworkUri != null }?.logoUri                // logo only drawn when artwork exists
```

`logoUri != null && artworkUri == null` → no label, no logo. A tile with nothing naming it, and
nothing logs.

**This got worse from the recent backdrop change.** The XMB backdrop now resolves through
`focusedItemBackdrop` (first candidate that decodes), so the logo gate at `:614` is testing a
field that no longer decides what is on screen.

- [ ] Define **one** predicate — `hasVisibleLogo` — and have both sites read it
- [ ] Test: a game with a logo and no artwork keeps its label
- [ ] Falsify: revert one site to its own condition, confirm red

## Task 2.2 — The selected item's name arrives 650 ms late ⚠️📱

`XMBShell.kt:619` (logo) and `:683` (metadata) both `delay(650)` then `tween(500)`, and snap out on
any cursor move. Held-scroll repeat is 35–110 ms, so while scrolling the timer never fires — **no
identity is shown for any row passed through**, and on the row you stop at full legibility is
~1.15 s away. The real XMB staggers icon → PIC1 → PIC0, but the *name* is on the item immediately.

Task 2.1 fixes this for logo'd games as a side effect. Keep the 650 ms stagger for the PIC0 logo —
that part is correct.

- [ ] Confirm on device that the delay reads as sluggish before changing anything
- [ ] If so: show the row's title immediately for the selected row regardless of `logoUri`

## Task 2.3 — Vertical cursor movement is a hard cut, and the glide code is dead ✅ (dead code) / 📱 (feel)

`XMBItemList.kt:531` places the live list with an integer `sel`; nothing interpolates. The per-row
scale spring cannot help because slot 0 is always the selected slot, so its target never changes.
`CenterLockedColumn` at `:404` — LazyColumn plus a cadence-adaptive `tween(70..240ms)`, the only
real glide in the file — has **one reference in the whole repo: its own definition.**

The purist's point: the XMB's single most recognisable property is that the cursor never moves and
the column flows under it.

- [ ] Device check first: does the snap actually read wrong at 147 games?
- [ ] If so: make `sel` a float via `animateFloatAsState`; `XmbGameColumn` already places by absolute offset
- [ ] Delete `CenterLockedColumn` either way

## Task 2.4 — Nothing on screen names the folder you are in ✅

`drillTitle` is computed across ~30 careful branches in `XMBViewModel` and **never rendered** — all
four UI uses are `!= null` as a boolean. Inside "Nintendo 64" you see a console icon, a ◀ and a
column of games. The flyout's sibling column is drawn with `showLabels = false`.

- [ ] Render `drillTitle` once — label the active sibling, or one line beside the ◀
- [ ] The string already exists and is already correct

## Also from the purist, deliberately **not** scheduled

- **Back at a category root opens the app drawer.** `XMBViewModel.kt:4870` says so on purpose. The
  purist argues Circle should mean nothing there. That is a design call, not a defect — Seth's.
- **One sound for scroll / category-change / select.** Confirmed: all three map to `SOUND_SCROLL`
  and `res/raw/` ships one cursor sample. But the code already says *"a default, not a law (75+
  call sites still distinguish the events)"*. This is waiting on two audio files, not on code.
- **Category change slides the column vertically** while the bar slides horizontally ⚠️. Three-line
  fix (`slideInHorizontally`), but judge it on the device first.

---

# Batch 3 — the app states something false

| # | Finding | Fix |
|---|---|---|
| 3.1 ✅ | `EmulatorsSettingsScreen.kt:197` promises "it offers them all"; `RetroArchCoreScanner.kt:143` says *"an empty set yields no profiles. There is deliberately no fallback."* Unlinked means **zero** cores, so every console without a standalone emulator resolves to nothing | Two strings |
| 3.2 ✅ | A per-game emulator override can be set but never cleared: `GameDetailViewModel.kt:1409` always writes one; `XMBViewModel.kt:5433` does it correctly with `takeIf { it != "default" }` | A "Use system default" row through the same predicate |
| 3.3 ✅ | Preflight validates `romUri` (`EmulatorIntentResolver.kt:121`) while RetroArch's profile ships `"ROM" to "{rom_path}"` (`EmulatorDetector.kt:91`) — `File(romPath).exists()` is unreachable for every SAF game | Also require a readable `romPath` when the profile references `{rom_path}` |
| 3.4 ✅ | `FALLBACK_CATEGORIES` never gained `LIBRARY`; `BUILT_IN_CATEGORIES` has it. `canonicalXmbCategories` derives `builtInIds` from the fallback, so Library loses the canonical-icon guarantee — and that function's KDoc says it exists *because these two drifted before* | One line, plus a test asserting the two id lists match |
| 3.5 ✅ | `MusicPlayerScreen.kt:191` `formatTime` has no hour branch — a 72-minute track renders `"72:14"` | Delete it; use `LibraryRowText.formatDuration`, which handles hours |
| 3.6 ✅ | The Remove dialog draws two buttons, neither reachable, and Confirm is destructive (`GameDetailViewModel.kt:868`, engine nodes at `:574` dead, `:595` prefers the destructive one) | Folded into 4.1 below |
| 3.7 ✅ | Back while searching the App Drawer exits the drawer: `searchActive` is a local at `AppDrawerScreen.kt:90` and is absent from `overlayOpen` at `:96` | Add it, give it a BACK branch |

---

# Batch 4 — the open question

## Task 4.1 — Settle whether `AlertDialog` is controller-deaf 📱

The accessibility reviewer retracted its own mechanism and replaced it with a bigger claim:
`androidx.compose.material3.AlertDialog` renders into its **own platform Window**, so
`Activity.dispatchKeyEvent` — where `GamepadInputHandler` lives — is never called. If that holds,
then across ~20 `AlertDialog` sites the controller can move focus but can neither confirm nor
cancel: Compose's `clickable` accepts `DPAD_CENTER`/`ENTER` and not `BUTTON_A`, and the dialog
answers `KEYCODE_BACK` and not `BUTTON_B`.

This contradicts the intent of about a dozen deliberate BACK branches written for those dialogs
(`GameDetailViewModel.kt:877`, `XMBViewModel.kt:4683`), so either it is a real systemic bug or
something compensates that is not visible in source.

- [ ] **Device check: open any dialog (rename an app, or Options ▸ Remove) and press B.**
- [ ] If it does not dismiss, this outranks everything in batch 2 and 3
- [ ] Fix is to replace `AlertDialog` with the in-tree overlay pattern already used correctly by
      `PspContextMenu`, `DetailContextMenu`, `HsvColorPickerDialog` — plain Boxes in the main window

---

# Batch 5 — cheap performance

All five are one line each. None changes behaviour.

- [ ] **5.1 ✅** `@Immutable` on `XMBUiState` — there are **zero** `@Immutable` annotations in
      feature-xmb, and the state class holds `List`/`Map` fields, so `XMBShell`'s ~1200-line body
      can never be skipped. The VM only ever replaces state with `copy`, so the contract holds
- [ ] **5.2 ✅** `key(items[i].id)` in the row loop at `XMBItemList.kt:533` — without it every
      visible row recomposes and re-issues its image request on every D-pad press
- [ ] **5.3 ✅** `.flowOn(Dispatchers.Default)` in `GameRepositoryImpl` — zero `flowOn` in the file,
      so 147 `toDomain()` allocations land on the main thread per games write
- [ ] **5.4 ✅** `COLLATE NOCASE` on `GameDao`'s 8 `ORDER BY title ASC` clauses — it is the **only**
      DAO in the package without it (Book 2, Video 3, MusicTrack 6, Photo 2)
- [ ] **5.5 ✅** Cold start always races a full SAF rescan: `RescanTriggerBus.kt:25` seeds
      `lastResumeRunAt = Long.MIN_VALUE` and `:31` explicitly bypasses the throttle for it

Deferred, needs measurement: foreground/expedited on the six `CoroutineWorker`s (**zero** declare
any today), batching `LibraryScanner`'s per-row upserts, an index on `disc_set_key`.

---

# Batch 6 — derived guards, dead code, dormant tests

## Task 6.1 — Turn the backup's hand-written lists into derived ones ✅

Two mirrors, both unguarded in the same way. `BackupKeyCoverageTest` asserts a hand-written list
of names, so it can only ever confirm keys someone already remembered.

Measured against the tree: **4 keys the backup writes and restores exist nowhere else** —
`display_wave_mode`, `display_icon_style`, `library_root_path`, `display_auto_reduce` — and these
are declared and never backed up, absent from the deliberate-exclusion list: `ra_username`,
`steam_id64`, `vita3k_ux0_tree_uri`, `retroarch_documents_tree_uri`, `artwork_crop_preview_enabled`,
`auto_resolved_cores`.

The same shape is missing for **tables**: `PFPDatabase` declares 30 entities, the export carries 24,
and there is no equivalent of the "excluded on purpose" test for them. A 31st entity added later
carries real rows and silently does not survive a restore.

- [ ] One test: every `*PreferencesKey("…")` literal in the tree is either backed up or in an explicit `DELIBERATELY_NOT_BACKED_UP` set
- [ ] One test: `PFPDatabase`'s entity set equals `exportedTables + DELIBERATELY_NOT_BACKED_UP_TABLES`, with the six current exclusions named and reasoned
- [ ] Leave the six table exclusions alone — `artwork_records` is correct and documented
- [ ] Fix the four dead keys and decide on the six missing ones

## Task 6.2 — Add the two missing migration tests ✅

`MIGRATION_34_35` and `MIGRATION_35_36` are the only two in the 32–46 range with an exported schema
and no test. `Migration33To34Test` validates at 34 and stops; `Migration36To37Test` starts at 36.

## Task 6.3 — Delete what no longer runs ✅

- [ ] `RomScanner.scan()` (~190 lines, own hardcoded `/sdcard/ROMs` list, a third parallel copy of disc logic) — zero callers; live entry points are `scanTree` 5, `scanPcFolder` 4, `listSubfolderNames` 4, `createSubfolders` 2, `scanDirectory` 1
- [ ] `scrollToTopToken` as an `XMBItemList` parameter — declared at `:482`, never read (the `MusicBrowserScreen` token is a different, live one)
- [ ] `CenterLockedColumn` (with task 2.3)
- [ ] Their stale comments — `XMBViewModel.kt:501` still claims the list scrolls to item 0 on that token

## Task 6.4 — Tests that cannot fail ✅

- [ ] `GoldenPtfTest` — 7 tests `assumeTrue` on a corpus defaulting to `~/Downloads`; there are **zero** `.ptf` files there and nothing in the build sets `themekit.golden.dir`. **These are the 7 "skipped" in every suite run.** The theme parser's hostile-input tests have never run. Commit a minimal synthetic fixture, or wire the property
- [ ] `SettingsScaffoldScreenshotTourTest` — zero `assert`/`verify`/`fail(` in the file; writes PNGs nothing reads back; own KDoc says "TEMPORARY". Delete
- [ ] `StudioCropPreviewTest` — body is a `forEach` with no assertion over a property the compiler already enforces. Delete

---

# Batch 7 — the structural one

## Task 7.1 — Prove the extraction pattern on Books ✅

`XMBViewModel`: **8,133 lines, 42 injected dependencies, 357 member functions**, `XMBUiState` with
~110–117 fields, 250 `_uiState.update` call sites. Two independent counts agreed on 357.

The real cost is not size. **No test in the tree ever constructs an `XMBViewModel`.** The 15 test
files exercise only pure top-level functions that happen to live in the file. Cursor movement,
drill in/out, the 436-line gamepad router and the 784-line context-menu block have zero automated
coverage — 42 constructor args is why nobody will write that fixture.

The seam already exists and Seth cut it: `canonicalXmbCategories` was extracted as a pure
top-level function **specifically** so the merge rule could be tested without a ViewModel, and its
KDoc says so. The class is the residue of that pattern, not the opposite of it.

Books first, because it is the only vertical that touches nothing else — across lines 2916–3127 it
references exactly two injected deps. `booksRootItems()` at `:2954` is already a pure function in
member-function clothing: it reads four `_uiState.value` fields and returns `List<XMBItem>`.

- [ ] Change its signature to `internal fun booksRootItems(state: XMBUiState): List<XMBItem>`
- [ ] Move it with `bookItems` / `bookShelfItems` / `bookSeriesItems` / `emptyBooksItem` to `XmbBooksItems.kt`
- [ ] Write the tests that were impossible before
- [ ] Mechanical, zero behaviour change, no state moves
- [ ] **Do not** attempt a sub-ViewModel — that requires moving `booksNav` out of `XMBUiState`
- [ ] Then repeat on Photo (311 L), Video (244 L), Music (720 L), in that order

---

# Logged, not scheduled

- **TalkBack.** The XMB row has no `contentDescription`, `role` or click semantics; every settings
  row is a `pointerInput` tap handler with no click action; Back/Options/app-drawer are unlabelled
  glyphs. All real. This is a personal launcher on one handheld — a choice, not an obligation.
- **No `imePadding` anywhere in the tree** ⚠️ — with `decorFitsSystemWindows = false` and a 462 dp
  screen, the keyboard likely covers text fields in the lower half of settings lists. Needs the device.
- **Package-backed entries can never be marked missing** ⚠️ — `is_missing` is keyed on `rom_path`,
  which is null by construction for PC and Android entries.
- **Scrape failures collapse to a bare count** ⚠️ — `SsFailureReason` (quota, bad credentials, rate
  limit) exists, is typed, and reaches only Timber.
- **Add-by-ID needs two hand-typed fields** ⚠️ and an id GameNative does not display, on a
  controller-only device.
- **~70 preference keys are declared in two or more production files**, kept in step by 27 "Must
  match X" comments. `display_custom_wallpaper` is declared in six.
- **Four byte formatters still disagree** on tiers; `VideoDetailScreen`'s has no KB tier, so a
  300 KB file reads `"0 MB"`.
- **Zero `androidTest/` anywhere.** `app/` has no tests at all (937 lines, including startup wiring).

# What the council said to leave alone

The cross geometry (measured, and the 48.6 % figure independently re-derived) · `GamepadInputHandler`
(repeat ramp, stick hysteresis, HAT/DPAD dedupe, newest-axis-wins diagonals, each reasoned in
comments) · `Icon1VideoOverlay`'s ICON1 semantics and power gates · the emulator ladder (returns
*why*, refuses a dead choice rather than substituting) · `AutoCoreMemory` · the no-fabricated-cores
decision, held in three places · multi-disc set building · the library reconciler's refusal to
delete on an untrusted survey · `RestoreArchive` (zip bombs, traversal, confined staging — the
strongest security code in the tree) · the icon-slot four-way mirror · `AccentDeriver`'s
single-sourcing · Room migrations 32→46, which validate against exported schemas rather than a
transcription · no wake locks, no keep-screen-on, no periodic work · zero `TODO`/`FIXME` in 97,000
lines.
