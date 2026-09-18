package com.psplauncher.core.ui.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.ui.R
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The two brand marks, and the one way each fails silently on a device while looking fine in a
 * preview.
 *
 *  - `psp_icon_mark` is the adaptive icon's foreground AND its Android 13+ monochrome layer. Every
 *    launcher mask crops to at most the SAFE ZONE — the 66dp circle inside the 108dp canvas — so
 *    ink outside it is ink the user never sees. A re-cut that fills the canvas still builds, still
 *    previews correctly, and loses the ends of the wordmark on a round-mask launcher.
 *
 *  - `psp_logo` is the boot mark, drawn through [PortalIcon], which tints with `BlendMode.SrcIn`:
 *    the ALPHA carries the shape and the tint replaces the colour. So the asset has to be a
 *    silhouette on transparency. Swap in a flat opaque image — a JPEG, say, which cannot hold
 *    alpha at all — and SrcIn paints a solid tinted rectangle over the boot wave. Nothing throws.
 */
@RunWith(RobolectricTestRunner::class)
// Legacy graphics is a no-op canvas: every draw would report zero ink and these tests
// would pass while proving nothing. NATIVE actually rasterises.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PspLogoTest {

    private fun render(resId: Int, size: Int): Bitmap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = requireNotNull(ContextCompat.getDrawable(context, resId)) { "did not load" }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(it))
        }
    }

    @Test
    fun `the icon mark draws, and all of its ink lands inside the adaptive icon safe zone`() {
        val size = 432                       // the 108dp canvas at 4x, so thin strokes still land
        val bitmap = render(R.drawable.psp_icon_mark, size)

        val centre = size / 2f
        val safeRadius = size * 33f / 108f   // the 66dp safe circle, at the bitmap's scale
        var ink = 0
        var outside = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                ink++
                if (hypot(x + 0.5f - centre, y + 0.5f - centre) > safeRadius) outside++
            }
        }

        assertTrue("psp_icon_mark drew nothing", ink > 0)
        assertTrue(
            "$outside of $ink drawn pixels fall outside the adaptive icon's safe circle, " +
                "so a round-mask launcher would crop them",
            outside == 0,
        )
    }

    @Test
    fun `the boot mark is a silhouette, so PortalIcon's SrcIn tint has a shape to fill`() {
        val size = 256
        val bitmap = render(R.drawable.psp_logo, size)

        // Not `alpha == 255`: the supplied art tops out at 254, which SrcIn renders identically.
        // "Solid enough to be the shape" is the property that matters, so the threshold is a band.
        var solid = 0
        var transparent = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val a = Color.alpha(bitmap.getPixel(x, y))
                if (a == 0) transparent++ else if (a > 200) solid++
            }
        }
        val total = size * size

        assertTrue("psp_logo drew nothing at all", solid > 0)
        assertTrue(
            "psp_logo is $transparent/$total transparent — an asset with no transparent ground is " +
                "not a silhouette, and PortalIcon's SrcIn tint would paint a solid block",
            transparent > total / 4,
        )
    }
}
