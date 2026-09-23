# PSPLauncher

**A controller-first Android game launcher inspired by the PSP's XMB (Cross Media Bar).**

A horizontal category bar crosses a vertical item list — the **crossbar** — and replaces your
Android home screen as a single front end for ROM emulation, Android games, PC-layer titles
(Winlator), native apps, and your music, video and photo libraries.

<p align="center">
  <img src="docs/screenshots/recent-shelf.jpg" alt="PSPLauncher — the Recent shelf, cover rail and hero art" width="720">
</p>

<p align="center">
  <b>Latest: 1.7.0</b> &nbsp;·&nbsp;
  <a href="https://github.com/Sonophage/platform-selection-portal-launcher/releases">Releases</a>
  &nbsp;·&nbsp; Side-loaded APK (not on the Play Store)
  &nbsp;·&nbsp; Desktop <b>Theme Studio</b> companion
</p>

> This document is the **user manual**. It walks you from install to daily use, feature by
> feature. Building the project from source is covered last, in
> **[For Developers](#7-for-developers)**. For a deep architectural tour of the codebase, see
> **[ARCHITECTURE.md](ARCHITECTURE.md)**.

---

## Screenshots

*Shot on an AYANEO Pocket FIT Elite at 1920x1080, on a real 151-game library, in September 2026.
Every capture here is from the current `main`; older ones were deleted rather than left to drift.
Game artwork and app icons belong to their respective owners.*

### The crossbar

| | |
|:---:|:---:|
| <img src="docs/screenshots/recent-shelf.jpg" width="420"> | <img src="docs/screenshots/home-emulation.jpg" width="420"> |
| The Recent shelf reorders on every visit, and its cover rail hides until LEFT brings it back | The Emulation column — All Games, Favourites, and the consoles under them |
| <img src="docs/screenshots/library-column.jpg" width="420"> | <img src="docs/screenshots/video-column.jpg" width="420"> |
| Books sit beside the reader that opens them | Video — scanned files, and collections of what you were watching |
| <img src="docs/screenshots/music-column.jpg" width="420"> | <img src="docs/screenshots/photo-column.jpg" width="420"> |
| Music — songs, and Artists split out of the library's own credit lines | Photos and albums |
| <img src="docs/screenshots/network-column.jpg" width="420"> | <img src="docs/screenshots/search-typing.jpg" width="420"> |
| Network — quick search and pinned sites | Search shows results while the keyboard is still up, and opening one plays it |

### Launching

| | |
|:---:|:---:|
| <img src="docs/screenshots/launch-disc.jpg" width="420"> | <img src="docs/screenshots/settings-launch-cues.jpg" width="420"> |
| The thing you picked becomes a disc wearing its own cover, sinks and spins, and hands over as it leaves | Both of the ceremony's cues are yours to assign, alongside every other sound |

### First run

| | |
|:---:|:---:|
| <img src="docs/screenshots/setup-splash.jpg" width="420"> | <img src="docs/screenshots/setup-welcome.jpg" width="420"> |
| Setup opens on the mark and the wave, and one press takes you in — first run only | A place rather than a settings page: black, no rail, nothing borrowed from the wallpaper |
| <img src="docs/screenshots/setup-permissions.jpg" width="420"> | <img src="docs/screenshots/setup-personalize.jpg" width="420"> |
| Three optional grants, together, because all three are system screens | Theme, sound, boot logo and layout as four doors that open the real screens and come back |
| <img src="docs/screenshots/setup-retroarch-cores.jpg" width="420"> | |
| RetroArch's page, which appears only when RetroArch is installed — whichever ABI it ships as | |

### Settings

| | |
|:---:|:---:|
| <img src="docs/screenshots/settings-one-row.jpg" width="420"> | <img src="docs/screenshots/settings-library-manager.jpg" width="420"> |
| Settings opens on its seven sections rather than one long list | Library Manager — ROM roots, per-console cards, scan-all passes |
| <img src="docs/screenshots/settings-menu-music.jpg" width="420"> | <img src="docs/screenshots/theme-color-scheme-picker.jpg" width="420"> |
| Menu music: one track, looping, that gives way the moment anything else wants the speaker | Colour Scheme, previewing live on the real crossbar |

---

## Table of contents

1. [What is PSPLauncher?](#1-what-is-psplauncher)
2. [Getting started](#2-getting-started)
   - [2.1 Requirements](#21-requirements)
   - [2.2 Install the APK](#22-install-the-apk)
   - [2.3 Set PFP as your home screen (Optional)](#23-set-pfp-as-your-home-screen-optional)
   - [2.4 Grant permissions](#24-grant-permissions)
   - [2.5 First-run setup](#25-first-run-setup)
3. [Navigation & controls](#3-navigation--controls)
4. [Feature guide](#4-feature-guide)
   - [4.1 The Game library](#41-the-game-library)
   - [4.2 Setting up a console (Memory Card)](#42-setting-up-a-console-memory-card)
   - [4.3 Emulators](#43-emulators)
   - [4.3b Last Played](#43b-last-played)
   - [4.4 Favorites & Collections](#44-favorites--collections)
   - [4.5 Game & app options (△)](#45-game--app-options-)
   - [4.6 Artwork & the Artwork Studio](#46-artwork--the-artwork-studio)
   - [4.7 Icon display modes & video snaps](#47-icon-display-modes--video-snaps)
   - [4.8 Android apps & non-gaming categories](#48-android-apps--non-gaming-categories)
   - [4.9 The App Drawer](#49-the-app-drawer)
   - [4.10 Music, Video & Photo](#410-music-video--photo)
   - [4.10b Finding things](#410b-finding-things)
   - [4.11 Categories](#411-categories)
   - [4.12 Themes & personalization](#412-themes--personalization)
   - [4.13 Custom XMB icons](#413-custom-xmb-icons)
   - [4.14 Motion wallpapers](#414-motion-wallpapers)
   - [4.15 Interface sounds & boot videos](#415-interface-sounds--boot-videos)
   - [4.16 Adjusting the layout for your screen](#416-adjusting-the-layout-for-your-screen)
   - [4.17 Backup & restore](#417-backup--restore)
   - [4.18 Settings reference](#418-settings-reference)
5. [Permissions & privacy](#5-permissions--privacy)
6. [Troubleshooting](#6-troubleshooting)
7. [For Developers](#7-for-developers)
   - [7.1 Tech stack](#71-tech-stack)
   - [7.2 Prerequisites](#72-prerequisites)
   - [7.3 Get the code & open it in Android Studio](#73-get-the-code--open-it-in-android-studio)
   - [7.4 Build variants](#74-build-variants)
   - [7.5 Run & debug from Android Studio](#75-run--debug-from-android-studio)
   - [7.6 Release signing](#76-release-signing)
   - [7.7 Command-line builds & the `dist` task](#77-command-line-builds--the-dist-task)
   - [7.8 The Theme Studio desktop app](#78-the-theme-studio-desktop-app)
   - [7.9 Module structure](#79-module-structure)
   - [7.10 Testing](#710-testing)
8. [Credits](#8-credits)
9. [License](#9-license)

---

## 1. What is PSPLauncher?

PSPLauncher is a **home-screen replacement** for Android handhelds, tablets and phones that
gives your whole library the look and feel of a PlayStation Portable. It is a unified game
frontend: ROM emulation, Android games, PC-layer titles and native apps brought together under one
cohesive interface that feels like the golden era of handheld gaming. Everything is one crossbar
away and fully controller-navigable:

- **Games** — ROMs launched through the emulators you already have installed, Android games, and
  PC-layer titles (Winlator and friends), unified under one **Game** category.
- **Media** — Music, Video and Photo sections that scan folders you choose.
- **Apps** — your installed apps, organized into categories you design.
- **Personalization** — a deep theme system (custom wallpapers, one-color palettes, imported PSP
  themes), replaceable icons, sounds and boot videos, plus a desktop **Theme Studio** for authoring.

It is **local-first**: no account, no telemetry, and the network is only touched when *you* ask it
to fetch artwork. See [Permissions & privacy](#5-permissions--privacy).

---

## 2. Getting started

### 2.1 Requirements

| | |
|---|---|
| **Android version** | 10 (API 29) or newer |
| **Form factor** | Phones, handhelds, tablets, and foldables (the layout adapts to each) |
| **Input** | A game controller is recommended; full touch navigation is also supported |
| **Emulators** | Installed separately — PFP launches them, it does not emulate anything itself |

### 2.2 Install the APK

PFP is distributed as a **side-loaded APK** (it is not on the Google Play Store).

1. Download `PSPLauncher-<version>.apk` from
   [Releases](https://github.com/Sonophage/platform-selection-portal-launcher/releases).

   > **1.7.0 ships a debug build** (`PSPLauncher-1.7.0-debug.apk`). It installs under
   > `com.psplauncher.launcher.debug`, so it sits *beside* an existing PSPLauncher with its own
   > library rather than upgrading it, it is marked debuggable, and it is signed with the Android
   > SDK's public debug key. Build a signed release yourself instead:
   > [For Developers](#7-for-developers).
2. Open the file on your device. Android will ask you to allow installs from your browser or file
   manager the first time — approve it.
3. Tap **Install**.

> Building the APK yourself instead? See [For Developers](#7-for-developers).

### 2.3 Set PFP as your home screen (Optional)

PFP registers as an Android **HOME** launcher.

1. Press the **Home** button.
2. When Android asks which launcher to use, pick **PSPLauncher** and choose **Always**.

You can change this later under *Android Settings ▸ Apps ▸ Default apps ▸ Home app*.

> A few features (importing game shortcuts from other launchers, capturing pinned shortcuts)
> require PFP to be the **active default launcher**.

### 2.4 Grant permissions

PFP asks for permissions **only when a feature needs them**:

- **Notifications** (Android 13+) — lets background scans and artwork fetches report progress, and
  lets other apps ask you to confirm a game shortcut before it is added.
- **Usage access** (optional) — powers the "Recently Used" filter in the App Drawer.

Full detail in [Permissions & privacy](#5-permissions--privacy).

### 2.5 First-run setup

On a fresh install PFP opens a guided **setup wizard**:

1. **Welcome** — what the wizard will set up.
2. **ROM Roots** — grant one or more root folders; consoles live in subfolders under them.
3. **Music / Video / Photo** — one optional root per media section (multi-root supported).
4. **Artwork** — the artwork library folder, with an embedded import offer.
5. **Online Services** — connect SteamGridDB and IGDB, plus the ScreenScraper *user*
   account (each optional; IGDB and ScreenScraper credentials are tested live).
6. **Vita Data Folder** & **RetroArch** — offered only when those apps are installed.
7. **Finish.**

Every step can be skipped and everything it configures is the same setting you can reach
later in Settings — the wizard is just a shortcut. You can re-run it any time from
**Settings ▸ Re-Run Setup Wizard**. Upgrading installs that are already configured never
see it.

> ScreenScraper additionally requires a **developer ID/password** pair. PFP ships with an
> obfuscated built-in pair, so scraping works out of the box — nothing to enter or configure.

With a ROM root set, the fastest way to load your library is
**Settings ▸ Library ▸ Library Manager ▸ Add ROM Root** — granting a root walks its
ES-DE system folders straight away and creates a Memory Card for every console that
contains games (including a **Windows Memory Card** for PC games). There is no separate
detect step: a newly added root is not useful until that pass runs, so it always does.
Full detail in
[Setting up a console](#42-setting-up-a-console-memory-card).

---

## 3. Navigation & controls

PFP is built for a controller but works fully with touch.

| Action | Controller | Touch |
|---|---|---|
| Move between items | D-Pad / Left Stick | Tap an item |
| Switch category (left / right) | D-Pad ◀ ▶ | Tap the category |
| Select / launch / open | **A / ✕** | Tap |
| Back / close / exit a folder | **B / ◯**, or D-Pad ◀ (see below) | On-screen Back / left-edge swipe / swipe left |
| Options (context) menu | **Y / △** (or long-press) | Long-press |
| Switch App-Drawer tabs | **L1 / R1** | Tap a tab |
| Search your libraries | **Select** | Magnifier, bottom-right |
| Confirm in pickers | **Start** | Confirm button |

**D-Pad ◀ backs you out.** Inside a folder, a flyout or a settings screen, LEFT leaves one level —
but only where LEFT is not already doing something (stepping into a row's inline buttons, or
adjusting a slider), so nothing it used to do is taken away. Turn it off in
*Settings ▸ Controller ▸ Left Backs Out*. The touch equivalent — a leftward swipe inside a folder or
flyout — is always on, like the left-edge pull. On a Settings screen or a wizard page, a drag
anywhere scrolls the page, including on the header and the footer.

The **horizontal bar** is your categories — by default **Settings, Photo, Music, Video,
Last Played, Game, Network, App Store, Library**, plus any custom ones. The **vertical list** under the selected category is its
items. While any menu, settings screen, picker or dialog is open, the crossbar is locked — input
only drives the overlay on top. Every binding is remappable in *Settings ▸ Controller*.

---

## 4. Feature guide

### 4.1 The Game library

Selecting **Game** shows, in order:

1. **All Games** — every real game across all consoles, aggregated. Only actual games appear here;
   Android / Video / Music apps never show up automatically.
2. **Favorites** — appears directly under All Games **only when you have favorited at least one
   game**, and hides again when you have none.
3. **Your Collections** — user-made folders (see [4.4](#44-favorites--collections)).
4. **Memory Cards** — one row per console you have configured.

Open All Games, Favorites, a collection, or a console to drill in; press **B / ◯** to go back. On
wide and foldable screens the crossbar slides to the left edge while drilled in, giving the game
list and its artwork the center-right of the screen.

### 4.2 Setting up a console (Memory Card)

Consoles are added as **Memory Cards** and managed entirely through your **ROM Root** —
one folder grant covers every console; there is no per-console folder picking. PFP never
auto-scans your whole device.

**The fast path — adding the root.** *Library Manager ▸ Add ROM Root* walks the
root's ES-DE system folders (`gba`, `snes`, `psx`, …), creates a Memory Card for every
folder that actually contains games, and loads them in one scan. It also sets up the
**Windows Memory Card** (wiring `<root>/windows` and its `import/` drop folder, and
importing any exported PC games).

**Adding one console by hand:**

1. **Settings ▸ Library ▸ Library Manager ▸ Add Console** (requires a ROM Root).
2. **Choose Platform** (NES, SNES, PSP, PS2, Dreamcast, Xbox 360, …). The console's
   folder is derived from the root automatically — an existing recognized subfolder if
   one is there, otherwise the standard ES-DE folder name.
3. **Assign Emulator** — this becomes the console's default. (Windows skips this step;
   PC games go through your installed PC launchers instead.)
4. **Scan Now**, or create the card and scan later.

Manage a card any time from *Library Manager*: rename it, change its emulator, hide/show
it, **Scan This Console**, or remove it (ROM files on disk are never deleted). Each root's
row lists the consoles homed under it. Library-wide passes live here too: **Scan All
Consoles** (add-only) and **Re-Scan All (Remove Missing)**, which additionally removes
entries whose ROM file has vanished — behind a confirm step, and skipped for any console
whose folder cannot be read, so an unmounted SD card never wipes a library.

> **Scanning is always manual** — there is no background watcher. Re-scan after adding ROMs.
> ROMs on removable SD cards / USB volumes are supported.

### 4.3 Emulators

PFP launches games through **external emulator apps** — install the emulators you want and PFP
detects them automatically on startup from a curated catalog, plus one profile per installed
**RetroArch** core. A selection of what is recognized out of the box:

| System | Emulators |
|---|---|
| PSP | PPSSPP / PPSSPP Gold |
| PS1 | DuckStation |
| PS2 | NetherSX2 / AetherSX2 |
| GameCube / Wii | Dolphin |
| Nintendo DS / 3DS | melonDS, DraStic / Azahar, Citra, Lime3DS |
| Switch | Sudachi / Yuzu / Suyu family |
| N64 | Mupen64Plus FZ / AE |
| GB / GBC / GBA | mGBA, My Boy!, GBA.emu, GBC.emu |
| NES / SNES / Genesis / PC Engine / Neo Geo / WonderSwan / Lynx | the `*.emu` family |
| Dreamcast | Flycast, Redream |
| Xbox 360 | X360 Mobile (`.iso`) |
| Anything with libretro cores | RetroArch (one profile per installed core) |

**Which emulator launches a game?** PFP resolves it in priority order:
**per-game override → Memory Card emulator → the platform default → first available.** Set a
per-game emulator from a game's **△** options; set a console default in *Library Manager*.

You never have to guess which of those won. A game's detail screen shows the emulator, the core,
and *where the choice came from* — with a tap to change it right there.

**Per-System Defaults** (*Settings ▸ Emulators ▸ Per-System Defaults*) is the overview: every
console with its default emulator and core, how many games it covers, and badges for the two states
that break launches — **NO EMULATOR** (nothing installed can run this system) and **CORE MISSING**
(the emulator is there, but the RetroArch core it needs is not). You can also clear per-game
overrides in bulk for one console from here.

**When a launch fails**, PFP tells you instead of dropping you back at the crossbar. It checks
before launching that the emulator still exists and that PFP still has permission to read the ROM —
both of which an OS or emulator update can quietly revoke — and it verifies afterwards that the
emulator actually came to the foreground. If something goes wrong you get a recovery sheet: retry,
switch emulator or core, jump to Per-System Defaults, or copy a diagnostic you can paste into a bug
report.

**Custom Emulator Wizard** — for anything not in the catalog, *Settings ▸ Emulators ▸ Add Custom
Emulator* walks you through it: pick an installed app, let PFP auto-detect its launch settings,
edit any field, **Test Launch** with a real ROM, then **Save**. The result is usable as a platform,
Memory Card, or per-game emulator.

### 4.3b Last Played

A column immediately left of Game holding the games you actually played, most recent first, each
one wearing its own artwork behind the whole screen as you move over it.

It fills as you play: a game is recorded when the emulator hands the launcher back the foreground,
so a title that crashed on boot is deliberately not counted. An existing install starts empty —
nothing wrote that column before 1.4 — and says so rather than looking broken. The order is the
meaning, so the column is never sorted, and nothing can be assigned to it by hand.

### 4.4 Favorites & Collections

- **Favorites** — mark any game from its **△** options (*Add to Favorites*). A **Favorites** folder
  appears under All Games and hides automatically when empty.
- **Collections** — custom folders (e.g. "RPGs", "Currently Playing"). A game can live in several at
  once. Create one from *Settings ▸ Collections* or a game's **△ ▸ Add to Collection**; toggle
  membership with a ✓. Manage (rename, reorder, delete) from *Settings ▸ Collections*.

### 4.5 Game & app options (△)

Press **Y / △** (or long-press) on any item for its context menu.

- **Games** — Launch Game, Edit Title, Edit Note. Everything else (favorites,
  collections, artwork, emulator choice, icon display, file location) lives on the
  **Game Detail** screen and its Options menu.
- **Memory Cards** — Scan This Console, **Update Metadata** (text-only pass, artwork
  untouched), **Scrape Missing Artwork** (fills only games missing primary art), pin,
  hide, rename. The Windows card adds **Import PC Games**.
- **All Games** — sorting plus **Manage Library**, which opens the Library settings.
- **Android apps** — Launch, Edit App Details, Add to Favorites/Collection,
  Move/Pin/Hide/Rename.

The full **Game Detail** and **App Detail** screens also show hero art, metadata,
screenshots, publisher, and total play time.

### 4.6 Artwork & the Artwork Studio

Box art, hero banners, logos, screenshots and icons are fetched **on request**. Add a free
**SteamGridDB** key (and optionally ScreenScraper / IGDB) in *Settings ▸ Artwork*.

- **Quick scrape** — *Settings ▸ Artwork* offers scrape-all, scrape-missing-only, and cache
  clearing. "Scrape Missing" fills only the gaps and never overwrites existing art.
- **Artwork Studio** — from a game's **Artwork** button, a full-screen, controller-first editor with
  a tab per artwork kind (ICON0, ICON1, Box Art, 3D Box, Physical Media, Hero, Background, Logo,
  Screenshot, Manual, Video). Each tab can pull from **ScreenScraper, SteamGridDB** (with an NSFW
  filter), **IGDB,** and **Local File**. Preview a candidate before applying (videos
  play a muted loop, manuals page through), then commit. Press **Start** for per-slot actions:
  Adjust Crop, Restore Previous, Reset to Scraped Default, Clear, and File Info.
- **Crop / position editor** — an aspect-locked frame per kind with the image panning and scaling
  behind it. Crops bake into the displayed file while the untouched original is kept for lossless
  re-crops.

**Portable artwork library** — in *Settings ▸ Artwork ▸ Artwork Folder & Import* you can point PFP
at a folder it keeps in an **ES-DE-compatible** layout, so your art is user-owned and readable by
other frontends with no export step. The same screen imports ES-DE `downloaded_media` (and
`gamelist.xml` metadata), relinks moved files, and exports for ES-DE.

#### Folder layout

The library is a clean two-folder root. Everything under `Artwork/` is a standard ES-DE
`downloaded_media` tree, so you can point ES-DE (or any frontend) straight at
`{Artwork Folder}/Artwork` with no export step.

```text
{Artwork Folder}/
├─ pfp-artwork-library.json        manifest — marks this folder as a PFP library
├─ Import/                         drop zone for other launchers' media (see below)
│   └─ {Launcher}/ …               an ES-DE downloaded_media tree
└─ Artwork/                        the library — ES-DE downloaded_media shape
    └─ {platformId}/               e.g. ps2, snes, psp
        ├─ covers/                 box art        →  {PortableName}.{ext}
        ├─ miximages/              hero
        ├─ fanart/                 background
        ├─ marquees/               logo
        ├─ screenshots/            screenshot
        ├─ titlescreens/           title screen
        ├─ physicalmedia/          cartridge / disc
        ├─ 3dboxes/                3D box
        ├─ manuals/                PDF manual
        ├─ videos/                 video
        └─ pfp/                    PFP-only namespace (skipped by scan & export)
            ├─ icon0/              144:80 ICON art
            ├─ icon1/              icon video snap
            ├─ originals/{kind}/   untouched pre-crop copies (lossless re-crop)
            └─ versions/{kind}/    one-previous backup ("Restore Previous")
```

The `pfp/` namespace holds PFP-only assets that are not ES-DE media types; `versions/` and
`originals/` are nested per `{kind}` so a game's box-art and icon backups (same filename) never
collide. Incoming **videos** are transcoded locally into a 60-second `icon1/` snap — the full-size
file is never stored.

#### How import works

1. **Drop** another launcher's media under `Import/{Launcher}/` in an ES-DE `downloaded_media`
   shape.
2. **Match** — PFP detects it by structure and links each file to a game in three passes: exact ROM
   filename → display title → tag-stripped title. Ambiguities are reviewed, never guessed.
3. **File** into `Artwork/{platform}/{mediaDir}/{PortableName}.{ext}`. Same-volume transfers move
   with zero bytes copied; otherwise they copy. Existing or locked artwork is never overwritten.

| ES-DE folder | Imports as (PFP kind) |
|---|---|
| `covers` | Box Art |
| `miximages` | Hero |
| `fanart` | Background |
| `marquees` | Logo |
| `screenshots` | Screenshot |
| `titlescreens` | Title Screen |
| `physicalmedia` | Physical Media |
| `3dboxes` | 3D Box |
| `manuals` | Manual (PDF) |
| `videos` | Video → transcoded to an ICON1 snap |
| `backcovers` | Recognized as library structure, not imported |

### 4.7 Icon display modes & video snaps

Every game tile can be drawn four ways — set a global default in *Settings ▸ Artwork ▸ Game Icon
Display*, per Memory Card, or per game via its **△** menu:

- **Custom Icon** — the PSP-authentic 144:80 ICON0 fill.
- **Box Art** — the game's cover at its natural aspect.
- **Physical Media** — the platform's cartridge/disc shot.
- **3D Box Art** — a rendered 3D box.

In **Custom Icon** mode, resting on a game plays its **video snap** inside the icon (muted, capped at
60 seconds, then fading back to the still) — the PSP's ICON1.PMF revived. It is battery-conscious:
one shared player, skipped under Battery Saver, low battery, or thermal pressure, and gated by an
**Animated Icons** master toggle in Settings.

### 4.8 Android apps & non-gaming categories

- **Add apps** to a section (App Store / Video / Music / Network / custom) via its **Add Apps** row.
- **App artwork** — apps show their launcher icon by default; give one custom art via
  **△ ▸ Edit App Details ▸ Icon** and it renders as a landscape tile. Apps stay tagged as apps, so
  they never appear in All Games.
- **Android games** — open the Android library card and choose **Find Games**; these are addable to
  collections but stay out of All Games.

### 4.9 The App Drawer

A bottom-right button (shown while using touch, or bound on a controller) opens the **App Drawer**:
all your apps with quick filters — **All Apps / Games / Emulators / Tools / Recently Used** —
switchable with **L1 / R1**.

### 4.10 Music, Video & Photo

Each media section is driven by **one or more root folders** you set in its Settings
screen (SAF folder picker — no storage permission):

- **Music** — scan folders, browse `[cover] title / artist`, and play in a full-screen player with a
  **background service** and media-notification controls. Create and manage **playlists**.
- **Video** — scanned libraries with thumbnails, Recently Watched / Favorites / Playlists, and a
  built-in player or your chosen external app.
- **Photo** — scanned albums, a fullscreen viewer (zoom, pan, rotate, L1/R1 paging), and
  **Set as Launcher Wallpaper** (EXIF-stripped; location data is never read).

Each section also lists **the apps that belong to it** — YouTube and Plex under Video, Spotify
under Music, your readers under Library — at the root of the column rather than behind an extra
row, with **Add …** at the end. Where a column would otherwise end in two "Add" rows (one for a
folder, one for apps), they collapse into a single **Add** row that opens the rest as a submenu.

### 4.10b Finding things

Two searches, both reached without leaving the crossbar.

**Library search** looks in your own libraries:

- The **Search** row at the top of Game, Video, Photo and Library searches only that library.
- The **Select** button searches everything from anywhere on the home screen — games, video,
  photos, books and music at once. With touch on, the magnifier beside the app-drawer button does
  the same.
- Matching ignores punctuation and takes the words in any order, so `ocarina zelda` finds
  *The Legend of Zelda: Ocarina of Time*, and a partial word is enough. Each result says which
  library it came from, and opening one takes you to it in the column it lives in.
- Music has no Search row of its own: its **Music** and **Playlist** rows already open a
  searchable browser, and it is still covered by the Select-button search.

**Quick Search** at the top of Network searches the *web*. Type words and it searches; type
something that looks like an address and it opens that instead. PFP never picks a browser — the
intent goes to whichever one you have already chosen.

### 4.11 Categories

Categories are the horizontal bar. Manage them in *Settings ▸ Categories*:

- **Create** a category, choose a **content type** (Gaming = games & collections, Non-gaming =
  apps), and pick an **icon** from the image-based picker.
- **Rename, reorder** (move left/right), **hide/show,** or **delete** custom categories. Built-in
  categories are protected from deletion.

### 4.12 Themes & personalization

Everything lives in **Settings ▸ Themes**, built around one idea: *pick a background and one
color — the whole crossbar follows* (wave, gradient, cursor and icons all derive from it).

- **Color Scheme** — 12 PSP-style presets, previewed live on the real crossbar (including the
  month-cycling *Original*).
- **Icon Color** — one tint across every crossbar glyph: 8 curated swatches (*Default* is the
  native white) plus a **Custom** swatch that opens an HSV picker (Hue / Saturation /
  Brightness bars, adjustable by D-pad or touch). Game art, covers and app icons are never
  tinted.
- **New Theme from Photo** — any picture becomes the wallpaper; the theme color is auto-derived from
  its dominant hue.
- **Import PSP Theme (`.ptf`)** — convert an official PSP theme you own (wallpaper + derived color).
  CXMB firmware files are safely declined.
- **My Themes** — your saved themes as cards: apply, **Share** (`.pfptheme`), or Remove.

**Theme Studio** is a desktop companion (Windows / Linux / macOS) for authoring themes with a live
crossbar preview, an icon editor, wallpaper crop presets, crossbar alignment assist, and batch
`.ptf → .pfptheme` conversion. See [7.8](#78-the-theme-studio-desktop-app).

### 4.13 Custom XMB icons

> **Not in 1.2.1.** Ships in the next release.

Every glyph on the crossbar can be replaced with your own image — the category-bar icons, the menu
glyphs, and the per-console art on your Memory Cards. Around 55 theme slots plus one slot per
console.

Open **Settings ▸ Themes ▸ Custom Icons**. The editor runs *live over your real crossbar*, so you
are always looking at the actual result rather than a preview pane:

- Move with the D-pad to the icon you want to change, press **✕** to pick an image.
- **△** clears the selected slot back to whatever the theme (or the built-in art) provides.
- Changes apply instantly. Back out when you are happy.

**What you can use**

| | |
|---|---|
| **Formats** | PNG, JPG, WEBP, BMP, HEIC, and animated **GIF** |
| **Size limit** | 8 MB per icon |
| **Animated GIFs** | 512 px or smaller, up to 120 frames, 10 seconds |

Animated icons only play on the row or column you are currently on, and stop entirely when battery
saver is on or a dialog is open — so a set of animated icons does not cost you frame rate while you
browse.

**Custom icons survive theme changes.** Applying a different theme swaps the theme's icons
underneath, but anything *you* picked stays on top. The order is always **your pick → the theme's
icon → the built-in art**. To get a theme's icon back, clear your pick for that slot with **△**.

**Saving your look** — *Settings ▸ Themes ▸ Save Current Look* bundles everything as it currently
draws (your picks already flattened in) into a shareable `.pfptheme`. Your screen-layout
adjustments are deliberately left out, since those are specific to your device.

### 4.14 Motion wallpapers

> **Not in 1.2.1.** Ships in the next release.

The crossbar background can be a looping video or animated image instead of a still.

Pick one from **Settings ▸ Themes ▸ Wallpaper** the same way you pick a photo — choose a video file
and PFP takes it from there, grabbing the first frame as a poster still at import.

| | |
|---|---|
| **Formats** | MP4, WebM, and animated GIF |
| **Resolution** | 1080p or smaller |
| **Length** | Up to 60 seconds |
| **File size** | Under 60 MB |
| **Frame rate** | 30 fps or lower recommended |

The clip pauses to its poster still whenever motion would be wasteful or distracting — during
video playback, behind fullscreen overlays, and on battery saver. It is fully released rather than
left paused in the background, so a motion wallpaper does not quietly drain your handheld while you
are doing something else.

Motion wallpapers can be authored into a shareable theme with the desktop **Theme Studio**, and
ride along inside the `.pfptheme` file.

### 4.15 Interface sounds & boot videos

> **Not in 1.2.1.** Ships in the next release.

**Settings ▸ Interface ▸ Sound** lets you replace PFP's interface audio and its startup sequence
with your own files. Each row has a **Preview** button (it plays even if menu sounds are muted) and
**Use Default** to revert.

**The seven sounds**

| Row | Plays when | Max length |
|---|---|---|
| **Navigation** | Moving the cursor around the crossbar | 0.5 s |
| **Back / Cancel** | Backing out of anything | 1 s |
| **Confirm / Apply** | Committing a choice — picking apps or games, importing an icon, saving a theme | 1 s |
| **Error / Invalid** | A refused launch or a rejected import | 1 s |
| **Launch Sound** | Starting an app (games boot silently — see GameBoot above) | 3 s |
| **Notification** | Reserved — not played in this build (it used to chime on background scans and backups) | 2 s |
| **Boot Sound** | The startup sequence | 10 s |

Audio can be MP3, WAV, OGG or M4A. Each sound's max length keeps playback snappy — SoundPool
holds a menu sound fully in memory, and the boot presentation must still end on time.

**Boot and GameBoot videos**

- **Boot Sequence** (*Settings ▸ Display ▸ Boot Sequence*) — one field: supply your own video (up
  to 10 seconds) and it replaces the PFP logo animation. Press **✕** or **○** to skip it. If your
  video has its own audio track that audio is used, and the Boot Sound row on the Sound screen
  steps aside.
- **GameBoot** — the short presentation that plays as a game launches. Built in, it is a
  five-second light sweep timed to the launch sound; supply your own clip (up to 10 seconds) and it
  replaces the whole thing, its own audio included. That audio is independent of your menu-sound
  setting, so muting menu sounds does not silence it. Switching GameBoot off gives a silent
  launch — no animation and no sound; games never fall back to the menu's launch chime. If the
  presentation has not finished in time the game launches anyway — GameBoot can never hold your
  game hostage.

Video can be MP4 or WebM, up to 25 MB.

Boot Video, GameBoot Video and every sound row work the same way and carry the same two controller
shortcuts on the focused row, bound to physical buttons so an X/Y swap cannot move them: the
**north** button (Y on Xbox/PlayStation pads, X on Nintendo) restores the PFP default, and the
**west** button (X on Xbox/PlayStation, Y on Nintendo) plays a preview.

Everything you assign here is included in **Backup & restore**.

### 4.16 Adjusting the layout for your screen

PFP scales itself to fit your device automatically, including near-square foldable inner displays.
To fine-tune it, open **Settings ▸ Display ▸ Adjust XMB Layout** — a live editor over the real
crossbar:

- **Scale** the whole interface, and **shift the crossbar** up/down and left/right.
- Drive it with the **D-Pad** (move), **L1 / R1** (scale), **Y** (reset), **A** (save), **B**
  (cancel) — or an on-screen **slider** panel.
- Each screen size keeps its **own** tuning, so a handheld and a foldable never share (and distort)
  one layout.

### 4.17 Backup & restore

*Settings ▸ Backup & Restore* writes a `.pfpbackup` archive (library + settings) into a folder you
pick, and restores from one. Because on-device cloud backup is disabled for privacy, this is how you
move your setup to a new device or recover after a reinstall. Restoring re-links your ROM/media
folders via *Library ▸ Root Access*.

### 4.18 Settings reference

| Section | What it covers |
|---|---|
| **Library** | ROM roots, Library Manager (consoles, scan-all passes), Import PC Games, Root Access |
| **Emulators** | Detected emulators, Custom Emulator Wizard |
| **Artwork** | API keys, scrape all/missing, Game Icon Display, Artwork Folder & Import |
| **Themes** | Color scheme, icon color (presets + Custom HSV), wallpaper, New Theme from Photo, PSP import, My Themes |
| **Display** | Wave style, wallpaper, boot sequence, Adjust XMB Layout, Animated Icons |
| **Music / Video / Photo** | Root folder, rescan, default player, thumbnail cache |
| **Controller** | Remap every binding, scroll speed, touch navigation button |
| **Collections / Categories** | Create, rename, reorder, hide |
| **Backup & Restore** | Export / import `.pfpbackup` |
| **Logs** | Open a rolling, redacted log in an external viewer; Share for bug reports |
| **Re-Run Setup Wizard** | Replays the first-run wizard (see [2.5](#25-first-run-setup)) |
| **About / Credits** | Version, attributions |

---

### 4.13 What changed in 1.5

**Video thumbnails and album art come back.** Renaming the app orphaned every cached thumbnail and
cover: they were recorded at a path inside the old package, which the renamed app is not allowed to
read. Nothing was lost, but nothing could be shown either, and rescanning did not help because each
scan handed the same dead path back. Both scanners now notice a cached file they cannot open and
regenerate it. **Run Rescan once** on Video and on Music and they fill in.

**Steam's own store is a scraper source.** PC games imported from Steam already carry the app id
Steam uses, so PFP can ask Steam about them directly — no API key, no search, no guessing which
game a shortcut is. It brings a description, developer, publisher, genre and year, plus the three
artworks Steam publishes per app: a wide backdrop, portrait box art, and a transparent logo.

**Video rows say more.** Duration, resolution and file size, the same three facts in the same order
as a photo row.

**The Artwork Studio tells you where you stand.** A band above the tabs shows how many of a game's
eleven artwork kinds are filled, how many results the current source returned, and how many
ScreenScraper requests today's account has used against its cap.

**Things that were hard to read, aren't.** On a light colour scheme the app now picks dark text and
the dimmer label colours follow it, instead of leaving pale labels on a pale wallpaper. Dialog cards
flip with the text. An options panel takes the artwork's hue at a surface's darkness, so the game's
own logo no longer reads through the menu on top of it.

**Search works with the keyboard up.** On a 1080p handheld the keyboard took more than half the
screen and the results list was laid out with no height at all — you typed and saw nothing. The
title block and prompt bar now stand down while you type.

**A failed launch is answerable.** Every button on the recovery sheet is reachable with a pad, and
when PFP has lost access to a game's file the sheet leads with reconnecting the storage rather than
with Retry.

**Smaller things.** Counts read one way everywhere ("3 games", not "3 Games"). Empty rows line up
with the rest of the column. A game's name is the same on its page and in an error about it. Windows
shortcuts lose the underscore a filename forced on them. Searching a console's name finds its games.
The settings rail centres on wherever you are.

## 5. Permissions & privacy

PFP is a **local-first** launcher: your data stays on your device. There is no analytics, no
telemetry, and no account. PFP reaches the network only for the things you connect:
artwork/metadata scraping (SteamGridDB, ScreenScraper, IGDB). Everything is HTTPS.

**What PFP stores, and how**
- **On-device only.** Your library, settings and artwork live in app storage. **Backup is disabled**
  (`allowBackup=false`), so nothing is uploaded or transferred automatically — use
  [Backup & restore](#417-backup--restore) to move devices.
- **Scraper API keys are encrypted at rest** with a hardware-backed Android Keystore key.
  On the rare devices where the Keystore is unavailable, a key you enter is stored
  unencrypted and the app tells you so at save time.
- **Network is HTTPS-only.** Cleartext is blocked, and release builds trust only the system
  certificate store.
- **Logs are redacted at write time** — credentials, tokens, account names and emails never reach
  disk.

**Why the broad permissions exist (and how they are minimized)**
- **ROM, media, theme and backup folders all use SAF** — you grant exactly the folders
  PFP reads, and no storage-all permission is ever requested. A handful of legacy raw-path
  libraries may still ask for media access on older Android versions.
- **Query installed apps** is required to *be* a launcher.
- **Usage access** is optional and only powers "Recently Used".

**Other apps can't silently change your library.** Legacy "install shortcut" broadcasts are
sanitized and require you to **confirm each one** before it appears.

---

## 6. Troubleshooting

| Symptom | Fix |
|---|---|
| Home button doesn't open PFP | Set it as default: *Android Settings ▸ Apps ▸ Default apps ▸ Home app*. |
| A console shows no games after adding ROMs | Scanning is manual — open the card's **△ ▸ Scan This Console**. |
| A game won't launch | Confirm the emulator app is installed; check the per-game/console emulator in **△** / Library Manager. |
| Disc/multi-file game not found | Open the game's console folder in a file manager and confirm the file is there; if the console uses a legacy raw-path library, re-grant its folder in *Settings ▸ Library*. |
| Artwork won't download | Add a SteamGridDB (or other) API key in *Settings ▸ Artwork* and check your connection. |
| Interface too big/small or off-center | Tune it in *Settings ▸ Display ▸ Adjust XMB Layout*. |

If something looks like a bug, grab the log from *Settings ▸ Logs ▸ Share* — it is redacted and safe
to send.

---

## 7. For Developers

> This section is for building PFP from source. It assumes familiarity with Android development.

### 7.1 Tech stack

- **Language:** Kotlin `2.4.10`
- **UI:** Jetpack Compose (Compose BOM `2026.08.00`), MVVM + state hoisting
- **DI:** Hilt
- **Database:** Room — **schema v40**, hand-written migrations only (never destructive)
- **Settings:** DataStore Preferences
- **Networking:** Ktor (artwork / metadata scrapers)
- **Media:** Media3 (video snaps + in-app player)
- **Image loading:** Coil
- **Background work:** WorkManager + Android notifications
- **Serialization:** Kotlinx Serialization
- **Desktop companion:** Compose Multiplatform Desktop (`:studio`)
- **Testing:** JUnit 4 + MockK + Turbine
- **Build:** Gradle `9.7.1` (Kotlin DSL), AGP `9.4.0` (built-in Kotlin), KSP2

### 7.2 Prerequisites

- **Android Studio** — a recent stable release (Ladybug or newer recommended). Use the **bundled
  JetBrains Runtime (JBR 17/21)** as the IDE boot runtime.
- **JDK 17** for command-line Gradle (`JAVA_HOME` pointing at a JDK 17). The desktop `:studio`
  module targets a JVM 17 toolchain.
- **Android SDK 37** installed (compileSdk 37; targetSdk stays 35). Minimum supported device
  API is **29** (Android 10).

### 7.3 Get the code & open it in Android Studio

```bash
git clone <repo-url>
cd platform-selection-portal-launcher
```

1. In Android Studio choose **Open** and select the project root (the folder with
   `settings.gradle.kts`).
2. Let Gradle sync finish. Android Studio downloads the wrapper (`9.7.1`) and the declared
   plugins/dependencies automatically.
3. If prompted, install the matching **Android SDK 37**, **NDK,** and **CMake** from the SDK
   Manager.

### 7.4 Build variants

There are no product flavors — one app, two build types: `debug` (`.debug` application-id suffix)
and `release` (R8 + signing). Switch the active one in the **Build Variants** tool window.

### 7.5 Run & debug from Android Studio

1. Pick your device/emulator and press **Run** (or **Debug**).

### 7.6 Release signing

Release builds are signed from a **gitignored** `keystore.properties` at the repo root. Without it,
release builds still assemble but stay **unsigned**.

```properties
# keystore.properties (do not commit)
storeFile=/absolute/path/to/release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

### 7.7 Command-line builds & the `dist` task

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK; signed if keystore.properties is present
./gradlew :app:assembleRelease

# Unit tests
./gradlew test
```

**One command for everything shippable:**

```bash
./gradlew dist
```

`dist` builds the release APK and the Theme Studio installer for the current OS and
collects them, cleanly named, into the gitignored **`dist/`** folder:

```
dist/
├── PSPLauncher-<version>.apk
└── PlayField-Theme-Studio-<version>.msi   (or .dmg / .deb per OS)
```

Every individual release build also finalizes a copy into `dist/`.

### 7.8 The Theme Studio desktop app

`:studio` is a Compose Multiplatform Desktop app that shares `:core:theme-kit` with the launcher
(and must never grow an Android dependency).

```bash
# Run it
./gradlew :studio:run          # or run-theme-studio.bat on Windows

# Package a native installer for the current OS (MSI / DMG / DEB)
./gradlew :studio:packageReleaseDistributionForCurrentOS
```

### 7.9 Module structure

Strict dependency direction: **features → core**; `app` wires everything via Hilt.

```
app/                      MainActivity (HOME launcher), PFPApplication, Hilt app module
studio/                   Theme Studio — Compose Desktop companion (Win/Linux/macOS)
core/
  theme-kit/              Pure-JVM theme core shared with Theme Studio: PTF/BMP/GIM/LZR parsers,
                          .pfptheme codec, color cascade, icon-slot registry, layout spec + adjust,
                          and the shared limits (UiMediaLimits, MotionLimits, IconGifSupport)
  core-archive/           Pure-JVM bounded ZIP ingestion shared by themes, backup and the codec
  core-common/            Shared utilities and extensions
  core-domain/            Domain models, repository interfaces
  core-data/              Room DB (v41), DAOs, DataStore, repository impls, migrations,
                          and the user-asset stores (CustomIconStore, UiMediaStore, PfpThemeStore)
  core-navigation/        Pure navigation logic, no Android dependency — NavigationEngine, gridMove
  core-ui/                PFPTheme/PFPColors, WaveStyle, PortalIcon, category-icon catalog,
                          motion-wallpaper surfaces, MenuSoundPlayer
feature/
  feature-xmb/            Crossbar shell, XMBViewModel, game/app detail, Artwork Studio, boot
  feature-library/        ROM scanner, rescan triggers, disc-image resolver, platform map
  feature-launcher/       Emulator detection, the launch-resolution ladder, LaunchDispatcher
  feature-artwork/        Scraper clients, portable artwork library, ES-DE import/export
  feature-themes/         Theme loader/repository, built-in themes
  feature-settings/       Settings screens + ViewModels
  feature-appbar/         App drawer, app→category classification, filters
  feature-backup/         BackupManager, backup/restore workers
```

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for data-flow, launch-pipeline, and state detail.

### 7.10 Testing

```bash
./gradlew test                     # all unit tests
./gradlew :feature:feature-xmb:test  # a single module
```

Unit tests use JUnit 4 + MockK + Turbine; `:core:theme-kit` additionally ships golden tests against
Sony's own example PSP themes.

---

## 8. Credits

### Interface design — inspired by Sony's XMB
The look and feel is inspired by the **XMB (XrossMediaBar)**, the interface Sony created for the
PlayStation Portable, PlayStation 3 and other devices. The crossbar layout, flowing wave
background, navigation model and options-menu behaviour are homages to Sony's original design.

**"XrossMediaBar", "XMB", "PSP", "PlayStation" and related marks are trademarks of Sony Interactive
Entertainment Inc.** PSPLauncher is an independent, non-commercial fan project. It is **not
affiliated with, endorsed by, or sponsored by Sony**, and ships none of Sony's code, firmware,
fonts or audio. The bundled UI artwork comes from the community *XMB Menu for ES-DE* theme (see
below) and remains the property of its respective authors; the menu sounds are original to this
project.

**On the name.** *PFP* is a deliberate double entendre — *PSPLauncher* as the product name,
and the affectionate shorthand from anime and gaming communities. The trademarks above cover the
*names* "XMB" and "Cross Media Bar", not the visual style itself, which is not protectable as trade
dress in a non-competing product category. That reading is why the homage is drawn as openly as it
is, while the marks themselves are left alone.

### App icon & logo
The PSPLauncher **app icon and logo** were created by **johakovi**
([u/silverloc96](https://www.reddit.com/user/silverloc96) on Reddit), who generously volunteered
their time to make them. The work is amazing — please go check out their work.

### System & console artwork
The system, console and category icons come from the
**[XMB Menu for ES-DE](https://github.com/anthonycaccese/xmb-menu-es-de)** theme — a community
recreation of the PSP's crossbar interface for ES-DE.

**All rights to this artwork belong to its creators — [Anthony Caccese](https://github.com/anthonycaccese),
building on the original work by InitialDin.** Used here with gratitude; it remains the property of
its respective authors.

- Project: XMB Menu for ES-DE · Authors: Anthony Caccese · InitialDin
- Source: https://github.com/anthonycaccese/xmb-menu-es-de
- Used for: category-bar icons, per-console system icons, the physical-media (cartridge) icon set

### Controller button icons
Every on-screen button prompt — the PlayStation, Xbox and Nintendo face buttons, D-pads,
bumpers, triggers, sticks and system buttons — is drawn from **Zacksly's** button icon packs.

- Author: **Zacksly** · Website: https://zacksly.itch.io · Support: https://www.patreon.com/zacksly
- Packs: *PS5 Button Icons and Controls*, *Xbox Series Button Icons and Controls*,
  *Switch 2 Button Icons and Controls* (all "Buttons Solid / White / 128w")
- License: **CC BY 3.0** — http://creativecommons.org/licenses/by/3.0/
- Used for: `ctl_ps_*`, `ctl_xb_*`, `ctl_ns_*` in `core-ui` — the glyphs behind every
  `ControllerPrompt`, resolved to the user's chosen controller type
- **Unmodified.** The bundled PNGs are byte-identical to the pack originals; only the file
  names were changed to Android resource names, and only the needed subset is included.

> "PS5 Button Icons and Controls - Zacksly
> Licensed under CC BY 3.0 - https://zacksly.itch.io"

### Menu sounds
The seven bundled **sound effects** — navigation, back, confirm, error, launch, notification and
the boot sound — are **original, authored for this project**. They aim for the *feel* of the XMB
without being derived from it: each one is cross-correlated against reference material and has to
score below a fixed similarity threshold to ship. No Sony firmware audio is bundled, and none is
committed to this repository.

Earlier builds bundled menu sounds from the community *XMB Menu for ES-DE* theme; those were
replaced by the original set. You can replace any of the seven with your own audio — see
[4.15](#415-interface-sounds--boot-videos).

### Game artwork & metadata
Fetched at the user's request from third-party providers and remaining the property of their owners:
- **SteamGridDB** — community artwork (grids, heroes, logos, icons)
- **IGDB** — optional metadata / artwork source

If you are a rights holder and would like attribution changed or an asset removed, please open an
issue and it will be addressed promptly.

## 9. License

See [LICENSE](LICENSE).
