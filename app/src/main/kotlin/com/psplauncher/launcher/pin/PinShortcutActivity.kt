package com.psplauncher.launcher.pin

import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.psplauncher.core.data.repository.CollectionRepository
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.launcher.PcShortcutImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PinShortcutActivity : ComponentActivity() {
    @Inject lateinit var gameRepository: GameRepository
    @Inject lateinit var collectionRepository: CollectionRepository
    @Inject lateinit var pcShortcutImporter: PcShortcutImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { handlePinRequest() }.onFailure { Timber.e(it, "Pin-shortcut handling failed") }
        finish()
    }

    private fun handlePinRequest() {
        val launcherApps = getSystemService(LauncherApps::class.java)
            ?: return Timber.w("Pin request: no LauncherApps service")
        val request = launcherApps.getPinItemRequest(intent)
            ?: return Timber.w("Pin request: intent carried no PinItemRequest")
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            return Timber.w("Pin request: unsupported type ${request.requestType}")
        }
        val shortcut = request.shortcutInfo
            ?: return Timber.w("Pin request: no shortcutInfo payload")

        if (!runCatching { request.accept() }.getOrDefault(false)) {
            Timber.w("Pin request not accepted for ${shortcut.id}")
            return
        }

        val hostPackage = shortcut.`package`
        val shortcutId = shortcut.id
        val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcutId

        Timber.i("Accepted pinned shortcut: \"$label\" from $hostPackage")

        runBlocking {
            withTimeoutOrNull(STORE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    runCatching { store(hostPackage, shortcutId, label) }
                        .onFailure { Timber.e(it, "Failed to store pinned shortcut $shortcutId") }
                }
            } ?: Timber.e("Storing pinned shortcut $shortcutId timed out")
        }
    }

    private suspend fun store(hostPackage: String, shortcutId: String, label: String) {
        if (pcShortcutImporter.isPcLauncher(hostPackage)) {
            val result = pcShortcutImporter.importPinnedShortcut(hostPackage, shortcutId, label)
            if (result.needsSetup) WindowsSetupNotifications.post(applicationContext, label)
            return
        }

        val hostLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(hostPackage, 0)).toString()
        }.getOrNull() ?: "Shortcuts"
        val gameId = gameRepository.getLauncherShortcut(hostPackage, shortcutId)?.id
            ?: gameRepository.upsert(
                Game(
                    title         = label,
                    platformId    = ANDROID_PLATFORM_ID,
                    packageName   = hostPackage,
                    isManualEntry = true,
                    contentType   = GameContentType.ANDROID_APP,
                    shortcutId    = shortcutId,
                ),
            )
        val collectionId = collectionRepository.getAll().firstOrNull { it.name == hostLabel }?.id
            ?: collectionRepository.create(hostLabel)
        collectionRepository.addGame(collectionId, gameId)
        Timber.i("Stored pinned shortcut \"$label\" into collection \"$hostLabel\"")
    }

    private companion object {
        const val STORE_TIMEOUT_MS = 5_000L
    }
}
