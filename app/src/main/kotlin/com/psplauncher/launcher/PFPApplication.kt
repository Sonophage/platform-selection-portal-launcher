package com.psplauncher.launcher

import android.app.Application
import androidx.work.Configuration
import com.psplauncher.core.data.database.seeder.DatabaseInitializer
import com.psplauncher.core.data.database.seeder.StartupDataPrep
import com.psplauncher.feature.artwork.api.ArtworkImageCache
import com.psplauncher.feature.launcher.EmulatorAutoConfigService
import com.psplauncher.feature.launcher.EmulatorProfileRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PFPApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory
    @Inject lateinit var databaseInitializer: DatabaseInitializer
    @Inject lateinit var startupDataPrep: StartupDataPrep
    @Inject lateinit var emulatorProfileRepository: EmulatorProfileRepository
    @Inject lateinit var emulatorAutoConfigService: EmulatorAutoConfigService
    @Inject lateinit var artworkImageCache: ArtworkImageCache

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initLogging()

        artworkImageCache.installAsSingleton()
        initDatabase()
        initEmulators()
    }

    private fun initDatabase() {
        appScope.launch {
            runCatching {
                databaseInitializer.initialize()

                startupDataPrep.run(appVersionCode())
            }.onFailure { Timber.e(it, "Database initialization failed") }
        }
    }

    private fun appVersionCode(): Int = runCatching {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    private fun initEmulators() {
        appScope.launch {
            runCatching {
                emulatorProfileRepository.initialize()
                emulatorAutoConfigService.runOnStartup()
            }.onFailure { Timber.e(it, "Emulator initialization failed") }
        }
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) =
                    super.log(priority, tag, com.psplauncher.core.common.logging.LogRedaction.redact(message), t)
            })
        }

        Timber.plant(
            com.psplauncher.core.common.logging.PfpFileLoggingTree(
                java.io.File(filesDir, "logs")
            )
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.WARN
            )
            .build()
}
