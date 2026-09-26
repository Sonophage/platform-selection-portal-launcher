package com.psplauncher.core.ui.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Phone - Landscape", device = "spec:width=891dp,height=411dp,orientation=landscape", group = "Devices")
@Preview(name = "Tablet - Landscape", device = "spec:width=1280dp,height=800dp,orientation=landscape", group = "Devices")
@Preview(name = "Desktop (XMB Baseline)", widthDp = 960, heightDp = 540, group = "Devices")
annotation class DevicePreviews

@Preview(name = "Small Font", fontScale = 0.85f, group = "Accessibility")
@Preview(name = "Normal Font", fontScale = 1.0f, group = "Accessibility")
@Preview(name = "Large Font", fontScale = 1.15f, group = "Accessibility")
@Preview(name = "Extra Large Font", fontScale = 1.3f, group = "Accessibility")
annotation class FontScalePreviews

@DevicePreviews
@FontScalePreviews
annotation class CombinedPreviews
