package com.psplauncher.feature.backup

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every table the database declares, against every table the backup exports — the mirror of
 * [BackupKeyDriftTest], for rows rather than preferences.
 *
 * `BackupKeyCoverageTest` has a "keys excluded on purpose stay excluded" case for preferences.
 * There was no equivalent for tables, so a thirty-first entity added in six months would carry
 * real user rows, silently fail to survive a restore, and fail nothing. The user finds out on a
 * new device.
 *
 * Derived from the source rather than written out, for the same reason as the key test: a
 * hand-written list of tables is a third copy that drifts like the two it is meant to guard.
 */
class BackupTableDriftTest {

    /**
     * Tables that must NOT be exported, each with the reason. Every one of these is either
     * rebuildable from something more authoritative, or device-bound and meaningless elsewhere.
     */
    private val deliberatelyNotExported = mapOf(
        // The entity's own comment: "the folder stays the source of truth and Relink/Scan can
        // rebuild rows, so losing them is never data loss."
        "ArtworkRecordEntity" to "a fast map over the artwork folder; Relink rebuilds it",
        "ArtworkImportReportEntity" to "a record of one past import, not state",
        // A dumb log of launch verdicts, snapshotted per attempt on THIS device's emulators.
        "LaunchOutcomeEntity" to "launch diagnostics, device-bound",
        // Cached ScreenScraper responses. Re-fetched, and carrying them would restore another
        // device's quota state alongside them.
        "SsMediaCacheEntity" to "a provider response cache",
        // The entity's own comment says it is RETIRED: nothing reads or writes it, and it exists
        // only to hold the schema version steady for existing installs.
        "ScanTombstoneEntity" to "retired; nothing reads or writes it",
        // SAF tree URIs whose grants cannot survive a reinstall, so restored rows would point at
        // folders the new install has no permission to open. The backup screen tells the user to
        // re-link their roots instead. This is the one on this list worth revisiting: the FOLDER
        // NAMES are useful even when the grants are not, and restoring them as un-granted
        // placeholders would at least show the user what to re-pick.
        "LibrarySourceEntity" to "SAF grants cannot be restored; the user re-links roots",
    )

    private val repoRoot: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    /** The entity classes listed in `@Database(entities = [...])`. */
    private val declared: List<String> by lazy {
        val src = File(repoRoot, "core/core-data/src/main/kotlin/com/psplauncher/core/data/database/PFPDatabase.kt")
            .readText()
        val start = src.indexOf("entities = [")
        check(start >= 0) { "could not find the @Database entity list" }
        val block = src.substring(start, src.indexOf("]", start))
        Regex("""(\w+Entity)::class""").findAll(block).map { it.groupValues[1] }.distinct().toList()
    }

    /** The entity types the export actually serialises. */
    private val exported: List<String> by lazy {
        val src = File(repoRoot, "feature/feature-backup/src/main/kotlin/com/psplauncher/feature/backup/BackupManager.kt")
            .readText()
        Regex("""listSerializer<(\w+Entity)>""").findAll(src).map { it.groupValues[1] }.distinct().toList()
    }

    @Test
    fun `the scan found both lists, so the rest of this means something`() {
        // A guard on the guard: a renamed file or a reformatted annotation would otherwise make
        // every assertion below pass over an empty list.
        assertTrue(declared.size >= 25, "only found ${declared.size} entities")
        assertTrue(exported.size >= 20, "only found ${exported.size} exported tables")
    }

    @Test
    fun `every table is either exported or deliberately excluded`() {
        val unaccounted = (declared - exported.toSet() - deliberatelyNotExported.keys).sorted()
        assertEquals(
            emptyList(),
            unaccounted,
            "these tables hold user rows that would not survive a restore. Export them in " +
                "BackupManager, or add each to deliberatelyNotExported with its reason: $unaccounted",
        )
    }

    @Test
    fun `the backup exports no table the database has dropped`() {
        val phantom = (exported - declared.toSet()).sorted()
        assertEquals(emptyList(), phantom, "exported but no longer a table: $phantom")
    }

    @Test
    fun `nothing is in both lists`() {
        val both = (exported.toSet() intersect deliberatelyNotExported.keys).sorted()
        assertEquals(emptyList(), both, "a table cannot be both exported and deliberately excluded")
    }

    @Test
    fun `the exclusion list has no entries for tables that no longer exist`() {
        // Otherwise the list becomes a graveyard, and a real exclusion is harder to see among
        // reasons for tables nobody has had for a year.
        val stale = (deliberatelyNotExported.keys - declared.toSet()).sorted()
        assertEquals(emptyList(), stale, "excluded but not a table any more: $stale")
    }
}
