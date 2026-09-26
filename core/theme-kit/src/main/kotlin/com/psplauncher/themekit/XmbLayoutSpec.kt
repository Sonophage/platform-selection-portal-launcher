package com.psplauncher.themekit

import kotlinx.serialization.Serializable

@Serializable
data class XmbLayoutSpec(

    val barTopFraction: Float = 0.11f,

    val contentTopPaddingDp: Float = 20f,

    val categoryIconSelectedDp: Float = 72f,

    val categoryIconDp: Float = 56f,

    val itemIconDp: Float = 62f,
    val itemIconSlotDp: Float = 74f,

    val itemTextSelectedSp: Float = 22f,
    val itemTextSp: Float = 18f,

    val itemTextStartGapDp: Float = 14f,

    val leftAnchorExtraDp: Float = 6f,

    val previousItemRiseRows: Float = 0.5f,
) {
    companion object {
        val DEFAULT = XmbLayoutSpec()
    }
}
