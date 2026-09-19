# Remove achievements

**Status:** Planned · **Branch:** `chore/remove-achievements` · **Written:** 2026-09-19

## Goal

Delete achievement tracking from PSPLauncher: RetroAchievements, Steam, the local-Steam emulator
kit, Shiba Coins, the player card, and the 18 MB binary that ships for it.

## Why now

The next run of work is the settings tree, the game library and the game edit menu. All three are
full of achievement UI, so removing it first means that work happens once instead of twice.

It is also the single biggest thing in the APK:

```
release APK               19.5 MB
└─ assets/runtime         18.2 MB   pfp_bridge_x64.bin, one reference in the codebase
```

Expect roughly **19.5 MB to 1.5 MB**, and about **10,000 lines** gone.

## What is NOT achievements

The most expensive mistake available here is cutting something that merely sits nearby. Four things
look like achievements and are not.

| Looks like it | Actually | Verdict |
|---|---|---|
| `feature-artwork/rom/RomHasher` | The **scraper's** ROM identity (CRC32, ScreenScraper) | **KEEP** |
| `feature-achievements/match/RaRomHasher`, `ChdReader`, `DiscImage`, `ParamSfo` | RetroAchievements hashing only | Delete |
| `feature-settings/pc/*`, `feature-launcher/PcShortcutImporter` | Import PC Games, a library feature | **KEEP** |
| `feature-achievements/provider/steam/SteamShortcut` | Nothing outside the module references it | Delete |

There are **two ROM hashers**. `MetadataRepository` calls `RomHasher.identify(...)` and writes
`romCrc32` on every scrape. Deleting the wrong one silently breaks artwork matching for every ROM,
and no test in the achievements suite would notice.

## The one real entanglement

`PcGameScanner` — part of Import PC Games, which stays — imports from the module being deleted:

```kotlin
import com.psplauncher.feature.achievements.provider.localsteam.EmuGameImportResult
import com.psplauncher.feature.achievements.provider.localsteam.LocalSteamGameImporter
```

`LocalSteamGameImporter` in turn depends on `AchievementController`, `AchievementProvider` and
`LocalCopyOwnership`, so it cannot simply move: it is genuinely achievement-shaped.

It is one contribution among several, and the scanner already tolerates it failing
(`.getOrDefault(EmuGameImportResult(0, 0))` at `PcGameScanner.kt:152`). **Decision: drop the
local-Steam contribution from PC import and keep the rest.** PC games found through PFP export
files, shortcut import and manual scanning are unaffected; games discovered only by reading a local
Steam emulator's folders stop being found.

## Decisions taken

1. **The six tables are dropped**, not left inert, in a new `MIGRATION_45_46`. Historical
   migrations that CREATE them are never edited: v33 and v34 still create tables that v46 removes,
   which is correct and is how a migration history is supposed to read.
2. **`BuiltInCategory.ACHIEVEMENTS` joins `Category.RETIRED_IDS`.** This is not optional. The id is
   currently seeded and protected; `RETIRED_IDS` already holds `"social"` because a restored backup
   resurrected the retired Discord category once. The same bug is waiting here.
3. **PC import loses the local-Steam path** (above).

## Tasks

Ordered leaf-first so the build stays green as long as possible, and so the risky data change
happens after the code that reads it is already gone.

### Task 1 — Sever the one outside consumer

**Files:** `feature/feature-settings/.../pc/PcGameScanner.kt`, `PcGameScannerTest.kt`

- [ ] Drop `LocalSteamGameImporter` / `EmuGameImportResult` from `PcScanReport` and `scan()`.
- [ ] Confirm the remaining paths still populate a report; update `PcGameScannerTest`.
- [ ] Falsify: a PC scan still imports from a PFP export file.

**Verify:** `./gradlew :feature:feature-settings:testDebugUnitTest --tests '*PcGameScanner*'`

### Task 2 — Remove the UI surfaces

**Files:** `XMBViewModel.kt`, `XMBItemList.kt`, `SettingsNavHost.kt`, `GameDetailScreen.kt`,
`GameDetailViewModel.kt`, `GameDetailNav.kt`, `LibraryManagerViewModel.kt`, `InitialSetupScreen.kt`,
`PlayerStatus*`, `ShibaCoins*`, `ShibaLibrary*`, `AchievementsSettings*`, `DisplaySettingsViewModel.kt`

- [ ] `AchievementsNav`, `DrillOutStep.ACHIEVEMENTS`, the `activeShibaCoinsTarget` /
      `activeShibaLibrary` / `activePlayerStatus` overlay state and every branch reading them.
- [ ] The five `settings_achievements*` routes, from `SETTINGS_SCREEN_ROUTES` **and** the `when`.
      `SettingsHierarchyTest` fails if they drift.
- [ ] **This is where the Discord removal went wrong.** That cut took `onItemSelected` and
      `openPlatformFolder` with it and needed restoring from `git show HEAD:`. Remove only lines
      that name achievements; re-read each `when` afterwards.

**Verify:** `./gradlew :feature:feature-xmb:testDebugUnitTest --tests '*SettingsHierarchy*'`

### Task 3 — Icon slots, three ways plus a count

**Files:** `IconSlots.kt`, `StudioIconSet.kt`, `DefaultSlotGlyph.kt`, `XMBItemList.kt`,
`DefaultSlotGlyphTest.kt`

Slots to remove: `catbar_achievements`, `item_shiba_connect`, `item_shiba_track`,
`item_shiba_untracked`.

- [ ] Remove from all four registration sites; a one-sided change fails `StudioIconSetTest`.
- [ ] **Bump the hardcoded crossbar count DOWN** in `DefaultSlotGlyphTest` (currently `10`).
      Adding a slot without bumping it up failed with `expected:<10> but was:<9>`; removing one
      fails the same way in reverse.
- [ ] Delete `core-ui/achievement/BoneGlyph.kt` and `LocalSteamConvertPickerDialog.kt`.

**Verify:** `./gradlew :feature:feature-xmb:testDebugUnitTest :core:theme-kit:test :studio:test`

### Task 4 — Domain and data

**Files:** `core-domain/achievement/*`, `core-data/achievement/*`, 5 entities, 4 DAOs,
`DatabaseModule.kt`, `PFPDatabase.kt`, `GameDao.kt`, `GameEntity.kt`, `Game.kt`,
`GameRepository.kt`, `CategoryRepositoryImpl.kt`, `Category.kt`

- [ ] Delete `ShibaCoins`, `ShibaLevel`, `ShibaTier`, `LibraryStanding`, `LocalCopyOwnership`,
      `AchievementProvider`, `AchievementCredentialsProvider`.
- [ ] Remove the achievement DAOs and entities from `@Database` and `DatabaseModule`.
- [ ] Drop the seed row and the `PROTECTED_BUILTINS` entry; **add `"achievements"` to
      `RETIRED_IDS`** (decision 2).
- [ ] Remove `achievements_enabled` from `BACKUP_KEYS` and the tables from the backup table lists;
      extend `BackupKeyCoverageTest`.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest :feature:feature-backup:testDebugUnitTest`

### Task 5 — Migration v45 → v46

**Files:** `PFPDatabase.kt`, `DatabaseModule.kt`, `schemas/.../46.json`,
Test `Migration45To46Test.kt`

```sql
DROP TABLE IF EXISTS account_achievements;
DROP TABLE IF EXISTS account_achievement_sets;
DROP TABLE IF EXISTS achievement_match_notes;
DROP TABLE IF EXISTS provider_game_links;
DROP TABLE IF EXISTS steam_owned_games;
DROP TABLE IF EXISTS steam_no_achievements;
```

- [ ] Write the test first: seed a v45 database with rows in `games` **and** in two achievement
      tables, migrate, assert the achievement tables are gone and the games survived untouched.
      The surviving-data half is the assertion that can actually fail on its own, as in
      `Migration44To45Test`.
- [ ] Commit `46.json`, or every later migration test dies with `FileNotFoundException`.
- [ ] Falsify by dropping `games` too and confirming the survival assertion goes red by name.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest --tests '*Migration45To46*'`

### Task 6 — Delete the module

**Files:** `feature/feature-achievements/` (94 files, 9,961 lines), `settings.gradle.kts`,
every `build.gradle.kts` depending on it, `app/src/debug/.../ShibaStandingSeeder.kt` and the debug
menu rows

- [ ] Delete the module and its 18.2 MB `assets/runtime/pfp_bridge_x64.bin`.
- [ ] Remove `include(":feature:feature-achievements")` and every dependency line.

**Verify:** `./gradlew :app:assembleRelease` and record the new APK size.

### Task 7 — Repair the shared tests

Achievement-only test files are deleted with the module. These are **shared** and must be repaired
rather than removed:

`Migration32To33Test`, `Migration33To34Test` (they assert on tables v46 now drops — they run at v33
and v34, so they should still pass, but confirm rather than assume), `SettingsHierarchyTest`,
`DefaultSlotGlyphTest`, `GameDaoProjectionTest`, `BackupKeyCoverageTest`,
`ContextMenuPredicateTest`, `GameDetailNavTest`, `GameDetailScreenContentTest`,
`GameDetailViewModelTest`, `InitialSetupViewModelTest`, `LibraryManagerViewModelTest`,
`TextLegibilityTest`, `DetailScaffoldLayoutTest`, `SafSiblingDocumentIdTest`.

- [ ] Full suite green, and the count drops by the achievement tests rather than by anything else.
      Baseline before starting: **2375 tests, 10 skipped, 0 failures.**

**Verify:** `./gradlew test testDebugUnitTest --continue`

## Risk

`PFPDatabase` and `BackupManager` are the two files that can lose data beyond the achievement rows,
and Task 5 is the only irreversible step in the plan. Everything before it is code deletion that
`git` can undo.

The quiet risk is Task 2. The Discord removal in this project cut a working `when` branch along
with the feature and it was only caught because a build failed; a cut that leaves a compiling but
wrong `when` would not be. Read every `when` after editing it.

`RETIRED_IDS` is the other one. Without decision 2 the Shiba Coins category returns from any
restored backup, on a build with no code to render it.
