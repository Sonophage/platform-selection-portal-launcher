plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.security.MessageDigest
import java.util.Properties

// ── ScreenScraper developer-pair obfuscation (build-time) ─────────────────────
// Loads screenscraper.devId and the per-build-type screenscraper.devPasswordDebug /
// screenscraper.devPasswordRelease (each falling back to plain screenscraper.devPassword, or the
// legacy SS_DEV_* property and environment names for CI) plus the optional
// screenscraper.obfuscationSalt from local.properties and XOR-encodes each value with
// SHA-256(salt + propertyName) as the keystream. Encoded as buildConfigField byte arrays;
// credentials/DevPairDecoder reassembles them at runtime. DevPairDecoderTest mirrors this
// derivation exactly — change both together or the tests will catch the drift.
// Precedence: modern prop name → legacy prop name → environment. Absent credentials
// compile to empty arrays, which the decoder turns into null — the fallback simply disables.
private val ssProps: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * Writes the dev-password pair of fields onto [target], from [prop]/[envName] if either is set and
 * from the build-type-agnostic `screenscraper.devPassword` otherwise.
 *
 * The keystream is derived from the property NAME, so debug and release encode under different
 * keys even when they fall back to the same value — which is the point: two identical byte arrays
 * in two APKs would advertise that the fallback happened, and the fallback is the normal case.
 */
private fun ssDevPassword(
    target: com.android.build.api.dsl.VariantDimension,
    prop: String,
    envName: String,
) {
    val (share, mask) = ssEncoded(prop, envName, fallbackProp = "screenscraper.devPassword")
    target.buildConfigField("byte[]", "SS_DEV_PASSWORD_SHARE", share)
    target.buildConfigField("byte[]", "SS_DEV_PASSWORD_MASK",  mask)
}

private fun ssEncoded(prop: String, envName: String, fallbackProp: String? = null): Pair<String, String> {
    val value = ssProps.getProperty(prop)
        ?: ssProps.getProperty(envName)
        ?: System.getenv(envName)
        ?: fallbackProp?.let { ssProps.getProperty(it) ?: System.getenv("SS_DEV_PASSWORD") }
        ?: ""
    if (value.isEmpty()) return "new byte[]{}" to "new byte[]{}"
    val salt = (ssProps.getProperty("screenscraper.obfuscationSalt")
        ?: "playfieldportal-default-salt").toByteArray(Charsets.UTF_8)
    val key = MessageDigest.getInstance("SHA-256")
        .digest(salt + prop.toByteArray(Charsets.UTF_8))
    val plain = value.toByteArray(Charsets.UTF_8)
    val share = ByteArray(plain.size) { i ->
        (plain[i].toInt() xor key[i % key.size].toInt()).toByte()
    }
    val mask = ByteArray(plain.size) { i -> key[i % key.size] }
    fun bytesLiteral(b: ByteArray) = "new byte[]{" + b.joinToString(",") { it.toString() } + "}"
    return bytesLiteral(share) to bytesLiteral(mask)
}

android {
    namespace  = "com.psplauncher.feature.artwork"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "SS_SOFT_NAME", "\"PlayFieldPortal\"")

        // ScreenScraper developer pair (devid/devpassword), obfuscated into the APK.
        //
        // The WebAPI refuses every call without this pair, so it has to ride along. History:
        // these used to be plain buildConfigField strings from local.properties, which shipped a
        // live credential recoverable from the binary with `strings` (R8 does not touch string
        // literals). They were then moved to per-user entry under Settings ▸ Artwork, which left
        // every user unable to configure the provider at all. The current compromise ships the
        // pair XOR-encoded with a key derived from `screenscraper.obfuscationSalt` in
        // local.properties, split across four buildConfigField byte arrays; DevPairDecoder
        // reassembles them at runtime. There is no user-entered override — this is the only dev
        // pair the app has.
        //
        // This is obfuscation, not security: dex2jar + a decompiler recovers it. It defeats
        // `strings` scrapes and automated harvesters only. Rotation = change the values (and
        // ideally the salt) in local.properties and release; no server round-trip.
        val (ssDevIdShare, ssDevIdMask) = ssEncoded("screenscraper.devId", "SS_DEV_ID")
        buildConfigField("byte[]", "SS_DEV_ID_SHARE", ssDevIdShare)
        buildConfigField("byte[]", "SS_DEV_ID_MASK",  ssDevIdMask)
        // The password is per build type — see buildTypes below. Declared here too so every
        // variant has the field even if a build type is ever added without setting it: a missing
        // buildConfigField does not compile, and a variant that silently fell back to the wrong
        // password would be worse than one that does not build.
        ssDevPassword(this, "screenscraper.devPassword", "SS_DEV_PASSWORD")
    }
    buildTypes {
        // A hook for a per-build-type developer password, unused at present.
        //
        // It exists because ScreenScraper issues a password per registered APPLICATION, and a
        // project with a debug and a release package id registered separately would need two.
        // Whether PFP is such a project is NOT established: the account held two passwords, and
        // testing both against ssinfraInfos.php showed only one authenticates with devid
        // `badwolfvi` — the other is 403 "Erreur de login", so it belongs to something else.
        //
        // Both build types therefore fall back to the plain `screenscraper.devPassword` today.
        // The keys stay because the fallback is the whole mechanism: a machine (or CI, via
        // SS_DEV_PASSWORD) with one password builds both variants, and a second password can be
        // adopted later by setting one key, with no code change.
        getByName("debug") {
            ssDevPassword(this, "screenscraper.devPasswordDebug", "SS_DEV_PASSWORD_DEBUG")
        }
        getByName("release") {
            ssDevPassword(this, "screenscraper.devPasswordRelease", "SS_DEV_PASSWORD_RELEASE")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }

    testOptions {
        // Robolectric 4.16 emulates up to SDK 36. Library modules default targetSdk to
        // compileSdk (37), which Robolectric rejects outright, so pin the test target here.
        // This affects unit tests only — the published library is unchanged.
        targetSdk = 36
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.ktor)
    // Animated GIF/WebP decoding (motion wallpaper) — registered on the app-wide ImageLoader.
    implementation(libs.coil.gif)
    // api, not implementation: ArtworkImageCache exposes coil3.ImageLoader in its constructor,
    // so :app needs the type on its compile classpath for Hilt to construct it.
    api(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.workmanager.ktx)
    // Local ICON1 snap generation: trim + downscale full videos when SS has no normalized snap.
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
    testImplementation(libs.bundles.test.unit)
    // ArtworkImageCacheTest drives a real Coil ImageLoader, which needs an Android Context.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-ui"))
}
