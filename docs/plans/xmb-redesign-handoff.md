# XMB redesign — handoff

**Status: nothing here is approved. Nothing here has been implemented.**

A design bundle arrived from Claude Design on 2026-09-23 proposing seventeen screens. Seth's
instruction, verbatim:

> "design claude did ALOT i didnt want and id rather work through it"

So this is a **menu of proposals, not a specification**. Treat every screen as a suggestion that
has not been accepted. He will take them one at a time and tell you what he wants from each —
and he has said his feedback will be mostly about **what data a screen shows**, not about its
pixels. Do not read a screen's mock as settled because it is detailed.

## The one rule

**Ask before implementing. Every screen. Every time.**

This is his explicit instruction and it is also what the bundle's own README asks for. A screen
that looks obviously right is still a screen he has not agreed to. The cost of asking is one
message; the cost of not asking is a day of work he did not want, on surfaces that took a full
session to get correct.

## What IS decided

- **The font is being swapped.** The design uses Instrument Sans. Seth confirmed: "the font for
  sure were swapping." This is the only part of the design he has signed off.
- **Order of work: easy to hard.** His words. The ordering below is the proposal; confirm it.

## What is NOT decided, and is contentious

- **3e Setup collapses the wizard from 12 steps to 4.** The 12-step wizard was rebuilt on
  2026-09-23 to Seth's own spec — the Permissions, Books and Make It Yours pages were his asks
  that morning. This screen deletes most of that. Raise it explicitly before going near it. It is
  the single largest conflict in the bundle.
- **9h vs 9i context menus** are two mutually exclusive treatments (rail up the right edge vs
  inline under the focused title). He has picked neither.
- **`github.md` in the bundle advertises "Recent (2 options)" and maps screens 1b/1c to
  `recent-shelf.jpg`. There is no 1b in the exported file.** Either the Recent redesign did not
  make the export or it was folded into 1c. Do not plan around a screen that is not there; ask.

## Where the bundle is

    ~/Downloads/Platform launcher design feedback-handoff.zip     (source of truth)
    ~/Downloads/platform-launcher-design-feedback/                 (extracted, same content)

The primary file is `project/XMB Redesign.dc.html` — 512 lines, every screen inline-styled at
1920x1080 and scaled to 0.5 for display. `project/support.js` is the design tool's own runtime
(1911 lines); it drives the interactive 1c prototype and is not something to port. `project/art/`
holds nine covers **cropped from this repo's own screenshots**, so they are the real games in
Seth's library. `project/docs/screenshots/` is a copy of ours.

The bundle's README asks you not to render these in a browser — read the HTML and CSS directly,
the numbers are all in the source.

## The seventeen screens, easiest to hardest

Effort is my estimate from reading the file and the code each screen touches. Confirm before
trusting it.

### Tier 1 — self-contained, small blast radius

| Screen | What it proposes | Touches |
|---|---|---|
| **Font swap** | Instrument Sans, five sizes, floor of 28px at 1080p | `core-ui/theme/PfpType.kt`, app-wide |
| **6h Toasts** | Bottom-right above the hint bar, 4 s, max two stacked, each showing the art of what it refers to | `core-ui/notification/` |
| **8a Ritual** | *Extends* the existing ceremony rather than replacing it: a case that slides left, the disc rolling out from behind it, and a slit of light that opens where it left and closes to a point at hand-off | `core-ui/components/DiscLaunchCeremony.kt` |

**8a quotes the real timings** — 3850 ms drop, 4500 ms hand-off — which are the constants
currently in `DiscCeremony`. Whoever made this read the code. That makes it the safest of the
three to attempt.

### Tier 2 — one screen each, moderate reach

| Screen | What it proposes | Touches |
|---|---|---|
| **1a XMB Home** | Selected category icon full-size and full-bright, others at 50%; the focused item's tile is a **collage of the covers inside it**; items below step down in opacity rather than being clipped; a fan of the newest covers on the right | `XMBCategoryBar.kt`, the item column, `XmbBackground.kt` |
| **6a / 6b / 6c Media columns** | Per-medium focus tiles: a scrubber for the playing track, 16:9 with a progress line for video, page progress for books | the media columns in `feature-xmb` |
| **3c Game Details** | Same backdrop as the column so the item "opens up"; one white primary button with Favorite and More as round secondaries; box art, screenshots and video in one media row | `ui/detail/GameDetailScreen.kt` |
| **6e / 8q App Drawer** | A–Z grid eight across; X pins an app to the cross bar; pinned emulators in a large row above a three-column list | `feature-appbar` — **and new state: pinned apps** |
| **9h or 9i Context menu** | Pick one | `ui/ContextMenus` |

**1a is the one to do first** if he wants a single screen taken all the way. It is the flagship,
it exercises the new type scale, and if the direction does not land on a real 6" panel that is
much cheaper to learn once than seventeen times.

### Tier 3 — hard, or resting on something else

| Screen | Why it is hard |
|---|---|
| **1c Navigation** | It is the interaction model, not a layout: per-column cursor memory, items passing *up over* the bar as on the PS3, and the backdrop taking its colour from the focused item's cover. This is the XMB's core motion and the riskiest thing in the bundle. |
| **8b Settings Appearance** | A live miniature XMB in the right panel that repaints as you change colour, background or icon size. That means rendering the crossbar twice, in two sizes, from one source of truth. |
| **8o / 9j / 9l Scraping** | Compare, carousel and queue over the artwork pipeline — the most complex subsystem in the app, with four providers and a rate-limited account. |
| **3e Setup 12 → 4** | Contentious (see above) and it rewrites work that is one day old. |

## State of the code you are inheriting

Everything below is on `main` and pushed. `1.7.0` is released (debug APK only); everything after
it sits in `[Unreleased]`.

Verified on hardware today:
- Menu music, the two assignable launch cues, the whole sound screen.
- The rebuilt setup wizard, all twelve steps, plus its splash.
- RetroArch: the cores link (0 → 195 cores), and the `CONFIGFILE` fix that took Pokémon Unbound
  from a black screen to its title.
- The Recent filter names responding to touch; the crossbar appearing on an empty shelf; the
  launch spine no longer covering the Apps button.

**Not verified, and it matters because the redesign moves the same code:**
1. The **rail toggle** on the Recent shelf — tapping the artwork to show/hide the cover rail.
   Needs a device with a library; the tablet has none.
2. The **swipe on a populated shelf**. Same reason.
3. Whether **Pokémon Unbound actually renders** past the title screen.

All three want the AYANEO Pocket FIT, which dropped off adb mid-afternoon and did not return.

## Traps this session paid for

Read these before you trust a measurement.

- **`screencap` returns pure black for some surfaces.** RetroArch's GL output, and *the whole
  launcher UI* on the nubia tablet. `screenrecord` sees both. A black screenshot is the tool, not
  the app — I nearly reported a working emulator as broken on this.
- **Use `uiautomator dump` for anything about position or hit-testing.** The launch spine
  overlapped the Apps button by 114 of its 139 pixels and *nothing looked wrong*, because the
  spine's gradient had faded to nothing there. Three wrong diagnoses came from reading
  screenshots; the bounds settled it in one command.
- **Two devices, one `adb`.** An emulator appearing made several commands silently target the
  wrong thing and return empty. Always `export ANDROID_SERIAL=<serial>`.
- **Never pipe a Gradle sweep through `head`.** It closes the pipe, kills the run mid-suite, and
  reports interrupted tests as failures. Redirect to a file and grep the file.
- **Never `git checkout <file>` to undo a deliberate break.** It discards uncommitted work in the
  same file. Copy to the scratchpad and copy back.
- The device's **prompt pills dispatch real gamepad actions**, so a tapped prompt is a genuine
  end-to-end test of the same path the pad uses.
- `./gradlew test --continue` is the only correct sweep. A hand-typed module list once hid twelve
  red tests for a day.
- `DisplaySettingsViewModelGameBootTest` flakes under a parallel sweep (shared DataStore) and
  passes alone. It has done so three times today. Re-run before believing it.

## How the redesign arrived

Not through the MCP the handoff prompt names. `claude_design` is not installed in this
environment and the built-in `DesignSync` refused with *"needs design-system authorization, run
/design-login"* — an interactive flow only Seth can run, and one scoped to design-*system*
projects, which this is not. He exported the bundle to `~/Downloads` instead. If a future prompt
asks you to import from `claude.ai/design`, expect the same wall and ask for an export.
