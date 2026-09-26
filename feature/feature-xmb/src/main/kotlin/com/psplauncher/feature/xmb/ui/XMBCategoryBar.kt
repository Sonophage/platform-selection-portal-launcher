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

private val SelectedIcon = Color.White

private const val FlatUnfocusedIconAlpha = 0.58f

private val LabelInactive: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.inactive
private val SelectedLabelShadow = Shadow(
    color = Color(0x73001627),
    offset = Offset.Zero,
    blurRadius = 12f,
)

internal val CategorySlotWidth = 124.dp
private val ItemSlotWidth = CategorySlotWidth

internal val XmbLeftAnchor = CategorySlotWidth + XmbLayoutSpec.DEFAULT.leftAnchorExtraDp.dp

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

    drilledIn: Boolean = false,

    fadeByDistance: Boolean = true,

    iconAnimatingAllowed: Boolean = false,
) {
    val listState = rememberLazyListState()

    val rowCategories = remember(categories, drilledIn, selectedIndex) {
        visibleCategories(categories, selectedIndex, drilledIn)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val anchor = (XmbLeftAnchor + LocalXmbHorizontalShift.current).coerceAtLeast(0.dp)
        val endPadding = (maxWidth - anchor - ItemSlotWidth).coerceAtLeast(24.dp)

        var lastTarget by remember { mutableIntStateOf(-1) }
        LaunchedEffect(selectedIndex, rowCategories.size, maxWidth, anchor) {
            if (rowCategories.isEmpty()) return@LaunchedEffect
            val target = selectedIndex.coerceIn(0, rowCategories.lastIndex)

            if (lastTarget >= 0 && target != lastTarget) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
            lastTarget = target
        }

        LazyRow(
            state = listState,

            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = anchor, end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(rowCategories, key = { _, category -> category.id }) { index, category ->
                XMBCategoryItem(
                    category = category,
                    isSelected = index == selectedIndex,

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
    val iconSize = XmbLayoutSpec.DEFAULT.categoryIconDp.dp
    val itemAlpha by animateFloatAsState(

        targetValue = when {
            isSelected -> 1f
            fadeByDistance -> XmbDim.ranked(distance)
            else -> FlatUnfocusedIconAlpha
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbCategoryAlpha",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbCategoryLabelAlpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(112.dp)

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
            androidx.compose.runtime.CompositionLocalProvider(
                com.psplauncher.core.ui.icons.LocalIconAnimating provides
                    (isSelected && iconAnimatingAllowed),
            ) {
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
