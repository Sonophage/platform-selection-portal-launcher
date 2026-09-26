package com.psplauncher.launcher

import com.psplauncher.core.domain.model.GamepadAction
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import com.psplauncher.core.ui.theme.PFPTheme
import com.psplauncher.feature.library.scanner.LibraryRescanCoordinator
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber
import com.psplauncher.feature.xmb.gamepad.GamepadInputHandler
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel
import com.psplauncher.launcher.receiver.InstallShortcutReceiver
import com.psplauncher.launcher.receiver.MediaMountReceiver
import com.psplauncher.launcher.receiver.UsbDisconnectReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var gamepadInputHandler: GamepadInputHandler

    @Inject
    lateinit var menuSoundPlayer: com.psplauncher.core.ui.sound.MenuSoundPlayer

    @Inject
    lateinit var menuMusicPlayer: com.psplauncher.core.ui.media.MenuMusicPlayer

    @Inject
    lateinit var menuMusicPreferences: com.psplauncher.core.data.media.MenuMusicPreferences

    @Inject
    lateinit var uiMediaAudioPlayer: com.psplauncher.core.ui.media.UiMediaAudioPlayer

    @Inject
    lateinit var libraryRescanCoordinator: LibraryRescanCoordinator

    @Inject
    lateinit var uiMediaStore: com.psplauncher.core.data.repository.UiMediaStore

    @Inject
    lateinit var launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher

    private val xmbViewModel: XMBViewModel by viewModels()

    private var wasStopped = false

    private val installShortcutReceiver = InstallShortcutReceiver()

    private val mediaMountReceiver = MediaMountReceiver()

    private val usbDisconnectReceiver = UsbDisconnectReceiver()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            xmbViewModel.onStartupPermissionsSettled()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        hideSystemBars()
        requestNotificationPermissionIfNeeded()
        startMenuMusicIfWanted()
        ContextCompat.registerReceiver(
            this,
            installShortcutReceiver,
            IntentFilter(InstallShortcutReceiver.ACTION_INSTALL_SHORTCUT),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            mediaMountReceiver,

            IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            usbDisconnectReceiver,
            IntentFilter(UsbDisconnectReceiver.ACTION_USB_STATE),

            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

        lifecycleScope.launch {
            runCatching { uiMediaStore.pruneOrphans() }
                .onFailure { Timber.w(it, "Startup UI-media prune failed") }
        }

        setContent {
            PFPTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.psplauncher.core.ui.sound.LocalMenuSounds provides { sound -> menuSoundPlayer.play(sound) },
                    com.psplauncher.core.ui.sound.LocalLaunchDiscCue provides {
                        uiMediaStore.pathFor(
                            com.psplauncher.core.domain.model.UiMediaSlot.LAUNCH_DISC_AUDIO,
                        )?.let { track ->

                            uiMediaAudioPlayer.play(
                                uri = track,
                                clipEndMs =
                                    com.psplauncher.core.ui.components.DiscCeremony.HandOffMs.toLong(),
                                label = "launch-disc",
                            )
                        }
                    },
                ) {
                ProvideControllerPrompts {
                    AppXmbHost()
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()

        launchDispatcher.onHostResumed()

        if (wasStopped) {
            wasStopped = false
            xmbViewModel.onHostResumed()
        }

        lifecycleScope.launch {
            runCatching { libraryRescanCoordinator.onResume() }
                .onFailure { Timber.e(it, "Resume-triggered library rescan failed") }
        }
    }

    private fun startMenuMusicIfWanted() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                kotlinx.coroutines.flow.combine(
                    menuMusicPreferences.enabledFlow,
                    uiMediaStore.stamp,
                ) { enabled, _ -> enabled }
                    .collect { enabled ->
                        val track = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            uiMediaStore.pathFor(com.psplauncher.core.domain.model.UiMediaSlot.MENU_MUSIC)
                        }
                        menuMusicPlayer.setWanted(enabled, track)
                    }
            }
        }
    }

    override fun onStop() {
        menuMusicPlayer.setWanted(wanted = false, track = null)

        launchDispatcher.onHostStopped()
        wasStopped = true
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(installShortcutReceiver) }
        runCatching { unregisterReceiver(mediaMountReceiver) }
        runCatching { unregisterReceiver(usbDisconnectReceiver) }
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            xmbViewModel.onStartupPermissionsSettled()
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            xmbViewModel.onStartupPermissionsSettled()
            return
        }
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (minimalKeyboardKey(event)) return true

        if (gamepadInputHandler.onKeyEvent(event)) return true
        if (openSearchOnTypedCharacter(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun minimalKeyboardKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> enterOpensAppDrawer(event)
            KeyEvent.KEYCODE_SPACE -> {
                if (!xmbViewModel.typeToSearchAllowed()) return false
                xmbViewModel.onClaimedKey(GamepadAction.OPEN_CONTEXT_MENU)
                true
            }
            else -> false
        }
    }

    private fun enterOpensAppDrawer(event: KeyEvent): Boolean {
        if (!xmbViewModel.enterOpensAppDrawer()) return false
        xmbViewModel.onOpenAppDrawer()
        return true
    }

    private fun openSearchOnTypedCharacter(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val typed = event.unicodeChar
        if (typed == 0) return false
        val ch = typed.toChar()
        if (ch.isISOControl()) return false

        return xmbViewModel.onTypedCharacter(ch.toString())
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInputHandler.onMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }
}
