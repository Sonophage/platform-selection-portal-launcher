# Dummy content for the emulators

```sh
tools/emu/make-content.py /tmp/PFPTest          # generate
adb push /tmp/PFPTest/Music/.  /sdcard/Music/
adb push /tmp/PFPTest/Videos/. /sdcard/Movies/
adb push /tmp/PFPTest/Photos/. /sdcard/Pictures/
adb shell mkdir -p /sdcard/Books && adb push /tmp/PFPTest/Books/. /sdcard/Books/
adb push /tmp/PFPTest /sdcard/                  # ROMs stay under /sdcard/PFPTest/Roms
```

Everything is **generated, not downloaded** — no copyright question, and the metadata is exactly
what the app's own parsers read.

| type | count | where | notes |
|---|---|---|---|
| Games | 30 | `/sdcard/PFPTest/Roms/GBA/*.gba` | Extension-only placeholders. They list and open a detail page; they will **not** launch. Titles span A–Z for the letter rail — see below. |
| Music | 8 | `/sdcard/Music/<act>/<album>/` | Two albums. `artist` is a **credit string**, `album_artist` is the act, and they differ on two tracks — the case the Artists column groups on. |
| Video | 3 | `/sdcard/Movies/*.mp4` | h264+aac, three different resolutions. |
| Photos | 6 | `/sdcard/Pictures/*.jpg` | Test patterns at various aspect ratios. |
| Books | 3 | `/sdcard/Books/*.epub` | Real EPUB3 zips — `mimetype` first and STORED, `container.xml`, OPF with `dc:title`/`dc:creator`. Two share a `calibre:series` so `seriesIndex` gets exercised. |

## Media goes in the STANDARD folders, and that is deliberate

Every library in this app is granted through SAF, and the picker shows folder names without
paths. A nested `PFPTest/Music` is indistinguishable in the picker from the device's own empty
`Music`, and picking the wrong one fails **silently** — the app shows a root named "Music" either
way and simply scans zero tracks. Putting the media where Android already keeps it removes the
ambiguity. ROMs have no standard folder, so they stay under `PFPTest/Roms`.

## Granting the folders

Settings ▸ Library ▸ Add ROM Root → `PFPTest/Roms`, then Settings ▸ Media ▸ each tab ▸ Add … Root
→ `Music` / `Movies` / `Pictures` / `Books`, then Rescan on each tab.

**If scripting the picker**: DocumentsUI's `USE THIS FOLDER` bar covers roughly y 912–1024 on a
1920×1080 panel, and a folder tile underneath it still reports its own bounds in a `uiautomator`
dump. A tap at those coordinates hits the **bar**, which selects whatever folder is currently
open. Scroll the target above y≈900 and re-read its bounds before tapping, or the grant lands on
the wrong folder with no error anywhere.

## Verifying — do not trust the settings screen

It shows the folder's name, not its path, so a wrong grant looks identical to a right one. Ask the
database:

```sh
P=com.psplauncher.launcher.debug
for t in games music_tracks videos photos books; do
  printf "%-14s " $t
  adb shell "run-as $P sqlite3 databases/pfp_database 'select count(*) from $t'"
done
adb shell "run-as $P sqlite3 databases/pfp_database 'select tree_uri from music_folders'"
```

Expected with the tree above: **games 30, music_tracks 8, videos 3, photos 6, books 3**.


## Seeing the letter rail

It has now been confirmed running (26 games, Konker AVD): full A–Z rail down the right edge,
inside both chrome bands, 8 rungs down from A landing on `I` → "Iron Lantern", position held
after release.

Three things must all be true, and the count is the one people check:

1. **≥25 items and ≥3 distinct initials** — `LETTER_JUMP_MIN_ITEMS` / `MIN_LETTERS`.
2. **The list's initials must never go backwards.** `letterAnchors()` returns null otherwise, on
   purpose — a rail over a non-alphabetical list looks right and jumps wrong. The generated set's
   last four titles are out of order deliberately, so on **Recently Added** (insertion order) the
   rail is correctly absent. Use a title-sorted list, or drop those four.
3. **A real ≥400ms gap between key-down and key-up** (`SHOULDER_HOLD_MS`) on a shoulder.

`adb shell input keyevent` **cannot** do (3) — it sends down and up back to back. On an emulator,
`adb root` then `sendevent` on the keyboard device can, because `KEYCODE_PAGE_UP`/`PAGE_DOWN` are
bound to `PREV_`/`NEXT_CATEGORY`:

```sh
adb root
adb shell 'sendevent /dev/input/event1 1 109 1; sendevent /dev/input/event1 0 0 0'   # PAGE_DOWN down
sleep 1                                                                              # past 400ms
adb shell 'sendevent /dev/input/event1 1 108 1; sendevent /dev/input/event1 0 0 0; \
           sendevent /dev/input/event1 1 108 0; sendevent /dev/input/event1 0 0 0'    # walk a rung
adb shell 'sendevent /dev/input/event1 1 109 0; sendevent /dev/input/event1 0 0 0'   # release
```

Scancodes are Linux `input-event-codes`, not Android keycodes: 109 = `KEY_PAGEDOWN`,
108 = `KEY_DOWN`, 60 = `KEY_F2`. Confirm they land with
`getevent -l /dev/input/event1`. On the Konker it is simply holding L1 or R1.
