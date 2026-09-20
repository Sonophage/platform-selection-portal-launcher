# Apps in every category

**Status:** Phases 1 to 5 done and device-checked. Phase 6 was already done. Only the ES-DE reviewer's blocker below is left, and it is a plan of its own. · **Written:** 2026-09-20

## Goal

Let every category hold the external apps that belong to it — YouTube and Plex under Video,
Spotify under Music, readers under Books, browsers and remote-play clients under Network — and
give Network the two things it has never had: a search box, and somewhere for streaming clients
to live.

## The first thing to know

**Most of this already exists.** Before writing a line, Phase 0 checks what actually works on the
device, because it may delete two thirds of this plan.

| Asked for | What is already there |
|---|---|
| YouTube in Video | `AppClassifier.CURATED` maps `com.google.android.youtube` → Video, plus Netflix, Plex, VLC, Kodi, Twitch, Crunchyroll, Disney+, HBO, Prime Video |
| Spotify in Music | `com.spotify.music` → Music, plus YT Music, Apple Music, Deezer, SoundCloud, Pandora, TuneIn, Poweramp |
| Browsers in Network | Chrome, Firefox, Brave, Edge, Opera, DuckDuckGo, Samsung Internet, Kiwi, Tor |
| Add apps by hand | `ADD_MUSIC_APPS_ITEM_ID`, `ADD_VIDEO_APPS_ITEM_ID`, `ADD_PHOTO_APPS_ITEM_ID` each open the app picker |
| Move an app between categories | `AppCategoryRepository.moveToCategory(pkg, categoryId)`, reachable from App Detail |
| All the emulators | `EmulatorProfileEditorScreen` takes package, activity, action, extras, flags, category, MIME, core path and supported platforms — with **Test Launch with a ROM** |

`AppClassifier` resolves in three steps: curated package list, then Android's declared
`ApplicationInfo.category`, then label keywords. Its own comment says the result is only a
starting point and a user override wins permanently.

So the real question is not "how do we build this" but "why is it not showing up", and that is a
device question, not a code one.

## Architecture

Nothing new is invented. The three genuine gaps ride the machinery that already exists:

* A **curated group** is a list of package prefixes in `AppClassifier.CURATED`. Remote-play
  clients become one.
* An **apps row** is an `XMBItemType` plus a `*AppItems()` builder plus an `ADD_*_ITEM_ID` that
  opens the app picker. Books gets one, shaped exactly like Music's.
* **Quick Search** is the one genuinely new thing, and it is small: a row that opens the existing
  text-entry overlay and fires `Intent.ACTION_WEB_SEARCH`.

## Tech stack

Kotlin, Compose, Hilt, DataStore. `AppClassifier` and `AppCategoryRepository` are in
`feature-appbar`; the category rows are built in `XMBViewModel`; the emulator editor is in
`feature-settings`.

## Non-goals

An in-app browser. An in-app reader. Scraping metadata for streaming apps. Deep-linking into a
streaming app's library. Replacing Android's app picker.

---

# Phase 0 — ANSWERED on the device, 2026-09-20

The apps are all there. They are one level down, behind a row: Video root shows **Video Apps**,
which drills into YouTube, Plex and the rest. Same shape for Music and Photo.

So nothing is broken, and Phases 1 and 2 as originally written are void. What is left is the
thing the owner actually asked for, which is one fewer button press.

---

# Phase 1 — apps at the root of their category, Add at the end ✅

**The ask:** Video's root should list the video apps directly, with **Add Video Apps** as the last
row, instead of a **Video Apps** row that drills into them.

**Files:** Modify `XMBViewModel.kt` — `videoRootItems()` (:2553), `musicRootItems()`,
`photoRootItems()`, and the select branches that currently open the sub-view.

- [x] **1.1** Video: `videoRootItems()` appends `videoAppItems()` after Collections and Video
      Libraries, and ends with the existing `ADD_VIDEO_APPS_ITEM_ID` row. The `VIDEO_APPS` drill
      row goes.
- [x] **1.2** Music and Photo the same way. Music's `MusicNav.MusicApps` and the photo equivalent
      become unreachable — delete them with their select branches, or this is dead navigation of
      exactly the kind the remediation plan has been removing all week.
- [x] **1.3** Keep the ordering rule explicit and the same in all three: the category's own media
      first, then its apps, then Add. A user scanning down should hit content before tools.
- [x] **1.4** Check the empty case. A category with no apps installed must show **Add** and not a
      lonely empty row, and `emptyCategoryItem`'s "No video apps found." message may now be wrong
      — the category is not empty, it has its media rows.
- [x] **1.5** Cursor restore is keyed per view (`viewCursorKey`). Removing a drill level changes
      those keys; make sure backing out of Video does not land on a remembered index from a list
      that no longer exists. The existing clamp should cover it — verify rather than assume.
- [x] **1.6** Device check: one press from the Video column to launching YouTube.

**Watch for:** the drill-out ladder. `DrillOutStep` has rungs for the music and video sub-views;
removing the apps sub-view must remove its rung too, or Back climbs a level that is not there.

---

# Phase 2 — Network gets an Add row ✅

Already done before this plan was written, and confirmed on the device: Network's generic
category loader appends `addAppsItem()` to every app section, so the "Add Apps" row was already
there. No code changed.

- [x] **2.1** Add an **Add Network Apps** row, last, mirroring `ADD_VIDEO_APPS_ITEM_ID`, so a
      browser or streaming client the classifier missed can be added by hand.

---

# Phase 3 — Quick Search ✅

The one genuinely new thing, and the smallest.

**Files:** Modify `XMBViewModel.kt` (a Network root row + its select branch), reuse the existing
name-entry overlay (`collectionNameDialog` / `playlistNameDialog` are the shape).

- [x] **3.1** A `QUICK_SEARCH` row at the top of Network, above the browsers.
- [x] **3.2** Confirm opens the text overlay already used for naming a playlist. No new UI.
- [x] **3.3** On submit, fire `Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)`.
      Android routes it to the user's chosen browser, so PSPLauncher never picks one.
- [x] **3.4** Failure path: no activity resolves `ACTION_WEB_SEARCH` on a device with no browser.
      Catch `ActivityNotFoundException` and say so, rather than throwing on the launcher's own scope.
- [x] **3.5** Test the query builder as a pure function: trimming, an empty query doing nothing,
      and a query with spaces and punctuation surviving intact.
- [x] **3.6** Falsify: make it fire with a blank query, watch the named assertion go red.

**Decided: it does both.** `quickSearchActionFor` tells an address from a search, and the row
means "get me there". A bare host is opened with `https://` prepended; anything with a scheme is
taken at its word; anything with a space, or whose last dotted segment is not letters, stays a
search. That last rule is what keeps `3.5`, `v1.2.3` and `mario64.z64` out, and it is deliberately
not a list of real top-level domains: such a list is wrong the week it is written, and the cost of
guessing wrong here is one search result page.

---

# Phase 4 — Remote play ✅

Boosteroid, Moonlight, Chiaki, Steam Link and friends are currently classified by nothing and land
unassigned, reachable only through the App Drawer.

- [x] **4.1** Decide where they belong. **Network** is the honest answer — they are streaming
      clients, they need a connection, and a PSP owner would look under Network before Game. This
      is the one real design decision in this plan.
- [x] **4.2** Done, and the device check found things this list had wrong. `pm list packages` on
      the tablet showed **`com.limelight`** and **`com.boosteroid.streaming`** installed, so those
      two are verified rather than guessed; the tablet also carries `com.limelight.noir`
      (Artemis, a Moonlight fork) which the `com.limelight` prefix catches for free, and both it
      and Boosteroid were seen classified into Network on screen. Rainway and Sunshine are NOT in
      the list: Sunshine is a host rather than a client, and Rainway's Android client is gone. The
      remaining five carry each app's published id and have not been seen on a device here, which
      `AppClassifierTest` records package by package.
- [x] **4.3** Decided: GameNative stays a PC launcher only, and a test asserts it. **It is the complication:** It is already known to `PcLauncherCatalog` as a
      PC launcher, and PC games launch *through* it. If it is also curated as a remote-play app it
      appears in Network as itself AND backs a pile of entries under Game. Decide deliberately:
      probably leave it as a PC launcher only, since that is the richer relationship.
- [x] **4.4** `AppClassifierTest` gains a case per package, so a rename cannot silently unclassify
      the whole group.

---

# Phase 5 — Books gets readers, plural ✅

Music, Video and Photo each list several apps with an Add row. Books pins exactly one reader
(`XMBItemType.LIBRARY_READER`).

- [x] **5.1** Confirmed on the device: the Library root showed Shelves / Books / Add Book Folder
      and one pinned reader, with no way to reach a second reader from the XMB.
- [x] **5.2** Done, and without a `LIBRARY_APPS` drill row, since Phase 1 removed that shape
      everywhere else in the same commit. `bookAppItems()` publishes the apps at the root with
      `ADD_LIBRARY_APPS_ITEM_ID` last. The pinned reader stays what it was: the app a book opens
      *in*. These rows launch a reader on its own.
- [x] **5.3** Not curated. The Library column's Add Book Apps row lets a reader be picked by
      hand, and a curated reader list was not needed to make that work.
- [ ] ~~5.3~~ Curate the common ones — Moon+ Reader, ReadEra, Librera, KOReader, Kindle, Kobo,
      Google Play Books — again verifying packages on the device.

---

# Phase 6 — Emulators ✅

Already done, and listed here only so it is not re-litigated: custom emulator profiles are fully
user-editable with a dry-run Test Launch, and `RetroArchCoreScanner` offers exactly the cores that
are installed and deliberately never invents one.

- [x] **6.1** Confirm on the device (0.6). If Test Launch works, close this phase.
- [x] **6.2** The one real gap the council found here is not about adding emulators: the RetroArch
      settings copy was fixed in `0cc992d8`, and per-game overrides became clearable in the same
      commit. Nothing further planned.

---

# What this plan will not fix

The ES-DE reviewer's blocker stands and is unrelated to any of the above: **there is no text
search over the game library, and no jump-to-letter.** Quick Search in Phase 3 searches the web,
not your games. A library search is its own plan and it is the single change that would make this
a daily driver for a large library.
