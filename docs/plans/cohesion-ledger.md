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

### 3. `PfpHintBar` migration — PARTIAL

The review found three screens building their own footer, one hiding it, and the detail pages
drawing an inert copy. `PfpDetailHelperFooter` now takes `onAction`; `MusicBrowserScreen`,
`MusicPlayerScreen`, `ArtworkStudioScreen` and `VideoDetailScreen` are on `PfpHintBar`. Eleven
screens outside core-ui now call it.

`AppDrawerScreen`'s bar is **not** the holdout the review called it. `hintAlpha` now goes to 0
only for `state.confirmUninstall`, with a stated reason (a confirm dialog naming the drawer's keys
would name the wrong ones); the options menu rewrites the bar instead of removing it, exactly as
the XMB rail does. Verified in code (`AppDrawerScreen.kt:385-396`) and on the tablet on
2026-09-25 — the drawer's menu frame shows `Back Close | Tap Select`.

Left open:
- `VideoPlayerScreen` has never been checked.

```sh
# Screens that CALL it — the "(" excludes imports, which a bare name match counts as adopters.
grep -rl "PfpHintBar(" --include="*.kt" feature/ | grep -v Test          # 11 on 2026-09-25
grep -n "hintAlpha" feature/feature-appbar/src/main/kotlin/com/psplauncher/feature/appbar/AppDrawerScreen.kt
```

### 4. `HintBarHeight` honoured like `StatusStripHeight` — PARTIAL

Rule 13, exactly: two constants that must agree, one guarded. `HintBarHeight` was reserved by exactly **one**
screen, so whether content ran under the bottom band was decided per screen by accident.

Today: **7 reserve the top band, 3 reserve the bottom.**

```sh
# A reservation is a padding/height use. Filtering out imports AND comments matters here:
# counting raw matches gives 21 and 7, which reads as "nearly even" when it is 7 against 3.
# This prints 8 lines and 3. Seven of the eight are reservations; the eighth is the alias below.
res() { grep -rn "$1" --include="*.kt" feature/ | grep -v ":import" | grep -vE ":\s*(//|\*)" | grep -v Test; }
res StatusStripHeight; res HintBarHeight
```

Reserving both: `ContextMenuOverlay.kt:70`, `SearchScreen.kt:152-153`, `XMBShell.kt:1593`.
Reserving the top band only — **the gap**: `AppDrawerScreen.kt:301`, `SettingsScaffold.kt:1139`,
`GamePickerScreen.kt:117`, `AppPickerScreen.kt:114`.
(`XmbStatusStrip.kt:582` is the `StripHeight` alias, not a reservation. It is why the naive count
says 21.)

### 5. Two test holes — HALF

- `LetterJumpTest`'s `returnIndex` assertion — **CLOSED.** The fixture now opens at
  `anchors[cRung].index + 1` and an `assertNotEquals` keeps the two values apart, so the assertion
  can fail. It was comparing a value to itself.
- `promptedKeys` derived from `kbLabels` — **OPEN.** `kbLabels` has 14 entries;
  `KeyboardPromptsAreBoundTest.promptedKeys` hand-lists 11. Unchecked: `DPAD_ALL`, `START`,
  `SYSTEM`. The hole is exactly where the bug is: `START` prints "F1", there is no `KEYCODE_F1` in
  `DEFAULT_BINDINGS`, and `GamepadAction.HOME` — the Apply verb in four pickers — is bound to
  `KEYCODE_BUTTON_START` and nothing else. **Deriving the map will turn this test red, which is
  the correct outcome**: the keyboard footer currently tells you to press a key that does nothing.

```sh
grep -rn "promptedKeys" --include="*.kt" . | head -1     # find the test, then read the map
sed -n '/val kbLabels/,/^\s*)/p' core/core-ui/src/main/kotlin/com/psplauncher/core/ui/components/ControllerButtonGlyph.kt
```

---

## Open

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

### 7. One search field — OPEN

`PfpSearchField` (40dp pill) has three adopters. Two screens still roll an `OutlinedTextField` at
`RoundedCornerShape(10.dp)`: `SearchScreen.kt:196` and `MusicBrowserScreen.kt:150`.
`PfpSearchField.kt`'s own header says "A third copy would have learned it a third time" — there
are three copies of the behaviour, two of them not using it.

### 8. One placeholder grammar — OPEN

`"Search apps"` names its scope (`AppDrawerHeader.kt:56`, `AppPickerScreen.kt:233`). The other two
just say `"Search"` (`SearchScreen.kt:196`, `MusicBrowserScreen.kt:150`). Falls out of item 7.

### 9. Title-case the Artwork Studio's nine prompts — OPEN

`ArtworkStudioScreen.kt:862-877`: `sources`, `close`, `browse / pick file`, `back`, `check`,
`preview / apply`, `apply`, `search`, `options`. Every other screen title-cases its prompts. It is
also the only screen with slash-compound prompt labels.

### 10. One B label per kind of dismissal — OPEN

Four in use: "Back", "Close", "Cancel", plus lowercase "back"/"close" in the Studio (item 9).
"Apps" at the crossbar root is documented and defensible; the other three are not.

### 11. One empty-state grammar — OPEN

Three forms for the same event:
- `XMBViewModel.kt:3341 / 3588 / 4210` — "No X found"
- `LibrarySearch.kt:34-50` — "No X yet", with a concrete next step
- `XmbNotificationBar.kt:183` — a third form

Videos has both of the first two, in two different files.

### 12. Five App Drawer empty strings live in two files — OPEN

`AppDrawerScreen.kt:432-437` and `storefront/StorefrontAppDrawer.kt:828-833`.

### 13. Named type sizes — OPEN

26 distinct `.sp` literals across 68 files. The back chevron is 16sp on the detail page and 18sp in
two others; a list row title is 15sp, 16sp and 18/22sp in three places; a section heading is 9sp,
11sp and 13sp.

Scope deliberately bounded: **six named sizes in core-ui and the twenty highest-traffic call
sites**, not all 68 files.

```sh
grep -rho "[0-9]\+\.sp" --include="*.kt" feature/ core/ | sort -u | wc -l
```

### 14. Four tab treatments — OPEN

Four visual answers to "pick one of these views": the App Drawer's filter row, the game detail
page's tabs, the Artwork Studio's tabs, and the detail pills row.

### 15. The subtitle slot does four jobs — OPEN

In the Music column the first row's subtitle is a count ("3966 tracks") and the two beneath it are
instructions ("Browse by who made it" / "Browse by release") — same slot, same column, two
grammars stacked. The counts exist; the Artists screen prints them.

### 16. The status strip's centre is unlabelled — OPEN

It says "All" or "Title" with no cue which knob it is, while the hint bar directly below says
"Filter" or "Sort".

---

## New work, raised after the review

### N1. Wire `tools/stale-comments/check.py` to something

It runs and it self-tests. Nothing calls it. A gradle task or a pre-commit hook, ~10 lines.

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

### N5. `XMBViewModel` is 10,878 lines

Split proposed, never written up. Proposal only — no split without a plan on the table first.

```sh
wc -l feature/feature-xmb/src/main/kotlin/com/psplauncher/feature/xmb/viewmodel/XMBViewModel.kt
```

### N6. The known-bad test

`DisplaySettingsViewModelFontColorTest > white raises no notice at all` times out at the 60s
`eventually()` budget under full-suite load and passes in isolation. `xmb-redesign-handoff-3.md`
already raised it 10s→60s and it still times out. **Raising it a third time is fitting the test to
the machine.** It wants a deterministic dispatcher or a virtual clock.

### N7. Unresolved flag

`10-music-browser.png` showed the status strip while `musicBrowser` sits in `fullscreenOverlay`,
which should suppress it. Not retested — the tablet has no music library.

---

## What the review could not answer

- Whether the sound-slot split matters. Nobody has heard the app.
- Every frame in the review set is the Konker at `uiScale` exactly 1.0, so no screenshot in it
  could show a scale-dependent defect. The tablet pass on 2026-09-25 covered that gap for the
  chrome bands; it did not cover the media browsers or the letter rail (no library on that device).
