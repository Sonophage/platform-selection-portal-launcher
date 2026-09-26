package com.psplauncher.feature.backup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreferenceKeyTypeDriftTest {
    private val repoRoot: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root from ${File(".").absolutePath}")
    }

    private val declaration =
        Regex("""(string|boolean|int|float|long|double|stringSet)PreferencesKey\("([a-z_0-9]+)"\)""")

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
        assertTrue(
            declarations.size >= 60,
            "expected to find at least 60 preference keys, found ${declarations.size}. " +
                "The declaration pattern has probably stopped matching.",
        )

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
