package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.PfpSearchField
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursor
import com.psplauncher.core.ui.theme.deriveStorefrontColors
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
    /**
     * How many columns the grid measured. The cursor steps by a whole row, so the ViewModel has
     * to be given this same number — see SEARCH_GRID_COLUMNS.
     */
    onColumnsMeasured: (Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    // The measured count, hoisted so the three things that need it agree. It is set by the grid
    // below; SEARCH_GRID_COLUMNS is only the value for the frame before the first measurement.
    var columns by remember { mutableIntStateOf(SEARCH_GRID_COLUMNS) }
    LaunchedEffect(state.selectedIndex, state.scrollToTopToken, columns) {
        if (state.rows.isNotEmpty()) {
            // A ROW back, not an item back. The list scrolled to selectedIndex - 1 to keep one
            // entry visible above the cursor; in a grid that is one column to the left, which is
            // usually the same row and scrolls nothing.
            //
            // THE THIRD READER of the column count, and the one that is easy to miss: the grid
            // lays out by it, the ViewModel's cursor steps by it, and this scrolls by it. All
            // three have to be the same number or the row kept visible above the cursor is not
            // the row above the cursor.
            val target = (state.selectedIndex - columns).coerceIn(0, state.rows.lastIndex)
            gridState.animateScrollToItem(target)
        }
    }

    // Opens with the field focused and the keyboard up. A search screen that needs you to reach
    // out and tap the box before typing has wasted the press that opened it.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val keyboard = LocalSoftwareKeyboardController.current
    val sf = deriveStorefrontColors()

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

            // core-ui's shared field, not a third copy of it.
            //
            // PfpSearchField's own header names this screen: the drawer and this one each
            // learned the caret rule separately — a String value leaves the selection at 0 while
            // text arrives around it, so a query seeded from outside takes every character after
            // it at position zero and typing C then L gives "lc". Both had their own fix. This is
            // the one that stays.
            //
            // `active` is a constant true because this screen opens focused and never stops
            // being a search: the field is the reason the screen exists.
            PfpSearchField(
                query = state.query,
                active = true,
                focusRequester = focusRequester,
                placeholder = "Search",
                onActivate = {},
                onQueryChange = onQueryChange,
                onDone = { keyboard?.hide() },
                colors = sf,
            )

            Spacer(Modifier.height(12.dp))

            // imePadding here, around the results, rather than on the whole screen: the keyboard
            // is up the entire time this screen is on, so that is not a transient state, it is the
            // layout, and the results get every pixel above it. The prompts are not in here any
            // more — they are pinned to the screen edge and are not drawn at all while the IME is
            // up, so they have nothing to pad around.
            //
            Column(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
                // One mixed grid, apps first and then games and the media libraries — see
                // rebuildSearchRows for why apps lead. A list showed four results and left the
                // right half of the screen empty; the grid answers a search for "final" without
                // scrolling. How many to a row is MEASURED below, not a constant, and the same
                // number drives the D-pad's up and down.
                //
                // The EMPTY row (the "nothing found" placeholder) is still a full-width line: a
                // message is not a result and putting it in a cell would look like one.
                val empty = state.rows.singleOrNull()?.takeIf { it.type == XMBItemType.EMPTY }
                if (empty != null) {
                    SearchResultRow(row = empty, selected = false, onClick = {})
                    Spacer(Modifier.weight(1f))
                } else {
                    // The grid measures itself and divides by what a card wants.
                    //
                    // It was Fixed(SEARCH_GRID_COLUMNS). Because the card is a fixed 2:3 with an
                    // unspecified width, the column count IS the card's size — so a fixed seven
                    // made a wider panel draw BIGGER cards rather than more of them. Card size is
                    // the user's through the scale slider; how many fit is this division.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val measured = ((maxWidth + SEARCH_TILE_GAP) / (SEARCH_TILE_TARGET_WIDTH + SEARCH_TILE_GAP))
                            .toInt()
                            .coerceIn(SEARCH_GRID_MIN_COLUMNS, SEARCH_GRID_MAX_COLUMNS)
                        columns = measured
                        // The cursor steps by a whole row, so it must be given the same number the
                        // grid laid out with — stepping by seven through a nine-wide grid lands two
                        // cards from the one under the eye.
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
                                    // An app will never have art, so its tile is permanent: a
                                    // letter rather than its own name repeated under itself.
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
                // Both prompts route to the callbacks this screen already takes. Without this the
                // bar named two buttons and answered neither — and on a touch-only device Search
                // drew a "Back" there was no way to press.
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

/**
 * The width a search card wants, from which the column count is derived.
 *
 * Not a taste number: it is what the old fixed seven PRODUCED on the reference handheld. 821dp of
 * panel less this screen's 40dp gutters is 741dp, and seven columns with six 14dp gaps leaves
 * (741 - 84) / 7 = 93.86dp a card. Keeping the card at that width and letting the count float is
 * what turns a wider panel into more results rather than larger ones — the card's size is the
 * user's, through the scale slider. Derived from the old number precisely so the handheld keeps
 * drawing what it drew before.
 *
 * **93 and not 94**, and the difference is a whole column. The count is a truncating division, so
 * rounding the card UP past what it actually measures takes the handheld from seven to six:
 * (741 + 14) / (94 + 14) = 6.99. SearchGridColumnsTest pins 821dp at seven for exactly this
 * reason — it caught the 94 before it shipped.
 *
 * Lives here and not beside SEARCH_GRID_COLUMNS because a Dp belongs to the layout; the count is
 * navigation math and stays in the ViewModel with the cursor that steps by it.
 */
private val SEARCH_TILE_TARGET_WIDTH = 93.dp

/** The gap between cards, named because the column arithmetic has to subtract it. */
private val SEARCH_TILE_GAP = 14.dp
