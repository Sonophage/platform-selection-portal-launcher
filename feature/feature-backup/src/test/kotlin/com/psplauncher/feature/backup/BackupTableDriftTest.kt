package com.psplauncher.feature.backup

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupTableDriftTest {
    private val deliberatelyNotExported = mapOf(

        "ArtworkRecordEntity" to "a fast map over the artwork folder; Relink rebuilds it",
        "ArtworkImportReportEntity" to "a record of one past import, not state",

        "LaunchOutcomeEntity" to "launch diagnostics, device-bound",

        "SsMediaCacheEntity" to "a provider response cache",

        "ScanTombstoneEntity" to "retired; nothing reads or writes it",

        "LibrarySourceEntity" to "SAF grants cannot be restored; the user re-links roots",
    )

    private val repoRoot: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    private val declared: List<String> by lazy {
        val src = File(repoRoot, "core/core-data/src/main/kotlin/com/psplauncher/core/data/database/PFPDatabase.kt")
            .readText()
        val start = src.indexOf("entities = [")
        check(start >= 0) { "could not find the @Database entity list" }
        val block = src.substring(start, src.indexOf("]", start))
        Regex("""(\w+Entity)::class""").findAll(block).map { it.groupValues[1] }.distinct().toList()
    }

    private val exported: List<String> by lazy {
        val src = File(repoRoot, "feature/feature-backup/src/main/kotlin/com/psplauncher/feature/backup/BackupManager.kt")
            .readText()
        Regex("""listSerializer<(\w+Entity)>""").findAll(src).map { it.groupValues[1] }.distinct().toList()
    }

    @Test
    fun `the scan found both lists, so the rest of this means something`() {
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
        val stale = (deliberatelyNotExported.keys - declared.toSet()).sorted()
        assertEquals(emptyList(), stale, "excluded but not a table any more: $stale")
    }
}
