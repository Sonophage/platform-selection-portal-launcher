# Settings, context menus and the recents rail — decisions

Written overnight 2026-09-22 with the device disconnected, so **nothing here has been checked on
hardware**. Every claim about current behaviour comes from reading the code, and the line
references are how to check me.

Answer with the numbers (e.g. `1 · 2 · 1 · 3 …`) and I'll build it.

---

## Part 1 — Settings

### What is actually there

`core-domain/model/SettingsCatalog.kt` holds the whole tree as data: 7 sections, **29 screens**.
`SettingsScaffold.kt:523` draws the rail from it; `XMBViewModel:7720` uses it to decide where the
crossbar's Settings row lands.

Two things I expected to be wrong and are **not**:

- **Focus does not run off the end.** `NavigationContext.moveVertical` clamps with
  `coerceIn(0, focusable.lastIndex)` (`NavigationContext.kt:167`).
- **The lists do follow the cursor.** The rail centres the live row
  (`SettingsScaffold.kt:1421-1434`) and the content scrolls the whole focused row inside the
  viewport, both edges (`SettingsScaffold.kt:683-710`). Both carry comments about bugs already
  fixed there.

I had both of these on my list as outstanding. They are not. I could not reproduce either from the
code, and without the device I cannot test them — if either still misbehaves in your hands, say so
and I'll treat the code reading as wrong.

So settings is not weak on **mechanics**. It is weak on **discoverability**.

### The real problem: 29 screens, no way in except walking

To change one thing you must already know which of 7 sections owns it. There is no search, and
nothing on screen tells you what a section contains before you open it.

And the descriptions **already exist and are never drawn**: every `SettingsEntry` and every
`SettingsSectionId` carries a `subtitle` — 36 hand-written strings — with **no production reader**
(verified untruncated across the repo). The rail draws `title` alone. Meanwhile the crossbar's
Settings row hardcodes its own summary string, "Library, emulators, appearance, media & system"
(`XMBViewModel:9413`), which is a fourth copy of the same idea.

**Decision 1 — those 36 subtitles:**

1. **Draw them in the rail** under each title, dimmed and small. *(Recommended — the writing is
   done, and it makes the rail self-describing. Costs vertical space: the rail already goes to 13
   rows, so this likely needs the two-line row to be section-only.)*
2. Draw them only on section rows, not screen rows. Cheaper on space, most of the benefit.
3. Delete them. Honest, and reversible.

**Decision 2 — settings search:**

1. **A search row pinned at the top of the rail**, filtering all 29 screens by title and subtitle,
   Select from anywhere. *(Recommended — this is what the subtitles are for, and 29 is past the
   point where a tree alone is enough.)*
2. Reuse the global Select-button search instead: add settings screens as a fifth result kind, so
   one search finds games, media *and* settings.
3. No search; improve labels only.

Option 2 is more elegant and more work — it means settings entries flowing through
`XMBViewModel`'s search path and the disc-free activation of a screen rather than a launch.

**Decision 3 — two placements I think are wrong:**

- "Performance" (thermal, battery saver, direct launch) sits under **System**, next to Logs and
  Credits. It is a behaviour setting people look for under Interface or Appearance.
- "Setup Wizard" sits under **System** too, which is where you would look — but it is also the
  thing a stuck first-run user needs, and it is last in a 6-row section.

1. **Move Performance to Interface**, leave Setup Wizard. *(Recommended — smallest change that
   fixes the one I'd actually get lost in.)*
2. Move both: Performance to Interface, Setup Wizard to the top of System.
3. Leave both; the tree is fine.

---

## Part 2 — Context menus

### What is actually there

20 builders in `XMBViewModel`, 111 `XMBContextMenuItem(` constructions. They are already
**conditional** — the game menu alone gates on disc count, collection membership, gaming category,
Android-app-ness, the missing-ROM bucket and per-location hiding (`XMBViewModel:6035-6125`).

So "smarter" is not about showing fewer irrelevant items. That work is done. The problem is
**shape**: the game menu can reach **16 items in one flat list**, and `PspMenuRow`
(`PspContextMenu.kt:50`) only knows `label`, `isDestructive`, `checked`. There is no separator, no
heading, no grouping primitive at all. Sixteen rows on a handheld is a scroll, and the eye has
nothing to anchor to.

### Options

**Decision 4 — grouping:**

1. **Add a separator to `PspMenuRow` and group the game menu** into: open/play · organise ·
   configure · destructive. Purely visual, no behaviour change, ~4 groups.
   *(Recommended — cheapest real improvement, and every menu benefits.)*
2. Groups with headings ("Organise", "Configure"). Clearer, taller — costs more rows than it saves.
3. Keep flat, but reorder so the six most-used actions are always the top six.

**Decision 5 — length:**

1. **Overflow**: show the top ~8, then a "More…" row opening the rest as a submenu. Collections
   already proves the submenu pattern (`openCollectionPicker`).
2. **Promote by use**: track how often each action is chosen and float the top three. Adaptive
   menus are famously disorienting — I'd avoid it.
3. **Leave the length alone**; grouping (Decision 4) is enough.
   *(Recommended — 16 is only reachable in a narrow case: a multi-disc PC game inside a custom
   category in the missing bucket. Typical is 9-11. Fix the shape before cutting content.)*

**Decision 6 — one genuinely missing action.** There is no "Play" / "Launch" in the game context
menu — it opens Game Detail instead, deliberately ("the menu stays navigational",
`XMBViewModel:6043`). With direct launch on, confirm launches and the menu is how you *avoid*
launching, which is coherent. With direct launch **off**, both confirm and the menu go to Game
Detail, so the menu's first item duplicates the button you already pressed.

1. **Add "Play" as the first item only when direct launch is off.** *(Recommended.)*
2. Always show Play first.
3. Leave it.

---

## Part 3 — The recents rail

### What is actually there

`RecentsShelf.kt` merges four libraries by recency. `RECENTLY_PLAYED_LIMIT = 20`
(`XMBViewModel:9336`), applied **after** the merge for `ALL` and after the filter otherwise.
X cycles `RecentFilter` (All · Games · Music · Books · Video).

### Three concrete problems

**A. One album evicts everything.** The limit is 20 rows shared across all four media, ordered by
recency alone. Listen to a 20-track album and the `ALL` shelf is nothing but music — every game,
book and film pushed off the end. This is the one I'd fix regardless.

**B. The filter does not survive a restart.** `recentFilter` is a plain `XMBUiState` default
(`XMBViewModel:792`) with no DataStore key. Set it to Books, relaunch, you are back on All.

**C. Cycling is blind.** X steps to the next filter with no indication of what the others are or
whether they hold anything. Five presses to get back to where you were, and a filter with nothing
in it looks identical to a bug.

**Decision 7 — the eviction (A):**

1. **Per-medium floor**: take the newest N of each medium first, then fill the remainder by
   recency. Guarantees a game is always reachable on the home page.
   *(Recommended — it is what you actually want from a "what was I doing" shelf.)*
2. **Collapse consecutive runs**: 20 tracks from one album become one "Album — 20 tracks" row.
   Nicer, and a bigger change: it needs a grouping concept the shelf does not have.
3. Raise the limit to 40 and leave the ordering alone. Cheap, does not fix it.

**Decision 8 — persistence (B):**

1. **Persist `recentFilter`** to DataStore like every other display preference. *(Recommended —
   two lines plus a backup-list entry.)*
2. Always reset to All on launch; treat the filter as a momentary lens.

**Decision 9 — the blind cycle (C):**

1. **Show the filter as a row of five labels** with the active one lit, dimming any that are
   empty. X still cycles; you can see where you are and where you are going.
   *(Recommended.)*
2. Put counts on the labels ("Games 12"). More useful, more query work per shelf rebuild.
3. Leave it; the footer already names the filter.

**Decision 10 — resume, the thing the rail knows and never says.** Books carry progress, videos
carry `resumePositionMs`, games carry last-played. The rail shows all of them identically, as "this
existed recently".

1. **A thin progress bar across the bottom of a card** that has a resume position. Quiet, and turns
   the shelf from a history into a "continue" list. *(Recommended.)*
2. Text on the hero instead ("42% · Chapter 901").
3. Neither.

---

## Part 4 — Code, for its own sake

No `TODO`, `FIXME` or `HACK` anywhere in `core`, `feature` or `app`. 108,063 lines of main source.

**`XMBViewModel.kt` is 9,436 lines — 8.7% of the codebase in one file.** It is the launcher's
brain, so it will always be large, but it currently owns: category loading for seven columns, all
20 context-menu builders, search, the music browser, the recents shelf, gamepad dispatch, settings
routing, GameBoot, the launch disc, and the preference reads for all of it.

`RecentsShelf.kt` is the proof that slicing works here: a pure function, an exhaustive `when`, its
own test, and the ViewModel just calls it.

**Decision 11 — slicing it:**

1. **Extract the context-menu builders** to `ContextMenus.kt` as pure functions returning
   `List<XMBContextMenuItem>` from an explicit state argument. ~700 lines out, every menu becomes
   unit-testable without a ViewModel, and it is the area you asked to change — so the tests land
   where the work is. *(Recommended, and it is the prerequisite for Decisions 4-6.)*
2. Extract the media columns (music/video/photo/book selection handlers) as well — another ~1,200
   lines, more risk, no device to check against.
3. Leave it until something concrete needs it.

I would not do 2 tonight without hardware.

---

## What I changed while you slept

Committed, suite green, no behaviour change:

- **`ExternalLaunch` collapsed to a boolean.** When the launch disc replaced Video Detail's
  "Launching…" card, both fields of that data class stopped being read — including a
  `PackageManager` lookup run on every external launch to build a player name with no reader. It is
  now `handedOffToPlayer: Boolean`, which is the one job it still had.
- **`SettingsCatalog`'s KDoc corrected.** It claimed feature-xmb builds the crossbar's Settings
  column from the catalog. It does not — the column is two fixed rows in
  `XMBViewModel.SETTINGS_ROOT_ITEMS`, and feature-xmb reads the catalog for the landing screen and
  section lookup instead. I nearly "fixed" a different claim on the strength of a `grep | head -20`
  that had silently truncated the evidence; the untruncated grep is what caught it.
- **The unread `subtitle` fields are now documented as unread**, rather than looking load-bearing.

---

## Part 5 — Build hygiene

A `--rerun-tasks` compile of the whole repo emits **10 warnings**. That is low, but tonight is the
argument for driving it to zero: the unreachable `OPEN_CONTEXT_MENU` branch had been printing
`Duplicate branch condition in 'when'` on every single build, next to a comment warning about that
exact failure mode, and nobody saw it. Warning noise is where the next one hides.

What the 10 are:

- **6 third-party deprecations** — Media3 in `MusicPlaybackService`, two `PackageManager` calls in
  `InstalledAppRepository`, four coroutine `animateTo` overloads in `VideoSnapTranscoder`. Each is
  a real migration with real behaviour to re-check, and I will not do those blind.
- **2 benign** — `{ _, _ -> Unit }` and `.map { Unit }` in the two category repositories. Kotlin
  flags the bare `Unit` literal; the code is correct and clear. Changing working code for a
  stylistic warning is churn, so I left it.
- **1 unnecessary safe call** in `MetadataRepository.kt:180` — a `?.` on something non-null.
  Harmless, mildly misleading.
- **1 fixed tonight** — the duplicate branch.

**Decision 12:**

1. **Fix the 2 benign and the 1 safe call**, leave the deprecations, and treat "zero warnings that
   are ours" as the bar. *(Recommended.)*
2. Do the six deprecation migrations too, with the device back, as one focused pass.
3. Leave all ten.

### One thing I found and deliberately did not delete

`app/build.gradle.kts` declares `ENABLE_PERF_OVERLAY` in both build types — `true` for debug,
`false` for release. **Nothing reads it.** It has been there since the initial commit on
2026-06-18, so it is three months dead.

It reads like an intent marker for a performance overlay you meant to build, and deleting someone's
intention while they sleep to save one generated boolean is a bad trade. So it is still there.

1. Drop the flag.
2. **Keep it** — the perf overlay is still on the list. *(No recommendation; only you know.)*
3. Build the perf overlay.
