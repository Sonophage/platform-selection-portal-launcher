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

// ── An installed app's icon, without the tile it came on ──────────────────────
//
// The crossbar's whole language is a flat monochrome silhouette on the wallpaper. An Android app
// icon is a coloured square, and dropping one into the same slot at the same size makes it the
// only opaque saturated object on the screen. Three columns do it (Network, App Store, and any
// category holding apps), and it is the loudest thing in the app.
//
// The hardware's answer was never to tint third-party art. PSP and PS3 ICON0.PNG is a fixed
// 144x80 rectangle and the Vita masked every icon into one round-rect: the container was Sony's,
// the art stayed the publisher's. That is what this does. An adaptive icon is two layers, and the
// background layer IS the tile — drop it and Boosteroid becomes a magnifier floating on the
// wallpaper while the Play Store keeps its colours, because its foreground is the coloured
// triangle. The art is untouched; only the thing it sits on changes.
//
// Deliberately NOT a desaturate. An icon whose background layer carries the colour would become a
// grey square, which is worse than the colour was, and it would cross the line PortalIcon draws
// between "our glyph, tintable" and "their art, untouchable".

/**
 * How far outside a [size]-square an adaptive icon's foreground layer must be drawn so that its
 * safe zone fills the square (pure — unit-tested).
 *
 * An adaptive icon is authored on a 108-unit canvas of which only the central 72 are guaranteed
 * visible; the rest is bleed that the system mask crops. Drawing the foreground at its natural
 * size on no background therefore renders the art at 72/108 of the space with a transparent
 * margin, so it reads smaller than every silhouette beside it. Scaling by 108/72 puts it back.
 *
 * Returns the inset to apply on every edge, so bounds are `(-inset, -inset, size + inset, size + inset)`.
 */
fun adaptiveForegroundInset(size: Int): Int = ((size * (ADAPTIVE_CANVAS - ADAPTIVE_SAFE_ZONE)) / (2 * ADAPTIVE_SAFE_ZONE))

private const val ADAPTIVE_CANVAS = 108
private const val ADAPTIVE_SAFE_ZONE = 72

/**
 * The container this app puts third-party icon art in when it cannot take the publisher's own tile
 * off.
 *
 * One shape for all of them, which is the point: a shelf of other people's squares reads as a mess,
 * and the same shelf masked into one silhouette reads as a shelf. Shared rather than private to a
 * screen because the crossbar rows and the drill grid must not pick different roundings for the
 * same icon.
 */
val AppIconContainerShape = RoundedCornerShape(14.dp)

/**
 * The app's icon art, with its background tile removed when there is one to remove.
 *
 * `AdaptiveIconDrawable` is API 26 and this app's minSdk is 29, so there is no version guard: an
 * icon either is adaptive, and its background layer is dropped, or it is a legacy bitmap that
 * never had a separate background to drop. A failure returns null and the row draws no icon,
 * which the caller already handles.
 *
 * **Stripping is not enough on its own, and the caller must still apply [AppIconContainerShape].**
 * Measured on the device: Artemis is adaptive and comes back as a bare white pinwheel, exactly as
 * intended. Boosteroid ships only `ic_launcher.png` with its purple tile baked into the bitmap, so
 * there is nothing to separate. The Play Store is adaptive and *still* renders a white square,
 * because its foreground layer contains the tile — and no API distinguishes that from a foreground
 * that is only art. So the container is what actually guarantees one silhouette, and the strip is
 * what makes the good citizens look native. Clipping costs a stripped icon nothing: its corners
 * are already transparent.
 */
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
