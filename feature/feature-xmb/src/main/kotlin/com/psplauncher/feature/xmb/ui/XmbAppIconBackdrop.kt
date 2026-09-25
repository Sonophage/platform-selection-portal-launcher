package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.psplauncher.core.ui.icons.appIconBitmap

// ── An app's own icon as its backdrop ─────────────────────────────────────────
//
// A game brings artwork; an app brings a 48dp icon and nothing else, so the space behind a
// focused app row was the bare wallpaper. This fills it with the one image the app does have,
// blown up until it is colour rather than picture.
//
// THE BLUR IS A DOWNSCALE, NOT Modifier.blur. That modifier is RenderEffect, which does nothing
// below API 31 — and minSdk here is 29, so on Android 10 and 11 it would have left a sharp icon
// stretched across the screen and looked like a bug rather than a missing effect. XmbGlow already
// refused it for the same reason. Decoding the icon at [SOURCE_PX] and letting the GPU's bilinear
// filter smear it back up to full screen costs one small bitmap, works on every API this app
// supports, and cannot silently degrade: if the filtering were ever lost the result would be
// visibly blocky rather than invisibly absent.
//
// The foreground layer is what [appIconBitmap] returns for an adaptive icon, so this is the app's
// artwork without the launcher-shaped mask around it.

/**
 * What goes behind the focused row.
 *
 * Two sources, one decision. It is a sealed type rather than two nullable fields because the
 * Crossfade in the shell animates on ONE target: with two, moving from a game to an app would
 * have been a fade-out and a fade-in of different layers rather than one swap, and the pair would
 * also have needed a rule about which wins — written in one place and read in another.
 */
sealed interface XmbBackdrop {
    /** Real artwork: a decoded uri from [XMBItem.backdropArt]. */
    data class Art(val uri: String) : XmbBackdrop

    /** An app with no artwork at all, standing in with its own icon. */
    data class AppIcon(val packageName: String) : XmbBackdrop
}

/** Small enough that the upscale is the blur; large enough to keep more than one colour. */
private const val SOURCE_PX = 24

/**
 * Low, and lower than the game backdrop's.
 *
 * A game's artwork is a picture the user recognises and wants to see; this is a 24-pixel smear of
 * one icon, and at full strength it reads as the theme having changed colour rather than as the
 * row having a background. The legibility scrim the shell draws over this sits on top, so the
 * crossbar keeps the contrast it has over any other backdrop.
 */
private const val BACKDROP_ALPHA = 0.55f

@Composable
fun XmbAppIconBackdrop(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Keyed on the package: the cursor crossing a shelf of apps re-reads only when the app under
    // it changes, and PackageManager is not asked again for a row that is already on screen.
    val icon = remember(packageName) { context.appIconBitmap(packageName, sizePx = SOURCE_PX) } ?: return

    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // High quality on the way up is the whole effect — Low would nearest-neighbour it into
        // 24 visible squares.
        filterQuality = FilterQuality.High,
        modifier = modifier.fillMaxSize().alpha(BACKDROP_ALPHA),
    )
}
