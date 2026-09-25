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

**This is a decision, not a cleanup, so it is not made here.** The options:

1. **Leave it.** It is a deliberate affordance and only runs while plugged in.
2. **Only glint over the XMB.** The wave is animating there anyway, so the frames are already
   being paid for; the drawer, Settings and the pickers would go back to 0fps. Visual change.
3. **Keep it everywhere, make it cheaper.** `travel` is read inside `drawWithCache`'s *build*
   block (`XmbStatusStrip.kt:436-449`), so every frame rebuilds the brush. Reading it in the draw
   lambda instead confines the invalidation to the draw phase. Cheaper per frame — but still one
   frame per vsync, so it does not change the 60fps.

Nothing here has been changed. Option 2 is the only one that recovers the idle case.

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

## 3. Responsive layout — PLANNED

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

1. **Settings root.** One screen, no grid, no cursor arithmetic — just a list that has no reason
   to be 522dp wide on a 1067dp panel. Lowest risk, most visible.
2. **App Drawer's "Everything else".** `SECTION_LIST_ROWS` becomes measured, and `SectionLayout`
   is handed the same number in the same commit.
3. **Search and the App Picker together**, because they share the grid-cursor pair and fixing one
   leaves the other half-guarded.
4. **Extend the scale provider to the chrome screens**, or decide deliberately that chrome stays
   at base density. Right now it is neither — it is simply unwired.
5. **The XMB crossbar is left alone.** Its composition is deliberate and the slider already works
   there.

## 4. Known bads — DONE

`DisplaySettingsViewModelFontColorTest > white raises no notice at all` was a race between the
test's virtual clock and a real `Dispatchers.IO`. Dispatcher injected via `@SettingsIoDispatcher`;
the test now runs in **0.063s** under a full 422-task run where it used to exhaust 60 seconds.

The lesson worth keeping: **`./gradlew test --rerun` does not re-run the suite.** It forces only
the requested task, and it reported 2719 green while 180 of 309 result files were 85 minutes old.
Use `--rerun-tasks`, and check the result files' ages before believing a green run.
