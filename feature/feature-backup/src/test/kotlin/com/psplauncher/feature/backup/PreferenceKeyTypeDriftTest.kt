package com.psplauncher.feature.backup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A preference key declared in several files must mean the same TYPE in all of them.
 *
 * 77 of this app's 89 preference keys are declared in two or more production files, kept in step
 * by 27 hand-written "Must match X" comments. That is a deliberate trade -- a single shared key
 * object would be one import away from every module, and moving 200-odd declarations at once is
 * how a setting silently resets to its default -- but it leaves one failure mode with real teeth.
 *
 * DataStore keys are typed by their constructor, not by the string. Declare
 * `booleanPreferencesKey("display_motion_wallpaper")` in one file and
 * `stringPreferencesKey("display_motion_wallpaper")` in another, and the two are different keys
 * to the compiler and the same key to the file on disk. The write succeeds. The read throws
 * ClassCastException at the moment the user opens that screen, on their device, with their saved
 * value, and never in a test.
 *
 * Nothing in the language can catch that, because the two declarations never meet. This can, and
 * it is the cheap half of the problem: it does not remove the duplication, it removes the one way
 * the duplication can hurt.
 */
class PreferenceKeyTypeDriftTest {

    private val repoRoot: File by lazy {
        // Walk up for the settings file rather than counting "../..": the working directory is
        // the module, and a module move would make a fixed depth point at nothing -- which reads
        // as "no keys found" and passes.
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    private val declaration =
        Regex("""(string|boolean|int|float|long|double|stringSet)PreferencesKey\("([a-z_0-9]+)"\)""")

    /** key name → the set of constructor types it is declared with, and where. */
    private val declarations: Map<String, Map<String, List<String>>> by lazy {
        val found = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { "${File.separator}build${File.separator}" in it.path }
            .filterNot { "${File.separator}src${File.separator}test${File.separator}" in it.path }
            .forEach { file ->
                declaration.findAll(file.readText()).forEach { m ->
                    val (type, key) = m.destructured
                    found.getOrPut(key) { mutableMapOf() }
                        .getOrPut(type) { mutableListOf() }
                        .add(file.relativeTo(repoRoot).path)
                }
            }
        found
    }

    @Test
    fun `the scan finds the preference keys it is supposed to be checking`() {
        // Guard on the guard, and the only thing standing between this test and passing forever
        // by finding nothing. A regex that stops matching -- a rename, a formatter putting the
        // string on its own line -- would otherwise turn every assertion below into a no-op.
        assertTrue(
            declarations.size >= 60,
            "expected to find at least 60 preference keys, found ${declarations.size}. " +
                "The declaration pattern has probably stopped matching.",
        )
        // And the duplication this test exists for is real, not hypothetical.
        val duplicated = declarations.count { (_, byType) -> byType.values.sumOf { it.size } > 1 }
        assertTrue(
            duplicated >= 20,
            "expected keys declared in several files; found $duplicated. If the duplication has " +
                "genuinely been removed, delete this test rather than lowering the number.",
        )
    }

    @Test
    fun `no preference key is declared with two different types`() {
        val conflicts = declarations.filterValues { it.size > 1 }
        assertEquals(
            emptyMap(),
            conflicts,
            "these keys are the same string on disk and different types in code, which throws " +
                "ClassCastException on read at runtime and never in a test: " +
                conflicts.entries.joinToString("; ") { (key, byType) ->
                    "$key is " + byType.entries.joinToString(" and ") { (type, files) ->
                        "${type}PreferencesKey in ${files.joinToString(", ")}"
                    }
                },
        )
    }
}
