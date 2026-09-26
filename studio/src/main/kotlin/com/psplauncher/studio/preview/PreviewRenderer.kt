package com.psplauncher.studio.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.psplauncher.studio.StudioState
import com.psplauncher.studio.io.ImageCodecs
import com.psplauncher.studio.io.PtfConversion
import com.psplauncher.themekit.PfpThemeBundle
import java.awt.EventQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalComposeUiApi::class)
object PreviewRenderer {
    private const val DESIGN_WIDTH_DP = 960f

    private fun <T> onAwtThread(block: () -> T): T =
        if (EventQueue.isDispatchThread()) block() else runBlocking(Dispatchers.Swing) { block() }

    fun renderPreviewPng(state: StudioState, widthPx: Int = 1280, heightPx: Int = 720): ByteArray = onAwtThread {
        ImageComposeScene(
            width = widthPx,
            height = heightPx,
            density = Density(widthPx / DESIGN_WIDTH_DP),
        ).use { scene ->

            val model = state.copy(previewMode = com.psplauncher.studio.PreviewMode.HOME).toPreviewModel()
            scene.setContent { XmbFrame(model) }
            scene.render(nanoTime = 0L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
    }

    fun renderPreviewPng(bundle: PfpThemeBundle): ByteArray {
        val state = StudioState(
            name = bundle.manifest.name,
            accentArgb = PtfConversion.parseHexRgb(bundle.manifest.accentColor)
                ?: PtfConversion.DEFAULT_ACCENT,
            wallpaperPng = bundle.wallpaper,
            wallpaperBitmap = bundle.wallpaper?.let(ImageCodecs::toImageBitmap),
            waveStyle = bundle.manifest.waveStyle,
            layout = bundle.manifest.layout
                ?.let(com.psplauncher.themekit.XmbLayoutSpecCodec::sanitize)
                ?: com.psplauncher.themekit.XmbLayoutSpec.DEFAULT,
        )
        return renderPreviewPng(state)
    }

    fun rasterizeDefaultIcon(key: String, sizePx: Int): ByteArray = onAwtThread {
        ImageComposeScene(width = sizePx, height = sizePx).use { scene ->
            scene.setContent {
                Image(
                    painter = StudioIconSet.defaultPainter(key),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            scene.render(nanoTime = 0L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
    }
}
