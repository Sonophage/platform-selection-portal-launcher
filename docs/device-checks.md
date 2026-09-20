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

**Pull the write-ahead log too, or you are reading a stale snapshot.** SQLite keeps recent writes
in `-wal` until a checkpoint, so a database copied on its own can be minutes behind. Reading one
without its WAL during this very check reported a retired category as un-pruned when it had in fact
been deleted.

```bash
rm -f /tmp/after.db /tmp/after.db-wal
adb -s $D exec-out "run-as $P cat databases/pfp_database"     > /tmp/after.db
adb -s $D exec-out "run-as $P cat databases/pfp_database-wal" > /tmp/after.db-wal
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
- Game Detail: no coins row; the cursor walks overview to Play/Details to media to information
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

## 8. Backup and restore — RUN, 2026-09-19

Round trip done on hardware. Backup wrote 26 JSON entries plus the wallpapers to
`/storage/6DBF-B253/PSPLauncher-Backups/`, and its counts matched the live database exactly:
147 games, 43 platforms, 9 categories, 0 collections, 80 books, 1 book library, 130 artwork
links and 0 dead ones. Restoring that file left every one of those tables identical, favourites
included, and the app restarted clean.

Two things it turned up. The manifest recorded `appVersionCode 0 / appVersionName "unknown"` on
every backup ever written, because the worker took them from input data and its one caller passed
none (fixed: the worker reads its own PackageManager entry). And the **Saved Backups** list on
that screen is labels only -- restoring goes through *Restore from File* and its document picker,
not by confirming a row in the list.

To re-run it: snapshot the database before and after and compare the counts per table.
**Pull the `-wal` with the database** or you are comparing stale snapshots.

## 9. Baseline profile — RUN, 2026-09-19

Generated and committed. **Not on the handheld:** it reports `ro.build.type=user` and
`ro.debuggable=0`, and the Macrobenchmark rule needs root or a userdebug build. It ran on the
`orca-pixel9` AVD (`google_apis`, API 37, `adb root` available):

```bash
~/Android/Sdk/emulator/emulator -avd orca-pixel9 -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect &
adb -s emulator-5554 root
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateReleaseBaselineProfile
```

It lands in `app/src/release/generated/baselineProfiles/baseline-prof.txt`, **not** the
`app/src/main/baseline-prof.txt` this document used to name, and it must be committed.

23,098 rules, 4,832 of them this app's own classes -- the shipped profile had none of its own
code before this. Prove the APK actually consumes it rather than trusting the count, by building
without it:

```bash
mv app/src/release/generated /tmp/holdout && ./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/*.apk assets/dexopt/baseline.prof | wc -c   # 16418
mv /tmp/holdout app/src/release/generated && ./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/*.apk assets/dexopt/baseline.prof | wc -c   # 18359
```

The compiled `.prof` is far smaller than the text it came from: it stores method references as
dex indices, not names, so a ~2.4 MB rule list becomes about 2 KB of added profile.

---

## Standing facts worth re-checking rather than trusting

- Release APK size. `ls -la app/build/outputs/apk/release/*.apk`
- Suite size and result. **Use `--rerun-tasks`:**

  ```bash
  ./gradlew test testDebugUnitTest --continue --rerun-tasks
  ```

  Without it Gradle reports a module's LAST result whenever that module's inputs have not
  changed, so a test that has gone red in a module you are not editing keeps printing green.
  That is not hypothetical: `DisplaySettingsViewModelLegibilityTest` was red on clean runs for
  days while every ordinary build called the suite green.
- Counting: the XML is the truth, `<skipped/>` is not a failure, and the glob below counts
  **every** result file under **every** `build/` — including one belonging to a module that has
  been deleted. That is not hypothetical either: a removed module's leftover `build/` was adding
  167 phantom tests to this count until the directory was deleted. If a number looks high, check
  which directories exist first:

  ```bash
  ls -d */*/build/test-results/*/ 2>/dev/null
  ```

  ```bash
  python3 - <<'PY'
  import glob, xml.etree.ElementTree as ET
  t = f = s = 0
  for p in glob.glob('**/build/test-results/**/*.xml', recursive=True):
      try: r = ET.parse(p).getroot()
      except Exception: continue
      t += int(r.get('tests', 0))
      f += int(r.get('failures', 0)) + int(r.get('errors', 0))
      s += int(r.get('skipped', 0))
  print(f"tests {t}  failures/errors {f}  skipped {s}")
  PY
  ```
- The skips are `GoldenPtfTest` and ROM-hash tests whose fixtures live in `~/Downloads` and are
  not present. They have always skipped; they are not passing.

### A fixed failure worth remembering the shape of

`DisplaySettingsViewModelLegibilityTest` failed only on a clean re-run, and the cause was worth
the hunt: a test collector launched with `CoroutineScope(dispatcher).launch { ... }` is QUEUED on
the test scheduler, not started. Until something pumps the scheduler the coroutine has never
subscribed, so a shared `WhileSubscribed` flow stays cold and a write made in the test body is
never observed.

`Job.isActive` reads `true` the whole time, which is what made it hard to see: the job exists, it
simply has not run. `dispatcher.scheduler.advanceUntilIdle()` at the end of `@Before` is the fix.

If a ViewModel-plus-DataStore test ever reports a state that will not follow a write that
demonstrably landed, look for a collector that was launched and never pumped before assuming the
flow is broken.
