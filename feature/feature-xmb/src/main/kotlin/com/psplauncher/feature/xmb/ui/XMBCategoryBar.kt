package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.ui.icons.CategoryIconGlyph
import com.psplauncher.themekit.XmbLayoutSpec
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// Classic PSP blue theme: the active category's label is crisp white with a dark glow. Labels on
// other categories are hidden entirely (alpha 0) until the user navigates to them — the bar stays
// uncluttered and only the focused category announces itself.
private val SelectedIcon = Color.White

/**
 * What every unselected CATEGORY fades to when "Fade By Distance" is off. Unchanged from before it.
 *
 * Deliberately not the same number as the item column's, which is 0.68 and always was: a caticon
 * is a lone glyph on a busy wallpaper and a row is a glyph beside its own label, so they have never
 * needed the same amount of dimming to read as unselected. Named apart from the row's constant so
 * the difference stays visible instead of looking like one of them drifted.
 */
private const val FlatUnfocusedIconAlpha = 0.58f
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val LabelInactive: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.inactive
private val SelectedLabelShadow = Shadow(
    color = Color(0x73001627),
    offset = Offset.Zero,
    blurRadius = 12f,
)

// Width of a single category slot. Exposed so the subitem column (XMBShell) can
// align its left edge to the selected category's slot — the XMB crossbar.
internal val CategorySlotWidth = 124.dp
private val ItemSlotWidth = CategorySlotWidth

// The XMB is left-anchored: the selected category slot (and the subitem column below it) sit
// at this fixed left offset instead of centering, keeping the right side clear for the context
// menu. It's exactly one slot width so the *previous* category tiles fully into x=0..slot with
// no partial "poke", and the category before that lands fully off-screen. At the last category
// you therefore see exactly the previous + selected — the last two, and only them — with no
// clipping. XMBShell reads this so the crossbar and its subitems stay on the same vertical line.
internal val XmbLeftAnchor = CategorySlotWidth + XmbLayoutSpec.DEFAULT.leftAnchorExtraDp.dp

/**
 * The categories the bar actually lays out. Drilled in, the XMB hides every category to the RIGHT
 * of the active one so focus collapses onto the active column (PSP second-level behaviour) — and
 * they are DROPPED here rather than rendered empty inside the row.
 *
 * That distinction is the whole point. An emptied `items` entry still occupies a slot, so the
 * trailing phantoms left the LazyRow holding just about exactly the scroll extent
 * `scrollToItem(selectedIndex)` asks for and no headroom: any re-measure clamped the scroll short
 * and the bar came to rest a whole slot off, sliding the selected caticon into the game column
 * (the flyout-then-resume corruption). Dropping them keeps the extent honest, and the resulting
 * size change re-seats the bar on every drill in/out for free.
 *
 * `take` keeps the surviving indices aligned with [categories], so [selectedIndex], the selected
 * flag and the click callbacks all still mean the same thing. A selection that isn't a real index
 * yet (nothing loaded, -1) hides nothing — an empty bar is worse than an unfiltered one.
 */
internal fun visibleCategories(
    categories: List<Category>,
    selectedIndex: Int,
    drilledIn: Boolean,
): List<Category> =
    if (drilledIn && selectedIndex in categories.indices) categories.take(selectedIndex + 1)
    else categories

@Composable
fun XMBCategoryBar(
    categories: List<Category>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCategoryLongPress: (Int) -> Unit = {},
    // When drilled into a sub-item, the XMB hides every category to the RIGHT of the active one so
    // the focus collapses onto the active column (PSP second-level behaviour).
    drilledIn: Boolean = false,
    // "Fade By Distance" (Display ▸ Appearance): when true, unselected category icons dim by how
    // many slots they sit from the cursor rather than all sharing one alpha. Default true
    // = today's dimming. (IconLegibility rides LocalIconLegibility, no parameter needed.)
    fadeByDistance: Boolean = true,
    // Whether the selected category's GIF icon may animate (battery saver / overlays gate it).
    iconAnimatingAllowed: Boolean = false,
) {
    val listState = rememberLazyListState()

    // The slots the row actually lays out (see [visibleCategories] for why drilled-in categories are
    // dropped rather than emptied).
    val rowCategories = remember(categories, drilledIn, selectedIndex) {
        visibleCategories(categories, selectedIndex, drilledIn)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Left-anchored: the selected slot rests at XmbLeftAnchor, shifted by the live layout-adjust
        // offset so the caticon bar tracks the item column when the cross is nudged left/right.
        val anchor = (XmbLeftAnchor + LocalXmbHorizontalShift.current).coerceAtLeast(0.dp)
        val endPadding = (maxWidth - anchor - ItemSlotWidth).coerceAtLeast(24.dp)

        // Seat the selected slot on the bar's first composition — including every time the XMB
        // foreground re-appears after a fullscreen menu closes (music browser, app drawer, Settings)
        // — so the bar never visibly scrolls in from the start (the old "snap back" on close).
        //
        // Keyed on the measured geometry as well as the selection. The cross runs under its own
        // LocalDensity, rebuilt whenever the per-form-factor layout adjustment resolves — and that
        // arrives asynchronously with the theme load, so coming back to the app re-measures this row.
        // A re-measure is exactly when the scroll can land short, so it has to re-seat here; keying
        // on the selection alone left the bar wrong until the next category press.
        var lastTarget by remember { mutableIntStateOf(-1) }
        LaunchedEffect(selectedIndex, rowCategories.size, maxWidth, anchor) {
            if (rowCategories.isEmpty()) return@LaunchedEffect
            val target = selectedIndex.coerceIn(0, rowCategories.lastIndex)
            // A real category change glides. A re-seat that lands on the SAME slot is geometry
            // catching up (first composition, a resume, a drill in/out), and must snap: animating
            // there is the visible scroll-in this effect exists to prevent.
            if (lastTarget >= 0 && target != lastTarget) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
            lastTarget = target
        }

        LazyRow(
            state = listState,
            // Selection-driven only: the bar auto-scrolls to the selected slot (above), and touch
            // swipes are handled by the home-screen gesture (step category), so user scrolling is
            // disabled to keep the bar from drifting out of sync with the selection.
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = anchor, end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(rowCategories, key = { _, category -> category.id }) { index, category ->
                XMBCategoryItem(
                    category = category,
                    isSelected = index == selectedIndex,
                    // Steps from the cursor along the bar. Drilled in, visibleCategories() has
                    // already dropped everything to the RIGHT of the selection and kept the
                    // surviving indices aligned, so this is never negative there and the ramp
                    // simply applies to whatever is still on the bar — no second rule for the
                    // drilled-in case, and the PSP behaviour it relies on is untouched.
                    distance = kotlin.math.abs(index - selectedIndex),
                    onClick = { onCategorySelected(index) },
                    onLongPress = { onCategoryLongPress(index) },
                    fadeByDistance = fadeByDistance,
                    iconAnimatingAllowed = iconAnimatingAllowed,
                    modifier = Modifier.width(ItemSlotWidth),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XMBCategoryItem(
    category: Category,
    isSelected: Boolean,
    distance: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    fadeByDistance: Boolean,
    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // ONE SIZE FOR EVERY CATEGORY. The selected slot used to grow to categoryIconSelectedDp.
    //
    // The cursor does not live on the bar — left and right change category and the column follows
    // at once, so there is no state in which the crossbar is the thing being pointed at. It was
    // nonetheless drawing the full focus treatment: bigger, brighter, and (briefly) glowing, at
    // the same moment the item column below was drawing the same three things around the row the
    // cursor actually was on. Two things claiming the cursor, and only one of them had it.
    //
    // The bar keeps the two cues that say "this column is open" rather than "you are here": it is
    // the only slot at full brightness, and the only one showing its label.
    val iconSize = XmbLayoutSpec.DEFAULT.categoryIconDp.dp
    val itemAlpha by animateFloatAsState(
        // "Fade By Distance" (Display ▸ Appearance): off, every unselected slot dims the same
        // amount, which is what this always did. On, it dims by how far it sits from the cursor.
        targetValue = when {
            isSelected -> 1f
            fadeByDistance -> XmbDim.ranked(distance)
            else -> FlatUnfocusedIconAlpha
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbCategoryAlpha",
    )
    // Only the active category shows its label; others stay hidden (alpha 0) until navigated to.
    // The label keeps its slot so icons never shift when labels fade in/out.
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbCategoryLabelAlpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(112.dp)
            // No ripple — the XMB shows focus with its own caticon scale/alpha, and the Android
            // highlight rectangle broke the PSP look (see the matching change in XMBItemList).
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(top = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(82.dp)
                .alpha(itemAlpha),
        ) {
            // The selected category's GIF (if the slot holds one) animates exactly while it is
            // the focused column — the same gate the item rows obey.
            androidx.compose.runtime.CompositionLocalProvider(
                com.psplauncher.core.ui.icons.LocalIconAnimating provides
                    (isSelected && iconAnimatingAllowed),
            ) {
            // All category icons resolve through the shared core-ui catalog (catbar_* column
            // glyphs and sysicon_* console art). The open column reads by alpha and by its label
            // alone — no halo here, and no size change; see [iconSize].
            CategoryIconGlyph(
                iconKey = category.iconKey,
                contentDescription = category.name,
                modifier = Modifier.size(iconSize),
            )
            }
        }

        Text(
            text = category.name,
            color = if (isSelected) SelectedIcon else LabelInactive,
            fontSize = if (isSelected) 15.sp else 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            style = if (isSelected) TextStyle(shadow = SelectedLabelShadow) else TextStyle.Default,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .alpha(labelAlpha),
        )
    }
}
