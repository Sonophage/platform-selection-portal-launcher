import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Consumes baseline-prof.txt from :baselineprofile and compiles it into the release APK.
    alias(libs.plugins.baselineprofile)
}

baselineProfile {
    // Never drive hardware as a side effect of an ordinary build; generating is an explicit run.
    automaticGenerationDuringBuild = false
}

// Load release signing config from keystore.properties (gitignored).
// Absent on machines without the signing key — release build then stays unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.psplauncher.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.psplauncher.launcher"
        minSdk = 29           // Android 10 — Winlator minimum
        targetSdk = 35
        versionCode = 14
        versionName = "1.7.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v3 is what makes key rotation possible later; without it this key is
                // permanent. v1 is dead weight above minSdk 24 and we are at 29.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("boolean", "ENABLE_PERF_OVERLAY", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_PERF_OVERLAY", "false")
            // Sign with the release key only when keystore.properties is present.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Installs the baseline profile on first run. Present transitively via Compose already;
    // named explicitly so a dependency change cannot silently drop it from the release APK.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.bundles.lifecycle)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)
    // Coil 3 service-loads its network fetcher; without this artifact remote URLs never load.
    implementation(libs.coil.network.okhttp)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.workmanager.ktx)
    implementation(libs.datastore.preferences)

    // All feature & core modules
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-ui"))
    implementation(project(":feature:feature-xmb"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-launcher"))
    implementation(project(":feature:feature-artwork"))
    implementation(project(":feature:feature-themes"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":feature:feature-appbar"))
    implementation(project(":feature:feature-backup"))

    debugImplementation(libs.compose.ui.tooling)
}

// ── Release artifact collection ──────────────────────────────────────────────
// Mirror every release APK into <root>/dist so all shippable builds land in one
// predictable, gitignored place. assembleRelease finalizes into its
// copy, so the APK is always in dist after a release build (or `./gradlew dist`).
val distDir = rootProject.layout.projectDirectory.dir("dist")
val appVersion = android.defaultConfig.versionName ?: "0"
val copyReleaseApk = tasks.register<Copy>("copyReleaseToDist") {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    // Clean, versioned name in dist (e.g. PSPLauncher-1.3.0.apk).
    rename { "PSPLauncher-$appVersion.apk" }
    into(distDir)
    // dist is a shared, versioned drop folder — always refresh so the current build is
    // guaranteed present even when the APK itself is up-to-date.
    outputs.upToDateWhen { false }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyReleaseApk)
}

// Same idea for debug builds: mirror the debug APK into <root>/debug so it's always found
// in one predictable, gitignored place.
val debugDir = rootProject.layout.projectDirectory.dir("debug")
val copyDebugApk = tasks.register<Copy>("copyDebugToDebugDir") {
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    rename { "PSPLauncher-$appVersion-debug.apk" }
    into(debugDir)
    outputs.upToDateWhen { false }
}
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}
