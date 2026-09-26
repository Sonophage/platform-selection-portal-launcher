package com.psplauncher.feature.backup

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupKeyDriftTest {
    private val deliberatelyNotBackedUp = mapOf(

        "db_seeded_v1" to "seed marker",
        "debug_seeded_v1" to "seed marker",
        "themes_seeded_v1" to "seed marker",
        "library_consolidated_v22" to "migration marker",

        "last_played_placed_v1" to "one-shot position fix that a restore must be able to re-run",

        "network_renamed_v1" to "one-shot name fix that a restore must be able to re-run",
        "data_prep_version" to "migration marker",

        "theme_icons_stamp" to "dangling pointer into un-bundled files",

        "display_wallpaper_luma" to "recomputed by StartupDataPrep from the restored wallpaper",

        "wallpaper_accent" to "recomputed by StartupDataPrep from the restored wallpaper",

        "retroarch_documents_tree_uri" to "SAF grant cannot be restored",
        "vita3k_ux0_tree_uri" to "SAF grant cannot be restored",

        "retroarch_cached_core_files" to "rebuilt by the core scan",
        "auto_resolved_cores" to "rebuilt by emulator detection",
    )

    private val repoRoot: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    private val keyPattern =
        Regex("""(?:string|boolean|int|float|long|double|stringSet)PreferencesKey\("([a-z_0-9]+)"\)""")

    private val dynamicKeyPrefixes = listOf("ui_media_name_")

    private val appSources: List<String> by lazy {
        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { "/build/" in it.path || "${File.separator}build${File.separator}" in it.path }
            .filterNot { "feature-backup" in it.path }
            .map { it.readText() }
            .toList()
    }

    private fun namedAnywhere(key: String): Boolean =
        appSources.any { "\"$key\"" in it }

    private val declared: Map<String, List<String>> by lazy {
        val found = mutableMapOf<String, MutableList<String>>()
        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { "/build/" in it.path || "${File.separator}build${File.separator}" in it.path }
            .filter { "src${File.separator}main" in it.path }

            .filterNot { "feature-backup" in it.path }
            .forEach { file ->
                keyPattern.findAll(file.readText()).forEach { m ->
                    found.getOrPut(m.groupValues[1]) { mutableListOf() }
                        .add(file.relativeTo(repoRoot).path)
                }
            }
        found
    }

    @Test
    fun `the scan actually found preferences, so the rest of this means something`() {
        assertTrue(declared.size >= 60, "only found ${declared.size} preference keys; the scan is broken")
        assertTrue("display_custom_wallpaper" in declared, "a known key is missing from the scan")
    }

    @Test
    fun `every preference the app declares is either backed up or deliberately excluded`() {
        val backedUp = BackupManager.BACKED_UP_KEY_NAMES
        val unaccounted = declared.keys
            .filterNot { it in backedUp || it in deliberatelyNotBackedUp }
            .sorted()
        assertEquals(
            emptyList(),
            unaccounted,
            "these settings silently do not survive a restore. Add each to BackupManager's typed " +
                "lists, or to deliberatelyNotBackedUp with the reason it must not be carried: " +
                unaccounted.joinToString { "$it (${declared[it]?.firstOrNull()})" },
        )
    }

    @Test
    fun `the backup carries no key the app has stopped using`() {
        val orphans = (BackupManager.BACKED_UP_KEY_NAMES - declared.keys - LEGACY_RESTORE_ONLY)
            .filterNot { key -> dynamicKeyPrefixes.any { key.startsWith(it) } }
            .filterNot { namedAnywhere(it) }
            .sorted()
        assertEquals(
            emptyList(),
            orphans,
            "the backup writes these and nothing in the app declares them; delete them, or list " +
                "them in LEGACY_RESTORE_ONLY if an old archive still needs migrating through: $orphans",
        )
    }

    @Test
    fun `every dynamic key prefix is real, so the exemption cannot hide anything`() {
        dynamicKeyPrefixes.forEach { prefix ->
            val found = repoRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { "/build/" in it.path || "feature-backup" in it.path }
                .any { prefix in it.readText() }
            assertTrue(found, "dynamic prefix '$prefix' appears nowhere in the app")
        }
    }

    @Test
    fun `nothing is in both lists`() {
        val both = BackupManager.BACKED_UP_KEY_NAMES intersect deliberatelyNotBackedUp.keys
        assertEquals(emptySet(), both, "a key cannot be both carried and deliberately excluded")
    }

    private companion object {
        val LEGACY_RESTORE_ONLY = setOf(

            "display_gameboot_mode",
        )
    }
}
