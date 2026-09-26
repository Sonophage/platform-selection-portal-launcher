package com.psplauncher.core.ui.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

fun adaptiveForegroundInset(size: Int): Int = ((size * (ADAPTIVE_CANVAS - ADAPTIVE_SAFE_ZONE)) / (2 * ADAPTIVE_SAFE_ZONE))

private const val ADAPTIVE_CANVAS = 108
private const val ADAPTIVE_SAFE_ZONE = 72

val AppIconContainerShape = RoundedCornerShape(14.dp)

fun Context.appIconBitmap(packageName: String, sizePx: Int = 192): ImageBitmap? =
    runCatching {
        val icon: Drawable = packageManager.getApplicationIcon(packageName)
        val layer = (icon as? AdaptiveIconDrawable)?.foreground
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (layer != null) {
            val inset = adaptiveForegroundInset(sizePx)
            layer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            layer.draw(canvas)
        } else {
            icon.setBounds(0, 0, sizePx, sizePx)
            icon.draw(canvas)
        }
        bmp.asImageBitmap()
    }.getOrNull()
