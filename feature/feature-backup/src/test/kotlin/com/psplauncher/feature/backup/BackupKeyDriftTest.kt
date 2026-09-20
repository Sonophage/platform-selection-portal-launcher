package com.psplauncher.feature.backup

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every preference the app declares, against every preference the backup carries — **derived**,
 * not written out.
 *
 * `BackupKeyCoverageTest` next door is a whitelist: it names keys somebody remembered and asserts
 * they are covered. It cannot see a key nobody remembered, and it cannot see a key the backup
 * still carries after the app stopped using it. Both had happened. When this was written the
 * backup was writing and restoring four preferences that exist nowhere else in the tree, and was
 * missing several the user had actually chosen.
 *
 * This walks the source instead, so it stays true without anyone maintaining it. A new
 * preference is in the backup or in [DELIBERATELY_NOT_BACKED_UP] with a reason, or this fails.
 */
class BackupKeyDriftTest {

    /**
     * Keys that must NOT ride a backup, each with the reason it would be wrong to carry.
     *
     * Adding to this list is a decision. Deleting from it is also a decision. What is not allowed
     * is a key in neither list.
     */
    private val deliberatelyNotBackedUp = mapOf(
        // Migration and seed markers describe THIS install's schema progress. Restoring them onto
        // a fresh device convinces it that migrations already ran.
        "db_seeded_v1" to "seed marker",
        "debug_seeded_v1" to "seed marker",
        "themes_seeded_v1" to "seed marker",
        "library_consolidated_v22" to "migration marker",
        "data_prep_version" to "migration marker",
        // Points at an extracted directory that is not bundled, so a restored stamp would send
        // observers at files that are not there.
        "theme_icons_stamp" to "dangling pointer into un-bundled files",
        // Derived on arrival. The luminance survey embeds the absolute path it was computed from,
        // so a restored copy names the SOURCE device and is rejected as stale on first read.
        "display_wallpaper_luma" to "recomputed by StartupDataPrep from the restored wallpaper",
        // SAF grants do not survive a reinstall; the URI without its grant is a dead string, and
        // the backup screen tells the user to re-link these roots.
        "retroarch_documents_tree_uri" to "SAF grant cannot be restored",
        "vita3k_ux0_tree_uri" to "SAF grant cannot be restored",
        // Rebuilt by a scan. Carrying a stale inventory would offer cores this device lacks,
        // which is the exact failure RetroArchCoreScanner refuses to cause.
        "retroarch_cached_core_files" to "rebuilt by the core scan",
        "auto_resolved_cores" to "rebuilt by emulator detection",
    )

    private val repoRoot: File by lazy {
        // Walk up for the settings file rather than counting "../.." — the test's working
        // directory is the module, and a module move would silently make a hardcoded depth point
        // at nothing, which reads as "no keys found" and passes.
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    private val keyPattern =
        Regex("""(?:string|boolean|int|float|long|double|stringSet)PreferencesKey\("([a-z_0-9]+)"\)""")

    /**
     * Key families assembled at runtime from a prefix plus an enum, so the full key never appears
     * as a literal anywhere. `UiMediaStore` builds `ui_media_name_<slot>` this way.
     *
     * This is the detector's blind spot, named rather than worked around. A prefix listed here
     * must appear SOMEWHERE in the tree outside this module -- asserted below -- so it cannot
     * become a quiet way to exempt a key.
     */
    private val dynamicKeyPrefixes = listOf("ui_media_name_")

    /** Every .kt file outside build output and outside this module, read once. */
    private val appSources: List<String> by lazy {
        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { "/build/" in it.path || "${File.separator}build${File.separator}" in it.path }
            .filterNot { "feature-backup" in it.path }
            .map { it.readText() }
            .toList()
    }

    /**
     * The key's name appears as a quoted literal somewhere in the app.
     *
     * Weaker than [declared] on purpose, and used only for the orphan direction. Some keys are
     * named by an enum constant and handed to `stringSetPreferencesKey(kind.key)` -- MediaRoot's
     * `book_root_tree_uris` is one -- so they are perfectly live while being invisible to a regex
     * that only reads key constructors. Asking "does this string exist at all" cannot produce a
     * false ORPHAN, which is the direction that matters here.
     */
    private fun namedAnywhere(key: String): Boolean =
        appSources.any { "\"$key\"" in it }

    /** key -> the files that declare it, for every module's main source. */
    private val declared: Map<String, List<String>> by lazy {
        val found = mutableMapOf<String, MutableList<String>>()
        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { "/build/" in it.path || "${File.separator}build${File.separator}" in it.path }
            .filter { "src${File.separator}main" in it.path }
            // NOT the backup's own source. Its typed lists declare every key it carries, so
            // counting those as "the app declares this" would make the orphan check below
            // tautologically green -- which is exactly what the first run of it did.
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
        // A guard on the guard. If the walk ever stops finding files — a module move, a renamed
        // source set — every assertion below would pass over an empty map and report success.
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
        // The other direction, and the one no whitelist can ever see. A retired preference left
        // in the list is written into every archive and restored onto every device, where nothing
        // reads it — invisible, permanent, and sitting under a tidy comment that makes it look
        // deliberate.
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
        // Without this, adding a prefix here would be a way to silence the orphan check. Each one
        // has to appear in the tree outside the backup module.
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
        /**
         * Retired keys the backup still READS so an archive written before they were retired can
         * migrate through a read-time rule. They are deliberately absent from the app's own
         * declarations, so the orphan check has to know about them.
         */
        val LEGACY_RESTORE_ONLY = setOf(
            // GameBoot was a three-way mode before it became a boolean; GameBootPreferences has a
            // read-time rule that migrates the old value instead of reverting to the default.
            "display_gameboot_mode",
        )
    }
}
