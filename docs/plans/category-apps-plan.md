# Apps in every category

**Status:** Not started · **Branch:** none yet · **Written:** 2026-09-20

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

# Phase 0 — find out what is actually broken

**Do this first, with the tablet connected. It decides the rest of the plan.**

- [ ] **0.1** Open Video. Is YouTube there? Is there an "Add Video Apps" row? Does it open a picker?
- [ ] **0.2** Same for Music and Spotify. Same for Photo.
- [ ] **0.3** Open Network. What is in it? Browsers should land there with no configuration.
- [ ] **0.4** Open Books. How is the reader chosen, and can more than one be pinned?
- [ ] **0.5** App Detail on any app: is "Move to Category" reachable, and does it stick?
- [ ] **0.6** Settings ▸ Emulators ▸ Custom: add a profile, use Test Launch. Does it work?

Record the answers here. Every green answer removes work below; a red one turns that task from a
feature into a bug, which is a different and usually smaller job.

**If 0.1–0.3 are green, tasks 1.x and 2.x are already done and only Phase 3, 4 and 5 remain.**

---

# Phase 1 — Video, Music, Photo (only if Phase 0 says they are broken)

- [ ] **1.1** Whatever 0.1–0.2 turned up. Likely candidates if something is wrong: the app is
      installed but hidden (`HideLocationType.CATEGORY`), the curated prefix does not match the
      installed package (regional or TV variants differ), or the category is empty because
      `appsForCategory` is filtered by something unexpected.
- [ ] **1.2** If a package simply is not curated, add it. That is a one-line table edit, and
      `AppClassifierTest` should gain a case naming the app so the table cannot silently lose it.

---

# Phase 2 — Network gets its apps

Network already receives browser apps directly, with no sub-row, because unlike Video and Music
it has no media of its own to list. That is the right shape and does not change.

- [ ] **2.1** Confirm on the device that an installed browser appears (0.3).
- [ ] **2.2** Add an **Add Network Apps** row, mirroring `ADD_VIDEO_APPS_ITEM_ID` exactly, so a
      browser the classifier missed can be added by hand rather than only by moving it from the
      App Drawer.

---

# Phase 3 — Quick Search

The one genuinely new thing, and the smallest.

**Files:** Modify `XMBViewModel.kt` (a Network root row + its select branch), reuse the existing
name-entry overlay (`collectionNameDialog` / `playlistNameDialog` are the shape).

- [ ] **3.1** A `QUICK_SEARCH` row at the top of Network, above the browsers.
- [ ] **3.2** Confirm opens the text overlay already used for naming a playlist. No new UI.
- [ ] **3.3** On submit, fire `Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)`.
      Android routes it to the user's chosen browser, so PSPLauncher never picks one.
- [ ] **3.4** Failure path: no activity resolves `ACTION_WEB_SEARCH` on a device with no browser.
      Catch `ActivityNotFoundException` and say so, rather than throwing on the launcher's own scope.
- [ ] **3.5** Test the query builder as a pure function: trimming, an empty query doing nothing,
      and a query with spaces and punctuation surviving intact.
- [ ] **3.6** Falsify: make it fire with a blank query, watch the named assertion go red.

**Open question for the device session:** whether the search row should also offer a URL. Typing
`news.bbc.co.uk` into a WEB_SEARCH intent searches for that string rather than opening it. A
second `ACTION_VIEW` branch when the text parses as a host is easy, but it is a decision about
what the row means.

---

# Phase 4 — Remote play

Boosteroid, Moonlight, Chiaki, Steam Link and friends are currently classified by nothing and land
unassigned, reachable only through the App Drawer.

- [ ] **4.1** Decide where they belong. **Network** is the honest answer — they are streaming
      clients, they need a connection, and a PSP owner would look under Network before Game. This
      is the one real design decision in this plan.
- [ ] **4.2** Add a curated remote-play group to `AppClassifier.CURATED`: Moonlight
      (`com.limelight`), Chiaki (`com.metallic.chiaki`), Steam Link (`com.valvesoftware.steamlink`),
      Boosteroid, GeForce NOW (`com.nvidia.geforcenow`), Xbox Cloud, Parsec, Rainway, Sunshine
      clients. **Verify each package name on the device rather than trusting this list** — a wrong
      prefix classifies nothing and fails silently, which is exactly the failure class the
      remediation plan keeps finding.
- [ ] **4.3** **GameNative is the complication.** It is already known to `PcLauncherCatalog` as a
      PC launcher, and PC games launch *through* it. If it is also curated as a remote-play app it
      appears in Network as itself AND backs a pile of entries under Game. Decide deliberately:
      probably leave it as a PC launcher only, since that is the richer relationship.
- [ ] **4.4** `AppClassifierTest` gains a case per package, so a rename cannot silently unclassify
      the whole group.

---

# Phase 5 — Books gets readers, plural

Music, Video and Photo each list several apps with an Add row. Books pins exactly one reader
(`XMBItemType.LIBRARY_READER`).

- [ ] **5.1** Confirm the current behaviour on the device (0.4) before changing it — a single
      pinned reader may be deliberate, since a book opens in one app and that is the whole
      interaction.
- [ ] **5.2** If plural is wanted: a `LIBRARY_APPS` row plus `ADD_LIBRARY_APPS_ITEM_ID`, mirroring
      Music. The existing pinned reader stays as the default used when opening a book; the new row
      is for launching a reader on its own.
- [ ] **5.3** Curate the common ones — Moon+ Reader, ReadEra, Librera, KOReader, Kindle, Kobo,
      Google Play Books — again verifying packages on the device.

---

# Phase 6 — Emulators

Already done, and listed here only so it is not re-litigated: custom emulator profiles are fully
user-editable with a dry-run Test Launch, and `RetroArchCoreScanner` offers exactly the cores that
are installed and deliberately never invents one.

- [ ] **6.1** Confirm on the device (0.6). If Test Launch works, close this phase.
- [ ] **6.2** The one real gap the council found here is not about adding emulators: the RetroArch
      settings copy was fixed in `0cc992d8`, and per-game overrides became clearable in the same
      commit. Nothing further planned.

---

# What this plan will not fix

The ES-DE reviewer's blocker stands and is unrelated to any of the above: **there is no text
search over the game library, and no jump-to-letter.** Quick Search in Phase 3 searches the web,
not your games. A library search is its own plan and it is the single change that would make this
a daily driver for a large library.
