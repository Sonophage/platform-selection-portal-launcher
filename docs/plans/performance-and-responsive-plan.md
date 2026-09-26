# Performance, swipe, responsive — the remaining list

Owner's order, from 2026-09-25. Items 1 and 4 are **done and measured**; 2 and 3 are planned here.

Measurements below are from the `konker-elite` AVD (821×462dp @374dpi, API 36) running
`1.11.1-debug`. **Run them again before trusting them** — `tools/emu/emu.sh konker-elite`.

---

## 1. Performance — DONE, and mostly already correct

Owner's framing: *"make sure this app is performance rated for all devices — the wave stops when
another app is up."*

### It already stops. Measured, not assumed.

CPU is `utime+stime` jiffies from `/proc/<pid>/stat` over 10s; frames from `dumpsys gfxinfo`.

| state | CPU (jiffies/10s) | frames/12s |
|---|---|---|
| XMB home, wave animating, **host GPU** | 237 (~24% of a core) | 722 = 60fps, **0% janky**, 19ms p50 |
| Backgrounded (Settings on top) | **2** | — |
| XMB home, wave animating, **swiftshader** | 1012 (~101% of a core) | 147 = 12fps, **100% janky**, 150ms p50 |

Two conclusions:

- **The backgrounded case is solved.** `XMBShell` folds `rememberAppVisible()` (ON_START/ON_STOP)
  into the motion budget, and `XmbWave` uses `withInfiniteAnimationFrameMillis`, which Compose
  suspends when the window is not drawn. 2 jiffies in 10 seconds.
- **Do not benchmark this app under swiftshader.** The 12fps/100%-janky figure is the emulator's
  software renderer, not the app. On a real GPU the same frame is 19ms with zero jank. Always
  boot with `-gpu host` before drawing a performance conclusion.

`waveCovered` is likewise already correct: it lists boot, the detail/player overlays, the app
drawer and the music player, so the wave freezes under anything opaque.

### The one real finding: the charging glint holds the whole app at 60fps

`XmbStatusStrip.BatteryLine` runs a 2400ms travelling highlight while charging. Toggled twice,
both directions, on an otherwise **static** screen (App Drawer — a list of apps, nothing else
animating):

| battery | frames/12s | CPU (jiffies/12s) |
|---|---|---|
| charging | 723 (60fps) | 232 |
| discharging | **0** | 36 |

```sh
adb shell dumpsys battery set status 2; adb shell dumpsys battery set ac 1   # charging
adb shell dumpsys gfxinfo <pkg> reset; sleep 12; adb shell dumpsys gfxinfo <pkg> | grep 'Total frames'
adb shell dumpsys battery reset
```

So on any screen where nothing else moves, the glint alone is the difference between an idle app
and a continuous 60fps redraw — roughly 19% of a core. A handheld is usually charging while in use.

**FIXED on 2026-09-25**, with option 2: the shimmer runs on the crossbar only.

It is gated on `stripShowsXmbContext`, the flag the strip already uses to decide whether the sort
label belongs to it. The reasoning is the measurement above — on the crossbar the wave is
animating regardless, so the shimmer's frames are already being paid for; anywhere else it is the
entire cost of a screen that is otherwise still.

`rememberInfiniteTransition` is not merely unread when suppressed, it is not started: the whole
`by` is inside the gate, because what costs a frame per vsync is READING an animated value in a
draw scope.

Measured after, same emulator, same method, still charging:

| screen | before | after |
|---|---|---|
| XMB home | 723 frames / 12s | **725** — unchanged, as intended |
| App Drawer | 723 frames, 232 jiffies | **0 frames, 17 jiffies** |

A plugged-in handheld sitting on the App Drawer now idles instead of redrawing at 60fps.

### Still unmeasured

Everything above is a desktop AMD GPU. **No number here characterises the Konker's or the
tablet's mobile GPU**, and a mobile part is where a fullscreen AGSL shader actually costs
something. The next real step is `dumpsys gfxinfo` on the hardware, which needs the devices.

---

## 2. Swipe on the capacitive keyboard — PLANNED

Owner: *"capacitive keyboard just means swiping."*

The Titan Elite's keyboard reports `KEYBOARD | TOUCH | TOUCH_MT` on `sub_touch`. It is a
multitouch surface the app currently ignores entirely.

Unknowns to close first, in order, and **all of them need the device**:

1. Does `sub_touch` deliver `MotionEvent`s to a foreground app at all, or only to the IME?
   `getevent -lp` on the Titan, then a throwaway `View` logging `onGenericMotionEvent`.
2. What coordinate space and resolution does it report? A key grid gives a very coarse surface.
3. Does using it suppress key events? A surface that swallows typing is not worth having.

Only then is the design question worth answering — and the honest default is that this is a
**scroll/flick surface for long lists**, which is the same job the letter rail already does. If
the rail covers it, this may be worth dropping rather than building.

Blocked on hardware. No code until (1) answers yes.

---

## 3. Responsive layout — 4 of 6 DONE

Owner: *"fix the dead space the app should be responsive."*

Measured on the real NP05J tablet (1067×668dp) on 2026-09-25:

| screen | dead space |
|---|---|
| App Drawer, APPS tab | content ends ~444dp, hint bar starts 634dp → **190dp** |
| Settings root | ~204dp below the last row, **~545dp** to the right of a 522dp list |
| XMB home | 4 categories and 2 rows on a 1067×668dp panel |

**The drawer's is list-dependent, not structural.** On the GAMES tab, where "Everything else"
holds all 66 apps, the 6-row grid fills the panel and there is no band. Measure a tab with a long
rest list before touching it. Settings and the XMB are sparse regardless.

### Size is already the user's, so the layout's job is to FILL, not to choose

Asked whether "responsive" meant more content or larger content, the owner's answer was
**neither** — the app already has a size control, so the layout must simply show as much as fits
at whatever size is set. That is a better answer than either option, and it makes the work
concrete.

The control is `XmbLayoutAdjust.scale` (0.6–1.8, stored **per form-factor bucket** so a handheld
and a tablet keep separate tuning), written by the live "Adjust XMB Layout" editor. It is applied
as a DENSITY MULTIPLIER:

```kotlin
// XMBShell.kt:621
LocalDensity provides Density(baseDensity.density * uiScale * layoutAdjust.scale, baseDensity.fontScale)
```

Which is why no size policy is needed: everything inside that provider already grows and shrinks
with the slider. So there are exactly two defects, and neither is about taste.

**(a) The slider does not reach the screens with the dead space.** Grep finds no
`XmbLayoutAdjust`, `layoutAdjust` or `uiScale` anywhere in feature-appbar or `SettingsScaffold`.
The App Drawer and Settings are composed outside that provider, so the one control the user has
over size does nothing on them.

```sh
grep -rn "XmbLayoutAdjust\|layoutAdjust\|uiScale" --include="*.kt" feature/feature-appbar/ \
  feature/feature-settings/src/main/kotlin/com/psplauncher/feature/settings/ui/SettingsScaffold.kt
```

**(b) The counts are constants, so they cannot fill anything.** `SECTION_LIST_ROWS = 6`
(`SectionLayout.kt:28`) and the fixed panel widths are what leave 190dp bare on a 668dp panel and
~545dp bare beside a 522dp Settings list. A measured count — `floor(available / rowHeight)`,
where `rowHeight` already scales with the slider — fills the panel at every size by construction.

`StudioGridCapacity` is the worked example already in the repo: it sizes by measurement,
`MIN_COLUMNS 3 / MAX_COLUMNS 8 / MAX_ROWS 6`.

### The pair that has to be guarded in the same commit

`SECTION_LIST_ROWS` is read eight times across **two concerns in two files**, and they must agree:

- to lay out — `GridCells.Fixed(...)` and the block's height (`AppDrawerSection.kt:126,128`)
- to navigate — `local % ...` / `local / ...` and the column steps (`SectionLayout.kt:58-71`)

```sh
grep -rn "SECTION_LIST_ROWS" --include="*.kt" feature/feature-appbar/src/main/kotlin/
```

The moment it becomes measured, a layout that fits 9 rows while the cursor still steps by 6 walks
the selection onto the wrong app with nothing to catch it. Rule 13, and the cheapest guard is a
test that drives the same measured number into both. `GridCells.Adaptive` has the identical
problem in Search and the App Picker via `moveSearch` / `gridMove`.

### Order

1. ~~**Settings root.**~~ **NOT A DEFECT — checked, and the reason is written down.**
   `SETTINGS_COLUMN_MAX_WIDTH = 560.dp` is deliberate: *"leaves roughly a third of the screen
   showing the wallpaper, which is the XMB's own proportion"* (`SettingsScaffold.kt:310-317`).
   The space measured beside the list IS the wallpaper, on purpose. The vertical gap is a
   seven-item list on a tall screen, and the only ways to fill it are the two readings the owner
   already rejected. Nothing to do.

   The one thing that is true: the cap is absolute dp tuned against 821dp, so the intended
   one-third becomes one-half at 1067dp. Left alone — a 715dp settings column would trade the
   stated proportion for a line length the same comment warns about.
2. **App Drawer's "Everything else".** DONE. `AppDrawerSection` measures the height left to it
   and divides by `ListRowHeight`; the count is reported up and `sectionMove` is given the same
   number. Guarded by four tests in `SectionLayoutTest`, two of which were falsified by reverting
   `sectionMove` to the constant (both go red with their own messages).

   **Outstanding: not yet confirmed on a running device.** The complement grid only draws when a
   tab has BOTH matched apps and a non-empty complement, and a bare emulator has neither — APPS
   has an empty complement, GAMES and EMULATORS short-circuit to their empty states. "Mark as
   Game" does not help: it writes an XMB platform row, while the drawer's GAMES tab reads the
   Play Store category. Needs a device with a Play-Store-flagged game or an emulator installed.
3. **Search.** DONE. The card is a fixed 2:3 with an unspecified width, so the column count WAS
   the card's size — a fixed seven drew bigger cards on a bigger panel. The grid now measures its
   width and divides by what a card wants (93dp, derived from what seven produced on the
   handheld). Confirmed on both AVDs: **Konker 821dp → 7 columns, unchanged; tablet 1067dp → 9
   columns at the same card size.** Three readers of the count now agree — the grid, the
   ViewModel's cursor, and the scroll-back-one-row effect in `SearchScreen`, which was the one
   nearly missed.

4. **The App Picker.** DONE. Same change: the grid measures its width and divides by a 99dp
   target tile (what the old fixed seven produced on the handheld — 100dp would have truncated it
   to six). `AppPickerState.columns` carries the measurement to `gridMove`, guarded across 3..12
   columns and falsified. It also exposed that `AppPickerThreeRowsTest` could never fail, and that
   the three-row guarantee holds only above a 356dp grid viewport.
5. **The chrome screens' base density is a DECISION already made, not an oversight.** My earlier
   entry here said "it is neither — it is simply unwired". That was wrong, and the evidence for
   it was wrong too: I grepped feature-appbar and `SettingsScaffold` for `uiScale` /
   `layoutAdjust`, found nothing, and concluded the slider did not reach them. Those files have
   no reason to read it — `LocalDensity` is ambient. The same shape of mistake as testing "has no
   gamepad position" for "is a keyboard key".

   What actually happens is `XMBShell.kt:1630`, a second provider spanning 1631..2034 that resets
   to base density, with the reason written on it: *"Everything from here down is a separate
   screen or overlay … not part of the XMB cross. Reset to the device's base density so the
   XMB-only canvas scale above stops at the cross: scaling the XMB never rescales any of these."*
   The control is called Adjust XMB **Layout** and it scales the crossbar.

   **And the reset is load-bearing.** `StatusStripHeight` is 34dp for the strip the shell draws AND
   for the six screens that reserve room under it — the pair `ChromeBands` exists to keep in step.
   The strip is drawn at base density (`XMBShell.kt:1469`, itself a fix for this exact bug). Scale
   only the screens and the two disagree:

   | slider | screen reserves | strip occupies | result |
   |---|---|---|---|
   | 0.6 | 20dp | 34dp | **strip overlaps content by 14dp** |
   | 1.0 | 34dp | 34dp | exact |
   | 1.8 | 61dp | 34dp | 27dp of empty band |

   So "wire it" is not a one-line change of scope. It is a decision about what the slider IS:

   - **(a) Leave it.** The slider scales the XMB canvas, as its name and that comment say. The
     chrome screens are already responsive by measurement (the drawer, Search and the picker all
     fill now), which is the thing the dead space actually needed.
   - **(b) Make it a global UI scale.** Then the strip and the hint bar must scale with it, both
     base-density resets come out, and `ChromeBands`' contract becomes "34dp at the CURRENT
     density" rather than "34dp, once". That is the honest version and it is a real piece of work
     with a regression path straight back through the chrome-band bug.

   Not done. It needs (a) or (b) chosen, and the owner asked for "wire it" believing it was
   unwired rather than scoped.
6. **The XMB crossbar is left alone.** Its composition is deliberate and the slider already works
   there.

## 4. Known bads — STILL OPEN. The fix was insufficient and the claim was wrong.

`DisplaySettingsViewModelFontColorTest > white raises no notice at all`.

**What was done:** the ViewModel's five hard-coded `Dispatchers.IO` hops were injected via
`@SettingsIoDispatcher`, so a test can hand in its own scheduler. That is correct on its own
merits and stays.

**What was claimed:** fixed and verified, on the strength of ONE green full run in which the test
took 0.063s where it used to exhaust 60 seconds.

**What is true:** it failed again on 2026-09-25 under `./gradlew test --rerun-tasks` run
alongside another task, with the same message — `condition not met within 60s: colour surfaced` —
after the full 60.085s. A rerun of the same sweep passed, and two module-alone runs passed in
0.087s and 0.058s. So it is exactly what it was before: **intermittent under load.** One green run
is not evidence about a flake, and treating it as evidence is how this got called done.

**Why the fix could not have been enough**, which the `eventually` helper already said in its own
KDoc: the thing the test waits for is "a real DataStore write to a real file on a real thread,
which no amount of `advanceUntilIdle` will hurry". Injecting the VIEWMODEL's dispatcher moves the
ViewModel's hop; DataStore keeps its own scope and its own threads. The remaining race is there,
untouched.

**What would actually fix it:** make the DataStore instance test-controllable — `pfpDataStore` is
a single app-wide delegate (`core/core-data/.../datastore/PFPDataStore.kt`), so this means giving
it an injectable scope, not patching the test. Until then the honest options are to accept a known
flake or to stop the test depending on a real file at all.

**More evidence, 2026-09-25:** the flake moved to a SIBLING —
`DisplaySettingsViewModelGameBootTest > a fresh install shows the toggle on and turning it off
persists` timed out at the same 60s, with `UncompletedCoroutinesError: the test body did not run
to completion`. That is the diagnosis confirming itself: the race belongs to DataStore, not to one
test, so it surfaces wherever the load lands.

It also exposed an inconsistency: only `FontColorTest` was passing `io = dispatcher`. The other
three classes still built the ViewModel with the real `Dispatchers.IO`, so the partial improvement
was never applied to them. All four now inject it. **This does not make them deterministic** — it
removes the ViewModel's half of the race and leaves DataStore's.

The lesson worth keeping: **`./gradlew test --rerun` does not re-run the suite.** It forces only
the requested task, and it reported 2719 green while 180 of 309 result files were 85 minutes old.
Use `--rerun-tasks`, and check the result files' ages before believing a green run.
