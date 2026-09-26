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

@RunWith(RobolectricTestRunner::class)

@Config(sdk = [34])
class LaunchStampsTheShelfTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repoFor(entry: GameEntity?): Pair<InstalledAppRepository, GameDao> {
        val dao = mockk<GameDao>(relaxed = true)
        coEvery { dao.getAppEntry(any()) } returns entry
        return InstalledAppRepository(context, dao) to dao
    }

    private fun installLauncherIntentFor(pkg: String) {
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
        val pkg = "com.example.notinlibrary"
        installLauncherIntentFor(pkg)
        val (repo, dao) = repoFor(null)

        repo.launchApp(pkg)

        coVerify(timeout = 2_000, exactly = 0) { dao.markOpened(any(), any()) }
    }
}
