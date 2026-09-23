package com.psplauncher.launcher

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

    // Provided into the composition (LocalMenuSounds) so shared chrome with no ViewModel of its
    // own -- the settings scaffold, which is one navigation dispatch for every settings screen --
    // can make the same noises the XMB does.
    @Inject
    lateinit var menuSoundPlayer: com.psplauncher.core.ui.sound.MenuSoundPlayer

    // Menu music, and the two things that decide whether it plays: the user's switch and whether
    // the launcher is the thing on screen. Driven from here rather than from a ViewModel because
    // the second of those is an Activity fact — a ViewModel survives the launcher being covered
    // by a game, which is exactly when the music must stop.
    @Inject
    lateinit var menuMusicPlayer: com.psplauncher.core.ui.media.MenuMusicPlayer

    @Inject
    lateinit var menuMusicPreferences: com.psplauncher.core.data.media.MenuMusicPreferences

    @Inject
    lateinit var libraryRescanCoordinator: LibraryRescanCoordinator

    // Owns the user's ui-media assignments; a cold start prunes anything left by slots the
    // current build no longer has (removed sound slots, crashed-import staging files).
    @Inject
    lateinit var uiMediaStore: com.psplauncher.core.data.repository.UiMediaStore

    // B1 launch verification: the home-launcher handshake. A dispatched game launch is only
    // "real" if another activity covers this launcher (onStop) and the user comes back after a
    // real session (onResume). Nothing else in the app reports lifecycle to the dispatcher.
    @Inject
    lateinit var launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher

    // Same activity-scoped instance the shell's hiltViewModel() resolves — used to report when
    // the notification-permission dialog is out of the way so the boot sequence can start.
    private val xmbViewModel: XMBViewModel by viewModels()

    // True once the launcher has actually been stopped, so onResume can tell "back from a game"
    // apart from the cold start's own first onResume.
    private var wasStopped = false

    // Runtime-registered so it actually fires on Android 8+ (manifest receivers are blocked for
    // this implicit broadcast). Lives for the activity's lifetime.
    private val installShortcutReceiver = InstallShortcutReceiver()

    // Also runtime-registered: ACTION_MEDIA_MOUNTED is an implicit broadcast, so a manifest entry
    // would never fire on Android 8+. Lives for the activity's lifetime.
    private val mediaMountReceiver = MediaMountReceiver()

    // Covers the USB-cable case the mount receiver can't: an MTP transfer never unmounts storage,
    // so unplugging fires no MEDIA_MOUNTED. USB_STATE's disconnect edge is the actual unplug signal.
    private val usbDisconnectReceiver = UsbDisconnectReceiver()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Best-effort grant; either way the dialog is resolved and startup can continue.
            xmbViewModel.onStartupPermissionsSettled()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        hideSystemBars()
        requestNotificationPermissionIfNeeded()
        ContextCompat.registerReceiver(
            this,
            installShortcutReceiver,
            IntentFilter(InstallShortcutReceiver.ACTION_INSTALL_SHORTCUT),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            mediaMountReceiver,
            // The "file" data scheme is required — ACTION_MEDIA_MOUNTED carries a file:// URI for
            // the mounted volume, and a filter without a scheme never matches it.
            IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            usbDisconnectReceiver,
            IntentFilter(UsbDisconnectReceiver.ACTION_USB_STATE),
            // NOT_EXPORTED: USB_STATE is a protected system broadcast, so only the OS can send it —
            // no need to accept it from other apps, and this is the flag Android recommends for a
            // receiver registered purely for system broadcasts.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                //Left blank so that it can be ignored, preventing users from exiting the launcher.
                //Back is already handled by the gamepad input handler.
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

        // Orphan sweep for the user's ui-media directory (removed slots, stale staging files).
        // Off the main thread; cheap (one directory listing) when there is nothing to remove.
        lifecycleScope.launch {
            runCatching { uiMediaStore.pruneOrphans() }
                .onFailure { Timber.w(it, "Startup UI-media prune failed") }
        }

        setContent {
            PFPTheme {
                // Controller prompts are ambient: every footer resolves its glyphs from the
                // live bindings supplied here, so none of them can contradict the pad.
                androidx.compose.runtime.CompositionLocalProvider(
                    com.psplauncher.core.ui.sound.LocalMenuSounds provides { sound -> menuSoundPlayer.play(sound) },
                ) {
                ProvideControllerPrompts {
                    // AppXmbHost is defined per build variant: the debug source set wraps the shell so
                    // long-pressing Settings opens DebugMenuScreen; the release source set calls
                    // XMBShellContainer directly, keeping debug code out of the APK.
                    AppXmbHost()
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        // B1: PFP is foreground again — classify any pending launch hand-off (success if the
        // emulator held the foreground for a real session, never-foregrounded otherwise).
        launchDispatcher.onHostResumed()
        // Only a RETURN counts for "Show Boot Sequence on Resume" — the very first onResume after
        // onCreate is the cold start, which already plays its own boot.
        if (wasStopped) {
            wasStopped = false
            xmbViewModel.onHostResumed()
        }
        // Same "back from a game" moment is the weak rescan signal: it catches ROMs downloaded or
        // deleted while PFP was backgrounded. The coordinator throttles this internally (5 min), so
        // calling it on every resume costs nothing when it fires in quick succession.
        lifecycleScope.launch {
            runCatching { libraryRescanCoordinator.onResume() }
                .onFailure { Timber.e(it, "Resume-triggered library rescan failed") }
        }
        startMenuMusicIfWanted()
    }

    /**
     * Starts the music if the user wants it and has given it something to play.
     *
     * Collected for the whole time the launcher is resumed rather than read once, so toggling the
     * switch or assigning a track in Settings takes effect where you did it instead of on the
     * next cold start. The collection is cancelled by [onStop] tearing the scope down with the
     * STARTED state.
     */
    private fun startMenuMusicIfWanted() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                // The switch, and the store's change stamp. pathFor is a synchronous read, so
                // the stamp is what turns "the user just assigned a different track" into an
                // emission — the same signal every other ui-media consumer re-reads on.
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
        // Whatever covered the launcher gets the speaker. The repeatOnLifecycle collection above
        // ends with RESUMED, but the player is told explicitly rather than left to a cancellation
        // — a cancelled collector stops OBSERVING, it does not stop the music.
        menuMusicPlayer.setWanted(wanted = false, track = null)
        // B1: another activity covered the launcher — the dispatched emulator came to front.
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

    // Background-task notifications need the POST_NOTIFICATIONS runtime grant on API 33+.
    // Every early-return path reports the permission flow settled so the boot sequence
    // (which holds until then) can start.
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

    // ── Controller input forwarding ───────────────────────────────────────────

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let the gamepad handler process it first; fall back to normal dispatch
        if (gamepadInputHandler.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInputHandler.onMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }
}
