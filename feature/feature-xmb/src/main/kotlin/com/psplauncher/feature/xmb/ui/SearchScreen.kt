package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursor
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.feature.xmb.viewmodel.SearchState
import com.psplauncher.feature.xmb.viewmodel.isInstalledApp
import com.psplauncher.core.ui.components.PfpMediaCard
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MAX_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MIN_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.XMBItemType
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

private val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary

private val SecondaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary
private val CoverPlaceholder = Color(0xFF1B1B27)

@Composable
fun SearchScreen(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onActivateAt: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,

    onColumnsMeasured: (Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()

    var columns by remember { mutableIntStateOf(SEARCH_GRID_COLUMNS) }
    LaunchedEffect(state.selectedIndex, state.scrollToTopToken, columns) {
        if (state.rows.isNotEmpty()) {
            val target = (state.selectedIndex - columns).coerceIn(0, state.rows.lastIndex)
            gridState.animateScrollToItem(target)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val pfpColors = LocalPFPColors.current
    Box(
        modifier = modifier
            .fillMaxSize()

            .background(
                Brush.verticalGradient(
                    0f to pfpColors.backgroundTop.copy(alpha = 0.72f),
                    1f to pfpColors.backgroundBottom.copy(alpha = 0.90f),
                )
            ),
    ) {
        val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)

                .padding(
                    top = StatusStripHeight + if (imeUp) 10.dp else 24.dp,
                    bottom = if (imeUp) 10.dp else 24.dp + HintBarHeight,
                ),
        ) {
            if (!imeUp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                ) {
                    Text("◀", color = SecondaryText, fontSize = 18.sp, modifier = Modifier.padding(end = 16.dp))
                    Column {
                        Text(state.scope.label, color = PrimaryText, fontSize = 22.sp)
                        Text(state.scope.hint, color = SecondaryText, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
            }

            var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
            LaunchedEffect(state.query) {
                if (state.query != field.text) {
                    field = TextFieldValue(state.query, TextRange(state.query.length))
                }
            }
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    onQueryChange(it.text)
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SecondaryText) },
                placeholder = { Text("Search", color = SecondaryText.copy(alpha = 0.7f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedBorderColor = menuCursorEdge(),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = menuCursorEdge(),
                    focusedContainerColor = Color(0x22FFFFFF),
                    unfocusedContainerColor = Color(0x14FFFFFF),
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            Spacer(Modifier.height(12.dp))

            Column(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
                val empty = state.rows.singleOrNull()?.takeIf { it.type == XMBItemType.EMPTY }
                if (empty != null) {
                    SearchResultRow(row = empty, selected = false, onClick = {})
                    Spacer(Modifier.weight(1f))
                } else {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val measured = ((maxWidth + SEARCH_TILE_GAP) / (SEARCH_TILE_TARGET_WIDTH + SEARCH_TILE_GAP))
                            .toInt()
                            .coerceIn(SEARCH_GRID_MIN_COLUMNS, SEARCH_GRID_MAX_COLUMNS)
                        columns = measured

                        LaunchedEffect(measured) { onColumnsMeasured(measured) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(measured),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(SEARCH_TILE_GAP),
                            verticalArrangement = Arrangement.spacedBy(SEARCH_TILE_GAP),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                PfpMediaCard(
                                    title = row.title,
                                    art = row.shelfCoverArt,
                                    subtitle = row.subtitle,

                                    initialOnly = row.isInstalledApp,
                                    focused = index == state.selectedIndex,
                                    onClick = { onActivateAt(index) },
                                    width = Dp.Unspecified,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!imeUp) {
            PfpHintBar(
                items = listOf(
                    ControllerPromptItem(GamepadAction.BACK, "Back"),
                    ControllerPromptItem(GamepadAction.SELECT, "Open"),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),

                onAction = { action ->
                    when (action) {
                        GamepadAction.BACK -> onBack()
                        GamepadAction.SELECT ->
                            state.selectedIndex.takeIf { it in state.rows.indices }?.let(onActivateAt)
                        else -> Unit
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResultRow(row: XMBItem, selected: Boolean, onClick: () -> Unit) {
    val clickable = row.type != XMBItemType.EMPTY

    val cursored = selected && clickable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)

            .menuCursor(cursored)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (clickable) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(CoverPlaceholder),
            ) {
                row.coverUri?.let {
                    AsyncImage(
                        model = rememberArtworkModel(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.padding(end = 12.dp))
        }
        Column {
            Text(
                text = row.title,
                color = PrimaryText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, color = SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val SEARCH_TILE_TARGET_WIDTH = 93.dp

private val SEARCH_TILE_GAP = 14.dp
