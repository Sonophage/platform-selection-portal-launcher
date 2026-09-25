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
import androidx.compose.ui.text.TextStyle
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
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.XMBItemType
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// Resolved per theme rather than fixed white: on a pale scheme the selected row was the
// brightest thing on an already bright wallpaper. See PFPTheme.
private val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val SecondaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary
private val CoverPlaceholder = Color(0xFF1B1B27)

/**
 * The library search screen: one text field and a list of what matches.
 *
 * Deliberately the same shape as MusicBrowserScreen rather than a new idea -- a fullscreen list
 * over the XMB's own background, a header you can tap to leave, and the controller prompt bar at
 * the bottom. A second visual language for "a searchable list" would be a worse answer to a
 * question this app has already answered.
 *
 * Stateless: it renders [state] and forwards intents. The cursor lives in the ViewModel, because
 * the D-pad reaches the ViewModel and not this composable.
 */


@Composable
fun SearchScreen(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onActivateAt: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.selectedIndex, state.scrollToTopToken) {
        if (state.rows.isNotEmpty()) {
            // A ROW back, not an item back. The list scrolled to selectedIndex - 1 to keep one
            // entry visible above the cursor; in a grid that is one column to the left, which is
            // usually the same row and scrolls nothing.
            val target = (state.selectedIndex - SEARCH_GRID_COLUMNS).coerceIn(0, state.rows.lastIndex)
            gridState.animateScrollToItem(target)
        }
    }

    // Opens with the field focused and the keyboard up. A search screen that needs you to reach
    // out and tap the box before typing has wasted the press that opened it.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val pfpColors = LocalPFPColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            // The same scrim as MusicBrowserScreen, which this screen says it is shaped after, and
            // the same solved pair the settings scaffold uses. It was 0.78/0.93 here: darker than
            // the sibling it copies, for no stated reason, which is how one screen ends up reading
            // as a different app from the one next to it.
            .background(
                Brush.verticalGradient(
                    0f to pfpColors.backgroundTop.copy(alpha = 0.72f),
                    1f to pfpColors.backgroundBottom.copy(alpha = 0.90f),
                )
            ),
    ) {
        // The header stands down while the keyboard is up, and that is not a nicety.
        //
        // On the 1920x1080 handheld this screen is 462dp tall and the IME takes about 254 of them.
        // Header, field and padding took the rest, so the results list was laid out at zero height:
        // you typed "final", four Final Fantasy games matched, and the screen showed nothing at all
        // until you dismissed the keyboard — which the screen never asks you to do, because it
        // opens with the field focused on purpose. Measured on the device, not reasoned about.
        //
        // Giving the title and the hint back is worth roughly two result rows, and neither is
        // needed mid-query: you know what you are searching, you are looking at what you typed.
        val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
                // Room for the two bands the shell draws over this screen: the status strip above
                // and the hint bar below. Neither is in this column -- they are pinned to the
                // screen edges, so they line up with the App Drawer's and the crossbar's instead
                // of being inset by this screen's own 40dp gutter.
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

            // A TextFieldValue, not a String, so the CURSOR can be put where the text is.
            //
            // Type-to-search opens this screen with the character that opened it already in the
            // query. With a String-valued field the selection stays at 0, so the next letter is
            // inserted BEFORE the first one and typing "skyr" produces "kyrs" — which is what it
            // did. The effect below only fires when the text changed from outside, so ordinary
            // typing keeps the caret it already has.
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

            // imePadding here, around the results, rather than on the whole screen: the keyboard
            // is up the entire time this screen is on, so that is not a transient state, it is the
            // layout, and the results get every pixel above it. The prompts are not in here any
            // more — they are pinned to the screen edge and are not drawn at all while the IME is
            // up, so they have nothing to pad around.
            //
            Column(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
                // One mixed grid, best matches first — not grouped by medium. A list showed four
                // results and left the right half of the screen empty; the grid shows five to a
                // row, so a search for "final" is answered without scrolling.
                //
                // The EMPTY row (the "nothing found" placeholder) is still a full-width line: a
                // message is not a result and putting it in a cell would look like one.
                val empty = state.rows.singleOrNull()?.takeIf { it.type == XMBItemType.EMPTY }
                if (empty != null) {
                    SearchResultRow(row = empty, selected = false, onClick = {})
                    Spacer(Modifier.weight(1f))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(SEARCH_GRID_COLUMNS),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                            PfpMediaCard(
                                title = row.title,
                                art = row.shelfCoverArt,
                                subtitle = row.subtitle,
                                // An app will never have art, so its tile is permanent: a letter
                                // rather than its own name repeated under itself.
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

        // The prompt bar drops out while the keyboard is up, for the same reason the header does.
        // It costs a result row to say "A Open, B Back" next to a keyboard that is already showing
        // its own confirm key, and on this screen a row is most of what there is.
        if (!imeUp) {
            PfpHintBar(
                items = listOf(
                    ControllerPromptItem(GamepadAction.BACK, "Back"),
                    ControllerPromptItem(GamepadAction.SELECT, "Open"),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SearchResultRow(row: XMBItem, selected: Boolean, onClick: () -> Unit) {
    val clickable = row.type != XMBItemType.EMPTY
    // The cursor only ever sits on something that answers. "Type to search" and "No matches" are
    // EMPTY rows at index 0, so painting the fill on `selected` alone drew the brightest band on
    // the screen around a row that cannot be pressed, under a footer reading "A Open". The
    // crossbar already gets this right: an empty row is dimmed and does not activate.
    val cursored = selected && clickable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            // The shared cursor rather than a hand-rolled fill. This row used to paint
            // menuCursorFill() with no edge, which made it the one focused row in the app without
            // the bright border that every menu, picker and settings list draws.
            .menuCursor(cursored)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (clickable) {
            // Whatever art the entry has -- box art, a frame grab, a cover, an album cover. The
            // placeholder keeps the same footprint, so a list of mixed results does not jitter
            // left and right as it scrolls past entries that have art and entries that do not.
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
