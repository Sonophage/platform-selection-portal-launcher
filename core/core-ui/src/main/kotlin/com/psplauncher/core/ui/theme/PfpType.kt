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
 * Instrument Sans is the substitute, under the SIL Open Font License, so it can simply ship. The
 * full text is in LICENSES/InstrumentSans-OFL.txt. It replaced Inter as part of the XMB redesign;
 * the design bundle asks for it at weights 400, 500, 600 and 700.
 *
 * ONE variable font file, not four statics. The wght axis carries all four cuts in 190 KB;
 * [FontVariation.weight] is what picks one. minSdk is 29, comfortably past the API 26 that
 * variable fonts need.
 *
 * NO LIGHT. Instrument Sans's wght axis floors at 400, where Inter's ran down to 100, so there is
 * no 300 to declare. Eleven call sites still ask for [FontWeight.Light] and Compose resolves each
 * of them to the 400 cut, because its matching rule looks below the requested weight first and
 * finds nothing. That is a real, deliberate change in how those eleven labels render, not an
 * oversight: the alternative is a second font file for one weight the design never asked for.
 */
val InstrumentSansFontFamily = FontFamily(
    instrumentSansWeight(FontWeight.Normal, 400),
    instrumentSansWeight(FontWeight.Medium, 500),
    instrumentSansWeight(FontWeight.SemiBold, 600),
    instrumentSansWeight(FontWeight.Bold, 700),
)

private fun instrumentSansWeight(weight: FontWeight, axis: Int): Font = Font(
    resId = R.font.instrument_sans_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

/**
 * Material's type scale with every style moved onto [InstrumentSansFontFamily].
 *
 * Sizes, line heights and letter spacing are left exactly as Material sets them. This is a
 * typeface change and nothing else: the screens size their own text against XmbLayoutSpec, and a
 * scale that also moved those numbers would relayout the app under cover of a font swap.
 */
internal fun pfpTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InstrumentSansFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InstrumentSansFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InstrumentSansFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InstrumentSansFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InstrumentSansFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = InstrumentSansFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InstrumentSansFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = InstrumentSansFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = InstrumentSansFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InstrumentSansFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InstrumentSansFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = InstrumentSansFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InstrumentSansFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = InstrumentSansFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = InstrumentSansFontFamily),
    )
}
