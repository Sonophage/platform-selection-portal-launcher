package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.ControllerHintPolicy
import com.psplauncher.core.domain.model.TextLegibilityStyle
import com.psplauncher.core.domain.model.IconLegibilityStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.components.HsvColorPickerDialog
import com.psplauncher.core.ui.components.hexOf
import com.psplauncher.core.ui.components.hsvToArgbLong
import com.psplauncher.core.ui.motion.MotionWallpaperBackground
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.composite
import com.psplauncher.core.ui.theme.solveScrimColor
import com.psplauncher.feature.settings.viewmodel.DisplaySettingsUiState
import com.psplauncher.feature.settings.viewmodel.DisplaySettingsViewModel

enum class DisplaySection { APPEARANCE, LAYOUT, BOOT, INPUT, PERFORMANCE }

@Composable
fun DisplaySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    section: DisplaySection? = null,
    onOpenXmbLayoutAdjust: () -> Unit = {},
    onOpenCustomIcons: () -> Unit = {},
    onPreviewBootSequence: () -> Unit = {},
    onPreviewGameBoot: () -> Unit = {},
    viewModel: DisplaySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var fontPickerOpen by remember { mutableStateOf(false) }
    var pickerHue by remember { mutableFloatStateOf(0f) }
    var pickerSat by remember { mutableFloatStateOf(0f) }
    var pickerVal by remember { mutableFloatStateOf(1f) }
    var pickerChannel by remember { mutableIntStateOf(0) }

    var pspConfirmFocus by remember { mutableStateOf<Int?>(null) }

    var focusedSlot by remember { mutableStateOf<UiMediaSlot?>(null) }

    var focusTargetSlot by remember { mutableStateOf<UiMediaSlot?>(null) }
    var focusRequestToken by remember { mutableIntStateOf(0) }
    var importWasActive by remember { mutableStateOf(false) }

    fun requestMediaFocus(slot: UiMediaSlot) {
        focusTargetSlot = slot
        focusRequestToken++
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onWallpaperPicked(it) } }

    val uiMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onUiMediaPicked(uri)
        } else {
            focusTargetSlot?.let(::requestMediaFocus)
        }
    }

    fun pickUiMedia(slot: UiMediaSlot) {
        requestMediaFocus(slot)
        viewModel.onUiMediaPickerLaunchedFor(slot)
        uiMediaPicker.launch(viewModel.uiMediaPickerMime(slot))
    }

    LaunchedEffect(state.wallpaperImporting) {
        if (state.wallpaperImporting) {
            importWasActive = true
        } else if (importWasActive) {
            importWasActive = false
            focusTargetSlot?.let(::requestMediaFocus)
        }
    }

    fun launchWallpaperPicker() {
        focusTargetSlot = null

        wallpaperPicker.launch(
            arrayOf(
                "image/png", "image/jpeg", "image/webp",
                "video/mp4", "video/webm", "image/gif",
            )
        )
    }

    SettingsPageScaffold(

        subtitle = when (section) {
            DisplaySection.APPEARANCE  -> "Wallpaper & Text"
            DisplaySection.LAYOUT      -> "Layout"
            DisplaySection.BOOT        -> "Boot"
            DisplaySection.INPUT       -> "Touch"
            DisplaySection.PERFORMANCE -> "Performance"
            null                       -> "Display"
        },
        onBack   = onBack,
        modifier = modifier,

        helperFooterItems = focusedSlot?.let { slot ->
            MediaRowShortcuts.promptsFor(state.xyLayout, isAssigned = slot.isAssignedIn(state))
        } ?: emptyList(),
        onInterceptAction = { action ->

            pspConfirmFocus?.let { focused ->
                when (action) {
                    GamepadAction.NAVIGATE_LEFT, GamepadAction.NAVIGATE_RIGHT ->
                        pspConfirmFocus = if (focused == PSP_CONFIRM_CANCEL) PSP_CONFIRM_APPLY else PSP_CONFIRM_CANCEL
                    GamepadAction.SELECT -> {
                        if (focused == PSP_CONFIRM_APPLY) viewModel.applyPspLayout()
                        pspConfirmFocus = null
                    }
                    GamepadAction.BACK -> pspConfirmFocus = null
                    else -> Unit
                }
                return@SettingsPageScaffold true
            }

            if (state.wallpaperPreviewVisible) {
                if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                    viewModel.hideWallpaperPreview()
                }
                return@SettingsPageScaffold true
            }

            val slot = focusedSlot ?: return@SettingsPageScaffold false
            when {
                MediaRowShortcuts.isNorthFace(action, state.xyLayout) && slot.isAssignedIn(state) -> {
                    requestMediaFocus(slot)
                    viewModel.clearUiMedia(slot)
                    true
                }
                MediaRowShortcuts.isWestFace(action, state.xyLayout) -> {
                    when (slot) {
                        UiMediaSlot.BOOT_VIDEO -> onPreviewBootSequence()
                        UiMediaSlot.GAMEBOOT_VIDEO -> onPreviewGameBoot()
                        else -> return@SettingsPageScaffold false
                    }
                    true
                }
                else -> false
            }
        },
    ) {
        val focusRegistry = LocalSettingsFocusRegistry.current
        LaunchedEffect(focusRequestToken) {
            if (focusRequestToken > 0) {
                withFrameNanos { }
                withFrameNanos { }
                focusTargetSlot?.let { slot ->
                    runCatching { focusRegistry["display_${slot.key}"]?.requestFocus() }
                }
            }
        }

        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            if (section == null || section == DisplaySection.APPEARANCE) {
                SettingsGroup("Appearance")

                if (state.wallpaperImporting) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                } else {
                    SettingsRow(
                        label    = "Choose Wallpaper",
                        sublabel = if (state.motionWallpaperPath != null) "Motion wallpaper set — a looping video replaces the wave"
                                   else if (state.customWallpaperPath != null) "Custom wallpaper set — replaces the wave"
                                   else "Pick an image or a short video (PNG, JPG, WEBP, MP4, WEBM, GIF) — replaces the wave",
                        onClick  = ::launchWallpaperPicker,
                    )

                    SettingsRow(
                        label    = "Preview Wallpaper",
                        sublabel = "See the selected wallpaper full-screen",
                        onClick  = { viewModel.showWallpaperPreview() },
                    )

                    if (state.customWallpaperPath != null) {
                        SettingsRow(
                            label    = "Reset Wallpaper",
                            sublabel = "Remove custom wallpaper and restore the default background",
                            onClick  = { viewModel.clearWallpaper() },
                        )
                    }
                }

                if (state.customWallpaperPath != null) {
                    SettingsToggleRow(
                        label    = "Wave Over Wallpaper",
                        sublabel = "Keep the wave, drawn on top of your wallpaper",
                        checked  = state.waveOverWallpaper,
                        onToggle = { viewModel.setWaveOverWallpaper(it) },
                    )
                }

                if (state.customWallpaperPath == null || state.waveOverWallpaper) {
                    SettingsPickerRow(
                        label    = "Wave Style",
                        options  = viewModel.waveStyleOptions.map { SettingsPickerOption(it.second) },
                        selectedIndex = viewModel.waveStyleOptions.indexOfFirst { it.first == state.waveStyle },
                        onPick   = { viewModel.setWaveStyle(viewModel.waveStyleOptions[it].first) },
                    )
                } else if (state.motionWallpaperPath != null) {
                    SettingsPickerRow(
                        label    = "Background Motion",
                        options  = viewModel.waveStyleOptions.map { SettingsPickerOption(it.second) },
                        selectedIndex = viewModel.waveStyleOptions.indexOfFirst { it.first == state.waveStyle },
                        onPick   = { viewModel.setWaveStyle(viewModel.waveStyleOptions[it].first) },
                    )
                }

                SettingsPickerRow(
                    label    = "Icon Legibility",
                    sublabel = "How XMB icons separate from the background",
                    options  = IconLegibilityStyle.entries.map { SettingsPickerOption(it.label) },
                    selectedIndex = IconLegibilityStyle.entries.indexOf(state.iconLegibility),
                    onPick   = { viewModel.setIconLegibility(IconLegibilityStyle.entries[it]) },
                )

                SettingsToggleRow(
                    label    = "Apps On The Recent Shelf",

                    sublabel = "Show recently used apps beside games, music, books and video. " +
                        "Needs usage access; without it no app has a last-used time and none appear",
                    checked  = state.recentsIncludeApps,
                    onToggle = { viewModel.setRecentsIncludeApps(it) },
                )

                SettingsToggleRow(
                    label    = "Card Art Grid",
                    sublabel = "Show a console card as four covers from inside it, instead of its console icon",
                    checked  = state.cardArtGrid,
                    onToggle = { viewModel.setCardArtGrid(it) },
                )

                SettingsToggleRow(
                    label    = "Fade By Distance",
                    sublabel = "Fade rows and icons further the further they sit from the cursor — off, every unselected one dims the same",
                    checked  = state.fadeByDistance,
                    onToggle = { viewModel.setFadeByDistance(it) },
                )

                SettingsToggleRow(
                    label    = "Text Shadow",
                    sublabel = "Drop shadow behind row helper text — keeps it readable over bright wallpaper regions",
                    checked  = state.textShadow,
                    onToggle = { viewModel.setTextShadow(it) },
                )

                SettingsValueRow(
                    label    = "Font Colour",
                    sublabel = "Colour for labels and body text across the interface",
                    value    = state.textColorArgb
                        ?.let { hexOf(Color(it and 0xFFFFFFFFL)) }
                        ?: "Theme Default",
                    onClick  = {
                        val seed = state.textColorArgb ?: 0xFFFFFFFFL
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV((seed and 0xFFFFFF).toInt(), hsv)
                        pickerHue = hsv[0]; pickerSat = hsv[1]; pickerVal = hsv[2]
                        pickerChannel = 0
                        fontPickerOpen = true
                    },
                )

                if (state.textColorArgb != null) {
                    SettingsRow(
                        label    = "Reset Font Colour",
                        sublabel = "Go back to the colour the current theme supplies",
                        onClick  = { viewModel.setTextColor(null) },
                    )

                    SettingsToggleRow(
                        label    = "Use My Exact Colour",
                        sublabel = "Render the colour exactly as picked. Legibility protection still " +
                            "applies — text may get a shadow or a plate behind it",
                        checked  = state.textColorExact,
                        onToggle = { viewModel.setTextColorExact(it) },
                    )
                }

                SettingsPickerRow(
                    label    = "Text Legibility",
                    sublabel = "How text separates from what is behind it",
                    options  = TextLegibilityStyle.entries.map { SettingsPickerOption(it.label) },
                    selectedIndex = TextLegibilityStyle.entries.indexOf(state.textLegibility),
                    onPick   = { viewModel.setTextLegibility(TextLegibilityStyle.entries[it]) },
                )
            }
            if (section == null || section == DisplaySection.LAYOUT) {
                SettingsGroup("XMB Layout")
                Text(
                    text     = "Position the XMB live for this screen — scale it, and shift the crossbar " +
                        "up/down and left/right — over the real interface. Each screen size (handheld, " +
                        "foldable, tablet) keeps its own tuning.",
                    color    = SettingsSubtext,
                    fontSize = 12.sp,

                    style    = androidx.compose.ui.text.TextStyle(shadow = SettingsTextShadow),
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
                )

                SettingsRow(
                    label    = "Adjust XMB Layout",
                    sublabel = "Live editor — scale + reposition the crossbar with the D-pad or sliders",
                    onClick  = onOpenXmbLayoutAdjust,
                )

                SettingsRow(
                    label    = "Biblically Accurate PSP XMB",
                    sublabel = if (state.pspLayoutApplied) {
                        "Applied to this screen. Change the layout with Adjust XMB Layout to use it again"
                    } else {
                        "Apply the PSP's own proportions to this screen"
                    },
                    enabled  = !state.pspLayoutApplied,
                    onClick  = { pspConfirmFocus = PSP_CONFIRM_CANCEL },
                )

                SettingsRow(
                    label    = "Customize XMB Icons",
                    sublabel = "Replace any icon with your own image or GIF — live over the XMB",
                    onClick  = onOpenCustomIcons,
                )
            }
            if (section == null || section == DisplaySection.BOOT) {
                SettingsGroup("Boot Sequence")

                SettingsToggleRow(
                    label    = "Show Boot Sequence",
                    sublabel = "PSP-style boot animation on every launch",
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    checked  = state.showBootSequence,
                    onToggle = { viewModel.setShowBootSequence(it) },
                )

                SettingsToggleRow(
                    label    = "Show Boot Sequence on Resume",
                    sublabel = "Also play when returning from a game",
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    checked  = state.showBootOnResume,
                    onToggle = { viewModel.setShowBootOnResume(it) },
                )

                MediaAssignmentRow(
                    label    = "Boot Video",
                    focusKey = "display_${UiMediaSlot.BOOT_VIDEO.key}",
                    sublabel = "Play your own video instead of the PSP logo animation " +
                        "(MP4 or WebM, up to 10 seconds)",
                    value    = state.bootVideoLabel,
                    isAssigned = state.bootVideoAssigned,
                    onPick   = { pickUiMedia(UiMediaSlot.BOOT_VIDEO) },
                    onPreview = onPreviewBootSequence,
                    onUseDefault = { viewModel.clearUiMedia(UiMediaSlot.BOOT_VIDEO) },
                    onFocusChanged = { focusedSlot = if (it) UiMediaSlot.BOOT_VIDEO else null },
                )

                SettingsGroup("Launch Disc  ·  two switches, one animation")

                SettingsToggleRow(
                    label    = "Launch Disc  (everything but games)",
                    sublabel = "The cover turns into a spinning disc between confirming something " +
                        "and it opening — films, books, music and apps.  Games have their own " +
                        "switch, GameBoot, directly below: it plays the SAME disc, and turning " +
                        "one off never affects the other.  Off opens these straight away.",
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    checked  = state.launchDiscEnabled,
                    onToggle = { viewModel.setLaunchDiscEnabled(it) },
                )

                SettingsGroup("GameBoot")

                SettingsToggleRow(
                    label    = "GameBoot  (games only)",
                    sublabel = "The same disc as Launch Disc above, for games — between " +
                        "confirming one and the emulator opening.  Two things only this switch " +
                        "has: a sound as the disc leaves, and the option to replace the whole " +
                        "thing with your own clip below.  Off is a silent launch, and it is why " +
                        "a game can open with no animation while a film still gets one.",
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    checked  = state.gameBootEnabled,
                    onToggle = { viewModel.setGameBootEnabled(it) },
                )

                if (state.gameBootEnabled) {
                    MediaAssignmentRow(
                        label    = "GameBoot Video",
                        focusKey = "display_${UiMediaSlot.GAMEBOOT_VIDEO.key}",
                        sublabel = "Replace the built-in sequence with your own clip, which plays with " +
                            "its own sound — even with Menu Sounds off (MP4 or WebM, up to 10 seconds)",
                        value    = state.gameBootVideoLabel,
                        isAssigned = state.gameBootVideoAssigned,
                        onPick   = { pickUiMedia(UiMediaSlot.GAMEBOOT_VIDEO) },
                        onPreview = onPreviewGameBoot,
                        onUseDefault = { viewModel.clearUiMedia(UiMediaSlot.GAMEBOOT_VIDEO) },
                        onFocusChanged = { focusedSlot = if (it) UiMediaSlot.GAMEBOOT_VIDEO else null },
                    )
                }
            }
            if (section == null || section == DisplaySection.LAYOUT) {
                SettingsGroup("Orientation")

                SettingsValueRow(
                    label    = "Screen Orientation",
                    sublabel = "Follows the device — the XMB is drawn for landscape, so a portrait " +
                        "device will letterbox it",
                    value    = "Android auto-rotate",
                )
            }
            if (section == null || section == DisplaySection.INPUT) {
                SettingsGroup("Interface")

                SettingsPickerRow(
                    label    = "Touch Navigation Button",
                    sublabel = "On-screen App Drawer / Back button",
                    options  = viewModel.touchNavButtonOptions.map { SettingsPickerOption(it.second) },
                    selectedIndex = viewModel.touchNavButtonOptions
                        .indexOfFirst { it.first == state.touchNavButtonMode },
                    onPick   = { viewModel.setTouchNavButtonMode(viewModel.touchNavButtonOptions[it].first) },
                )

                SettingsPickerRow(
                    label    = "Touch Sensitivity",
                    sublabel = "How far a swipe travels per XMB step",
                    options  = viewModel.touchSensitivityOptions.map { SettingsPickerOption(it.second) },
                    selectedIndex = viewModel.touchSensitivityOptions
                        .indexOfFirst { it.first == state.touchSensitivity },
                    onPick   = { viewModel.setTouchSensitivity(viewModel.touchSensitivityOptions[it].first) },
                )

                SettingsToggleRow(
                    label    = "Button Hints",
                    sublabel = "Show the on-screen button prompts, and let them be tapped",
                    checked  = state.contextMenuHintEnabled,
                    onToggle = { viewModel.setContextMenuHintEnabled(it) },
                )

                SettingsSliderRow(
                    label     = "Hint Delay",

                    sublabel  = "Always shown at ${formatHintDelay(ControllerHintPolicy.MIN_DELAY_SECONDS)}, " +
                        "or hide until a pause of up to ${formatHintDelay(ControllerHintPolicy.MAX_DELAY_SECONDS)}",
                    value     = state.contextMenuHintDelaySeconds,
                    onValueChange = viewModel::setContextMenuHintDelaySeconds,
                    valueRange = ControllerHintPolicy.DELAY_RANGE,
                    steps     = ControllerHintPolicy.DELAY_STEPS,
                    enabled  = state.contextMenuHintEnabled,
                    valueFormatter = { formatHintDelay(it) },
                )
            }
            if (section == null || section == DisplaySection.PERFORMANCE) {
                SettingsGroup("Performance")

                SettingsToggleRow(
                    label    = "Thermal Throttle Awareness",
                    sublabel = "Automatically reduce background quality when device runs hot",
                    checked  = state.thermalThrottleAware,
                    onToggle = { viewModel.setThermalThrottleAware(it) },
                )

                SettingsToggleRow(
                    label    = "Battery Saver Mode",
                    sublabel = "Freeze the background (wave or motion wallpaper) when Battery Saver is active",
                    checked  = state.respectBatterySaver,
                    onToggle = { viewModel.setRespectBatterySaver(it) },
                )

                SettingsGroup("Games")

                SettingsToggleRow(
                    label    = "Launch Games Directly",
                    sublabel = "Confirm starts the game immediately instead of opening Game Details — use \"View Game Details\" in a game's Options menu to edit",
                    checked  = state.directLaunch,
                    onToggle = { viewModel.setDirectLaunch(it) },
                )
            }
        }
    }

    if (state.wallpaperPreviewVisible && state.customWallpaperPath != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { viewModel.hideWallpaperPreview() },
        ) {
            val posterPath = state.customWallpaperPath
            val motionPath = state.motionWallpaperPath
            if (motionPath != null && posterPath != null) {
                MotionWallpaperBackground(
                    posterPath = posterPath,
                    motionPath = motionPath,
                    decision   = MotionWallpaperPolicy.Decision.PLAY,
                    modifier   = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model              = state.customWallpaperPath,
                    contentDescription = "Wallpaper preview",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (fontPickerOpen) {
        val pfp = LocalPFPColors.current
        val anchors = remember(pfp.backgroundTop, pfp.backgroundBottom) {
            composite(solveScrimColor(pfp.backgroundTop, 0.72f).copy(alpha = 0.72f), Color.White) to
                composite(solveScrimColor(pfp.backgroundBottom, 0.90f).copy(alpha = 0.90f), Color.White)
        }
        HsvColorPickerDialog(
            title           = "Font Colour",
            hue             = pickerHue,
            saturation      = pickerSat,
            brightness      = pickerVal,
            selectedChannel = pickerChannel,
            accent          = SettingsAccent,
            subtext         = SettingsSubtext,
            contrastAnchors = anchors,
            onChannelFraction = { channel, fraction ->
                pickerChannel = channel
                when (channel) {
                    0 -> pickerHue = (fraction * 360f).coerceIn(0f, 360f)
                    1 -> pickerSat = fraction.coerceIn(0f, 1f)
                    else -> pickerVal = fraction.coerceIn(0f, 1f)
                }
            },
            onConfirm = {
                viewModel.setTextColor(hsvToArgbLong(pickerHue, pickerSat, pickerVal))
                fontPickerOpen = false
            },
            onCancel = { fontPickerOpen = false },
        )
    }

    pspConfirmFocus?.let { focused ->
        PspLayoutConfirmPanel(
            focusedOption = focused,
            onCancel = { pspConfirmFocus = null },
            onApply = { viewModel.applyPspLayout(); pspConfirmFocus = null },
        )
    }

    if (state.textContrastNotice != null) {
        SettingsActionsOverlay(
            title = "Font colour adjusted",
            message = state.textContrastNotice!!,
            actions = listOf(
                "OK" to { viewModel.dismissTextContrastNotice() },
                "Use my exact colour" to { viewModel.setTextColorExact(true) },
                "Don't warn again" to { viewModel.suppressTextContrastNotice() },
            ),
            onCancel = { viewModel.dismissTextContrastNotice() },
        )
    }

    if (state.wallpaperMessage != null) {
        SettingsMessageOverlay(
            title = "Wallpaper",
            message = state.wallpaperMessage!!,
            onDismiss = { viewModel.dismissWallpaperMessage() },
        )
    }
}

private const val PSP_CONFIRM_CANCEL = 0
private const val PSP_CONFIRM_APPLY = 1

@Composable
private fun PspLayoutConfirmPanel(focusedOption: Int, onCancel: () -> Unit, onApply: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF2101018))
                .border(1.dp, SettingsDivider, RoundedCornerShape(8.dp))

                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Text("Apply Biblically Accurate PSP XMB?", color = SettingsText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sets this screen's XMB scale and crossbar position to the PSP's own proportions. " +
                    "Your current layout for this screen size is replaced; other screen sizes keep theirs.",
                color = SettingsSubtext,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PspConfirmOption("Cancel", focusedOption == PSP_CONFIRM_CANCEL, onCancel)
                PspConfirmOption("Apply", focusedOption == PSP_CONFIRM_APPLY, onApply)
            }
        }
    }
}

@Composable
private fun PspConfirmOption(label: String, focused: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) SettingsAccent.copy(alpha = 0.25f) else Color.Transparent)
            .border(1.dp, if (focused) SettingsAccent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (focused) Color.White else SettingsSubtext, fontSize = 14.sp)
    }
}

private fun formatHintDelay(seconds: Float): String = when {
    seconds <= 0f -> "Always"
    seconds % 1f == 0f -> "${seconds.toInt()}s"
    else -> "${seconds}s"
}

private fun UiMediaSlot.isAssignedIn(state: DisplaySettingsUiState): Boolean = when (this) {
    UiMediaSlot.BOOT_VIDEO -> state.bootVideoAssigned
    UiMediaSlot.GAMEBOOT_VIDEO -> state.gameBootVideoAssigned
    else -> false
}
