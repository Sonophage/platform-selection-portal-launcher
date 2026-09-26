package com.psplauncher.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.psplauncher.core.ui.R

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
