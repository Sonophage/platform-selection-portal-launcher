# Last Played

**Status:** Not started · **Branch:** none yet · **Written:** 2026-09-20

## Goal

A **Last Played** section on the crossbar: the games you actually played, most recent first, each
one wearing its own art behind the whole screen exactly as it does when you hover it in the games
list.

## Why this is small now, and was impossible last week

Most of it is already built and has been sitting dormant, because nothing ever wrote the column it
depends on.

| Piece | Where | State |
|---|---|---|
| `last_played_at`, `total_play_time_millis` | `GameEntity`, indexed | **Now written** — `90fe587f` gave `recordPlaySession` its first caller |
| `play_sessions` table + DAO | `PlaySessionDao` | Built, now populated |
| `GameDao.observeRecentlyPlayed(limit)` | `GameDao:149` | Built, **zero callers** |
| `observeRecentPlatforms` + `RecentPlatform(platform, lastPlayedAt, recentGames)` | `GameRepositoryImpl:50` | Built, unused |
| `BuiltInCategory.RECENTLY_PLAYED` | `Category.kt:37` | The id exists |
| Protected from deletion | `CategoryRepositoryImpl:156` | Already in `PROTECTED_BUILTINS` |
| Per-item art backdrop + accent | `XMBShell`, `XMBItem.backdropArt` | Built, and applies to any row with art |

So the section is a category row, a loader, and almost nothing else. The art behaviour the owner
asked for — "the art of the last played game like the hover when looking through the game list" —
needs **no work at all**: `focusedItemBackdrop` and `focusedItemAccentArgb` already fire for any
focused row that has art, in any category, since `f716af25`.

The one genuinely absent piece: `RECENTLY_PLAYED` is **not** in `BUILT_IN_CATEGORIES`, so the
category has never appeared on the bar.

## Architecture

A built-in category whose items are games, exactly like a Memory Card's — same `XMBItem`, same
`toXmbItems()`, same detail page on confirm. It differs only in where its list comes from
(`observeRecentlyPlayed` rather than a platform query) and in that it is never sorted by the user.

## Tech stack

Kotlin, Room, Compose. No new dependency, no schema change.

## Non-goals

Play-time leaderboards. A "most played" section. Per-session history UI. Clearing individual
entries (the whole section is derived; hiding a game already works through `HiddenPlacement`).

---

# Task 1 — put the category on the bar

**Files:** Modify `core/core-domain/.../model/Category.kt`

- [ ] **1.1** Add `RECENTLY_PLAYED` to `BUILT_IN_CATEGORIES`, named **Last Played**, icon
      `ic_recent` (confirm an icon key exists; add one to the slot vocabulary if not — and note
      `IconSlots` / `DefaultSlotGlyph` / `StudioIconSet` are a four-way mirror that must all agree).
- [ ] **1.2** Position: appended past the others, the way Library was, so an established database
      gains it without colliding with positions its rows already hold.
- [ ] **1.3** `CategoryBarFallbackTest` covers ids and positions already and will fail if the new
      entry collides. Run it rather than assuming.

**Decision for the device session:** where it should sit once reordered. Left of Game is the
natural home for "what I was doing", but that is a taste call.

---

# Task 2 — fill it

**Files:** Modify `XMBViewModel.kt` — a `BuiltInCategory.RECENTLY_PLAYED` branch in
`loadItemsForCategory`.

- [ ] **2.1** Collect `gameRepository.observeRecentlyPlayed(limit)` and publish through the same
      `publishGameItems` path the platform cards use, so cursor memory, hiding and the context
      menu all work with no new code.
- [ ] **2.2** Limit: start at 20. It is a "what was I doing" shelf, not an archive.
- [ ] **2.3** Never user-sorted. `activeSortModes` must not offer a sort cycle here — the order IS
      the meaning. Check `sortModeFor`, which is a `when` over category with an `else` that
      silently hands back the GAMES mode.
- [ ] **2.4** Empty state: a first-run device has played nothing. It needs its own message —
      "Nothing played yet" — not "No games assigned."
- [ ] **2.5** Respect `HideLocationType`. Hiding a game from All Games should almost certainly hide
      it here too; decide deliberately and write the decision down.

---

# Task 3 — the art

- [ ] **3.1** Nothing to build. Confirm on the device that focusing a row here brings up its
      backdrop and retints the wave, exactly as in a platform card.
- [ ] **3.2** Confirm the **Backdrop & Tint** switch (`e5bb5416`) turns it off here too. It gates
      `XMBShell` centrally, so it should — verify rather than assume.

---

# Task 4 — tests

- [ ] **4.1** The ordering rule as a pure function if any mapping is added; otherwise lean on
      `observeRecentlyPlayed`'s `ORDER BY last_played_at DESC`, which is SQL and already correct.
- [ ] **4.2** A DAO test: three games with known `last_played_at`, assert the order and the limit.
      `GameDao` has no coverage of this query today.
- [ ] **4.3** Falsify by reversing the `ORDER BY`.
- [ ] **4.4** A test that a game with a null `last_played_at` never appears — the query already
      filters it, and that filter is what keeps a fresh library's section empty rather than full
      of games in arbitrary id order.

---

# What to watch

**The column is only as good as its writer.** `last_played_at` is stamped when the emulator gives
the launcher back the foreground, and only then — a launch that never foregrounded is deliberately
not play time (`LaunchDispatcher.onHostResumed`). So a game that crashed on boot will not appear
here, which is correct and will still look like a bug the first time it happens.

**Existing installs start empty.** Nothing wrote this column before `90fe587f`, so the section is
blank until games are played. Worth saying in the empty state rather than letting it read as
broken.
