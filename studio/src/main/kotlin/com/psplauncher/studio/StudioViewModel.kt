package com.psplauncher.studio

import androidx.compose.ui.graphics.ImageBitmap
import com.psplauncher.studio.io.ConvertOutcome
import com.psplauncher.studio.io.ImageCodecs
import com.psplauncher.studio.io.PtfConversion
import com.psplauncher.studio.io.VideoCodecs
import com.psplauncher.themekit.IconGifSupport
import com.psplauncher.themekit.IconSlots
import com.psplauncher.themekit.MotionLimits
import com.psplauncher.themekit.XmbLayoutSpecCodec
import com.psplauncher.themekit.PfpThemeBundle
import com.psplauncher.themekit.PfpThemeCodec
import com.psplauncher.themekit.PfpThemeManifest
import com.psplauncher.themekit.PfpThemeSource
import com.psplauncher.themekit.ThemeImage
import com.psplauncher.themekit.ThemeMotion
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface IconColorChoice {
    data object Auto : IconColorChoice
    data class Custom(val argb: Int) : IconColorChoice
}

sealed interface TextColorChoice {
    data object Auto : TextColorChoice
    data class Custom(val argb: Int) : TextColorChoice
}

enum class PreviewMode(val label: String) {
    HOME("Home"),
    CONTEXT_MENU("Menu"),
    FULLSCREEN_MENU("Fullscreen"),
}

enum class WallpaperPreset(val label: String, val width: Int, val height: Int) {
    PSP("PSP (480×272)", 480, 272),
    HD("HD (1280×720)", 1280, 720),
    FULL_HD("Full HD (1920×1080)", 1920, 1080),
    ORIGINAL("Keep original", 0, 0),
}

data class PendingWallpaper(
    val source: java.awt.image.BufferedImage,
    val fileName: String,
    val thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
)

sealed interface StudioDialog {
    data object CxmbRejected : StudioDialog
    data class Error(val message: String) : StudioDialog

    data class Notice(val title: String, val message: String) : StudioDialog
    data class BatchDone(val summary: com.psplauncher.studio.io.BatchSummary) : StudioDialog
}

data class StudioState(
    val name: String = "Untitled Theme",
    val accentArgb: Int = PtfConversion.DEFAULT_ACCENT,
    val iconColor: IconColorChoice = IconColorChoice.Auto,
    val textColor: TextColorChoice = TextColorChoice.Auto,
    val waveStyle: String = PfpThemeManifest.WAVE_ANIMATED,
    val wallpaperPng: ByteArray? = null,
    val wallpaperBitmap: ImageBitmap? = null,
    val wallpaperFileName: String? = null,

    val wallpaperBusy: Boolean = false,

    val pendingWallpaper: PendingWallpaper? = null,

    val motionFile: File? = null,

    val motionFileName: String? = null,

    val layout: com.psplauncher.themekit.XmbLayoutSpec = com.psplauncher.themekit.XmbLayoutSpec.DEFAULT,

    val previewMode: PreviewMode = PreviewMode.HOME,

    val iconOverrides: Map<String, ByteArray> = emptyMap(),

    val iconExtensions: Map<String, String> = emptyMap(),

    val iconBitmaps: Map<String, ImageBitmap> = emptyMap(),
    val source: PfpThemeSource? = null,
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val dialog: StudioDialog? = null,

    val batchProgress: com.psplauncher.studio.io.BatchProgress? = null,
) {
}

class StudioViewModel(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private var pendingMotion: File? = null
    private var pendingMotionName: String? = null

    private fun scratchMotion(source: File): File {
        val scratch = File.createTempFile("studio-motion-", ".${source.extension.lowercase()}")
        source.copyTo(scratch, overwrite = true)
        scratch.deleteOnExit()
        return scratch
    }

    private fun discardMotion(state: StudioState) {
        state.motionFile?.delete()
    }

    internal fun clearMotion() {
        discardMotion(_state.value)
        _state.update { it.copy(motionFile = null, motionFileName = null) }
    }

    fun newTheme() {
        abandonPendingMotion()
        discardMotion(_state.value)
        _state.update { StudioState() }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setAccent(argb: Int) = _state.update { it.copy(accentArgb = argb) }
    fun setIconColor(choice: IconColorChoice) = _state.update { it.copy(iconColor = choice) }
    fun setTextColor(choice: TextColorChoice) = _state.update { it.copy(textColor = choice) }
    fun setWaveStyle(style: String) = _state.update { it.copy(waveStyle = style) }
    fun setPreviewMode(mode: PreviewMode) = _state.update { it.copy(previewMode = mode) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }
    fun clearStatus() = _state.update { it.copy(statusMessage = null) }

    fun setBarTopFraction(fraction: Float) = _state.update {
        it.copy(
            layout = it.layout.copy(
                barTopFraction = fraction.coerceIn(
                    XmbLayoutSpecCodec.BAR_TOP_MIN,
                    XmbLayoutSpecCodec.BAR_TOP_MAX,
                ),
            ),
        )
    }

    fun resetLayout() = _state.update { it.copy(layout = com.psplauncher.themekit.XmbLayoutSpec.DEFAULT) }

    fun detectBarTop() = runBusy {
        val png = _state.value.wallpaperPng ?: return@runBusy
        val image = ImageCodecs.decodeImage(png) ?: return@runBusy

        val detected = com.psplauncher.themekit.CrossBandDetector.detectBarTopFraction(
            ImageCodecs.toBmpImage(image),
        )
        if (detected != null) {
            _state.update {
                it.copy(
                    layout = it.layout.copy(barTopFraction = detected),
                    statusMessage = "Crossbar detected at ${(detected * 100).toInt()}% of the wallpaper",
                )
            }
        } else {
            _state.update { it.copy(statusMessage = "No crossbar band found in this wallpaper") }
        }
    }

    fun openFile(file: File) {
        when (file.extension.lowercase()) {
            "ptf", "ctf" -> openPtf(file)
            PfpThemeCodec.FILE_EXTENSION -> openPfpTheme(file)
            else -> _state.update { it.copy(dialog = StudioDialog.Error("Unsupported file type: .${file.extension}")) }
        }
    }

    private fun openPtf(file: File) = runBusy {
        val bytes = com.psplauncher.studio.io.SafeIo.readBytesCapped(file)
        if (bytes == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is too large to be a theme file")) }
            return@runBusy
        }
        when (val outcome = PtfConversion.convert(bytes, file.name)) {
            is ConvertOutcome.Converted -> {
                hydrate(outcome.bundle, "Imported ${file.name}")
                outcome.warning?.let { warning ->
                    _state.update { it.copy(dialog = StudioDialog.Notice("Imported with a caveat", warning)) }
                }
            }
            ConvertOutcome.Cxmb -> _state.update { it.copy(dialog = StudioDialog.CxmbRejected) }
            is ConvertOutcome.Failed -> _state.update {
                it.copy(dialog = StudioDialog.Error("${file.name}: ${outcome.reason}"))
            }
        }
    }

    private fun openPfpTheme(file: File) = runBusy {
        val bundle = PfpThemeCodec.read(file)
        if (bundle == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is not a valid .pfptheme bundle")) }
        } else {
            hydrate(bundle, "Opened ${file.name}")
        }
    }

    private fun hydrate(bundle: PfpThemeBundle, status: String) {
        val manifest = bundle.manifest
        val iconBitmaps = bundle.icons.mapNotNull { (key, png) ->
            ImageCodecs.toImageBitmap(png.bytes)?.let { key to it }
        }.toMap()
        val wallpaperBusy = bundle.wallpaper
            ?.let(ImageCodecs::decodeImage)
            ?.let { com.psplauncher.themekit.WallpaperMetrics.isBusy(ImageCodecs.toBmpImage(it)) }
            ?: false

        abandonPendingMotion()
        discardMotion(_state.value)
        var motionSpillError: String? = null
        val motionFile = bundle.motion?.let { motion ->
            runCatching {
                val scratch = File.createTempFile("studio-motion-", ".${motion.extension}")
                scratch.outputStream().use { motion.copyTo(it) }
                scratch.deleteOnExit()
                scratch
            }.onFailure { e -> motionSpillError = e.message }.getOrNull()
        }
        _state.update {
            StudioState(
                name = manifest.name,
                accentArgb = PtfConversion.parseHexRgb(manifest.accentColor) ?: PtfConversion.DEFAULT_ACCENT,
                iconColor = manifest.iconColor
                    .takeIf { c -> c != PfpThemeManifest.ICON_COLOR_AUTO }
                    ?.let { c -> PtfConversion.parseHexRgb(c) }
                    ?.let { argb -> IconColorChoice.Custom(argb) }
                    ?: IconColorChoice.Auto,
                textColor = manifest.textColor
                    .takeIf { c -> c != PfpThemeManifest.ICON_COLOR_AUTO }
                    ?.let { c -> PtfConversion.parseHexRgb(c) }
                    ?.let { argb -> TextColorChoice.Custom(argb) }
                    ?: TextColorChoice.Auto,
                waveStyle = manifest.waveStyle,
                wallpaperPng = bundle.wallpaper,
                wallpaperBitmap = bundle.wallpaper?.let(ImageCodecs::toImageBitmap),
                wallpaperFileName = manifest.source?.file,

                iconOverrides = bundle.icons.mapValues { (_, image) -> image.bytes },
                iconExtensions = bundle.icons.mapValues { (_, image) -> image.extension.lowercase() },
                iconBitmaps = iconBitmaps,
                wallpaperBusy = wallpaperBusy,
                motionFile = motionFile,

                motionFileName = motionFile?.let { "motion.${it.extension}" },
                layout = manifest.layout?.let(XmbLayoutSpecCodec::sanitize)
                    ?: com.psplauncher.themekit.XmbLayoutSpec.DEFAULT,
                source = manifest.source,
                statusMessage = status,
            )
        }

        motionSpillError?.let { message ->
            _state.update {
                it.copy(dialog = StudioDialog.Notice("Motion wallpaper not kept", message))
            }
        }
    }

    private fun abandonPendingMotion() {
        pendingMotion?.delete()
        pendingMotion = null
        pendingMotionName = null
    }

    private val MOTION_PICK_EXTENSIONS = setOf("mp4", "m4v", "webm")

    fun onWallpaperPicked(file: File) {
        if (file.extension.lowercase() in MOTION_PICK_EXTENSIONS) importVideo(file) else stageWallpaper(file)
    }

    fun stageWallpaper(file: File) = runBusy {
        val image = ImageCodecs.loadImage(file)
        if (image == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is not a readable image")) }
            return@runBusy
        }
        abandonPendingMotion()
        stage(image, file.name)
    }

    fun restageEmbeddedWallpaper() = runBusy {
        val current = _state.value
        val image = current.wallpaperPng?.let(ImageCodecs::decodeImage) ?: return@runBusy
        abandonPendingMotion()
        stage(image, current.wallpaperFileName ?: "wallpaper")
    }

    fun importVideo(file: File) = runBusy {
        when (val outcome = VideoCodecs.accept(file)) {
            is VideoCodecs.Outcome.Rejected ->

                _state.update { it.copy(dialog = StudioDialog.Error(outcome.message)) }

            is VideoCodecs.Outcome.Accepted -> {
                val scratch = scratchMotion(file)

                discardMotion(_state.value)
                pendingMotion?.delete()
                pendingMotion = scratch
                pendingMotionName = file.name
                stage(outcome.poster, file.name)
                _state.update { it.copy(motionFile = null, motionFileName = null) }
            }
        }
    }

    private fun stage(image: java.awt.image.BufferedImage, name: String) {
        _state.update {
            it.copy(
                pendingWallpaper = PendingWallpaper(
                    source = image,
                    fileName = name,
                    thumbnail = ImageCodecs.toImageBitmap(ImageCodecs.toPngBytes(ImageCodecs.thumbnail(image, 320))),
                ),
            )
        }
    }

    fun cancelWallpaperImport() {
        abandonPendingMotion()
        _state.update { it.copy(pendingWallpaper = null) }
    }

    fun confirmWallpaper(preset: WallpaperPreset) = runBusy {
        val pending = _state.value.pendingWallpaper ?: return@runBusy
        val image = if (preset == WallpaperPreset.ORIGINAL) pending.source
        else ImageCodecs.centerCropScale(pending.source, preset.width, preset.height)
        val png = ImageCodecs.toPngBytes(image)
        val bitmap = ImageCodecs.toImageBitmap(png)
        val bmp = ImageCodecs.toBmpImage(image)

        val derived = com.psplauncher.themekit.AccentDeriver.deriveAccent(bmp)

        val stagedVideo = pendingMotion
        val stagedName = pendingMotionName
        pendingMotion = null
        pendingMotionName = null
        _state.update {
            it.copy(
                pendingWallpaper = null,
                wallpaperPng = png,
                wallpaperBitmap = bitmap,
                wallpaperFileName = pending.fileName,
                wallpaperBusy = com.psplauncher.themekit.WallpaperMetrics.isBusy(bmp),
                motionFile = stagedVideo ?: it.motionFile,
                motionFileName = stagedName ?: it.motionFileName,
                accentArgb = derived ?: it.accentArgb,
                statusMessage = "Wallpaper: ${pending.fileName} (${image.width}×${image.height})",
            )
        }
    }

    fun clearWallpaper() {
        discardMotion(_state.value)
        _state.update {
            it.copy(
                wallpaperPng = null,
                wallpaperBitmap = null,
                wallpaperFileName = null,
                wallpaperBusy = false,
                motionFile = null,
                motionFileName = null,
            )
        }
    }

    fun setIconOverride(key: String, file: File) = runBusy {
        val slot = IconSlots.byKey(key) ?: return@runBusy

        val bytes = com.psplauncher.studio.io.SafeIo.readBytesCapped(file)
        val decoded = bytes?.let(ImageCodecs::decodeImage)
        if (bytes == null || decoded == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is not a readable image")) }
            return@runBusy
        }

        if (bytes.size > PfpThemeCodec.MAX_ICON_BYTES) {
            _state.update { it.copy(dialog = StudioDialog.Error(IconGifSupport.MSG_TOO_LARGE_BYTES)) }
            return@runBusy
        }
        val frames = IconGifSupport.countFrames(bytes)
        if (IconGifSupport.isGif(bytes) && frames > 1) {
            val (width, height) = IconGifSupport.logicalScreenSize(bytes) ?: (decoded.width to decoded.height)
            IconGifSupport.validateAnimated(width, height, frames, IconGifSupport.durationMs(bytes))
                ?.let { rejection ->
                    _state.update { it.copy(dialog = StudioDialog.Error(rejection)) }
                    return@runBusy
                }
            val gifBitmap = ImageCodecs.toImageBitmap(bytes)
            if (gifBitmap == null) {
                _state.update { it.copy(dialog = StudioDialog.Error(IconGifSupport.MSG_UNDECODABLE)) }
                return@runBusy
            }
            _state.update {
                it.copy(
                    iconOverrides = it.iconOverrides + (key to bytes),
                    iconExtensions = it.iconExtensions + (key to "gif"),
                    iconBitmaps = it.iconBitmaps + (key to gifBitmap),
                )
            }
            return@runBusy
        }

        val png = ImageCodecs.normalizeIconPng(file, slot.templateSizePx)
        val bitmap = png?.let(ImageCodecs::toImageBitmap)
        if (png == null || bitmap == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is not a readable image")) }
            return@runBusy
        }
        _state.update {
            it.copy(
                iconOverrides = it.iconOverrides + (key to png),
                iconExtensions = it.iconExtensions + (key to "png"),
                iconBitmaps = it.iconBitmaps + (key to bitmap),
            )
        }
    }

    fun clearIconOverride(key: String) = _state.update {
        it.copy(
            iconOverrides = it.iconOverrides - key,
            iconExtensions = it.iconExtensions - key,
            iconBitmaps = it.iconBitmaps - key,
        )
    }

    fun clearAllIconOverrides() = _state.update {
        it.copy(iconOverrides = emptyMap(), iconExtensions = emptyMap(), iconBitmaps = emptyMap())
    }

    fun buildManifest(state: StudioState = _state.value, today: LocalDate = LocalDate.now()): PfpThemeManifest =
        PfpThemeManifest(
            name = state.name.ifBlank { "Untitled Theme" },
            accentColor = PtfConversion.toHexRgb(state.accentArgb),
            iconColor = when (val c = state.iconColor) {
                IconColorChoice.Auto -> PfpThemeManifest.ICON_COLOR_AUTO
                is IconColorChoice.Custom -> PtfConversion.toHexRgb(c.argb)
            },
            textColor = when (val c = state.textColor) {
                TextColorChoice.Auto -> PfpThemeManifest.ICON_COLOR_AUTO
                is TextColorChoice.Custom -> PtfConversion.toHexRgb(c.argb)
            },
            waveStyle = state.waveStyle,

            layout = state.layout.takeUnless { it == com.psplauncher.themekit.XmbLayoutSpec.DEFAULT },
            source = state.source ?: PfpThemeSource(type = PfpThemeSource.TYPE_USER_CREATED),
            created = today.toString(),
        )

    fun exportTo(file: File, renderPreview: suspend (StudioState) -> ByteArray?) = runBusy {
        val snapshot = _state.value

        val motion = snapshot.motionFile
            ?.takeIf { snapshot.wallpaperPng != null && it.isFile }
            ?.let { video ->

                MotionLimits.bundleExtensionFor(video.extension)?.let { ThemeMotion.ofFile(video, it) }
            }
        val bundle = PfpThemeBundle(
            manifest = buildManifest(snapshot),
            wallpaper = snapshot.wallpaperPng,
            preview = runCatching { renderPreview(snapshot) }.getOrNull(),

            icons = snapshot.iconOverrides.mapValues { (key, png) ->
                ThemeImage(png, snapshot.iconExtensions[key] ?: "png")
            },
            motion = motion,
        )
        runCatching { file.outputStream().use { PfpThemeCodec.write(bundle, it) } }
            .onSuccess { _state.update { it.copy(statusMessage = "Exported ${file.name}") } }
            .onFailure { e -> _state.update { it.copy(dialog = StudioDialog.Error("Export failed: ${e.message}")) } }
    }

    fun unpackPtf(file: File, outDir: File) = runBusy {
        val bytes = com.psplauncher.studio.io.SafeIo.readBytesCapped(file)
        if (bytes == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is too large to be a theme file")) }
            return@runBusy
        }
        if (com.psplauncher.themekit.PtfParser.detect(bytes) == com.psplauncher.themekit.PtfParser.Kind.CXMB) {
            _state.update { it.copy(dialog = StudioDialog.CxmbRejected) }
            return@runBusy
        }
        val dump = com.psplauncher.themekit.PtfUnpacker.unpack(bytes)
        if (dump == null) {
            _state.update { it.copy(dialog = StudioDialog.Error("${file.name} is not a PSP theme file")) }
            return@runBusy
        }
        val summary = runCatching { com.psplauncher.studio.io.PtfUnpackWriter.write(dump, outDir) }
            .getOrElse { e ->
                _state.update { it.copy(dialog = StudioDialog.Error("Unpack failed: ${e.message}")) }
                return@runBusy
            }
        _state.update {
            it.copy(
                dialog = StudioDialog.Notice(
                    "Theme unpacked",
                    buildString {
                        append("${summary.images} images")
                        if (summary.other > 0) append(" and ${summary.other} data files")
                        append(" written to ${outDir.name} (see report.txt).")
                        if (summary.failed > 0) append(" ${summary.failed} resources could not be decompressed.")
                    },
                ),
                statusMessage = "Unpacked ${file.name}: ${summary.images} images",
            )
        }
    }

    fun batchConvert(
        inputDir: File,
        outputDir: File,
        renderPreview: (com.psplauncher.themekit.PfpThemeBundle) -> ByteArray?,
    ) = runBusy {
        val summary = com.psplauncher.studio.io.BatchConverter.convertFolder(
            input = inputDir,
            output = outputDir,
            renderPreview = renderPreview,
            onProgress = { progress -> _state.update { it.copy(batchProgress = progress) } },
        )
        _state.update { it.copy(batchProgress = null, dialog = StudioDialog.BatchDone(summary)) }
    }

    fun exportIconTemplates(dir: File, rasterize: (key: String, sizePx: Int) -> ByteArray) = runBusy {
        runCatching {
            dir.mkdirs()
            for (slot in IconSlots.ALL) {
                File(dir, "${slot.key}.png").writeBytes(rasterize(slot.key, slot.templateSizePx))
            }
        }
            .onSuccess { _state.update { it.copy(statusMessage = "Templates exported to ${dir.name} (${IconSlots.ALL.size} icons)") } }
            .onFailure { e -> _state.update { it.copy(dialog = StudioDialog.Error("Template export failed: ${e.message}")) } }
    }

    internal fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(Dispatchers.IO) { block() }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    internal fun update(transform: (StudioState) -> StudioState) = _state.update(transform)
}
