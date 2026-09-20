package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptBar
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.theme.menuCursorFill
import com.psplauncher.feature.xmb.viewmodel.SearchState
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.XMBItemType

private val PrimaryText = Color.White
private val SecondaryText = Color(0xFFC9C7E8)
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
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedIndex, state.scrollToTopToken) {
        if (state.rows.isNotEmpty()) {
            listState.animateScrollToItem((state.selectedIndex - 1).coerceIn(0, state.rows.lastIndex))
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
            .background(
                Brush.verticalGradient(
                    0f to pfpColors.backgroundTop.copy(alpha = 0.78f),
                    1f to pfpColors.backgroundBottom.copy(alpha = 0.93f),
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
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

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
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

            // imePadding here, around the results and the prompts, rather than on the whole
            // screen. Padding everything squeezes the title and the field up into the top of the
            // display, and the keyboard is up the entire time this screen is on, so that is not
            // a transient state -- it is the layout. This way the header stays put, the results
            // get every pixel above the keyboard, and the prompt bar stays visible rather than
            // sitting behind it.
            Column(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                        SearchResultRow(
                            row = row,
                            selected = index == state.selectedIndex,
                            onClick = { onActivateAt(index) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                ControllerPromptBar(
                    items = listOf(
                        ControllerPromptItem(GamepadAction.SELECT, "Open"),
                        ControllerPromptItem(GamepadAction.BACK, "Back"),
                    ),
                    labelColor = SecondaryText.copy(alpha = 0.7f),
                    labelStyle = TextStyle(fontSize = 11.sp),
                    glyphSize = 16.dp,
                    arrangement = Arrangement.spacedBy(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(row: XMBItem, selected: Boolean, onClick: () -> Unit) {
    val clickable = row.type != XMBItemType.EMPTY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) menuCursorFill() else Color.Transparent)
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
