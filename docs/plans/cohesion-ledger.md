# Cohesion ledger

Where the app says the same thing two ways, or does the same job with two mechanisms.

Source: the five-lens UX/UI review of 1.11.1 (34 Konker Elite frames + code), section 5.
Every line below was **re-verified against the working tree on 2026-09-25**, not copied forward
from the review. Status is `DONE`, `PARTIAL` or `OPEN`.

**Each item carries the command that decides it.** Run the command; do not trust the status line.
A status is a claim with a date on it and it drifts exactly like the claim it replaced.

---

## Closed

### 1. `Favourite`, one label — DONE

Was: `GameDetailScreen.kt` said `Favourited`/`Favourite`, `PillActions.kt` said `Favorite`/
`Unfavorite`, and four test assertions ratified the split. Both spellings were reachable on the
same page one press apart. The DB column is `is_favorite`.

Now: American everywhere, matching the column.

```sh
grep -rn "Favourite\|Favourited" --include="*.kt" .   # expects: no output
```

### 2. Four stale comments — DONE

Closed in `5935ecf2` along with 25 others. The detector that found them is
`tools/stale-comments/check.py`.

```sh
python3 tools/stale-comments/check.py --selftest
```

> **This script is not wired to anything.** No gradle task, no CI workflow, no hook calls it.
> A detector nobody runs is the same as no detector. See *New work* item N1.

---

## Half-closed

### 3. `PfpHintBar` migration — DONE

The review found three screens building their own footer, one hiding it, and the detail pages
drawing an inert copy. `PfpDetailHelperFooter` now takes `onAction`; `MusicBrowserScreen`,
`MusicPlayerScreen`, `ArtworkStudioScreen` and `VideoDetailScreen` are on `PfpHintBar`. Eleven
screens outside core-ui now call it.

`AppDrawerScreen`'s bar is **not** the holdout the review called it. `hintAlpha` now goes to 0
only for `state.confirmUninstall`, with a stated reason (a confirm dialog naming the drawer's keys
would name the wrong ones); the options menu rewrites the bar instead of removing it, exactly as
the XMB rail does. Verified in code (`AppDrawerScreen.kt:385-396`) and on the tablet on
2026-09-25 — the drawer's menu frame shows `Back Close | Tap Select`.

`VideoPlayerScreen` is **not** a holdout either, now that it has been looked at. Its prompts are
`PfpControllerHints(style = OVERLAY)` inside the transport controls — beside the time readout and
the speed/screen-mode status, over the video. `OVERLAY` is a deliberate third style whose KDoc
names this exact screen and gives its bug history: a themed colour comes out dark on the fixed
black of a media scrim, which is the inverse of the bug that made `INLINE` theme-aware. A bottom
chrome band over a playing video would be the wrong component.

**This item is closed.** Every screen that should have a `PfpHintBar` has one.

```sh
# Screens that CALL it — the "(" excludes imports, which a bare name match counts as adopters.
grep -rl "PfpHintBar(" --include="*.kt" feature/ | grep -v Test          # 11 on 2026-09-25
grep -n "hintAlpha" feature/feature-appbar/src/main/kotlin/com/psplauncher/feature/appbar/AppDrawerScreen.kt
```

### 4. `HintBarHeight` honoured like `StatusStripHeight` — NOT A DEFECT, closed

The count was 7 screens reserving the top band against 3 reserving the bottom, read as a missing
guard. It is two correct layouts, and the two numbers are not comparable.

The status strip is ALWAYS drawn by the shell over these screens, so every one of them reserves
it. The hint bar is not: `AppDrawerScreen`, `AppPickerScreen`, `GamePickerScreen` and
`SettingsScaffold` each draw their **own** bar as the last child of a Column, where it takes its
own space — `AppDrawerScreen`'s comment says so outright ("the bar brings its own height and
scrim"). Reserving `HintBarHeight` there would leave a 34dp gap above a bar that is already in
the layout.

The three that DO reserve it are the three where the bar overlays: `ContextMenuOverlay` and the
letter rail sit under the shell's bar, and `SearchScreen` puts its own bar in a `Box` with
`align(BottomCenter)` — an overlay over its own content Column, which therefore must pad for it.

**The rule is: reserve the bottom band when the bar is drawn over you, not when it is beside you.**

### 5. Two test holes — DONE

- `LetterJumpTest`'s `returnIndex` assertion — closed earlier; it had been comparing a value to
  itself.
- `promptedKeys` derived from `kbLabels` — **closed, and it found a live bug.**

The hand-written list was 11 positions against a label table of 14, so three were never checked.
It is now derived from the label table itself, and what it reported was real:

**`START` printed "F1" and F1 was bound to nothing.** `GamepadAction.HOME` had exactly one
binding, `KEYCODE_BUTTON_START` — so the four pickers that prompt it (Game, App, Music Track,
Artwork Studio) drew "F1 Apply" / "F1 Add" at a key that did nothing, and **Apply was gamepad-only
on a machine with no gamepad**. `KEYCODE_F1` is now bound to `HOME`; it types no character, so it
costs no keystroke.

**`SYSTEM` prints "Home" and is bound on no input at all** — `KEYCODE_BUTTON_MODE` appears in no
binding and no prompt anywhere asks for the position. That is dead weight rather than a lie told
to a keyboard user, so it is exempted — by a rule that **un-exempts itself**: the exemption is
computed as "positions no input reaches", so the moment anything binds it, it drops out and the
test goes red if the keyboard still cannot get there.

Two derivation mistakes were made getting here and both are recorded in the test: treating "has no
gamepad position" as "is a keyboard key" reported all four arrow keys unreachable (a keyboard
sends them as `KEYCODE_DPAD_*`, which ARE gamepad positions), and the composite `DPAD_ALL` — the
"◀▶" glyph, which no keycode resolves to in any family — had to be named as a legend rather than
a key.

```sh
./gradlew :core:core-ui:testDebugUnitTest --tests '*KeyboardPromptsAreBound*'
```

### 6. One action-language for the action menus — MOSTLY DONE

The review said three. **There were four** — it missed `core-ui`'s `PspContextMenuOverlay`, which
already had 4 call sites (Logs, Themes, Artwork Studio) and whose own header claimed to be
"shared by the XMB's Y/Triangle menu and any settings screen ... one source, no style drift".
That claim was false: `DetailContextMenu` was a near-verbatim second copy of it.

Merged on 2026-09-25:
- **`DetailContextMenu.kt` deleted** (203 lines). Its 6 call sites — AppDetail ×2, GameDetail ×2,
  VideoDetail, PhotoViewer — now call `PspContextMenuOverlay`. The two were identical in row
  metrics (15/16sp, 12dp padding, same glow gradient, same drop shadow) and title block; the
  survivor also has `checked` rows and a preview.
- **`AppDrawerOptions` deleted.** The drawer's 280dp centred panel is now the same right-edge
  panel as everywhere else. Its file is renamed `UninstallConfirmDialog.kt` after what is left.
- **One real bug fixed by the merge**: `DetailContextMenu`'s scrim was `0x40000000`, the value
  `PspContextMenuOverlay`'s own comment records as having been raised to `0x99000000` *because
  artwork read straight through it*. The detail pages are the screens with the most artwork and
  were still on the old value.
- **One fix carried the other way**: `PspContextMenuOverlay`'s `LazyColumn` had top padding only,
  so a list longer than the panel cut its last row in half. `DetailContextMenu` had fixed that
  with `contentPadding(bottom = 32.dp)`; that is now in core-ui.

Verified on the tablet 2026-09-25: the drawer's menu draws the right-edge panel with the app's
label as title, the glow band on the selected row and Uninstall in red.

**Still open — the XMB rail, deliberately not merged.** `ContextMenuOverlay` + `XmbRailCapsule`
is the crossbar's own idiom: right-aligned capsules with initial badges, no panel. Folding it into
the panel would change how the home screen looks, which is a taste call, not a cohesion defect.
Flagged for a decision rather than done.

```sh
grep -rl "PspContextMenuOverlay" --include="*.kt" . | grep -v Test   # the one panel
grep -rl "XmbRailCapsule\|ContextMenuOverlay" --include="*.kt" .    # the rail, still separate
```

### 7. One search field — DONE

`PfpSearchField` has four callers now: `AppDrawerHeader`, `AppPickerScreen`, `SearchScreen` and
`MusicBrowserScreen`. No screen rolls its own search box.

**`MusicBrowserScreen` had the bug the component exists to prevent.** It passed a plain `String`
to `OutlinedTextField`, which leaves the selection at 0 while text arrives around it — so a query
seeded from outside the field takes every character after it at position zero, and typing C then L
reads "lc". `SearchScreen` had its own fix for the same thing; `PfpSearchField`'s header already
named both screens as having learned it separately.

Three `OutlinedTextField`s remain in feature-xmb and are **not** search: "Display Name", "Note"
and "Display Title" are labelled editors, a different component's job.

```sh
grep -rl "PfpSearchField(" --include="*.kt" feature/ core/ | grep -v Test     # 4 callers + the file
grep -rn "OutlinedTextField(" --include="*.kt" feature/feature-xmb/src/main   # 3 editors, 0 search
```

### 8. One placeholder grammar — NOT A DEFECT, closed

The claim was that two screens name their scope and two do not. Read properly, all four obey the
same rule: **the scope is named once, in whatever band the screen has for it.**

- `AppDrawerHeader` is "one search field, the width of the screen. Nothing else" — no heading, so
  the placeholder carries the scope: "Search apps".
- `AppPickerScreen` names its scope in the hint bar, a different band from the field.
- `SearchScreen`'s heading directly above the field is `SearchScope.label` — already "Search
  Games", "Search Video". A placeholder repeating it would be the exact redundancy
  `AppDrawerHeader`'s header comment records removing ("the magnifier and the word Search sat
  beside a box whose own placeholder reads Search").
- `MusicBrowserScreen` does the same with `state.title`.

Changing the two bare ones would have ADDED the redundancy a previous pass removed.

### 9. Title-case the Artwork Studio's nine prompts — DONE

It was the only screen with lowercase prompts, and the survey is unambiguous: eight lowercase
labels, all of them in this one file, against roughly sixty title-cased everywhere else — with
exact duplicates across the line (`back`/`Back`, `options`/`Options`).

The slash-compounds stayed compounds. They were listed as a second oddity, but the app already
title-cases those elsewhere — `Play / Pause`, `Apply / Toggle`, `Expand / Collapse` — so
`Browse / Pick File` and `Preview / Apply` are in style, not out of it.

```sh
grep -rhoE 'ControllerPromptItem\([^,]+,\s*"[a-z][^"]*"' --include="*.kt" feature/ core/   # expects: no output
```

### 10. One B label per kind of dismissal — DONE, and it was four words for four meanings

The item read as four labels for one job. Read against the call sites it is one rule, already in
force, and the rule is about what pressing B COSTS:

- **Back** — you leave the page. Nothing dismissed, nothing abandoned. ("Apps" at the crossbar
  root is the documented exception: there B opens the drawer rather than going anywhere.)
- **Close** — an overlay goes away and it already did whatever it does.
- **Cancel** — something is PENDING and B abandons it.
- **Done** — you were editing, it applied as you went, and you are finished. ("Close" would be
  true and would read as though the work were being thrown away.)

`VideoDetailScreen` follows it exactly across five states. `GameDetailScreen` follows it across
nine — **with one violation**, now fixed: its emulator picker said "Cancel" while its own
collection picker and the video page's playlist picker said "Close" for the identical shape, and
`closeEmulatorPicker()` reverts nothing.

The rule was tacit. It is now written where the prompts are built (`ControllerPrompt.kt`), so the
next person choosing a word has something to choose against.

### 11. One empty-state grammar — DONE, and the two forms mean two things

Listed as three competing forms. Two of them are a real distinction the app mostly kept:

- **"No X yet"** — nothing has been ADDED. Pairs with a hint saying how ("Set a root folder in
  Settings ▸ Media ▸ Music").
- **"No X found"** — a search or a scan came back empty. Something was looked for.
- **"No matches"** — a QUERY matched nothing, which is a third thing and correctly its own words.

Four genuine violations, all in the same direction — "found" where nothing was searched:
`XMBViewModel`'s `emptyAllMusicItem`, `emptyAllVideosItem` and `emptyAllPhotosItem` each said
"No X found" under a subtitle reading "Add a … folder in Settings", while `LibrarySearch` already
said "No X yet" for the identical state. `AppDrawerScreen` said "No games found" three lines above
its own "No recently used apps yet". All four now say "yet".

Every surviving "found" is a real lookup: a remote scrape, a folder scan, a core scan, a launcher
scan.

**Bonus, found on the way**: the same breadcrumb was written with three different arrows —
`▸` (63), `→` (19), `>` (6). User-facing strings are now all `▸`; the remainder are comments.

```sh
grep -rn '"[^"]*Settings →' --include="*.kt" feature/ core/ | grep -v build   # expects: no output
```

### 12. Five App Drawer empty strings live in two files — DONE, by deletion

The second file was `storefront/StorefrontAppDrawer.kt`, 911 lines reached by nothing.
`AppDrawerViewModel` said so in a comment on `GRID_COLUMNS`: "read only by StorefrontAppDrawer,
which nothing reaches either." Deleting it removes the duplicate strings, the dead screen and the
constant whose last reader it was — a better answer than keeping two copies in step.

```sh
grep -rn "StorefrontAppDrawer" --include="*.kt" . | grep -v build   # expects: no output
```

### 13. Named type sizes — NOT A CLEANUP. Do not do it as written.

The item says "26 distinct `.sp` literals across 68 files", scoped to "six named sizes". Both
halves are wrong in a way that matters.

**The count conflates three properties.** Of the `.sp` literals, 334 are `fontSize`, 19 are
`lineHeight` and 8 are `letterSpacing` — and every one of the sub-5sp values the count treated as
a tiny font size (`0.8`, `1`, `1.4`, `1.6`, `2`, `2.4`) is letter spacing. There are about 24
distinct font sizes, not 26 of anything.

**And collapsing them is a redesign, not a rename.** The sizes are a smooth ramp:

| size | uses | | size | uses |
|---|---|---|---|---|
| 12sp | 105 | | 15sp | 32 |
| 13sp | 47 | | 10sp | 24 |
| 14sp | 45 | | 16sp | 15 |
| 11sp | 43 | | 18sp | 13 |

Six names cannot hold twenty-four values without changing about 150 call sites' actual appearance
— merging 13 and 14 alone moves 92 of them. That is a typographic pass someone should decide to
do, not a tidy-up that falls out of naming things.

**What is worth doing**: name the eight sizes that carry 89% of the usage and adopt them in new
code. Retrofitting the existing 334 call sites buys nothing until the ramp itself is a decision.

```sh
grep -rhoE "fontSize\s*=\s*[0-9]+(\.[0-9]+)?\.sp" --include="*.kt" feature/ core/ | sort | uniq -c | sort -rn
```

### 14. Four tab treatments — THREE, and the rule is the surface

One of the four is not a tab treatment. The "detail pills row" is
`PillActions.kt`, whose own header calls it "a short row of ACTIONS" — Details, Favorite, Open
with, Collection — each dispatching a context-menu id. It picks no view.

The three that do pick a view already follow a rule, and it is the SURFACE they sit on:

| row | look | sits on |
|---|---|---|
| App Drawer filter row | text + animated 2dp accent underline, no fill | the screen's own chrome |
| `DetailPanelStrip` | soft capsule of light on the current, bare labels either side | over artwork |
| Artwork Studio destinations | accent-filled chip with a border, scrolling, LB/RB at both ends | over a media scrim |

**Underline on chrome, filled shape over media** — and the reason is written at
`DetailPanelStrip`: a filled shape stays readable "over artwork it does not control", which an
underline does not.

What is genuinely left is narrow: the capsule and the chip are two filled looks for the same
situation. Converging them changes the appearance of one of them, and the Studio's follows an
approved mock — so it is a design decision, not a cleanup.

### 15. The subtitle slot does four jobs — REAL, and gated on the data layer

The slot carries counts ("3966 tracks"), instructions ("Browse by who made it"), status
("Now Playing · artist") and content lists ("Recently Watched, Favorites & Playlists").

The inconsistency is narrower than "four jobs" and it is inside ONE column. Photo, Video and Books
use counts throughout. **Music does not**: Songs says a count, while Artists, Albums and Playlists
say what the row is for. The counts exist — the Artists screen prints them.

**It is not a string edit.** `musicRootSections()` is a pure extension on `XMBUiState` and sees
only folder rows carrying a pre-aggregated `trackCount`; it has no track list. An artist count is
worse than a lookup: `artistGroups()` counts MEMBERSHIPS, not tracks, because a duet is one track
and two rows, and it needs the solo-credit evidence pass to split joint credits at all. Albums and
Playlists are cheaper, but doing those two and not Artists puts a new inconsistency inside the
same column.

**What it needs:** artist / album / playlist counts pre-aggregated in the data layer beside
`trackCount`, so the column builder stays pure. Then all four rows say how many, like every other
media column.

### 16. The status strip's centre is unlabelled — OPEN

It says "All" or "Title" with no cue which knob it is, while the hint bar directly below says
"Filter" or "Sort".

---

## New work, raised after the review

### N1. Wire `tools/stale-comments/check.py` — DONE

```sh
./gradlew staleCommentsSelftest staleComments
```

**Baselined, not absolute.** The detector finds 48 references today and most are legitimate:
framework symbols (`[WiFi]`, `[ENV]`), deliberate history ("Was `contextRailOnly`") and
placeholder shapes (`openXxxContextMenu`). A check that reports fifty things already decided is
one nobody reads — which is how it ended up wired to nothing. What is worth failing on is a NEW
one: a comment naming something the same change just deleted, which is the mistake this codebase
actually repeats. It caught one of mine the same hour it was wired.

Baseline keys are `file::symbol`, not line numbers, so code moving inside a file does not
invalidate an entry. Falsified both ways: a fresh stale reference fails the build and names the
file and symbol; removing it passes again.

### N2. Responsive layout — the tablet dead space

Measured on the real tablet (NP05J, 2400×1504 @ density 360 = **1067×668dp landscape**) on
2026-09-25, `1.11.1-debug`:

| screen | empty |
|---|---|
| App Drawer, APPS tab | content ends ~444dp; hint bar starts 634dp → **190dp** bare |
| Settings root | 7 rows end ~430dp → **204dp** below, and the list column is ~522dp of 1067 → **~545dp** to the right |
| XMB home | 4 categories, 2 rows on a 1067×668dp panel |

One cause: fixed-dp layouts authored against the 822×462dp handheld, on a panel 1.45× taller.
Nothing clips and nothing overlaps — it reads sparse.

**The drawer's emptiness is list-dependent, not structural.** On the GAMES tab, where "Everything
else" holds all 66 apps, the 6-row grid fills the panel properly and there is no dead band. The
APPS tab looked empty because its "rest" list is five items. Measure a tab with a long rest list
before changing anything there. Settings and the XMB home are not list-dependent in that way. `StudioGridCapacity` is the pattern to copy:
it sizes by measurement, `MIN_COLUMNS 3 / MAX_COLUMNS 8 / MAX_ROWS 6`, and its stated rule is
"a larger screen gets more columns and rows, never bigger tiles".

**Constraint:** `GridCells.Adaptive` breaks D-pad navigation on every device unless the measured
column count is fed back to `moveSearch` / `gridMove`. That feedback is the work; the grid change
is the easy half.

### N3. Performance on hardware

Owner's framing: *"make sure this app is performance rated for all devices — the wave stops when
another app is up."* The animated background should not run while the launcher is not foreground.
Unmeasured; nothing here is verified yet.

### N4. Capacitive keyboard as a swipe surface

The Titan Elite's keyboard reports `KEYBOARD | TOUCH | TOUCH_MT` on `sub_touch`. Owner wants it
usable for swipe gestures. Unscoped.

### N6. The known-bad test — ACCEPTED, not fixed

`DisplaySettingsViewModel*Test` flakes under full-suite load, moving between classes — it has
timed out in `FontColorTest` and in `GameBootTest`, both at the 60s wall clock. Owner's call on
2026-09-25: **ignore it.**

Recorded so nobody re-diagnoses it from scratch: the race is DataStore's, not the ViewModel's.
All four test classes now inject the ViewModel's dispatcher, which removes half of it; `pfpDataStore`
keeps its own scope and threads, which is the half that still bites. Raising the budget is not a
fix — it went 10s → 60s once already and still times out.

**It is a flake, not a red test.** One green sweep says nothing about it in either direction.

### N7. Unresolved flag

`10-music-browser.png` showed the status strip while `musicBrowser` sits in `fullscreenOverlay`,
which should suppress it. Not retested — the tablet has no music library.

### N8. The Last Played shelf's Play control — NOT MISSING, closed

Recorded here because the earlier entry was wrong and someone will otherwise re-find it.

`LaunchSpine` was deleted in `6e411c41` and I logged it as "the shelf now has no touch route to
launch at all". It has both routes:

- **Confirm launches.** `XMBUiState.enterOpensAppDrawer` excludes the shelf in as many words —
  "the Last Played shelf, where confirm launches what you were playing and is the main verb on the
  screen". Pad A, D-pad centre and Enter all land there.
- **Touch has the footer.** `primaryVerbFor` gives the shelf's focused game "Play" (or "Details"
  when Direct Launch is off), and the XMB hint bar is dispatched (`onAction = onPromptTapped`), so
  the prompt is pressable.

The spine was a second control for a verb the footer already carries. What the deletion actually
left behind was stale prose, which is cleaned up.
