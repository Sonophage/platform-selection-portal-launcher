# Device checks

What has never been run on hardware, and how to run it. Every check below carries the command
rather than a claim, because a claim goes stale and a command does not.

Written 2026-09-19, after the Library section, the baseline profile generator and the achievement
removal were all built against a disconnected device.

```bash
# Every command below assumes:
D=01411YEF01035627        # adb devices
P=com.psplauncher.launcher.debug
```

---

## 0. Before installing anything: the irreversible one

Installing this build and opening the launcher runs **migration v45 to v46**, which DROPs six
achievement tables. Coins, sync history and provider links go, and nothing brings them back.

**Decide first whether you want a copy.** Taking one costs ten seconds:

```bash
adb -s $D exec-out "run-as $P cat databases/pfp_database"     > ~/pfp_backup_v45.db
adb -s $D exec-out "run-as $P cat databases/pfp_database-wal" > ~/pfp_backup_v45.db-wal
```

Confirm what is in it before trusting it:

```bash
python3 -c "
import sqlite3; c=sqlite3.connect('$HOME/pfp_backup_v45.db')
print('user_version', c.execute('PRAGMA user_version').fetchone()[0])
for t in ('account_achievements','provider_game_links','steam_owned_games','games','books'):
    try: print(t, c.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0])
    except Exception as e: print(t, 'ERR', e)
"
```

A `user_version` of 45 and non-zero achievement counts mean the copy is real. **An empty result
means the copy failed, not that the data was already gone.**

---

## 1. The app starts at all

The one that nearly did not. Deleting the achievements module left a Hilt binding with no provider,
and `compileDebugKotlin` passed anyway: the graph is only validated when an APK is assembled. It
builds now, but building is not running.

```bash
./gradlew :app:assembleDebug
adb -s $D install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $D shell am force-stop $P
adb -s $D shell monkey -p $P -c android.intent.category.LAUNCHER 1
sleep 4 && adb -s $D exec-out screencap -p > /tmp/check1.png
```

Then read the log for anything that failed silently at startup:

```bash
adb -s $D logcat -d | grep -iE "FATAL|AndroidRuntime|MissingBinding|initialization failed" | head
```

## 2. The migration ran, and took only what it should

```bash
adb -s $D exec-out "run-as $P cat databases/pfp_database" > /tmp/after.db
python3 -c "
import sqlite3; c=sqlite3.connect('/tmp/after.db')
print('user_version', c.execute('PRAGMA user_version').fetchone()[0], '(want 46)')
t={r[0] for r in c.execute(\"SELECT name FROM sqlite_master WHERE type='table'\")}
gone=['account_achievements','account_achievement_sets','achievement_match_notes',
      'provider_game_links','steam_owned_games','steam_no_achievements']
print('still present:', [g for g in gone if g in t] or 'none  <- want none')
for k in ('games','platforms','books','book_libraries','categories'):
    print(k, c.execute(f'SELECT COUNT(*) FROM {k}').fetchone()[0])
"
```

**The counts are the point, not the dropped tables.** Compare `games` against the pre-install
backup: the migration must not have touched the library.

## 3. Shiba Coins does not come back

The category id joined `RETIRED_IDS`, which is the guard against a restored backup resurrecting it.

```bash
python3 -c "
import sqlite3; c=sqlite3.connect('/tmp/after.db')
for r in c.execute('SELECT id,name,position,is_visible FROM categories ORDER BY position'): print(r)
"
```

No `achievements` row. Then restore a backup made **before** the removal, from Settings, and run
this again: still no `achievements` row.

## 4. PS Vita game scanning — the highest-risk check

`VitaGameScanner` moved out of the achievements module into `feature-library`. It is the only
piece of the removal that creates library rows, and the only thing rescued rather than deleted.

Library Manager, the PS Vita card, **Scan**. Then:

```bash
python3 -c "
import sqlite3; c=sqlite3.connect('/tmp/after.db')
print('vita games:', c.execute(\"SELECT COUNT(*) FROM games WHERE platform_id='psvita'\").fetchone()[0])
for r in c.execute(\"SELECT title FROM games WHERE platform_id='psvita' LIMIT 5\"): print(' ', r[0])
"
```

Names come from `param.sfo` and icons from `icon0.png`, so a row titled with a raw Title ID
(`PCSE00123`) means `ParamSfo` did not survive the move intact.

## 5. PC and Steam games still import

Nothing here was meant to change: the local-Steam path I removed never created games. Prove it.

- Library Manager, Windows card, **Scan Import Folder** — `.pfpgame` files still restore.
- A pinned launcher shortcut still lands in the library.
- The Windows game context menu has **no "Install Goldberg Achievements"** row.

```bash
python3 -c "
import sqlite3; c=sqlite3.connect('/tmp/after.db')
print('windows games:', c.execute(\"SELECT COUNT(*) FROM games WHERE platform_id='windows'\").fetchone()[0])
print('with a storefront id:', c.execute('SELECT COUNT(*) FROM games WHERE storefront IS NOT NULL').fetchone()[0])
"
```

## 6. The achievement UI is gone everywhere

- Crossbar: no Shiba Coins column.
- Game Detail: no coins row; the cursor walks Launch to quick actions to overview to information
  with no dead stop where the coins row used to be.
- Settings: no Achievements section, and the tree still navigates.
- Settings, Credits: no RetroAchievements, Steam or Goldberg entries.
- Initial Setup (Settings, System, Run Setup Again): the wizard goes Services straight to Vita or
  RetroArch, with no achievements page and correct step numbering.

## 7. Library section — never tested on hardware

Still outstanding from the books work, which shipped without these.

- **Set a default reader** (Settings, Library, Default Reader), then open a book. It must open in
  that app. This path has never run.
- Clear the reader, open a book: the system chooser appears rather than an error.
- The pinned reader row on the Library root opens the reader with no document.
- **Deep Rescan**, then confirm covers sharpen: the cache went from 400px to 1200px and a quick
  rescan reuses the old files, so only a deep one regenerates them.

```bash
python3 -c "
import sqlite3; c=sqlite3.connect('/tmp/after.db')
for col in ('title','author','series','cover_uri'):
    print(col, c.execute(f'SELECT COUNT(*) FROM books WHERE {col} IS NOT NULL').fetchone()[0])
"
adb -s $D shell "run-as $P ls -la cache/book_covers | head -3"
```

Expect roughly 80 titles and authors, 77 covers, 36 series. Cover files should be materially larger
than before the change.

## 8. Backup and restore

```
Settings, System, Backup, Create Backup
Settings, System, Backup, Restore
```

Book libraries and the reader choice come back; folder grants need re-linking, which the Backup
screen already says. Nothing achievement-shaped returns (check 3).

## 9. Generate the baseline profile

The generator module is wired but **no profile for this app's own code has ever been recorded** —
the shipped one carries only AndroidX and Compose library profiles.

```bash
./gradlew :app:generateReleaseBaselineProfile
```

Needs a rooted emulator or a userdebug device; a locked retail device cannot record one. It writes
`app/src/main/baseline-prof.txt`, **which must be committed**. Confirm it holds this app's classes:

```bash
grep -c "com/psplauncher" app/src/main/baseline-prof.txt
```

Zero means it recorded nothing useful. After committing, rebuild release and check the profile grew
beyond the library-only one:

```bash
./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/*.apk assets/dexopt/baseline.prof | wc -c   # was 16941
```

---

## Standing facts worth re-checking rather than trusting

- Release APK is **10.56 MB** (was 19.46 MB). `ls -la app/build/outputs/apk/release/*.apk`
- Suite is **2225 tests, 10 skipped, 0 failures**. `./gradlew test testDebugUnitTest --continue`
- The 10 skips are `GoldenPtfTest` and ROM-hash tests whose fixtures live in `~/Downloads` and are
  not present. They have always skipped; they are not passing.
- `main` is **20 commits ahead of `origin/main`** and nothing has been pushed.
