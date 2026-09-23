# Polish council, 1.4 baseline

Four reviewers read the 1.4 tree on 2026-09-20: a PlayStation UI/UX lens, an XMB
mechanics lens, an emulation-frontend lens, and a visual-language lens. Scope was
Seth's: "this is good and a very solid baseline, right now it's polish and
cohesiveness."

> **The screenshots this document cites no longer exist.** `settings-rail.jpg`,
> `game-detail.jpg`, `library-search.jpg`, `last-played-bar.jpg` and `memory-card-menu.jpg` were
> deleted on 2026-09-23 when `docs/screenshots/` was refreshed — they were captures of the 1.4 and
> 1.5 UI, which is the point of citing them here and also the reason they had to go. The
> observations stand as written; the evidence for them is in the repository's history at
> `v1.5.0`, not in the working tree.

Every item below was re-checked against the source before it was written down.
The **Verified** marker means I ran the grep or read the lines myself, not that a
reviewer asserted it. Items without the marker are reviewer-reported and still
need a check before anyone acts on them.

---

## Tier 1, defects with a named wrong behaviour

### 1.1 The AlertDialog conversion is not finished. Two survived. (Verified)

`StorefrontAppDrawer.kt:790` (uninstall confirm) and `ThemesSettingsScreen.kt:125`
("Save Current Look as Theme", text entry) still call Material3 `AlertDialog`.
Both spell it fully qualified as `androidx.compose.material3.AlertDialog(`, which
is why a name-based sweep missed them.

By the measurement recorded in `PfpOverlayCard.kt:37-44`, both are controller
dead: no A, no B, no D-pad, and system Back only closes the keyboard.

The uninstall dialog also inverts the app's own destructive rule. Red "Uninstall"
is the `confirmButton`, Cancel is the dismiss, and the red is hardcoded
`0xFFFF6B6B` rather than the shared destructive token.

The Themes comment at `:122` says the block "reuses the app's rename-dialog
pattern". That pattern was replaced in 1.4, so the comment is now false and it is
sitting on live code.

Fix: `PfpTextPromptOverlay` for Themes, `SettingsConfirmOverlay(destructive = true)`
for uninstall, and correct the comment.

### 1.2 Eleven dead `AlertDialog` imports in feature-settings. (Verified)

`ArtworkSettingsScreen`, `AudioSettingsScreen`, `BooksSettingsScreen`,
`CategoryManagerScreen`, `CollectionsSettingsScreen`, `DisplaySettingsScreen`,
`EmulatorAssignmentScreen`, `EmulatorsSettingsScreen`, `LibraryManagerScreen`,
`MusicSettingsScreen`, `VideoSettingsScreen`.

I checked each one. Zero call sites. The only other mention anywhere is the
comment at `DisplaySettingsScreen.kt:671`, which correctly says the picker is
hand built *instead of* an AlertDialog and should stay.

These imports are why 1.1 stayed invisible. Deleting them makes the next
regression loud.

### 1.3 The focused game goes nameless for about half a second. (Verified)

`XMBShell.kt:871` passes `focusedLogoVisible = pic0Alpha > 0f`. `pic0Alpha` starts
a `tween(500)` at t=650ms, so on its second frame the value is about 0.002. At
`XMBItemList.kt:641`, `showGameText` flips false the instant that predicate turns
true, so the row title disappears while the logo is still at 0.2 percent opacity.
Between roughly 650ms and 1150ms the focused game has no legible identity.

The comment directly above that line states the intent the code breaks: "a row
must never be nameless."

Task 2.1 fixed the permanently nameless case and opened a shorter one.

Fix: cross the two rather than cutting. Give the title `alpha(1f - pic0Alpha)`,
or at minimum gate on `pic0Alpha > 0.5f`. One line, and it closes open item 2.2
without needing a device session, because this is a frame-order defect readable
from source rather than a question of taste.

### 1.4 `reScrapeAllGames` deletes before it fetches. (Verified)

`ArtworkRepository.kt:267` calls `clearAllArtwork()` and only then enters
`fetchForGames`, which sleeps `delay(500)` per game. At 147 games that is about
73 seconds of sleep before network time. Cancel, crash, or hit the ScreenScraper
quota partway and the library is left with artwork deleted and not replaced, with
no resume.

The correct shape already exists eight lines down: `scrapeMissingOnly` clears per
game, immediately before that game's own fetch. Making re-scrape-all match it
means a cancelled run leaves N games re-scraped and the rest untouched. Keep the
500ms, it is politeness to the provider. It just must not be paired with an
up-front wipe.

### 1.5 `categoryCursor` stores an index while its two siblings store an id. (Verified)

`XMBViewModel.kt:2897` is `mutableMapOf<String, Int>()`, written at `:6820` and
restored at `:6822` as a bare index.

`cursorAfterRefresh` (`:997-1001`) and `refreshMusicRootPreservingCursor` (`:2336`)
both relocate by row id, and `cursorAfterRefresh`'s own KDoc explains why: "Keeping
the INDEX would leave it on whichever game moved into that slot."

This is Rule 13, a pair where only one side is guarded. 1.4 made it bite: Last
Played re-sorts after every launch, so leaving the column and coming back restores
a slot that now holds a different game.

---

## Tier 2, cohesiveness

### 2.1 Sixteen of twenty-two README screenshots predate the Settings redesign. (Verified)

Sixteen are from 2026-07-08, six from 2026-09-20. `library-manager.jpg`,
`theme-settings.jpg` and `theme-my-themes.jpg` show full-bleed rows, a top-right
"Back" and helper text under every row. `settings-rail.jpg` shows the same Library
Manager screen with a left section rail, indented rows and a bottom-centre hint
pill.

All settings screens go through `SettingsScaffold`, so the old shots are stale
rather than a second live layout. README.md:41-63 puts both in the same tables, so
the repo's own answer to "does this look like one app" is currently no, for a
documentation reason.

`game-detail.jpg` is separately stale: it shows the fixed green Play button that
`DetailScaffold.kt:83-86` records as removed, plus the Android nav bar.

Cheapest high-value fix in the whole review. Re-capture on the device.

### 2.2 Secondary text is four colours across eleven files.

- `0xFFC9C7E8` lavender: `SearchScreen.kt:55`, `MusicBrowserScreen.kt:58`,
  `MusicTrackPicker.kt:45`, `MusicPlayerScreen.kt:50`, `GamePickerScreen.kt:132`
- `0xAAEEEEEE` grey: `AppDetailScreen.kt:97`, `CollectionPickerPanel.kt:52`,
  `VideoDetailScreen.kt:83`, `PhotoViewerScreen.kt:67`, `MetadataPreviewPanel.kt:43`
- `0xAAC8DAF2`: `XMBItemList.kt:147`
- `0xAAB8C6E0`: `GameDetailScreen.kt:121`

`PfpTextColors.kt` already documents this exact target and the seam landed. Only
`SettingsScaffold` reads it. The remaining work is re-pointing eleven module-level
constants, not a redesign.

Worst symptom, and the reason this ranks above the rest of the colour drift:
`SearchScreen.kt:54-55` hardcodes white primary text over a theme-derived
gradient, while `DetailScaffold.kt:58-59` states the opposite law and
`DetailPalette.kt:51` already flips to dark text for pale schemes. On a pale
Color Scheme the 1.4 search screen is white on pale.

### 2.3 "Focused" is drawn five ways, two of them on 1.4 screens.

- fill plus 1.5dp bright edge: `menuCursor()`, about twenty files
- fill plus 1.0dp edge at radius 7dp: `MusicBrowserScreen.kt:230-232`,
  `MusicTrackPicker.kt:191-193`
- fill, no edge: `SearchScreen.kt:180`
- neutral white plate, no hue: `SettingsScaffold.kt:325-327`
- full inversion: `PfpDetailLaunchButton`

The Settings plate is argued in the code and should stay. `SearchScreen` and
`MusicBrowserScreen` differing from `menuCursor()` and from each other is drift:
they are otherwise copy-siblings with byte-identical text-field colour blocks,
and they still diverge at 15sp versus 16sp titles and 12/9 versus 14/11 row
padding.

### 2.4 Three hint bars name physical buttons in prose.

`ArtworkStudioScreen.kt:1133` ("Up/Down Move, A Select, X Edit title, B Back"),
`CollectionPickerPanel.kt:82`, `ManualViewerOverlay.kt:194`.

`ArtworkStudioScreen` imports `ControllerPromptBar` and uses it elsewhere in the
same file, so one screen says "B" in text and draws the real glyph fifty lines
away. These strings go wrong the moment Confirm/Back is swapped or a PlayStation
pad is selected, which is precisely what `ControllerPrompt` exists to prevent.

Related: nine screens including `SearchScreen.kt:158-165` call `ControllerPromptBar`
directly rather than `ControllerHintBar`, whose docstring promises one prompt look
app wide. Three footer treatments are visible across `settings-rail.jpg`,
`library-search.jpg` and `last-played-bar.jpg`.

### 2.5 Terminology.

Counts are Title Case in the XMB (`XMBViewModel.kt:4722, 4734, 4749, 4781` give
"3 Games") and lowercase in Settings (`CollectionsSettingsScreen.kt:202` gives
"3 games"). Confirm is "Open" on `SearchScreen.kt:158` and "Enter" on
`SettingsScaffold.kt:368`. Dismiss defaults to "Close" in `PfpOverlayCard.kt` and
"OK" in `SettingsOverlays.kt`, same role, same card.

### 2.6 `DetailMenuRowView` duplicates `PspContextMenuRow`.

`DetailContextMenu.kt:170-201` against `PspContextMenu.kt:141-186`: same gradient,
same 16/15sp, same four colour branches including the same `0xFFFF7070` and
`0xAAFF7070`. Only the checkmark differs. Two copies that must stay in step with
nothing guarding them.

### 2.7 The options flyout goes translucent under a wallpaper theme.

`PspContextMenu.kt:100` fills the panel at `waveColor.copy(alpha = 0.75f)` with
unselected rows at 0.62 alpha. The comment defends it as letting the wave show
through, which is fair over a smooth gradient. `memory-card-menu.jpg` shows "Social"
and "Final Fantasy" reading through "Refresh Metadata" under Vaporwave.

Fix: composite the panel colour over an opaque base, `composite()` already exists
in `TextLegibility.kt`. Leave the scrim light, the scrim is the part that should
stay XMB-like.

---

## Tier 3, scale and correctness, not visible at 147 games

- **Auto-Detect drops unrecognised folders silently.**
  `RomRootDiscoveryScanner.kt:63-64` is `?: continue` twice and neither is counted.
  `Report.skipped` only counts baseline-read failures, so the user sees "Scanned N
  folders, no new ROMs found" and is never told which. `dos`, `ports` and `scummvm`
  resolve in `PlatformFolderHintResolver.kt:273-276` but are absent from
  `PlatformSeeder`, so they die on the second rung. Add `Report.unrecognised` and
  name them.
- **`UnmatchedRomDao` has no production callers.** (Verified) Entity, DAO, table
  and DI provider exist; nothing writes it. `ScanResult.Complete.unmatched` is
  computed and discarded. The backup-drift note claiming it is "rebuilt by the
  next scan" is therefore false, which is worse than absent. Wire one screen onto
  `observeAll()` or delete the table and the note.
- **Search truncates at 40 per library and says nothing.**
  `SEARCH_RESULTS_PER_LIBRARY = 40`, applied in repository title order with no
  relevance ranking. No query reaches 40 at this library size.
- **Platform is not in the search haystack.** `rebuildSearchRows:3804` matches
  title, developer, publisher, but the row already prints the platform at `:3915`,
  so "psx castlevania" returns nothing.
- **Global search cannot find an installed app.** `observeAllGames` filters
  `content_type = 'GAME'`. README 4.10b promises Select "searches everything", and
  on a home launcher the thing you cannot find is an app. The App Drawer's own
  search is a second surface behind a different button.
- **Search re-normalises every row per keystroke on the main thread.** Fine at 147
  games; normalise once in `openSearch`, where the snapshot is already taken.
- **Jump-to-letter is still absent**, and the scoped Search row exists only at a
  column root, so inside All Games the only vertical tool is a held D-pad.
- **`XMBViewModel.kt:5398`'s `CHANGE_SORT` is dead**, matched already at `:5393`.
  (Verified) Harmless today. It is the shape where someone later edits the wrong
  branch.
- **README 2.5 drift.** It tells the user to go run Auto-Detect after the wizard;
  `InitialSetupViewModel.kt:323/335` already calls `romRootScanRunner.kickoff()`
  when a root is granted. The app is better than the manual says.

---

## Conflicts, resolved rather than averaged

**The Search row's position.** The PlayStation reviewer wants it off index 0:
Game is the default category, so every cold start opens with the cursor on
"Search Games" rather than on the library, and Sony never heads a column with a
utility. The emulation reviewer wants *more* search, including inside All Games.

These are not actually opposed. Keep the row where it is for touch discoverability
and add the drilled-in one, but seed `selectedItemIndex` to the first non-SEARCH
row so a cold start lands on content. Select already reaches global search from
anywhere, which is what makes the row an affordance rather than the route.

**Open item 2.2, the 650ms name delay.** Recorded as "confirm on device first".
That is moot. Item 1.3 above is a frame-order defect readable from source. Close
2.2 by fixing 1.3.

**Open item 2.3, the hard vertical cursor cut.** The recorded premise, that the
per-row scale spring cannot help because slot 0 is always the selected slot, is
now half wrong. Since task 5.2 added `key(items[i].id)` at `XMBItemList.kt:471`
the incoming row keeps its state and its scale really does animate. The outgoing
row does not, because it crosses from the below-bar `Column` into the separate
above-bar `Box`, a different call site no key can bridge. So a step today reads as
"new row grows in, old row jumps."

The real fix is to make `sel` fractional at `:456` and render one extra row. The
caveat the old plan does not state: the glide must be cadence-adaptive and shorter
than the live repeat interval of 50 to 110ms, or a fixed tween lags permanently
under a held direction. The deleted `CenterLockedColumn` had exactly that shape at
70 to 240ms. That reasoning was worth keeping even though the code was not.

**Deferred item, "category change should use `slideInHorizontally`." Reverse it.**
The current `slideInVertically { it / 8 }` plus fade at `XMBShell.kt:840-846` is
closer to the hardware's fan-out-from-the-bar than a horizontal carry. The axis
mismatch that genuinely exists is a horizontal bar that glides against a vertical
column that snaps, which is the item above, not this one.

**New, and nobody had it on a list: drill in and out is a total hard cut.**
(Verified) `XMBShell.kt:815` is a plain `if (uiState.drillTitle != null)`. The
flyout replaces the `AnimatedContent` branch entirely, so the drill gets no
transition at all, and `hShift` at `:805-809` jumps from `maxWidth *
barLeftFraction` to `DRILL_CROSSBAR_LEFT_MARGIN - columnBaseInset` in one frame.
The whole cross teleports to the left edge while a second column appears from
nothing. On hardware the sub-column slides in from the right as the parent
collapses. More visible than the vertical snap.

---

## What every reviewer said to leave alone

- The in-window dialog doctrine. The `dispatchKeyEvent` reasoning at
  `PfpOverlayCard.kt:37-42` is correct and measured. Finish it, do not revisit it.
- `ControllerPrompt` resolving action to physical button to family art from the
  same `GamepadMappings` the input handler reads, consumed by 21 files. A footer
  cannot disagree with the pad. Best-built subsystem in the repo.
- The confirm/cancel contract, including `ConfirmBackLayout.REVERSED` moving the
  action rather than the position.
- The cross geometry. Derived rather than eyeballed, and the selected row lands at
  48.6 percent of height.
- The cursor never moving on either axis, and the asset ladder's staging: ICON0 at
  0ms, backdrop at 220ms then a 320ms crossfade, PIC0 at 650ms then a 500ms tween,
  ICON1 at 1500ms. Every delay exceeds the fastest held-repeat interval, so
  scrolling spins up nothing.
- `visibleCategories` dropping categories right of the active one on drill-in.
- Empty states as 0.5-alpha list rows rather than centred illustrations, and
  `emptyRecentlyPlayedItem`'s wording in particular.
- `PfpConfirmOverlay`'s stacked buttons with Cancel leading and destructive tinted
  at rest.
- Settings as one row plus the rail, and its deliberately colourless focus plate.
- Last Played never sorting and nothing being assignable to it.
- The emulator ladder reporting which rung won and refusing rather than
  substituting, plus `unresolvableMessage`.
- `NEVER_FOREGROUNDED`, lifecycle-verified launch outcomes with no usage-stats
  permission. It is what makes Last Played honest.
- `DiscSetBuilder`'s folder-in-the-key rule and the Parasite Eve II reasoning.
- The three-way search empty state, and blank-query-matches-nothing.
- ES-DE `downloaded_media` as the native on-disk layout, with no export step.
- The wave only ever lightening the gradient, so theme colour always survives.

---

## Two things the reviewers flagged as false statements in the tree

Both are Rule 8 problems, a stale comment sitting on live code:

1. `ThemesSettingsScreen.kt:122` claims to reuse "the app's rename-dialog pattern",
   which 1.4 replaced.
2. `XMBItemList.kt:286` says "every game card keeps its [Title] / {Platform
   (Emulator)} label". False since `c1367d8b`: `:641` gives unselected real games
   no label anywhere, drill included.

Also noted: the drill column never receives `focusedLogoVisible`, so inside a
drill the active game shows its title *and* its PIC0 logo, while at the root the
logo replaces the title. Task 2.1 asked for one predicate at both sites.
`hasVisibleLogo` unified the logo gate; the title gate still has two behaviours.

---

# Second pass, 2026-09-20 evening

Fourteen fresh screenshots were captured off the live device (AYN Thor, 2400x1504
at 360dpi, **empty library**: 0 games, 0 ROM roots, no saved themes). They sit in
`docs/screenshots/_review-2026-09-20/`. Nothing in `docs/screenshots/` was
overwritten, because the fresh Settings captures show empty-state screens and
would be a downgrade for the README. Re-capture for the README needs the
populated handheld.

The four reviewers were resumed with their original context and asked for deltas.
A fifth, an art director, was given the question Seth actually asked: what makes
this one uniform unit, modern and sleek, a quality-of-life update of the XMB.

Same rule as above: **Verified** means I ran the check myself.

## The answer to the question: two systems, not four

The art director's framing, which I agree with and which the other three reports
support:

> The wallpaper is the only surface. Nothing is a box. Focus is the one thing in
> the frame that is fully bright, fully saturated and slightly larger than
> everything else, and it is the **content** that brightens, never a container
> drawn behind it.

The app currently runs four systems: the cross, the panel, the page, and Material
leftovers. The cross wins and is not up for debate, because it is the only thing
here nobody else has. But it cannot govern a settings list, and
`SettingsScaffold.kt:1558-1568` argues that correctly. So: the cross governs home
and drill; the panel and the page are the same system and merge; the Material
leftovers get deleted.

## The spine

### S1. One focus token, one focus geometry. (Verified in part)

`menuCursorEdge()` is the most-adopted decision in the repo at about 20 files.
But `Modifier.menuCursor()`, which pairs it with a fill, a 1.5dp width and an 8dp
radius, is used by only a handful. The rest re-roll the geometry: 1dp at 7dp
radius, 1dp at 8dp, 1.5dp at 8dp, 2dp at CircleShape, 10dp in settings, and
`SearchScreen.kt:180` paints the fill with **no edge at all**.

The colour was never the problem. The geometry drifted. Mechanical fix, the
composable already exists.

### S2. Focus must not move the layout. (Verified)

`SettingsScaffold.kt:1596-1597` changes `fontSize` on focus:

```kotlin
fontSize = if (rowSelected) XmbLayoutSpec.DEFAULT.itemTextSelectedSp.sp
           else XmbLayoutSpec.DEFAULT.itemTextSp.sp,
```

The XMB reads the **same two spec values** but applies the delta through
`graphicsLayer` scale inside a fixed `ROW_HEIGHT`, so it does not touch layout.
Settings has no fixed row height, so every row below the cursor shifts. Measured
across the two settings captures: rows move about 4 to 9px as focus enters and
leaves the content pane. Same intent, two mechanisms, one jitters.

### S3. Delete the dead seam, finish the live one. (Verified)

`SettingsSelectedBg` (`SettingsScaffold.kt:295`) has **exactly one line in the
whole repo, its own definition**. Zero call sites. A second, accent-tinted focus
fill that lost an argument and was never removed.

Meanwhile `LocalPfpTextColors` is read by one feature. Its own docstring states
the end state: about twenty module-level colour constants re-pointed at the
CompositionLocal reaches roughly 400 `Text()` sites without touching any of them.
The constants are already sitting there.

Highest ratio of app unified per keystroke in the repo.

### S4. A spacing grid.

The dp histogram is a continuum: every integer from 1 to 16, then 18, 20, 22, 24,
26, 28. The **type** scale is already solved and nobody noticed: `XmbLayoutSpec`
holds the sizes and both the XMB row and `SettingsRow` read it. Extend the same
spec to spacing and the 19-font-size problem becomes "point the stragglers at the
spec that already exists."

## The icon problem, and the conflict over how to fix it

Three of the five reviewers independently ranked this the loudest break in the
app, and none of them had it in the first pass, because it only shows up when you
see several columns side by side. `AppListIcon` (`XMBItemList.kt:1145-1158`)
calls `getApplicationIcon()` and draws the whole drawable through
`rememberDrawablePainter` with a 6dp clip. (Verified.) That is why Artemis is a
black square and Boosteroid is a purple gradient square sitting in the same slot,
at the same size, as a flat white magnifier.

**Two fixes were proposed. They conflict. I am picking the art director's.**

- The XMB reviewer proposed `AdaptiveIconDrawable.getMonochrome()`, API 33, with
  a guard for minSdk 29 and a fallback to today's icon.
- The art director proposed drawing `AdaptiveIconDrawable.foreground` only,
  scaled about 1.5x for the 108/72 safe zone, on no background, in the same slot
  as every other glyph.

The second wins, for three reasons:

1. **No version guard.** `foreground` is API 26 and `app/build.gradle.kts:33` is
   `minSdk = 29`. (Verified.) `getMonochrome()` is API 33, so on this device it
   would be a guarded path with a fallback that is the common case, because few
   apps ship a monochrome layer.
2. **It matches what the hardware did.** Sony never tinted third-party art. PSP
   and PS3 `ICON0.PNG` is a fixed 144x80 rectangle; the Vita masked every icon
   into one round-rect. The precedent is containment by **shape**, not by colour.
3. **It does not cross a line the codebase already drew.** `PortalIcon`'s
   docstring says app icons keep their own colours. Drawing the foreground layer
   changes the container, not the art: Boosteroid becomes a white-and-blue
   magnifier on the wallpaper, Artemis becomes its asterisk, and the Play Store
   keeps its colours because its foreground **is** the coloured triangle.

Both reviewers agree on one thing and it is worth recording: **do not blanket
desaturate.** An adaptive icon with a coloured background layer becomes a grey
square, which is worse than colour.

Fall back to today's full drawable when `foreground` is null, for legacy icons.

Also, while in that function: app icons render at 48dp inside a 74dp slot while
silhouettes render at 62dp, and the same function contains 48, 46, 44, 32, 28 and
26dp glyphs. A 2dp difference between 48 and 46 is a typo, not a decision.

## New defects found only because the device was photographed

### N1. Every empty row in the app loses its icon slot. (Verified)

`XMBItemList.kt:1113` ends the leading-icon `when` with
`else -> Spacer(Modifier.width(12.dp))`, and `XMBItemType.EMPTY` appears in that
file exactly once, at `:572`, for alpha only. So an empty row never reaches
`LEADING_ICON_SLOT` and its text begins about 62dp left of every other row.
Predicted 62dp from the code, measured about 59dp in
`xmb-last-played-empty.jpg`.

There are **24** EMPTY-producing sites in `XMBViewModel`, so this is every empty
column in the app. One-line fix, best work-to-effect ratio anyone found.

### N2. The wave animates behind the search screen, invisibly. (Verified)

`XMBShell.kt:474-481` freezes the wave for boot, video, game, photo viewer, app,
drawer and music player. `search` is absent. The comment reasons that
"Settings/dialogs use a see-through scrim, so the wave keeps animating there",
but search's scrim is `SearchScreen.kt:93-97` at alpha 0.78 to 0.93, which is
effectively opaque. The wave's peak contribution is about 0.10 white, so under
0.93 it is about 0.007. Invisible, and still costing frames.

Two fixes, and they are the same fix seen from both sides: drop the scrim so the
wave shows, and add `search != null` to `waveCovered` if it does not.

### N3. The search empty row wears the cursor while being unpressable. (Verified)

`SearchScreen.kt:178` paints `menuCursorFill()` on `selected` with no reference to
`clickable`, and `clickable` is false for `XMBItemType.EMPTY` (`:173`). So "Type
to search" and "No matches" are drawn as the focused row, the brightest band on
screen, while the footer beneath reads "A Open". The crossbar already handles
this correctly, at 0.5 alpha and non-activating.

Two reviewers reached this independently. One called it the biggest fix-to-effect
ratio in the set.

### N4. The Game column's only pointer to Settings can never fire. (Verified)

`XMBViewModel.kt:4760` guards "No consoles configured / Open Library Manager to
add a Memory Card" on `enabledCards.isEmpty()`. An Android Memory Card is seeded
by default and is visible in both `xmb-game-column.jpg` and
`settings-library-manager.jpg`. So the guard is false on exactly the device that
needs it, and a fresh install's Game column reads "Search Games / All Games (Total
Games 0) / Android Memory Card (0 Games)" with nothing pointing anywhere.

`LibraryManagerScreen.kt:249-251` gets the same question right: it filters Windows
out **before** the emptiness test. The XMB does the Windows filter at `:4770`,
ten lines too late. This is the unguarded mirror of a guard that exists.

### N5. "No matches" is false on an empty library. (Verified)

`searchEmptyState` (`LibrarySearch.kt:78`) has three branches, LOADING, PROMPT,
NO_MATCHES, and no fourth for "nothing to search". Its own KDoc argues at length
that saying "No matches" before anything has been read is a claim about a library
nobody looked at. The empty-library case is the identical argument and was not
carried through. `search-no-matches.jpg` shows the result: zero games, query
"zel", and the app says "Nothing here matches that."

This is a detector that is correct for the fixture it was born in.

### N6. The Auto-Detect row does not exist, and the README names it four times. (Verified)

`LibraryManagerScreen.kt` contains **zero** occurrences of `autoDetect` in any
spelling. Auto-detect was deliberately fused into `addRomRoot`
(`LibraryManagerViewModel.kt:640-645`), with a comment saying a new root is not
useful until the pass runs. That is the right design.

Consequences: README lines 64, 223, 282 and 695 send the user to a row that is not
there (line 350's "auto-detect" is about emulator launch settings, a different
thing, leave it). And `onScanRomRoot` is now a dead parameter, declared at `:135`
and `:205`, threaded from `:89` and `:173`, stubbed at `:860`, called nowhere.

### N7. The count-noun drift is one file and one noun family. (Verified)

Seven Title Case sites, all Game/Games or App/Apps: `XMBViewModel.kt` lines 2222,
2250, 4712, 4722, 4734, 4749, 4781. Against fourteen or more lowercase ones:
track, library, video, shelf, book, album, photo.

The first pass blamed an XMB-versus-Settings seam. Wrong seam. It is one file and
one noun family that did not get the memo. Seven edits.

Related, same family: `"Total Games $totalGames"` is not a phrase.

### N8. Focus and selection are 1dp apart on the colour swatches. (Verified)

`core/core-ui/.../components/HsvColorPickerDialog.kt:319-320`:

```kotlin
val ringColor = if (focused || selected) accent else Color(0x66FFFFFF)
val ringWidth = if (focused) 3.dp else if (selected) 2.dp else 1.dp
```

Same colour for both states, separated only by ring width. The swatch is a fixed
34dp and does not grow. There is also a second, different swatch style at
`:209-224` in the same file.

### N9. Capitalisation changes inside one context menu. (Seen in the capture)

`xmb-context-menu.jpg`: nine rows in sentence case, "Launch", "Edit App Details",
"Mark as Game", "Add to Favorites", "Add to Collection", "Hide from Network",
"Hide Everywhere", "Rename Shortcut", against four **contiguous** rows in
every-word caps, "Move To Category", "Add To Category", "Remove From Category",
"Pin To Category". Adjacent means one batch written in one sitting.

The same menu is also twelve to thirteen ungrouped items with no dividers, four of
which are the same Category verb. The app already has the submenu pattern that
fixes this; it shipped in 1.4 for the Add rows.

### N10. The Settings glyph is the only containered icon in the cross.

`xmb-settings-column.jpg`: both rows sit on a filled disc. Every other column is a
flat silhouette. The comment at `XMBItemList.kt:1087-1090` argues Settings needs
an icon rather than a blank rail, which is right. The asset is the problem, not
the reasoning. Redraw it as a bare wrench.

### N11. The colour-scheme picker is bottom-clipped and repeats itself.

`settings-color-scheme-picker.jpg`: the panel runs off the bottom edge, cutting
"Custom / Choose a custom accent color" in half, and eleven rows repeat "Fixed
color preset" verbatim. The one row whose subtitle carries information is the
half-off-screen one. Drop the subtitle except on Custom.

## A conflict I had to settle from the code

**The empty band under the settings footer.** The PlayStation reviewer calls it a
defect. The cohesiveness reviewer **withdrew** the same finding as deliberate
reserved space. Both are half right and neither had both halves.

`SettingsScaffold.kt:391-402`: the reserved height **is** deliberate, so row
geometry does not shift as the hint fades. But `HorizontalDivider` is drawn
**outside** `Modifier.alpha(alpha)`; only `ControllerHintBar` fades. So the result
is a full-strength rule with nothing under it.

Keep the reserved height. Move the divider inside the alpha.

## A finding I rejected

The PlayStation reviewer wants `&& !railFocused.value` added at
`SettingsScaffold.kt:1218` so the help band blanks when the rail takes focus. That
is not drift. It is deliberate and the reason is written nine lines above the line
they cited, at `:913-914`:

> The help band is deliberately NOT affected: it reads cursorVisible directly, so
> it goes on describing the screen you are standing in while you pick a sibling
> out of the rail.

The cohesiveness reviewer independently reached the same conclusion and withdrew
the finding before filing it.

What survives is the other half of the suggestion, which does not contradict the
comment: give each rail entry its own one-line description, so the band describes
the rail item while the rail has focus. Additive, optional, Seth's call.

## Ordered plan

Ordered by how much of the app each step unifies per unit of work.

1. **Re-point the roughly twenty text-colour constants at `LocalPfpTextColors`**,
   and delete `SettingsSelectedBg` in the same commit. Mechanical. Reaches about
   400 text sites and kills the four-secondaries problem in one pass.
2. **Replace hand-rolled focus borders with `Modifier.menuCursor()`**, and add the
   missing edge at `SearchScreen.kt:180`. Mechanical, about eight call sites.
   Unifies every menu, picker and list.
3. **The three one-line layout fixes.** `LEADING_ICON_SLOT` in the EMPTY branch
   (N1); the divider inside the alpha (the conflict above); fixed row height in
   settings so focus stops reflowing (S2).
4. **Adaptive-icon foreground extraction in `AppListIcon`.** Needs judgement: look
   at about ten real icons, decide the safe-zone scale and the legacy fallback.
   Highest visual payoff per line in the repo.
5. **The four onboarding and honesty fixes**: N4, N5, N6, plus naming the folders
   Auto-Detect skipped.
6. **Normalise the leading-icon sizes.** Judgement: 48/46/44/32/28/26 must become
   one or two numbers, and the artwork tiles are genuinely a different shape.
7. **Delete the Material leftovers.** Judgement: the music player's near-black
   sheet with a stock blue slider thumb, and the green Play button whose green
   appears nowhere else. These need a real design decision, which is why they are
   last.

## What the second pass says to take, and to refuse

**Take:** focus that springs rather than cuts, using the same spring the XMB row
already uses. Optical alignment over box alignment, extending the icon-centre
pivot at `XMBItemList.kt:586-597`. A real empty state with its icon gutter held.
Something in the dead band at the foot of the settings screen, or nothing at all.

**Refuse:** cards, elevation and any Material surface stack, because the wallpaper
showing through is the identity and `SettingsScaffold.kt:1000-1012` already fights
to keep it. Backdrop blur, which costs frames on a handheld and the PSP never had.
Rounding the XMB rows, which have no radius and should keep none. Density
increases: 88dp rows on a 462dp screen is five rows, and that is correct for a
controller.

## Added to leave-alone

`XMBItemList.kt:562-600`, the row focus treatment: scale plus alpha plus weight
plus the icon-centre pivot. This is the signature, and every other focus treatment
should be measured against it rather than the reverse.

`XmbLayoutSpec`: one serialisable source for the cross geometry, pixel-matched to
a real PSP capture, already read by both halves of the app. It is the model for
what a spacing grid should look like.

The drill flyout's artwork backdrop. Replacing the wallpaper with the selected
game's own desaturated art is pure XMB thinking.

`PortalIcon`'s exclusion of content imagery. The line between "our glyph,
tintable" and "their art, untouchable" is drawn correctly, and the icon fix above
does not cross it.

The 0.72/0.90 scrim, derived against a contrast floor rather than picked by eye
and pinned by `TextLegibilityTest`.

The wave: two folds at uv.y 0.63 and 0.75, amplitude 0.045 and 0.06, no sparkles.
That is the PSP "Original", not the PS3 ribbon. Drift periods are 16.5s and 20.9s
and are independent of aspect ratio.
