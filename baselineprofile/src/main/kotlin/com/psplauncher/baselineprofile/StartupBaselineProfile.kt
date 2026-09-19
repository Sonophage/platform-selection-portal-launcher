package com.psplauncher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the startup baseline profile for PSPLauncher.
 *
 * Run it deliberately, against a rooted emulator or a userdebug device:
 *
 * ```
 * ./gradlew :baselineprofile:generateBaselineProfile
 * ```
 *
 * The result lands in `app/src/main/baseline-prof.txt` and must be committed. AGP compiles it into
 * `assets/dexopt/baseline.prof` on the release build, and ART pre-compiles those methods at install
 * time rather than interpreting them on the first launch.
 *
 * This is worth more to a launcher than to an ordinary app. PSPLauncher is the home screen, so its
 * cold start is paid every time the user presses Home, not once per session.
 *
 * ## What this profile does and does not cover
 *
 * [startup] covers process start through the first frame of the XMB. That is the whole of what a
 * Home press pays for, and it is deliberately all this records.
 *
 * It does NOT drive into a category, open Settings or scroll a game list. Those are worth profiling
 * too, but each needs the launcher to be in a known state, and a profile recorded against an empty
 * library would teach ART about the empty path. Add them as separate tests once there is a fixture
 * that guarantees the state, rather than recording whatever happens to be on the device.
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE_NAME) {
        // A cold start: the process is killed and the launcher is started fresh, which is what a
        // Home press does after the system has reclaimed it.
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        // The release applicationId. The debug build carries a `.debug` suffix and is not minified,
        // so a profile recorded against it would name classes that do not exist in what ships.
        const val PACKAGE_NAME = "com.psplauncher.launcher"
    }
}
