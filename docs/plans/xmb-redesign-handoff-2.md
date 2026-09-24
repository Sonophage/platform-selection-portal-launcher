# XMB redesign — handoff, 2026-09-24

Picks up from `xmb-redesign-plan.md`. **Tier A and Tier B are done. Tier C has not been started.**
Two items that were not in the plan at all (12b, 12g) were added and finished.

Everything below was driven on the Pocket FIT over adb and looked at, not inferred from
the source. Where something is unverified it says so.

---

## Run `./gradlew test` before trusting anything here

    find . -path "*test-results*" -name "TEST-*.xml" -delete
    ./gradlew test --continue

**Delete the result XML first.** A module whose task is up-to-date leaves its last XML in place,
and reading that is how a red suite looked green for three commits earlier in this project. As of
this handoff: **2643 tests across 16 modules, 0 failures, 0 errors, 8 skipped** (7 theme-kit,
1 feature-artwork, all pre-existing).

The device: `export ANDROID_SERIAL=01411YEF01035627` over USB. `tcpip` died on the reboot between
the two halves of this run, which is exactly what `memory/pocket-fit-wireless-adb.md` says it does;
the USB serial came back when the cable did.

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
| — | **Type to search**: any printable character on the XMB opens Search carrying it | `MainActivity.dispatchKeyEvent`, `XMBViewModel.openSearchTyping` |
| — | The settings picker takes a tap — options and scrim were both inert | `SettingsScaffold.SettingsPickerPanel` |

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

**A fix that was itself the next bug.** Binding Shift/Space/Tab/Q/E so the keyboard glyphs would
stop lying made those five keys *untypable* — `GamepadInputHandler` consumes a bound keycode before
any text field sees it. The hint bar became honest and the search box went deaf in the same commit.
**When you bind an input, ask what else was already reading it.**

**"It doesn't work over adb" was a real bug.** The controller-type picker was written off as
something injected input could not drive, and the Keyboard and Touch glyphs went a whole run
unverified because of it. The panel simply had no `clickable` on anything — no finger could drive it
either. *A harness that cannot reach a control is a claim about the control, not about the harness,
until you have read the control.*


---

# Health pass — same day, after the device went off

Ran after the redesign work, then again after the keyboard and touch work. `./gradlew test`:
**2643 tests, 0 failures, 0 errors, 8 skipped.**

## Lint: 55 errors → 5

Forty-seven of the original 55 were ONE issue repeated — media3's `UnstableApi` — which is enough
noise to bury the ones that were real. It took three attempts to silence correctly and the
difference is worth knowing:

| Attempt | Result |
|---|---|
| `@file:OptIn(UnstableApi::class)` | Compiles, silences **nothing**. `UnstableApi` is not `@RequiresOptIn`, and Kotlin warns that the opt-in has no effect. |
| `@UnstableApi` on the class | Satisfies lint here and **propagates** — every injection site of `VideoSnapTranscoder` became an unstable-API usage. 44 errors in one file became errors in four. |
| `@Suppress("UnsafeOptInUsageError")` | Local. Says this file knows what it calls, and nothing outside has to know anything. |

**The five that remain are all benign:** a `mutableStateOf` in a test harness, two permissions lint
cannot verify but the manifest declares (`PACKAGE_USAGE_STATS` is appops, the call is wrapped), and
`QUERY_ALL_PACKAGES`, which a launcher needs to enumerate apps.

## Two crashes that were live

- **`EpubMetadata`** called `ByteArrayOutputStream.toString(Charset)` — API 33, against minSdk 29.
  A `NoSuchMethodError` while reading a book's metadata on Android 10 through 12.
- **`EmulatorIntentResolver`** called `isExternalStorageManager` — API 30 — and survived on Android
  10 *only* because Kotlin's `runCatching` catches `Throwable`. A linkage error caught by a net
  cast for something else entirely.

## A collector that stacked

`startMenuMusicIfWanted` ran `repeatOnLifecycle` from `onResume`. That suspends until DESTROYED, so
every resume started **another** collector on the same flow, each racing the others to start the
music. It belongs in `onCreate`. The comment claiming the scope was torn down at `onStop` went with
it — `lifecycleScope` is cancelled at `onDestroy`.

## Build: 1478ms → 710ms on an up-to-date `:app:assembleDebug`

The configuration cache is on, which Gradle had been suggesting on every invocation. Heap 2048m →
6144m on a 30GB machine.

**The first measurement of this was worthless** and is worth remembering: the builds were *failing*
in 591ms and the failure was piped to `/dev/null`. `copyDebugToDebugDir`'s `rename` lambda closed
over the build script rather than over a string, which the cache cannot serialise. Both numbers
above were re-measured with the output read.

## Keyboard pass

The Keyboard glyph family shipped naming **Shift, Space, Tab, Q and E while none of them reached
the launcher** — and **Escape**, the key a keyboard user reaches for first, was bound to nothing.
`KeyboardPromptsAreBoundTest` holds that pair together: a glyph table in core-ui names a key, a
binding table in core-domain decides what it does, and nothing joined them.

**Binding those five was then wrong for a second reason.** `GamepadInputHandler` runs ahead of the
view tree, so a bound keycode never reaches a text field — Q, E, Space and Shift became keys you
could not type. The default set is now **keys that produce no character**: Escape, Tab, F2, F3,
PageUp, PageDown. The test gained `no default binding claims a key that types a character`,
falsified by binding `KEYCODE_S`, which reports `these bindings swallow a character key: [47]`.

Arrows always worked — Android delivers a real keyboard's arrows as `DPAD_*`.

**Driven from a keyboard, on the device**, each key pressed and the result read off the tree:

| Key | Action | What it did |
|---|---|---|
| Escape | BACK | Closed the context menu; on the XMB root it opens Apps, which is what `Ⓑ Apps` says |
| Tab | OPEN_SEARCH | Opened Search |
| F2 | CHANGE_SORT | Cycled the label `Recently Played → Date Added → Title` |
| F3 | OPEN_CONTEXT_MENU | Opened the app drawer's menu (`Add to Cross Bar`, `App Info`, …) |
| PgUp / PgDn | PREV/NEXT_CATEGORY | Stepped the drawer's tabs `Recently Used ⇄ Apps` |
| arrows | DPAD_* | Throughout |

**PgUp/PgDn do nothing on the XMB root, and that is correct.** The XMB shell never handles
PREV/NEXT_CATEGORY — it is a tab or page step, owned by the app drawer, settings, Game Details and
the photo and video viewers. Those screens' hint bars name it; the XMB's does not, so nothing is
lying. Worth knowing before someone "fixes" a dead key.

## Type to search

Typing any printable character on the XMB opens Search carrying that character. On this device the
keyboard is the hardwired input, and reaching for Tab first is a press that says nothing.

`MainActivity.dispatchKeyEvent` runs the gamepad handler first, then `openSearchOnTypedCharacter`:
ACTION_DOWN, no repeat, no Ctrl/Alt/Meta, `unicodeChar != 0`, not an ISO control. That order is what
keeps the six bound keys above from ever being read as text.

**`SearchScreen`'s field is a `TextFieldValue`, not a `String`.** With a `String` the selection stays
at 0 while the seeded text is inserted around it, so typing `S K Y R` produced `kyrs`. The caret is
set explicitly to `TextRange(query.length)`.

## Touch pass

Hiding the Last Played caticon fixed a controller problem and created a touch one: a finger has no
equivalent of "step left off Emulation", so the shelf became a page with no door. **The slot
returns whenever the last input was a finger** and goes again on the next button press.

Everything else added this run takes a tap — the rail, the pills, the notification sheet, the
strip's live corner, and the bottom bar's prompts (through the shared renderer, which is why
`XmbHintBar` has no `clickable` of its own).

**The settings picker could not be used by a finger at all.** No `clickable` on the option rows, and
none on the scrim: a touch user could open one and neither choose a value nor get back out. The
controller path — cursor, then SELECT — was the only way through, and it was complete, which is why
this survived. Options take a tap directly (a finger has no cursor, so the tap *is* the choice) and
the scrim dismisses, the way BACK does.

`feature-settings` has no Compose UI test rig — no `createComposeRule` anywhere in the module — so
this is covered by the device check only. Standing one up for three lines of `clickable` was judged
worse than saying so here.

## Both glyph families, seen on a screen — 2026-09-24

That picker bug is why they never had been. With it fixed, the family was switched by touch and the
bottom bar photographed:

- **Keyboard** — `PgUp Prev · PgDn Next · Esc Back · Enter Launch · F3 Options · F2 Search`. Every
  one of those keys is really bound; the bar is not naming anything it cannot do.
- **Touch** — `Tap Enter · Back Back`.
- Keycaps render at the height of the verb beside them, which was the sizing complaint.

The setting was put back to **PlayStation** through the same picker. The datastore was never
byte-edited — it is a length-delimited protobuf and an edit corrupts it.
