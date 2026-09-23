package com.psplauncher.feature.appbar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.entity.GameEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * That launching an app actually writes the stamp the Last Played shelf sorts on.
 *
 * The DAO tests prove the query reorders; this proves anything ever calls it. That distinction is
 * not academic — before this the column was simply never written for an app, so every piece of
 * machinery around the shelf worked perfectly on a number that never changed. Deleting the one
 * line that stamps compiles clean and breaks nothing else, which is precisely why it needs a test
 * of its own rather than trusting the call site to stay put.
 *
 * [InstalledAppRepository.launchApp] is the single funnel: the drawer, the XMB row, App Detail and
 * the storefront drawer all arrive here.
 */
@RunWith(RobolectricTestRunner::class)
// sdk pinned like the module's other Robolectric tests: compileSdk is 37 and
// Robolectric emulates to 36, which it refuses rather than approximates.
@Config(sdk = [34])
class LaunchStampsTheShelfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repoFor(entry: GameEntity?): Pair<InstalledAppRepository, GameDao> {
        val dao = mockk<GameDao>(relaxed = true)
        coEvery { dao.getAppEntry(any()) } returns entry
        return InstalledAppRepository(context, dao) to dao
    }

    private fun installLauncherIntentFor(pkg: String) {
        // Robolectric returns null from getLaunchIntentForPackage for a package it knows nothing
        // about, and launchApp returns early on null — so without this the test would pass while
        // proving only that nothing happens.
        Shadows.shadowOf(context.packageManager).addActivityIfNotPresent(
            android.content.ComponentName(pkg, "$pkg.MainActivity")
        )
        Shadows.shadowOf(context.packageManager).addIntentFilterForActivity(
            android.content.ComponentName(pkg, "$pkg.MainActivity"),
            android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
        )
    }

    @Test
    fun `launching a library app stamps it onto the shelf`() = runTest {
        val pkg = "app.gamenative"
        installLauncherIntentFor(pkg)
        val row = mockk<GameEntity>(relaxed = true)
        coEvery { row.id } returns 42L
        val (repo, dao) = repoFor(row)

        repo.launchApp(pkg)

        coVerify(timeout = 2_000) { dao.markOpened(42L, any()) }
    }

    @Test
    fun `an app that is not in the library is not invented onto the shelf`() = runTest {
        // Opening something from All Apps that was never added is not a shelf entry, and writing
        // one would be a different feature quietly arriving through this door.
        val pkg = "com.example.notinlibrary"
        installLauncherIntentFor(pkg)
        val (repo, dao) = repoFor(null)

        repo.launchApp(pkg)

        coVerify(timeout = 2_000, exactly = 0) { dao.markOpened(any(), any()) }
    }
}
