package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.clearWallpaperLuma
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.setWallpaperLuma
import com.psplauncher.core.domain.model.ControllerHintPolicy
import com.psplauncher.core.domain.model.UiMediaKind
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.domain.model.IconLegibilityStyle
import com.psplauncher.core.domain.model.TextLegibilityStyle
import com.psplauncher.core.domain.model.XmbColorScheme
import com.psplauncher.core.domain.model.lightBackgroundAnchors
import com.psplauncher.core.domain.model.resolve
import com.psplauncher.core.domain.model.TouchNavButtonMode
import com.psplauncher.core.domain.model.TouchSensitivity
import com.psplauncher.core.domain.model.XYLayout
import com.psplauncher.core.ui.theme.TextContrastRole
import com.psplauncher.core.ui.theme.clampLightnessForContrast
import com.psplauncher.core.ui.theme.composite
import com.psplauncher.core.ui.theme.solveScrimColor
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.themekit.MotionLimits
import com.psplauncher.themekit.UiMediaLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private val KEY_WAVE_STYLE         = stringPreferencesKey("display_wave_style")
private val KEY_SHOW_BOOT          = booleanPreferencesKey("display_show_boot")
private val KEY_BOOT_ON_RESUME     = booleanPreferencesKey("display_boot_on_resume")
private val KEY_THERMAL_AWARE      = booleanPreferencesKey("display_thermal_aware")
private val KEY_RESPECT_BATTERY    = booleanPreferencesKey("display_battery_saver")

private val KEY_WAVE_OVER_WALLPAPER = booleanPreferencesKey("display_wave_over_wallpaper")

private val KEY_TOUCH_NAV_BUTTON   = stringPreferencesKey("interface_touch_nav_button")

private val KEY_CONTEXT_MENU_HINT  = booleanPreferencesKey("interface_context_menu_hint")
private val KEY_CONTEXT_MENU_HINT_DELAY_SECONDS = floatPreferencesKey("interface_context_menu_hint_delay_seconds")

private val KEY_TOUCH_SENSITIVITY  = stringPreferencesKey("interface_touch_sensitivity")


private val KEY_ICON_LEGIBILITY    = stringPreferencesKey("display_icon_legibility")

private val KEY_FADE_BY_DISTANCE = booleanPreferencesKey("display_fade_by_distance")

private val KEY_CARD_ART_GRID = booleanPreferencesKey("display_card_art_grid")

private val KEY_RECENTS_INCLUDE_APPS = booleanPreferencesKey("display_recents_include_apps")

private val KEY_TEXT_SHADOW = booleanPreferencesKey("display_text_shadow")

private val KEY_TEXT_COLOR = longPreferencesKey("display_text_color")

private val KEY_TEXT_COLOR_EXACT = booleanPreferencesKey("display_text_color_exact")

private val KEY_TEXT_LEGIBILITY = stringPreferencesKey("display_text_legibility")

private val KEY_TEXT_NOTICE_SUPPRESSED = booleanPreferencesKey("display_text_contrast_notice_suppressed")

private val KEY_ACCENT_OVERRIDE = longPreferencesKey("theme_accent_override")
private val KEY_COLOR_SCHEME    = stringPreferencesKey("display_color_scheme")
internal val KEY_CUSTOM_WALLPAPER  = stringPreferencesKey("display_custom_wallpaper")

internal val KEY_MOTION_WALLPAPER  = stringPreferencesKey("display_motion_wallpaper")

private val SUPPORTED_WALLPAPER_MIME = setOf("image/png", "image/jpeg", "image/webp") + MotionLimits.SUPPORTED_MIME

private val MOTION_WALLPAPER_MIME = MotionLimits.SUPPORTED_MIME

private val TOUCH_NAV_BUTTON_LABELS = mapOf(
    TouchNavButtonMode.AUTO        to "Auto",
    TouchNavButtonMode.ALWAYS_SHOW to "Always Show",
    TouchNavButtonMode.ALWAYS_HIDE to "Always Hide",
)

private val TOUCH_SENSITIVITY_LABELS = mapOf(
    TouchSensitivity.LOW    to "Low",
    TouchSensitivity.NORMAL to "Normal",
    TouchSensitivity.HIGH   to "High",
)

private val WAVE_STYLE_LABELS = mapOf(
    WaveStyle.ANIMATED       to "Animated",
    WaveStyle.REDUCED        to "Reduced",
    WaveStyle.STATIC         to "Static",
    WaveStyle.REDUCED_STATIC to "Reduced + Static",
)

private data class Transient(
    val bootPreviewVisible: Boolean,
    val gameBootPreviewVisible: Boolean,
    val textContrastNotice: String?,
    val xyLayout: XYLayout,
)

data class DisplaySettingsUiState(
    val waveStyle: WaveStyle = WaveStyle.ANIMATED,
    val showBootSequence: Boolean = true,
    val showBootOnResume: Boolean = false,
    val thermalThrottleAware: Boolean = true,
    val respectBatterySaver: Boolean = true,

    val waveOverWallpaper: Boolean = false,
    val touchNavButtonMode: TouchNavButtonMode = TouchNavButtonMode.AUTO,

    val iconLegibility: IconLegibilityStyle = IconLegibilityStyle.DEFAULT,

    val fadeByDistance: Boolean = true,
    val cardArtGrid: Boolean = true,
    val recentsIncludeApps: Boolean = false,

    val textShadow: Boolean = true,

    val textColorArgb: Long? = null,

    val textColorExact: Boolean = false,
    val textLegibility: TextLegibilityStyle = TextLegibilityStyle.DEFAULT,
    val textContrastNoticeSuppressed: Boolean = false,

    val textContrastNotice: String? = null,

    val contextMenuHintDelaySeconds: Float = ControllerHintPolicy.DEFAULT_DELAY_SECONDS,
    val touchSensitivity: TouchSensitivity = TouchSensitivity.NORMAL,

    val customWallpaperPath: String? = null,

    val motionWallpaperPath: String? = null,
    val wallpaperMessage: String? = null,
    val contextMenuHintEnabled: Boolean = ControllerHintPolicy.DEFAULT_ENABLED,
    val wallpaperImporting: Boolean = false,
    val wallpaperPreviewVisible: Boolean = false,

    val bootVideoLabel: String = UI_MEDIA_DEFAULT_LABEL,
    val bootVideoAssigned: Boolean = false,
    val bootPreviewVisible: Boolean = false,

    val gameBootEnabled: Boolean = true,
    val launchDiscEnabled: Boolean = true,
    val gameBootVideoLabel: String = UI_MEDIA_DEFAULT_LABEL,
    val gameBootVideoAssigned: Boolean = false,
    val gameBootPreviewVisible: Boolean = false,

    val xyLayout: XYLayout = XYLayout.STANDARD,

    val pspLayoutApplied: Boolean = false,
) {
}

const val UI_MEDIA_DEFAULT_LABEL = "PSP Default"

@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiMediaStore: UiMediaStore,
    private val gameBootPreferences: GameBootPreferences,
    private val launchDiscPreferences: com.psplauncher.core.data.launch.LaunchDiscPreferences,
    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
    private val controllerLayout: ControllerLayoutRepository,

    @com.psplauncher.feature.settings.di.SettingsIoDispatcher
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _wallpaperMessage  = MutableStateFlow<String?>(null)
    private val _wallpaperImporting = MutableStateFlow(false)
    private val _wallpaperPreviewVisible = MutableStateFlow(false)
    private val _bootPreviewVisible = MutableStateFlow(false)
    private val _gameBootPreviewVisible = MutableStateFlow(false)

    private val _textContrastNotice = MutableStateFlow<String?>(null)

    private var pendingUiMediaSlot: UiMediaSlot? = null

    val uiState: StateFlow<DisplaySettingsUiState> = combine(
        context.pfpDataStore.data,
        _wallpaperMessage,
        _wallpaperImporting,
        _wallpaperPreviewVisible,

        combine(
            _bootPreviewVisible,
            _gameBootPreviewVisible,
            _textContrastNotice,
            controllerLayout.prefs,
        ) { boot, gameBoot, notice, layout ->
            Transient(boot, gameBoot, notice, layout.xyLayout)
        },
    ) { prefs, msg, importing, previewVisible, transient ->

        val assigned = uiMediaStore.assignments()
        fun label(slot: UiMediaSlot): String = when {
            slot !in assigned -> UI_MEDIA_DEFAULT_LABEL
            else -> prefs[UiMediaStore.displayNameKey(slot)]
                ?: if (slot.kind == UiMediaKind.VIDEO) "Custom video" else "Custom sound"
        }
        DisplaySettingsUiState(
            waveStyle            = runCatching {
                WaveStyle.valueOf(prefs[KEY_WAVE_STYLE] ?: WaveStyle.ANIMATED.name)
            }.getOrDefault(WaveStyle.ANIMATED),
            showBootSequence     = prefs[KEY_SHOW_BOOT]       ?: true,
            showBootOnResume     = prefs[KEY_BOOT_ON_RESUME]  ?: false,
            thermalThrottleAware = prefs[KEY_THERMAL_AWARE]   ?: true,
            respectBatterySaver  = prefs[KEY_RESPECT_BATTERY] ?: true,
            waveOverWallpaper    = prefs[KEY_WAVE_OVER_WALLPAPER] ?: false,
            touchNavButtonMode   = TouchNavButtonMode.fromName(prefs[KEY_TOUCH_NAV_BUTTON]),
            iconLegibility       = IconLegibilityStyle.fromName(prefs[KEY_ICON_LEGIBILITY]),
            fadeByDistance       = prefs[KEY_FADE_BY_DISTANCE] ?: true,
            cardArtGrid          = prefs[KEY_CARD_ART_GRID] ?: true,
            recentsIncludeApps   = prefs[KEY_RECENTS_INCLUDE_APPS] ?: false,
            textShadow           = prefs[KEY_TEXT_SHADOW] ?: true,
            textColorArgb        = prefs[KEY_TEXT_COLOR],
            textColorExact       = prefs[KEY_TEXT_COLOR_EXACT] ?: false,
            textLegibility       = TextLegibilityStyle.fromName(prefs[KEY_TEXT_LEGIBILITY]),
            textContrastNoticeSuppressed = prefs[KEY_TEXT_NOTICE_SUPPRESSED] ?: false,
            textContrastNotice   = transient.textContrastNotice,
            contextMenuHintEnabled = prefs[KEY_CONTEXT_MENU_HINT] ?: ControllerHintPolicy.DEFAULT_ENABLED,
            contextMenuHintDelaySeconds = ControllerHintPolicy.clampDelay(
                prefs[KEY_CONTEXT_MENU_HINT_DELAY_SECONDS] ?: ControllerHintPolicy.DEFAULT_DELAY_SECONDS
            ),
            touchSensitivity     = TouchSensitivity.fromName(prefs[KEY_TOUCH_SENSITIVITY]),
            customWallpaperPath  = prefs[KEY_CUSTOM_WALLPAPER],
            motionWallpaperPath  = prefs[KEY_MOTION_WALLPAPER],
            wallpaperMessage     = msg,
            wallpaperImporting   = importing,
            wallpaperPreviewVisible = previewVisible,
            bootVideoLabel       = label(UiMediaSlot.BOOT_VIDEO),
            bootVideoAssigned    = UiMediaSlot.BOOT_VIDEO in assigned,
            bootPreviewVisible   = transient.bootPreviewVisible,

            gameBootEnabled      = GameBootPreferences.resolve(prefs),
            launchDiscEnabled    = com.psplauncher.core.data.launch.LaunchDiscPreferences.resolve(prefs),
            gameBootVideoLabel   = label(UiMediaSlot.GAMEBOOT_VIDEO),
            gameBootVideoAssigned = UiMediaSlot.GAMEBOOT_VIDEO in assigned,
            gameBootPreviewVisible = transient.gameBootPreviewVisible,
            xyLayout             = transient.xyLayout,

            pspLayoutApplied     = PspXmbLayout.isApplied(prefs, PspXmbLayout.forWindow(context)),
        )
    }

        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplaySettingsUiState())

    fun onUiMediaPickerLaunchedFor(slot: UiMediaSlot) {
        pendingUiMediaSlot = slot
    }

    fun onUiMediaPicked(uri: Uri) {
        val slot = pendingUiMediaSlot ?: return
        pendingUiMediaSlot = null
        viewModelScope.launch {
            _wallpaperImporting.value = true
            val result = try {
                uiMediaStore.import(slot, uri)
            } finally {
                _wallpaperImporting.value = false
            }
            if (!result.ok) {
                menuSound.play(com.psplauncher.core.ui.sound.MenuSound.ERROR)
                _wallpaperMessage.value = result.message
            }
        }
    }

    fun clearUiMedia(slot: UiMediaSlot) = viewModelScope.launch { uiMediaStore.clear(slot) }

    fun setGameBootEnabled(enabled: Boolean) = viewModelScope.launch {
        gameBootPreferences.setGameBootEnabled(enabled)
    }

    fun setLaunchDiscEnabled(enabled: Boolean) = viewModelScope.launch {
        launchDiscPreferences.setLaunchDiscEnabled(enabled)
    }

    fun showBootPreview() { _bootPreviewVisible.value = true }
    fun hideBootPreview() { _bootPreviewVisible.value = false }
    fun showGameBootPreview() { _gameBootPreviewVisible.value = true }
    fun hideGameBootPreview() { _gameBootPreviewVisible.value = false }

    fun uiMediaPickerMime(slot: UiMediaSlot): Array<String> =
        if (slot.kind == UiMediaKind.VIDEO) UiMediaLimits.VIDEO_MIME.toTypedArray()
        else UiMediaLimits.AUDIO_MIME.toTypedArray()

    fun setWaveStyle(style: WaveStyle) = save { it[KEY_WAVE_STYLE] = style.name }

    val waveStyleOptions: List<Pair<WaveStyle, String>> =
        WaveStyle.entries.map { it to (WAVE_STYLE_LABELS[it] ?: it.name) }
    val touchNavButtonOptions: List<Pair<TouchNavButtonMode, String>> =
        TouchNavButtonMode.entries.map { it to (TOUCH_NAV_BUTTON_LABELS[it] ?: it.name) }
    val touchSensitivityOptions: List<Pair<TouchSensitivity, String>> =
        TouchSensitivity.entries.map { it to (TOUCH_SENSITIVITY_LABELS[it] ?: it.name) }

    fun setIconLegibility(style: IconLegibilityStyle) = save { it[KEY_ICON_LEGIBILITY] = style.name }

    fun setFadeByDistance(v: Boolean) = save { it[KEY_FADE_BY_DISTANCE] = v }
    fun setCardArtGrid(v: Boolean) = save { it[KEY_CARD_ART_GRID] = v }

    fun setRecentsIncludeApps(v: Boolean) = save { it[KEY_RECENTS_INCLUDE_APPS] = v }

    fun setTextShadow(v: Boolean) = save { it[KEY_TEXT_SHADOW] = v }

    fun applyPspLayout() = save { PspXmbLayout.write(it, PspXmbLayout.forWindow(context)) }

    fun setTextColor(argb: Long?) {
        viewModelScope.launch {
            context.pfpDataStore.edit { prefs ->
                if (argb != null) prefs[KEY_TEXT_COLOR] = argb else prefs.remove(KEY_TEXT_COLOR)
            }
            _textContrastNotice.value = noticeFor(argb)
        }
    }

    fun setTextColorExact(v: Boolean) {
        viewModelScope.launch {
            context.pfpDataStore.edit { it[KEY_TEXT_COLOR_EXACT] = v }

            if (v) _textContrastNotice.value = null
        }
    }

    fun setTextLegibility(style: TextLegibilityStyle) = save { it[KEY_TEXT_LEGIBILITY] = style.name }

    fun dismissTextContrastNotice() {
        _textContrastNotice.value = null
    }

    fun suppressTextContrastNotice() {
        _textContrastNotice.value = null
        save { it[KEY_TEXT_NOTICE_SUPPRESSED] = true }
    }

    private suspend fun noticeFor(argb: Long?): String? {
        if (argb == null) return null
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_TEXT_COLOR_EXACT] == true) return null
        if (prefs[KEY_TEXT_NOTICE_SUPPRESSED] == true) return null

        val picked = androidx.compose.ui.graphics.Color(argb and 0xFFFFFFFFL)
        val worst = settingsBackdropAnchors(prefs)
            .minByOrNull { clampLightnessForContrast(picked, it).achievedRatio } ?: return null
        val resolved = clampLightnessForContrast(picked, worst, TextContrastRole.BODY.threshold)

        return when {
            !resolved.adjusted && resolved.meetsTarget -> null
            resolved.adjusted -> "Adjusted for readability — the colour you picked reads at " +
                String.format("%.1f:1", contrastOf(picked, worst)) + " here, below the 4.5:1 bar."
            else -> "That colour can't reach 4.5:1 on this background, so text will get a " +
                "contrast plate behind it."
        }
    }

    private fun settingsBackdropAnchors(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): List<androidx.compose.ui.graphics.Color> {
        val accentOverride = prefs[KEY_ACCENT_OVERRIDE]
        val anchors: Pair<Long, Long> = if (accentOverride != null) {
            lightBackgroundAnchors(accentOverride and 0xFFFFFFFFL)
        } else {
            val scheme = runCatching {
                XmbColorScheme.valueOf(
                    prefs[KEY_COLOR_SCHEME] ?: XmbColorScheme.CLASSIC_BLUE.name,
                )
            }.getOrDefault(XmbColorScheme.CLASSIC_BLUE)
            val palette = scheme.resolve(java.time.LocalDate.now().monthValue)
            palette.backgroundTop to palette.backgroundBottom
        }
        fun backdrop(argb: Long, alpha: Float) = composite(
            solveScrimColor(androidx.compose.ui.graphics.Color(argb and 0xFFFFFFFFL), alpha)
                .copy(alpha = alpha),
            androidx.compose.ui.graphics.Color.White,
        )
        return listOf(backdrop(anchors.first, 0.72f), backdrop(anchors.second, 0.90f))
    }

    private fun contrastOf(
        fg: androidx.compose.ui.graphics.Color,
        bg: androidx.compose.ui.graphics.Color,
    ): Float = com.psplauncher.core.ui.theme.contrastRatio(fg, bg).toFloat()

    fun setShowBootSequence(v: Boolean)      = save { it[KEY_SHOW_BOOT]       = v }
    fun setShowBootOnResume(v: Boolean)      = save { it[KEY_BOOT_ON_RESUME]  = v }
    fun setThermalThrottleAware(v: Boolean)  = save { it[KEY_THERMAL_AWARE]   = v }
    fun setRespectBatterySaver(v: Boolean)   = save { it[KEY_RESPECT_BATTERY] = v }
    fun setWaveOverWallpaper(v: Boolean)     = save { it[KEY_WAVE_OVER_WALLPAPER] = v }
    fun setContextMenuHintEnabled(v: Boolean) = save { it[KEY_CONTEXT_MENU_HINT] = v }
    fun setContextMenuHintDelaySeconds(v: Float) = save {
        it[KEY_CONTEXT_MENU_HINT_DELAY_SECONDS] = v.coerceIn(1f, 5f)
    }

    fun setTouchNavButtonMode(mode: TouchNavButtonMode) = save { it[KEY_TOUCH_NAV_BUTTON] = mode.name }

    fun setTouchSensitivity(level: TouchSensitivity) = save { it[KEY_TOUCH_SENSITIVITY] = level.name }

    fun onWallpaperPicked(uri: Uri) {
        viewModelScope.launch {
            val mime = context.contentResolver.getType(uri)
            if (mime != null && mime !in SUPPORTED_WALLPAPER_MIME) {
                _wallpaperMessage.value = "Unsupported format — use PNG, JPG, WEBP, MP4, WEBM, or GIF"
                return@launch
            }
            _wallpaperImporting.value = true
            try {
                if (mime in MOTION_WALLPAPER_MIME) {
                    importMotionWallpaper(uri, mime!!)
                } else {
                    importStillWallpaper(uri)
                }
            } finally {
                _wallpaperImporting.value = false
            }
        }
    }

    private suspend fun importStillWallpaper(uri: Uri) {
        val dir = wallpaperDir()

        val stamp = System.currentTimeMillis()
        val dest = File(dir, "wallpaper_$stamp.jpg")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } != null && isDecodableImage(dest)
        }.getOrDefault(false)
        if (ok) {
            val luma = withContext(io) {
                WallpaperLuminanceProbe.survey(dest.absolutePath)
            }

            save {
                it[KEY_CUSTOM_WALLPAPER] = dest.absolutePath
                it.remove(KEY_MOTION_WALLPAPER)
                it.setWallpaperLuma(luma)
            }
            pruneWallpaperDir(keep = listOf(dest))
            _wallpaperMessage.value = "Wallpaper applied"
        } else {
            runCatching { dest.delete() }
            _wallpaperMessage.value = "Couldn't read that file — try a different one"
        }
    }

    private suspend fun importMotionWallpaper(uri: Uri, mime: String) {
        val dir = wallpaperDir()
        val stamp = System.currentTimeMillis()

        val motionExt = when (mime) {
            "video/webm" -> "webm"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "mp4"
        }
        val motionDest = File(dir, "wallpaper_$stamp.$motionExt")
        val posterDest = File(dir, "wallpaper_$stamp.jpg")

        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 }
        if (knownSize != null && knownSize > MotionLimits.MAX_BYTES) {
            _wallpaperMessage.value = MotionLimits.MSG_TOO_LARGE_BYTES
            return
        }

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                motionDest.outputStream().use { out -> input.copyTo(out) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            runCatching { motionDest.delete() }
            _wallpaperMessage.value = MotionLimits.MSG_UNDECODABLE
            return
        }

        val probe = probeMotionFile(motionDest, mime)
        val rejection = probe?.let { MotionLimits.validate(it) }
        if (probe == null || rejection != null) {
            runCatching { motionDest.delete() }
            _wallpaperMessage.value = rejection ?: MotionLimits.MSG_UNDECODABLE
            return
        }

        val poster = extractPoster(motionDest, mime)
        if (poster == null) {
            runCatching { motionDest.delete() }
            _wallpaperMessage.value = MotionLimits.MSG_UNDECODABLE
            return
        }
        val posterOk = runCatching {
            posterDest.outputStream().use { poster.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)
        poster.recycle()
        if (!posterOk) {
            runCatching { motionDest.delete() }
            _wallpaperMessage.value = MotionLimits.MSG_UNDECODABLE
            return
        }

        val luma = withContext(io) {
            WallpaperLuminanceProbe.survey(posterDest.absolutePath)
        }

        save {
            it[KEY_CUSTOM_WALLPAPER] = posterDest.absolutePath
            it[KEY_MOTION_WALLPAPER] = motionDest.absolutePath
            it.setWallpaperLuma(luma)
        }
        pruneWallpaperDir(keep = listOf(motionDest, posterDest))
        _wallpaperMessage.value = "Motion wallpaper applied"
    }

    private fun probeMotionFile(file: File, mime: String): MotionLimits.Probe? = runCatching {
        if (mime == "image/gif" || mime == "image/webp") {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            MotionLimits.Probe(
                mime = mime,
                width = bounds.outWidth,
                height = bounds.outHeight,
                durationMs = 0L,
                bytes = file.length(),
            )
        } else {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                MotionLimits.Probe(
                    mime = mime,
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    bytes = file.length(),
                )
            }
        }
    }.getOrNull()

    private fun extractPoster(motionFile: File, mime: String): Bitmap? = runCatching {
        if (mime == "image/gif" || mime == "image/webp") {
            val source = ImageDecoder.createSource(motionFile)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setTargetSampleSize(
                    maxOf(1, maxOf(info.size.width, info.size.height) / MotionLimits.MAX_HEIGHT),
                )
            }
        } else {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(motionFile.absolutePath)
                retriever.getFrameAtTime(1_000_000L)
                    ?: retriever.getFrameAtTime(0L)
            }
        }
    }.getOrNull()

    private fun isDecodableImage(file: File): Boolean {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    private fun wallpaperDir(): File = File(context.filesDir, "wallpaper").apply { mkdirs() }

    private suspend fun pruneWallpaperDir(keep: List<File>) {
        val keepNames = keep.map { it.name }.toSet()
        withContext(io) {
            wallpaperDir().listFiles()?.forEach { f ->
                if (f.name !in keepNames) runCatching { f.delete() }
            }
        }
    }

    fun clearWallpaper() {
        viewModelScope.launch {
            val current = context.pfpDataStore.data.first()
            val poster = current[KEY_CUSTOM_WALLPAPER]
            val motion = current[KEY_MOTION_WALLPAPER]

            save {
                it.remove(KEY_CUSTOM_WALLPAPER)
                it.remove(KEY_MOTION_WALLPAPER)
                it.clearWallpaperLuma()
            }

            withContext(io) {
                poster?.let { runCatching { File(it).delete() } }
                motion?.let { runCatching { File(it).delete() } }
            }
            _wallpaperMessage.value = "Wallpaper reset to default"
        }
    }

    fun dismissWallpaperMessage() {
        _wallpaperMessage.value = null
    }

    fun showWallpaperPreview() {
        if (uiState.value.customWallpaperPath != null) {
            _wallpaperPreviewVisible.value = true
        } else {
            _wallpaperMessage.value = "No wallpaper selected yet"
        }
    }

    fun hideWallpaperPreview() {
        _wallpaperPreviewVisible.value = false
    }

    private fun save(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { context.pfpDataStore.edit { block(it) } }
    }
}
