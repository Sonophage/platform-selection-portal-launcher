// Top-level build file
plugins {
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.android.library)         apply false
    alias(libs.plugins.kotlin.jvm)              apply false
    alias(libs.plugins.kotlin.compose)          apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.ksp)                     apply false
    alias(libs.plugins.hilt)                    apply false
    alias(libs.plugins.jetbrains.compose)       apply false
    alias(libs.plugins.android.test)            apply false
    alias(libs.plugins.baselineprofile)         apply false
}

// One command to build every shippable release artifact into <root>/dist (gitignored):
// the launcher APK and the Theme Studio installer for the current OS. The per-module copy
// tasks (finalizing each release build) do the actual placing.
tasks.register("dist") {
    group = "distribution"
    description = "Builds the release APK and the Theme Studio installer into <root>/dist."
    dependsOn(
        ":app:assembleRelease",
        ":studio:packageReleaseDistributionForCurrentOS",
    )
}

// ── Stale comments ────────────────────────────────────────────────────────────
//
// `./gradlew staleComments` fails only on references that are NOT in the baseline.
//
// Baselined rather than absolute because the detector has a real false-positive rate: of the 48
// it finds today, most are framework symbols ([WiFi], [ENV]), deliberate history ("Was
// `contextRailOnly`") or placeholder shapes (`openXxxContextMenu`). A check that reports fifty
// things you have already decided about is one nobody reads, and this one was in the repo wired
// to nothing at all. What is worth failing on is a NEW one — a comment naming something the same
// change just deleted or renamed, which is the mistake this codebase actually keeps making.
//
// Regenerate with:  python3 tools/stale-comments/check.py . --write-baseline tools/stale-comments/baseline.txt
// and say in the commit why the new entries are acceptable.
tasks.register<Exec>("staleComments") {
    group = "verification"
    description = "Fails on comment references to symbols that do not exist and are not baselined."
    workingDir = rootDir
    commandLine(
        "python3", "tools/stale-comments/check.py", ".",
        "--baseline", "tools/stale-comments/baseline.txt",
    )
}

// The detector's own selftest — it feeds the scanner a fixture of live and dead symbols and
// checks it flags exactly the dead ones. A detector nobody has tested is a detector that reports
// success; this one was wrong the first time it ran, and the fixture says how.
tasks.register<Exec>("staleCommentsSelftest") {
    group = "verification"
    description = "Checks the stale-comment detector against its own fixture."
    workingDir = rootDir
    commandLine("python3", "tools/stale-comments/check.py", "--selftest")
}
