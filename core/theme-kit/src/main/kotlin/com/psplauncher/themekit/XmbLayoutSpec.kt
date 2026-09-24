package com.psplauncher.themekit

import kotlinx.serialization.Serializable

/**
 * Per-theme XMB geometry. The defaults are the values pixel-tuned against a real-PSP theme
 * capture (see commit "XMB: align cross layout with authentic PSP theme geometry"); a theme
 * whose wallpaper draws its own cross/bands can override them so the XMB lands on the art.
 *
 * The Theme Studio companion can derive [barTopFraction] automatically by detecting the
 * wallpaper's dark cross-band, the same measurement used to tune these defaults.
 */
@Serializable
data class XmbLayoutSpec(
    /** Top of the category bar as a fraction of the XMB content height. */
    val barTopFraction: Float = 0.11f,
    /** Content inset below the status strip, dp. */
    val contentTopPaddingDp: Float = 20f,
    /**
     * NO LONGER READ BY ANYTHING THAT DRAWS. Kept so existing theme files still parse.
     *
     * The crossbar used to grow its selected slot to this. It does not any more: the cursor is
     * never on the bar, so the bar magnifying one slot competed with the item column magnifying
     * the row the cursor was actually on. Both XMBCategoryBar and the Studio preview now size
     * every category at [categoryIconDp].
     *
     * It stays in the format because theme files on disk carry the key and dropping it would make
     * them fail to parse. A theme author who sets it will see no effect, which is why this says so
     * here rather than leaving the field looking live.
     */
    val categoryIconSelectedDp: Float = 72f,
    /** Category icon size, dp — every category, selected or not. */
    val categoryIconDp: Float = 56f,
    /** First-level item leading icon glyph / centering slot, dp. */
    val itemIconDp: Float = 62f,
    val itemIconSlotDp: Float = 74f,
    /** First-level item title text, sp: selected / unselected. */
    val itemTextSelectedSp: Float = 22f,
    val itemTextSp: Float = 18f,
    /** Gap between the item icon column and its label, dp. */
    val itemTextStartGapDp: Float = 14f,
    /** Extra left shift of the whole cross beyond one category slot width, dp. */
    val leftAnchorExtraDp: Float = 6f,
    /** How far the dissolving previous item rises above the bar, in row heights. */
    val previousItemRiseRows: Float = 0.5f,
) {
    companion object {
        val DEFAULT = XmbLayoutSpec()
    }
}
