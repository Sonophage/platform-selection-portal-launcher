# XMB redesign — the plan

Supersedes the menu of proposals in `xmb-redesign-handoff.md`. That document said nothing was
approved. This one records what Seth approved, screen by screen, on 2026-09-23, after walking the
whole bundle and annotating it element by element in a browser feedback tool.

**Order is easy to hard. His call, confirmed.**

## What changed from the handoff

Two things the handoff called out as blockers are no longer blockers.

- **3e is not the conflict it looked like.** The handoff called the 12-step wizard collapsing to 4
  "the single largest conflict in the bundle". Seth's verdict: *"i want the wizerd to look like this
  minus the background its just how its set up and its missing media right now."* The twelve steps
  stay. It is a restyle. The bundle's version also omits the media steps; ours keeps them.
- **9h vs 9i is decided.** 9h, the right rail: *"i like this instead of our current context menus."*
  9i dies as a menu, but its pill row survives as a separate thing — see item 12.

Two screens are out. **1b** is not in the export. **8o** is not dead, despite carrying no
annotation of its own: *"8o comes in when selecting the art"* — it is the compare-and-pick step at
the end of the artwork chain.

## Verified state

Seth verified the handoff's three open hardware items on 2026-09-23 and reports all three good:
the Recent rail toggle, the swipe on a populated shelf, and Pokémon Unbound rendering past its
title screen. **I did not run these checks myself.**

Two claims I did verify on the Pocket FIT (`01411YEF01035627`), because reading the source gave the
wrong answer:

- **The selected item is already bigger.** `uiautomator dump`: selected caticon 172x172, unselected
  134x134; focused row title 66px tall, unfocused 45px.
- **Unselected rows do not dim on his device.** The dim is in the code (`XMBItemList.kt:606`,
  alpha 0.68) but `display_solid_unfocused_icons = true` in `pfp_prefs.preferences_pb`, and that
  flag is the first branch of the rule — `isSelected || solidUnfocusedIcons -> 1f`. Everything
  unselected renders at full brightness. The source shipped a dim; the settings turned it off.
- **Rows above the focus already pass up over the crossbar.** One `KEYCODE_DPAD_DOWN` moved
  `Moon+ Reader` from y=551 to y=141, with the bar occupying y=252..424. The handoff called this
  the riskiest item in the bundle. It is already built.

## Step 0

Nothing outstanding. Seth cleared the three hardware items above.

## Tier A — small, self-contained

| # | Work | Touches |
|---|---|---|
| 1 | **Font to Instrument Sans.** First, because every screen below inherits it. The only item signed off before this session. | `core-ui/theme/PfpType.kt`, app-wide |
| 2 | **Launch ritual hand-off 4500ms to ~6000ms.** *"right now this is set to 4.5 i would make it longer to about 6 seconds."* Extends what exists; does not replace it. | `DiscLaunchCeremony.kt` |
| 3 | **Toast restyle, position unchanged.** *"I just want the toast to look like this but let it stay where it is."* 640px card, 80px art at r16, 32px title over 26px subtitle at 75%, `rgba(22,9,2,.94)`, r24, inset 1px white 12%, shadow `0 18 40 / .35`. | `core-ui/notification/` |
| 4 | **Ranked dim** by distance from focus: `[1, .85, .55, .30]`, capped at 3. Vertically down the item column **and** horizontally along the crossbar. Numbers are from 1c's live prototype, not 1a's static mock — the bundle disagrees with itself (`.9/.6` there) and the running version wins. | `XMBItemList.kt:606`, `XMBCategoryBar.kt:194` |
| 5 | **Focus glow.** On the item tile: 4px solid white ring, `rgba(255,220,170,.45)` bloom at 36/6, black drop at 18/40. On the selected caticon: `drop-shadow(0 0 18px rgba(255,220,170,.55))`. Today there is no halo on either — `XMBCategoryBar.kt:234` says so outright. | both files above |
| 6 | **Row meta line.** *"This is what is missing from what we have now. if it hasnt been played it defaults to just the system and publisher."* So `System · last played`, falling back to `System · Publisher`. | `XMBViewModel` item builders |

## Tier B — one surface each

| # | Work | Touches |
|---|---|---|
| ~~7~~ | ~~**Per-column cursor memory on left/right.**~~ **DROPPED — see below.** | — |
| 8 | **Cover fan**, 520x380 at right:120 top:470. Three covers from inside the focused item, largest centre, +/-8deg, 85% opacity. Needs a "newest N covers in this item" query that does not exist. | `XMBShell.kt` |
| 9 | **2x2 art grid tile** on the focused item, Emulation and all four media columns only. Fewer than four covers: fill what you have, leave the rest empty. **Behind a setting** — art grid or the existing icon. | `XMBItemList.kt`, Display settings |
| 10 | **Media focus tiles**, each conditional, not permanent. Music: playing track in the focus slot with a scrubber, *"only for currently playing."* Video: 16:9 tile at the same height as other focus tiles, progress line on the thumbnail, *"same for currently playing."* Books: *"last book read would have this at the top"* with page progress. | media columns in `feature-xmb` |
| 11 | **App drawer, 6e.** A-Z grid eight across, `X` pins to the cross bar. Search bar pinned to the top — *"it still keeps it at top always."* Keyboard dismisses on a tap or on controller movement. 8q was the other option; 6e wins because it pins to the cross bar, which makes 8q's in-drawer Pinned row a second home for pins. One destination, not two. | `feature-appbar` |
| 12 | **Pill row** under the focused game in the column, always visible — *"it stays in column always visible."* Taken from 9i, which otherwise lost. | `XMBItemList.kt` |
| 13 | **9h right rail**, replacing the current context menus. Actions stack up the right edge, only the focused one shows its full label, up/down matches the column. Everything not in item 12's pills lives here — *"anything not there goes to the column."* | `ui/ContextMenus`, `ContextMenuOverlay.kt` |

## Tier C — hard

| # | Work | Touches |
|---|---|---|
| 14 | **3c replaces Game Details wholesale.** *"This would replace the game details screen."* Same backdrop as the column so the item reads as opening up; one white primary button with Favorite and More as round secondaries; box art, screenshots and video in one media row. | `ui/detail/GameDetailScreen.kt` |
| 15 | **3e's look on the existing 12-step wizard.** Labelled progress line, one question per step with the likely answer pre-picked. Drop its background treatment. Keep every step, including the media ones 3e omits. | setup wizard |
| 17 | **Artwork chain.** *Deferred to a larger Settings run — Settings is expected to move underneath it.* 9l queue, restricted to games **missing** art — *"art work review would be only to fill in missing art."* Then 9j's carousel, all sources in one row tagged by origin, L1/R1 stepping. Then 8o to compare current against found with the source list. Picker collapses to five tabs: logo, screenshots, manual, video, game art — **as grouping only**. The twelve `ArtworkKind` values are untouched. | `feature-artwork`, `ArtworkSettingsScreen.kt` |

## Item 16 was dropped

**8b's live Appearance preview, cut on 2026-09-24.** Seth's call. It was the only Tier C item that
bought nothing but a look at itself, and it was the most expensive of the four: a second crossbar
rendered at a second size from one source of truth, kept in step with the real one forever after.

**Item 17 is deferred rather than dropped**, to a larger Settings run. The artwork chain ends in
`ArtworkSettingsScreen`, and Settings is expected to move underneath it; building the chain first
would be building it against a surface about to change.

## Item 7 was dropped, and it is the one the plan got wrong

It read as cheap: the machinery exists — `viewCursor`, `viewCursorKey`,
`navigateRememberingCursor` — and `onCategorySelected` merely hardcodes
`selectedItemIndex = 0`. What that hardcoded zero actually is, is a **deliberate removal**.

Commit `a426a2dc`, 2026-09-22, the day before the design bundle arrived:

> The crossbar remembered where you were in each column and put you back, so sweeping across it
> left seven columns each scrolled to a different depth with nothing on screen to predict it. The
> drill cursor is a different thing and stays: going into a folder and back out is one column, not
> seven.

`XMBViewModel.kt:7261` carries that reasoning as a comment sitting on the exact line item 7 would
have changed. 1c proposes the behaviour back because the design tool could not know it had already
been tried and rejected on the handheld.

Dropped on 2026-09-23. The design's argument is PS3 fidelity; the counter-argument came from
running it. **A missing feature and a removed one look identical in the code — the difference is
in the history, and it is worth a `git log -S` before calling anything cheap.**

## Explicitly cut

- **1c's backdrop.** It wants the focused cover blurred 90px, saturated 1.3, at 50%. *"dont worry
  about the backdrop on 1c we keep ours."* `XMBGameBackdrop` stays as it is: still art solid on the
  left, fading out between `XMB_STILL_SOLID_END` (0.40) and `XMB_STILL_FADE_END` (0.68) so video
  snaps play on the open right.
- **1a's opacity stepping** at `.9/.6` — superseded by item 4's numbers.
- **1a's `Emulation / Sorted by title` header** and its hint-bar changes (`A Open`, `SEL Search`).
- **1a's fanned three-cover collage tile** — replaced by item 9's 2x2 grid.
- **8q.** See item 11.
- **9i as a menu.** Its pills survive as item 12.
- **1b.** Not in the export.

## Three things that will bite

1. **The horizontal ranked dim has nowhere to go when drilled in.** `visibleCategories()` drops
   every category to the right of the selected one on purpose — PSP second-level behaviour, and
   the comment at `XMBCategoryBar.kt:68` says the point is that you see exactly the previous and
   the selected, with no clipping. A distance ramp then only ever has a left side. Item 4 needs a
   decision: ramp at root only, or keep the right-hand categories when drilled in and ramp both
   ways. **Unanswered. Raise it at item 4.**
2. **"Solid Unfocused Icons" stops matching its name.** It becomes flat-dim vs ranked-dim rather
   than dim vs no-dim. Seth: *"so we rename the dim."* The old name appears in settings strings,
   in `DisplaySettingsViewModel`, in `XMBViewModel`, and in explanatory comments in both
   `XMBItemList.kt` and `XMBCategoryBar.kt`. The DataStore key `display_solid_unfocused_icons` is
   persisted on his device and must either keep its name or migrate.
3. **Item 9 and Icon Display Mode both decide what a tile shows.** Icon Display Mode already picks
   between `PHYSICAL_MEDIA`, `BOX_ART` and `BOX_3D` per column. The 2x2 art grid is a fourth
   answer to the same question. They need one owner. **Unanswered. Raise it at item 9.**

## Traps that still apply

From the handoff, unchanged and still true:

- `screencap` returns pure black for GL surfaces. `screenrecord` sees them.
- `uiautomator dump` is the only honest answer to anything about position or hit-testing.
- Two devices on one `adb`. Always `export ANDROID_SERIAL=01411YEF01035627`.
- Never pipe a Gradle sweep through `head` — it kills the run and reports interrupted tests as
  failures. Redirect to a file and grep the file.
- `./gradlew test --continue` is the only correct sweep.
- `DisplaySettingsViewModelGameBootTest` flakes under a parallel sweep and passes alone.

## Where the bundle is

    ~/Downloads/platform-launcher-design-feedback/project/XMB Redesign.dc.html

512 lines. Every screen is inline-styled at 1920x1080 and scaled to 0.5. The 1c prototype's real
logic — the dim ramp, the cursor memory, the window sizes — is in the `<script type="text/x-dc">`
block at line 369, not in the markup. Read that before trusting a number read off a mock.
