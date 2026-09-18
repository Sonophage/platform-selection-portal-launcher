# Books section

**Status:** Tasks 1 to 5 done, the data half is complete · **Branch:** `feat/books-section` · **Written:** 2026-09-18

## Goal

Add a **Books** media section: point it at SAF folders, see the EPUBs inside, and open one in a
reader app you pick once.

## Architecture

Books is the fourth instance of a shape this codebase already has three of (Music, Video, Photo):
a SAF library table, a pure file filter, a scanner, an intent resolver that pins to a chosen app,
and a settings screen. It is the *simplest* of the four, because there is no in-app reader to
write. The section models itself on **Photo** (libraries and files, no playlists) and borrows
Video's default-player idea for the reader.

One piece is deliberately NOT copied. `MusicIntentResolver` and `VideoIntentResolver` are already
near-identical, and Books would make three. Task 2 extracts the shared half first, so Books is the
reason there is one resolver rather than the reason there are three.

## Tech stack

Kotlin, Jetpack Compose, MVVM, Hilt, Room (currently v43), DataStore, SAF (`DocumentsContract`).
Tests are pure-JVM JUnit4 + MockK, with Robolectric where a resource or a real DB is needed.

## Non-goals (first pass)

Covers, reading progress, collections, series grouping, PDF/CBZ/MOBI, in-app reading. Progress is
the reader app's business: PSPLauncher hands over a file and stops. Covers are the obvious second
pass and the artwork subsystem is already there when you want them.

## Decisions taken (2026-09-18)

1. **Category id is `library`**, not `books`. The user already has a custom category called Books,
   holding a reader app, and a built-in of the same id would collide with it on a restored
   database. `library` was free: nothing in the codebase uses it as a category id. The section is
   titled **Library**.
2. **Category bar art is drawn**, at `core-ui/res/drawable/catbar_library.xml`: an open book, flat
   white silhouette at the weight of its neighbours. Nothing in the existing set reads as a book,
   and every catbar drawable is already spoken for, so borrowing one would put two identical icons
   on the bar. Swap it for other art whenever you like; only the file changes.

## File map

**Create**

| Path | What |
|---|---|
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/book/BookFileFilter.kt` | extension/MIME gate |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/media/MediaOpenIntent.kt` | shared "pin to app, else chooser" |
| `core/core-domain/src/main/kotlin/com/psplauncher/core/domain/model/Book.kt` | domain model |
| `core/core-domain/src/main/kotlin/com/psplauncher/core/domain/model/BookLibrary.kt` | domain model |
| `core/core-domain/src/main/kotlin/com/psplauncher/core/domain/repository/BookRepository.kt` | interface |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/database/entity/BookEntity.kt` | Room entity |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/database/entity/BookLibraryEntity.kt` | Room entity |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/database/dao/BookDao.kt` | DAO |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/database/dao/BookLibraryDao.kt` | DAO |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/repository/BookRepositoryImpl.kt` | impl + prefs |
| `core/core-data/src/main/kotlin/com/psplauncher/core/data/book/BookIntentResolver.kt` | open a book |
| `feature/feature-library/src/main/kotlin/com/psplauncher/feature/library/scanner/BookScanner.kt` | SAF scan |
| `feature/feature-settings/src/main/kotlin/com/psplauncher/feature/settings/ui/BooksSettingsScreen.kt` | settings |
| `feature/feature-settings/src/main/kotlin/com/psplauncher/feature/settings/viewmodel/BooksSettingsViewModel.kt` | settings state |

**Modify:** `PFPDatabase.kt`, `DatabaseModule.kt`, `SettingsNavHost.kt`, `XMBViewModel.kt`,
`XMBItemList.kt`, `CategoryRepositoryImpl.kt`, `Category.kt`, `CategoryIcons.kt`,
`XmbIconOverrides.kt`, `IconSlots.kt`, `StudioIconSet.kt`, `DefaultSlotGlyph.kt`,
`BackupManager.kt`, `BackupManifest.kt` (BUNDLED tables), `PreviewModel.kt`.

---

## Task 1 — The file filter

A pure function with no Android types, so it can be unit-tested directly. It exists because SAF
providers routinely report `application/octet-stream` for `.epub`, so a MIME-only gate finds
nothing. `PhotoFileFilter` already solved this; copy its shape.

**Files:** Create `core/core-data/src/main/kotlin/com/psplauncher/core/data/book/BookFileFilter.kt`,
Test `core/core-data/src/test/kotlin/com/psplauncher/core/data/book/BookFileFilterTest.kt`

- [x] Write the failing test first:

```kotlin
package com.psplauncher.core.data.book

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SAF providers on this device report `application/octet-stream` for .epub more often than they
 * report the real type, so a MIME-only gate finds an empty library and looks like a broken scan.
 * The extension fallback is the whole point of this object, not a nicety.
 */
class BookFileFilterTest {

    @Test fun `the real epub mime is accepted`() =
        assertTrue(BookFileFilter.isBook("Dune.epub", "application/epub+zip"))

    @Test fun `an octet-stream epub is accepted on its extension`() =
        assertTrue(BookFileFilter.isBook("Dune.epub", "application/octet-stream"))

    @Test fun `a missing mime falls back to the extension`() =
        assertTrue(BookFileFilter.isBook("Dune.EPUB", null))

    @Test fun `a non-book is refused even with a book-ish mime`() =
        assertFalse(BookFileFilter.isBook("cover.jpg", "image/jpeg"))

    @Test fun `an extensionless file is refused rather than guessed`() =
        assertFalse(BookFileFilter.isBook("Dune", null))
}
```

- [x] Run it, confirm RED (the class does not exist):
      `./gradlew :core:core-data:testDebugUnitTest --tests '*BookFileFilterTest*'`
- [x] Implement:

```kotlin
package com.psplauncher.core.data.book

/**
 * Pure book-file detection shared by the scanner. A file counts as a book when its MIME type is a
 * known book type, or — when the MIME is missing or the generic `application/octet-stream`, which
 * is what most SAF providers return for .epub — its extension is a known book type. Kept free of
 * Android types so it can be unit-tested directly. Mirrors [PhotoFileFilter].
 */
object BookFileFilter {

    const val EPUB_MIME = "application/epub+zip"

    val BOOK_EXTENSIONS = setOf("epub")

    private val BOOK_MIMES = setOf(EPUB_MIME)

    fun isBook(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType != "application/octet-stream") {
            return mimeType in BOOK_MIMES
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in BOOK_EXTENSIONS
    }
}
```

- [x] Run it, confirm GREEN.
- [x] **Falsify:** delete the octet-stream branch, confirm `an octet-stream epub is accepted on
      its extension` goes red by name, restore.
- [x] `/cs-verify`, then commit.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest --tests '*BookFileFilterTest*'`

---

## Task 2 — One intent resolver, not three

`MusicIntentResolver` (91 lines) and `VideoIntentResolver` (120 lines) already say the same thing:
build `ACTION_VIEW` on a SAF uri, grant read, pin to a user-chosen package when set, retry through
the chooser when the pinned app fails, never throw. Extract that half before adding a third caller.

**Files:** Create `core/core-data/src/main/kotlin/com/psplauncher/core/data/media/MediaOpenIntent.kt`,
Modify `MusicIntentResolver.kt`, `VideoIntentResolver.kt`,
Test `core/core-data/src/test/kotlin/com/psplauncher/core/data/media/MediaOpenIntentTest.kt`

- [x] Write the test first. It must pin the two behaviours that are easy to lose in a refactor:
      a pinned package is set on the intent, and a blank package is NOT (the sentinel case that
      made `MusicIntentResolver` leave `builtin` unpinned).

```kotlin
package com.psplauncher.core.data.media

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaOpenIntentTest {

    @Test fun `a chosen package is pinned`() {
        val i = MediaOpenIntent.build("content://doc/1", "application/epub+zip", "com.flyersoft.moonreader")
        assertEquals("com.flyersoft.moonreader", i.`package`)
    }

    @Test fun `a null or blank package stays unpinned so the chooser can run`() {
        assertNull(MediaOpenIntent.build("content://doc/1", "video/*", null).`package`)
        assertNull(MediaOpenIntent.build("content://doc/1", "video/*", "  ").`package`)
    }

    @Test fun `the read grant rides along or the target app sees nothing`() {
        val i = MediaOpenIntent.build("content://doc/1", "audio/*", null)
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
```

- [x] Run it, confirm RED.
- [x] Implement `MediaOpenIntent` with `build(uri, mime, packageName): Intent` and
      `launch(context, intent, chooserTitle): String?` returning null on success or a
      user-readable message, retrying once through the chooser when a pinned package fails.
      Lift the bodies from `VideoIntentResolver.buildViewIntent` / `launch` verbatim; they are
      already the more complete of the two.
- [x] Rewrite `MusicIntentResolver` and `VideoIntentResolver` to delegate. Keep both classes and
      their public signatures: they own the per-type MIME default and the error copy, which is the
      part that is genuinely different.
- [x] Run the existing music and video tests, confirm still GREEN. **This is the real check on
      the refactor, not the new test.**
- [x] `/cs-verify`, then commit.

**Done 2026-09-18.** One behaviour was unified rather than preserved: a pinned player that
failed sent Video to the system chooser and Music to a second bare attempt, which let Android
silently pick a different app. Neither was pinned by a test. Music now does what Video did.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest --tests '*MediaOpenIntent*' --tests '*MusicIntent*' --tests '*VideoIntent*'`

---

## Task 3 — Tables and migration v43 → v44

**Files:** Create `BookLibraryEntity.kt`, `BookEntity.kt`, `BookDao.kt`, `BookLibraryDao.kt`;
Modify `PFPDatabase.kt`, `DatabaseModule.kt`;
Test `core/core-data/src/test/kotlin/com/psplauncher/core/data/database/Migration43To44Test.kt`

Mirror `PhotoLibraryEntity` / `PhotoEntity` exactly, renaming photo to book and dropping
`width`, `height`, `date_taken`. Keep `author` and `title` nullable for a later metadata pass.
`books` cascade-deletes with `book_libraries` and is indexed on `library_id` and `uri`, as
`photos` is.

- [x] Write `Migration43To44Test` first, following `Migration42To43Test` and
      `MigrationTestSupport.kt`. Assert the two tables exist, that the foreign key cascades, and
      that an existing row in another table survives untouched.
- [x] Run it, confirm RED.
- [x] Add `MIGRATION_43_44` to `PFPDatabase.kt` as `CREATE TABLE IF NOT EXISTS` plus the two
      indices. **Never destructive migration.** Bump `@Database(version = 44)` and add the two
      entities and DAOs.
- [x] Register `PFPDatabase.MIGRATION_43_44` in `DatabaseModule.kt` beside `MIGRATION_42_43`.
- [x] Run, confirm GREEN. Room exports `schemas/com.psplauncher.core.data.database.PFPDatabase/44.json` —
      **commit that file**, or every later migration test fails with `FileNotFoundException`.
- [x] `/cs-verify`, then commit.

**Done 2026-09-18.** The cascade and index assertions were written into the migration test
first and could not be falsified there: `runMigrationsAndValidate` compares the result against the
exported schema and fires before any assertion, taking the whole class with it. The migration test
now asserts only the one claim that can fail on its own, that existing data survives; the cascade
moved to `BookCascadeTest` against a live in-memory database, where breaking it raises a real
`SQLiteConstraintException`.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest --tests '*Migration43To44*' --tests '*BookCascadeTest*'`

---

## Task 4 — Domain models, repository, and the reader preference

**Files:** Create `Book.kt`, `BookLibrary.kt`, `BookRepository.kt`, `BookRepositoryImpl.kt`;
Modify `BackupManager.kt`

Mirror `PhotoRepositoryImpl`. The one new thing is the reader preference, which follows
`VideoRepositoryImpl`'s exactly:

```kotlin
private val KEY_BOOK_DEFAULT_READER = stringPreferencesKey("books_default_reader")
```

- [x] Add `books_default_reader` to `BACKED_UP_STRING_KEYS` in `BackupManager.kt` beside
      `video_default_player`, and the two new tables to the backup/restore table lists. A setting
      that misses that list reverts silently on a restored device, which is exactly what the
      existing `BackupKeyCoverageTest` is there to catch.
- [x] Extend `BackupKeyCoverageTest` with a `books` case.
- [x] `/cs-verify`, then commit.

**Done 2026-09-18.** `BookIntentResolver` landed here too, rather than waiting for the UI: it
is what makes the reader preference mean anything, and it carries the one rule that is genuinely
about books. A scanned row may hold `application/octet-stream`, because that is what the provider
said, and forwarding it resolves no reader; the resolver asserts the EPUB type instead, which is
what got the row into the library.

**Verify:** `./gradlew :core:core-data:testDebugUnitTest --tests '*BookIntentResolverTest*' :feature:feature-backup:testDebugUnitTest`

---

## Task 5 — The scanner

**Files:** Create `feature/feature-library/.../scanner/BookScanner.kt`,
Test `feature/feature-library/src/test/kotlin/.../BookScannerTest.kt`

Model on `PhotoScanner`, which is the same walk. Reuse `core.data.saf.SafChildren` as is:
`querySafChildren`, `isIgnoredDir`, `hasNoMediaMarker`, `safScanStartDocId`. Books needs **less**
than PhotoScanner: no thumbnail generation, no EXIF, no bounded decode parallelism. Delete those
branches rather than carrying them.

Emit the same result shape (`Progress` / `Complete` / `Error`) so the settings screen and the
rescan path can treat it like the others.

- [x] Write the scanner test first against a fake SAF tree, asserting: a nested `.epub` is found
      when recursive, is not when not, a `.nomedia` folder is skipped, and a non-book file is
      ignored.
- [x] Run RED, implement, run GREEN.
- [x] `/cs-verify`, then commit.

**Done 2026-09-18.** The walk is a plain function taking its directory listing as a lambda,
rather than a class reading a `ContentResolver`, so the policy is testable with no device and no
Robolectric in this module. Dedupe is keyed on the document id rather than the uri, since the id
is the provider's identity for a file. The loop guard is the one rule whose failure mode is a hang
rather than a red assertion; removing it stops the suite completing, which is what a CI timeout
catches.

**Verify:** `./gradlew :feature:feature-library:testDebugUnitTest --tests '*BookScanner*'`

---

## Task 6 — Settings screen

**Files:** Create `BooksSettingsScreen.kt`, `BooksSettingsViewModel.kt`;
Modify `SettingsNavHost.kt`, `XMBViewModel.kt`

Mirror `PhotoSettingsScreen` for the library list (add folder, rename, toggle recursive, rescan,
remove) and `VideoSettingsScreen`'s default-player picker for **Default Reader**: the installed
apps that can handle `ACTION_VIEW` for `application/epub+zip`, plus "Ask Every Time". There is no
built-in option, so unlike Video there is no `PLAYER_BUILTIN` sentinel.

- [ ] Add `"settings_books"` to `SETTINGS_SCREEN_ROUTES` **and** to the `when` in the same file.
      The file's own comment says new screens must be in both; `SettingsHierarchyTest` fails if
      they drift.
- [ ] Add the L2 row to `XMBViewModel.kt` beside `settings_photo`:
      `XMBItem(id = "settings_books", title = "Books", subtitle = "Book libraries & reader")`
- [ ] Run `SettingsHierarchyTest`, confirm GREEN.
- [ ] `/cs-verify`, then commit.

**Verify:** `./gradlew :feature:feature-xmb:testDebugUnitTest --tests '*SettingsHierarchy*'`

---

## Task 7 — The XMB section

**Blocked on both decisions at the top of this plan.**

**Files:** Modify `XMBViewModel.kt`, `XMBItemList.kt`, `CategoryRepositoryImpl.kt`, `Category.kt`,
`CategoryIcons.kt`, `XmbIconOverrides.kt`, `IconSlots.kt`, `StudioIconSet.kt`,
`DefaultSlotGlyph.kt`, `PreviewModel.kt`

- [ ] `BooksNav` in `XMBViewModel.kt`, mirroring `PhotoNav`: `Root`, `AllBooks`, `Libraries`,
      `Library(id, name)`. Add the nav key, the title, the sibling list and the drill-out rung,
      as the Photo section does in all five places.
- [ ] `BuiltInCategory.BOOKS` in `Category.kt`, plus the seed row and `PROTECTED_BUILTINS` entry
      in `CategoryRepositoryImpl.kt`. Append its position after the current last built-in so an
      established database does not collide.
- [ ] **The icon slot is a three-way pair that must agree**, and two tests enforce it. Add
      `catbar_books` to `IconSlots.kt`, to `StudioIconSet.RESOURCE_SLOTS`, and the matching
      drawable; add the item slots to `DefaultSlotGlyph.kt`. `StudioIconSetTest` and
      `DefaultSlotGlyphTest` fail on a one-sided change, and `DefaultSlotGlyphTest` also asserts a
      hardcoded crossbar-slot COUNT that must be bumped.
- [ ] The pinned top row: an `XMBItem` that launches the chosen reader with no document. Build it
      with `MediaOpenIntent` and a null uri path, or `appCategoryRepository.launch(pkg)` if the
      reader is stored as a package. Hide the row when no reader is set.
- [ ] `/cs-verify`, then commit.

**Verify:** `./gradlew :feature:feature-xmb:testDebugUnitTest :core:theme-kit:test :studio:test`

---

## Task 8 — On the device

Unit tests do not prove a SAF grant, a real EPUB, or a real reader app.

- [ ] `./gradlew :app:assembleDebug && adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] Add a folder of EPUBs as a Books library, scan it, confirm the count.
- [ ] Set the default reader, open a book, confirm it opens **in that app**.
- [ ] Clear the reader, open a book, confirm the chooser appears rather than an error.
- [ ] Select the pinned row, confirm the reader opens with no document.
- [ ] Back up and restore, confirm the libraries and the reader choice come back. Folder access
      will need re-linking; that is expected and the Backup screen already says so.

**Verify:** manual, the six checks above

---

## Risk

`PFPDatabase` and `BackupManager` are the two files that can lose user data. The migration is
additive and `CREATE TABLE IF NOT EXISTS` only; no existing table is touched. Task 2 refactors two
resolvers that are on the music and video launch paths, which is the one place in this plan where
a regression would be user-visible without a test failing first, so run the existing music and
video suites before committing it.
