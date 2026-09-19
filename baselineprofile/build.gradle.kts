// baselineprofile: generates the startup baseline profile for :app. Never shipped.
//
// A `com.android.test` module builds an instrumentation APK that drives the real launcher on a
// real device, records which classes and methods run during startup, and writes the result into
// :app as `baseline-prof.txt`. AGP compiles that into `assets/dexopt/baseline.prof`, and ART
// pre-compiles those methods at install time instead of interpreting them on first launch.
//
// It matters more here than in most apps: this IS the home screen, so a cold start happens every
// time the user presses Home. Before this, the shipped profile carried only the AndroidX and
// Compose library profiles, and none of PSPLauncher's own startup path.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace  = "com.psplauncher.baselineprofile"
    compileSdk = 37
    defaultConfig {
        // Macrobenchmark needs 28+; the app's own floor is 29, so this follows the app.
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

baselineProfile {
    // Record against whatever is plugged in. Run it deliberately:
    //   ./gradlew :app:generateReleaseBaselineProfile
    // It needs a rooted emulator or a userdebug device; a locked retail device cannot record one.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
