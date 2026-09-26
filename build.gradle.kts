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

