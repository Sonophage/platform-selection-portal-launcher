package com.psplauncher.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.psplauncher.core.ui.R

/**
 * The launcher's typeface.
 *
 * The console this borrows its look from sets everything in SST, the humanist sans Monotype drew
 * for Sony in 2013. SST is licensable — Monotype sells it — but the licence that would let it ship
 * inside an APK is an app/OEM licence negotiated per product, and a non-commercial fan launcher
 * that deliberately bundles no Sony assets is the wrong place for it.
 *
 * Inter is the substitute: a humanist sans drawn for screens, under the SIL Open Font License, so
 * it can simply ship. The full text is in LICENSES/Inter-OFL.txt.
 *
 * ONE variable font file, not four statics. Inter's variable axis carries Thin through Black in
 * 876 KB, where four static cuts would be most of that each; [FontVariation.weight] is what picks
 * the cut. minSdk is 29, comfortably past the API 26 that variable fonts need.
 */
val InterFontFamily = FontFamily(
    interWeight(FontWeight.Light, 300),
    interWeight(FontWeight.Normal, 400),
    interWeight(FontWeight.Medium, 500),
    interWeight(FontWeight.SemiBold, 600),
    interWeight(FontWeight.Bold, 700),
)

private fun interWeight(weight: FontWeight, axis: Int): Font = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

/**
 * Material's type scale with every style moved onto [InterFontFamily].
 *
 * Sizes, line heights and letter spacing are left exactly as Material sets them. This is a
 * typeface change and nothing else: the screens size their own text against XmbLayoutSpec, and a
 * scale that also moved those numbers would relayout the app under cover of a font swap.
 */
internal fun pfpTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = InterFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InterFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = InterFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = InterFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InterFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = InterFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = InterFontFamily),
    )
}
