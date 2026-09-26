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

@RunWith(RobolectricTestRunner::class)

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
        val size = 432
        val bitmap = render(R.drawable.psp_icon_mark, size)

        val centre = size / 2f
        val safeRadius = size * 33f / 108f
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
