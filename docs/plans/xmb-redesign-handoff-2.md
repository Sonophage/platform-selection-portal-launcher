# XMB redesign — handoff, 2026-09-24

Picks up from `xmb-redesign-plan.md`. **Tier A and Tier B are done. Tier C has not been started.**
Two items that were not in the plan at all (12b, 12g) were added and finished.

Everything below was driven on the Pocket FIT over wireless adb and looked at, not inferred from
the source. Where something is unverified it says so.

---

## Run `./gradlew test` before trusting anything here

    find . -path "*test-results*" -name "TEST-*.xml" -delete
    ./gradlew test --continue

**Delete the result XML first.** A module whose task is up-to-date leaves its last XML in place,
and reading that is how a red suite looked green for three commits earlier in this project. As of
this handoff: **2642 tests, 0 failures, 0 errors, 8 skipped.**

The device: `export ANDROID_SERIAL=192.168.0.80:5555`. See `memory/pocket-fit-wireless-adb.md` —
the USB serial vanishes and `tcpip` dies on reboot.

---

## What shipped

### Tier A / B, as planned

| # | What | Where |
|---|---|---|
| 11 | App drawer: eight across, permanent search box, **Add to Cross Bar** on the Y menu | `feature-appbar` |
| — | All Apps tab **removed**; every other tab draws 8q — its own apps in a row, everything else in a compact A–Z list beneath | `AppDrawerScreen`, `SectionLayout.kt` |
| 12 | 9i action pills under the focused row, always visible; left/right walk into them | `PillActions.kt`, `XMBItemList.kt` |
| 13 | 9h right rail replaces **all 14** context menus | `RailActions.kt`, `ContextMenuOverlay.kt` |

### Added during the run, not in the plan

| # | What | Where |
|---|---|---|
| 12b | Header: live activity left, clock over a full-width battery hairline right, nav hints centre | `XmbStatusStrip.kt` |
| 12g | Bottom bar: `Ⓑ Apps │ Ⓐ Open All Games … Ⓧ Sort Ⓨ Options Ⓢ Search` | `XmbHintBar.kt`, `HintPrompts.kt` |
| — | Toast pill **deleted**; notifications live in the header and a pull-down sheet | `XmbNotificationBar.kt`, `SystemToasts.kt` |
| — | Android's own notifications, via a `NotificationListenerService` | `PfpNotificationListener.kt`, `AndroidNotifications.kt` |
| — | Real controller art (CC0), plus Keyboard and Touch glyph families | `ControllerButtonGlyph.kt`, `docs/legal/controller-glyph-art.md` |

---

## Decisions that will look arbitrary later

**Last Played has no caticon.** It is not a column — it replaces the whole screen — so a slot on
the bar was an icon you could never see selected. It is hidden from the BAR and still in the model,
so LEFT off Emulation reaches it. *There is no touch route to it.* If that matters, it needs one.

**The rail carries what the pills don't.** Pills take four ids, the rail drops those and caps the
rest at nine. There is no "More" — that was the owner's call, and what falls past the cap is not
reachable from the rail. **Destructive rows are never what gets cut**; they sort last in every
builder, so a plain `take(9)` would drop "Remove from Library" and keep "Move to Category".

**`BUILT_IN_CATEGORIES` positions were read off the owner's database**, not designed. The rule that
every new built-in must take a position past every old one is **gone** — `seededPositions` appends
a built-in the database has never seen past whatever it holds, which is what lets the default order
be an order rather than a list of reserved numbers.

**Prompts name actions, never buttons.** `ControllerPromptItem` resolves the button from the same
mappings the input handler reads, so a footer cannot disagree with the pad. Do not hand-draw a
glyph anywhere; that is the one way to break it.

**Xbox art is grey with a coloured letter and PlayStation is the DualShock 4.** Both are what the
hardware looks like. A DualSense prints its symbols in white, so accurate PS5 art has no colour —
the coloured PlayStation is the PS4. See `docs/legal/controller-glyph-art.md`.

---

## Still open

- **Tier C, untouched.** 14 (3c replaces Game Details), 15 (3e wizard restyle — 12 steps become 4),
  16 (8b live Appearance preview), 17 (artwork chain 9l→9j→8o over an untouched `ArtworkKind`).
- **The notification sheet has never been seen with a launcher notification in it.** Both columns
  render and the Android one is populated from real notifications; the "Launcher" column has only
  ever shown its empty state, because nothing posted a toast during the run.
- **Keyboard and Touch glyphs are unverified on the device.** They compile and the badge matches
  the drawer's corner ratio; nobody has switched the family and looked.
- **The crossfade off the recents shelf composes both trees** for its 220ms. The else-branch is
  ~360 lines of crossbar. If that step ever hitches, that is why, and the fix is to fade the two
  backgrounds rather than the whole subtree.
- **The bottom bar's target is not styled apart from the verb.** 12g draws "Open" quiet and
  "All Games" loud; here both ride in one label so the shared renderer draws them. Changing it means
  teaching that renderer about a second label — not hand-drawing here.
- **The recents caticon jank** the owner reported could not be reproduced. Frame diff across the
  first press showed only the clock changing. It stopped happening after the caticon was hidden.

---

## Traps this run actually fell into

**A test that cannot fail.** Three separate times. A fit test asserting "six rows fit" when
`GridCells.Fixed(6)` *always* fits six by squeezing them. A confirm-order test that built its own
rows and asserted on them. A `boundsInRoot` check that passes because those bounds are clipped to
the parent. **Break the code and watch the named assertion go red, in the same sitting.**

**Edits that silently did not apply.** Twice a python replace with a passing assertion still left
the file unchanged after a later edit moved the anchor — the live-slot chain and the tap handler
both vanished from `XMBShell` that way, and the corner was inert on the device while the source
looked right. **After a multi-step edit, grep for the symbol you added.**

**`| head` kills gradle mid-suite.** A backgrounded `./gradlew test | grep | head` SIGPIPEs the
JVM and every in-flight test is recorded FAILED. Ten AudioSettings failures were read as real reds
for an hour. Write the log to a file.

**Reading a screenshot instead of the pixels.** The rail's scrim was declared broken off a resized
capture; it had been working the whole time. `im.getpixel()` settles it in one line.

