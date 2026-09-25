# Global chrome — handoff, 2026-09-24 (evening)

Picks up from `xmb-redesign-handoff-2.md`, written the same day at 12:20. That one ends with Tier
A and B shipped and Tier C untouched. **Since then: item 14 shipped, and the app grew a top bar and
a bottom bar that are the same on every screen.** Tier C is now items 15 and 17 only.

Everything below was driven on the Pocket FIT and looked at. Where something was not verified on
the device it says so in the same sentence as the claim.

---

## Do these three before trusting anything here

    find . -path "*test-results*" -name "TEST-*.xml" -delete
    ./gradlew test --continue
    ./gradlew lint --continue

**Delete the result XML first.** A module whose task is up to date leaves its last XML in place,
and reading that is how a red suite looked green for three commits earlier in this project.

**Use `--continue` on lint.** Without it lint stops at the first failing module, which is how four
errors hid behind each other for weeks: fixing one only revealed the next. Both are green as of
this handoff (2695 tests, 0 failed, 8 skipped — 7 theme-kit, 1 feature-artwork, all pre-existing).

**`uiautomator dump` is not how you check the chrome.** It prunes nodes it thinks are covered, and
it decided the status strip was covered while the strip was being drawn on top. I concluded "the
strip is not drawing" twice from an empty dump and was wrong both times. Take a frame:

    adb shell screenrecord --time-limit 2 --size 960x540 /sdcard/f.mp4
    adb pull /sdcard/f.mp4 /tmp/f.mp4
    ffmpeg -y -i /tmp/f.mp4 -vf "select=eq(n\,30)" -vframes 1 /tmp/out.png

`screencap` is still the wrong tool on GL surfaces — see `memory/screencap-lies-on-gl-surfaces.md`.

---

## What shipped since handoff-2

| What | Where |
|---|---|
| **14** — Game Details replaced: white primary Play, Favourite/Options as secondaries, one media row (LOGO / INFO / VIDEO / BOX ART / MEDIA), Details as a submenu | `ui/detail/GameDetailScreen.kt`, `ContextMenus.kt` |
| **One bottom bar on every screen.** Search, the App Drawer, Settings and the detail pages each drew their own — an inline row, two different pills. All draw `PfpHintBar` now | `core-ui/components/PfpHintBar.kt` |
| **The status strip is global.** It stays on top of the App Drawer, Settings, Search and the detail pages; full-screen overlays still cover it | `XMBShell.kt`, `XMBViewModel.statusStripVisible` |
| **The notification sheet opens from anywhere the strip is drawn**, by Start or by the strip's corner | `XMBViewModel.onGamepadAction` |
| **The App Drawer's header is one search field** — breadcrumb and magnifier button deleted | `appdrawer/AppDrawerHeader.kt` |
| Lint green across every module; two library modules gained their first `AndroidManifest.xml` | `feature-appbar`, `feature-xmb` |

### The two ideas worth keeping from that work

**The partition, not the second list.** `otherBlockingOverlay` is 25 conditions and is now the OR
of `chromeOverlay` and `fullscreenOverlay`. A screen added later cannot land in neither. Had the
strip's rule been written as a *second* list, the failure would have been a screen that works with
a clock quietly missing from it — which nobody reports.

**"Is it drawn" and "do its words still apply" are different questions.** `statusStripVisible` and
`stripShowsXmbContext` answer them separately. Collapsing them is a real temptation and there is a
test that goes red when you do: the drawer showed "Title" over a grid it does not sort.

---

## Still open

### Plan items

- **15 — wizard restyle.** Untouched. `SetupStep` has 13 entries today, two of them conditional
  (VITA and RETROARCH appear only when those apps are installed):

      grep -A4 'enum class SetupStep' feature/feature-settings/src/main/kotlin/com/psplauncher/feature/settings/viewmodel/InitialSetupViewModel.kt

  The design folds them to 4. Keep every step including the media ones — that was the call in the
  plan and nothing since has changed it. **`WizardScaffold` supplies its own `footer`, so it did
  NOT pick up the new bottom bar**, deliberately: it is themed chrome and item 15 is where it gets
  looked at.

- **16 — dropped.** Live Appearance preview. Seth's call, 2026-09-24.

- **17 — artwork chain, deferred to the Settings run**, because it ends in `ArtworkSettingsScreen`
  and Settings is expected to move underneath it. That run has not started.

### Things agreed in conversation that are not built

- **Letter jump.** Hold a shoulder in a long list (151 games, Songs) for an A–Z scrubber. Agreed,
  never started — `grep -rn "letterJump" --include="*.kt"` returns nothing; the "scrubber" hits in
  `XMBItemList.kt` are the music progress bar. Needs a hold timer in `GamepadInputHandler`.

- **Apps have no "Remove from Recent".** App recency comes from `UsageStatsManager` and cannot be
  cleared, so it needs a hide record instead. `HideLocationType` has no value for it:

      grep -n 'enum class HideLocationType' core/core-domain/src/main/kotlin/com/psplauncher/core/domain/model/HiddenPlacement.kt

  Adding `RECENTS` there is the shape. Note the enum is persisted, so it wants a migration check.

- **Turning off emulator/game features.** Seth: *"just hide it."* The open question was never
  answered: **does the first-run wizard ask, or is it Settings-only?** It matters because the
  wizard is item 15 and the two would be built together.

### Carried over from handoff-2, still true

- **The "Launcher" column of the notification sheet has still only ever shown its empty state.**
  Nothing posted a launcher toast during any run. The Android column is populated from real
  notifications and works.
- **The bottom bar's target is not styled apart from the verb.** 12g draws "Open" quiet and
  "All Games" loud; both ride in one label so the shared renderer draws them. Changing it means
  teaching `ControllerPromptBar` about a second label, not hand-drawing in the bar.

---

## Unverified on the device — check before a release

- **The status strip during the disc-launch ceremony.** `discCeremony` is in `fullscreenOverlay`,
  so the strip should be covered. I caught one frame that looked like it was drawing there, but I
  am not confident the ceremony had actually started in that frame. **I did not confirm either
  way.** One deliberate check settles it: launch a game with the ceremony on and take a frame.
- **The four deep-linked Details actions** (item 14's `initialAction`).
- **The launch pulse and the wave's glow on launch.**
- **The shelf glyph path** — the ▶ / ✓ / ＋ badges rendering on a real marked game.

---

## Release state

**Nothing has been released since 1.10.0.** As of this handoff:

    git log --oneline origin/main..HEAD     # 4 commits, unpushed
    git tag --sort=-v:refname | head -1     # v1.10.0
    grep versionName app/build.gradle.kts   # 1.10.0

`CHANGELOG.md` has five entries under `[Unreleased]`, all UI: the bottom bar, the status strip,
the sheet, Settings' bar and the drawer's header. The lint pass has no entry — it changed nothing
a user can see. Count it rather than trusting this line:

    awk '/^## \[Unreleased\]/,/^## \[1\.10\.0\]/' CHANGELOG.md | grep -c '^- \*\*' 

**Tags are not GitHub Releases.** Pushing a tag does not create one, and saying "pushed" after
checking only `git log` was wrong once in this project already. Check the Releases page.

**The signing key lives only on this machine and is gitignored** — see
`memory/release-signing-key.md`. Lose it and no build can ever upgrade an existing install.

---

## Two traps this run fell into

**A flaky test was a real diagnosis, not a retry.** `eventually()` in the DisplaySettings tests
polls a REAL DataStore write to a REAL file against a wall clock, and the budget was 10 seconds —
ample on an idle machine, a coin flip with thirteen module JVMs running. It existed **four times,
byte for byte**, one per test file. One copy now, in `Eventually.kt`, at 60s. Nothing waits longer
on success. The lesson is the count, not the number: a shared idiom pasted four times is a number
that gets raised in one file and leaves three still broken.

**A caution I had not checked cost a whole extra pass.** I left the notification corner pressable
only on the crossbar, reasoning that the screens underneath own their own d-pad and would leave a
sheet nothing could walk. The sheet's branch in `onGamepadAction` runs *before* the per-screen
routing and returns — it has always taken every key. The gate was invented, not observed, and
Seth caught it in one line. **Read the dispatch order before claiming a routing hazard.**
