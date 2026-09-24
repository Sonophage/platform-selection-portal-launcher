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

    // The launch disc's opening cue, and the GameBoot cue that takes over from it partway
    // through — one shared one-shot player, which is what makes the second one replace the first
    // rather than sound over it.
    @Inject
    lateinit var uiMediaAudioPlayer: com.psplauncher.core.ui.media.UiMediaAudioPlayer

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
                    com.psplauncher.core.ui.sound.LocalLaunchDiscCue provides {
                        // pathFor is one stat call against the ui-media directory, once per
                        // launch; not worth a coroutine hop that would let the disc's first
                        // frame beat its own sound onto the screen. No path means no sound: the
                        // slot has no bundled sample, so the ceremony opens in silence as before.
                        uiMediaStore.pathFor(
                            com.psplauncher.core.domain.model.UiMediaSlot.LAUNCH_DISC_AUDIO,
                        )?.let { track ->
                            // Clipped to the hand-off, not to the slot's import ceiling. The cue
                            // is scenery for the disc; on a launch with no GameBoot sound to take
                            // over from it, an 8-second file would otherwise play on underneath
                            // the app that just opened.
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
    }

    /**
     * Starts the music if the user wants it and has given it something to play.
     *
     * Collected for the whole time the launcher is resumed rather than read once, so toggling the
     * switch or assigning a track in Settings takes effect where you did it instead of on the
     * next cold start.
     *
     * CALLED FROM onCreate, not onResume. repeatOnLifecycle suspends until the lifecycle is
     * DESTROYED and restarts its block on every RESUMED — so one call covers every resume for the
     * life of the activity, and calling it from onResume started a SECOND collector on the second
     * resume, a third on the third, each collecting the same flow and racing the others to start
     * the music. The old comment here said the scope was torn down at onStop; lifecycleScope is
     * cancelled at onDestroy.
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
        if (enterOpensAppDrawer(event)) return true
        // Let the gamepad handler process it first; fall back to normal dispatch
        if (gamepadInputHandler.onKeyEvent(event)) return true
        if (openSearchOnTypedCharacter(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * Enter opens the App Drawer, on the crossbar and nowhere else.
     *
     * BEFORE the gamepad handler, unlike type-to-search, and that order is the point: ENTER is
     * bound to SELECT, so letting the handler see it first would confirm whatever the cursor is
     * on before this ever ran. Claiming it here is what "unbind it, on this screen" means when
     * the binding table has one row per keycode and no idea which screen is showing.
     *
     * Everywhere else the handler gets it and Enter is confirm exactly as before. The ViewModel
     * owns the "is this the crossbar" question — see XMBViewModel.enterOpensAppDrawer.
     */
    private fun enterOpensAppDrawer(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.keyCode != KeyEvent.KEYCODE_ENTER) return false
        if (!xmbViewModel.enterOpensAppDrawer()) return false
        xmbViewModel.onOpenAppDrawer()
        return true
    }

    /**
     * Start typing anywhere on the XMB and you are searching.
     *
     * AFTER the gamepad handler, never before: the bound keys are actions first. Escape backs out
     * and Space opens options, and a keyboard user pressing them is pressing a button, not writing
     * the letter " ". Anything the handler did not claim and that produces a character is text.
     *
     * The guards are all about not stealing a letter someone meant to type. [unicodeChar] is 0 for
     * a key with no character — the arrows, the function row, a bare modifier — and the control
     * range covers Enter, Tab and Backspace, which arrive with a character but are not typing. Ctrl
     * and Alt mean a shortcut is being attempted, whether or not this app has one. Shift alone does
     * not, because a capital letter is still a letter.
     *
     * The ViewModel decides whether anything else owns the keyboard right now.
     */
    private fun openSearchOnTypedCharacter(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val typed = event.unicodeChar
        if (typed == 0) return false
        val ch = typed.toChar()
        if (ch.isISOControl()) return false
        if (!xmbViewModel.typeToSearchAllowed()) return false
        xmbViewModel.openSearchTyping(ch.toString())
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInputHandler.onMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }
}
