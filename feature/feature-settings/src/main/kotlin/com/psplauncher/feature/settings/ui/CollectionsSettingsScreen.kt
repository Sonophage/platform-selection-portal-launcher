package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameCollection
import com.psplauncher.core.ui.icons.CATEGORY_ICON_CATALOG
import com.psplauncher.core.ui.icons.categoryIconFor

/**
 * Collections' detail step and its dialogs. The list itself lives in [CategoryManagerScreen].
 *
 * Collections and Categories were two Settings entries, in two different sections, for what
 * reads as one idea: the groups the crossbar is made of. They are one screen now -- the rows
 * moved, the flows did not, and everything below still takes plain parameters, so the merged
 * screen drives them exactly as this file used to.
 */
internal data class CollectionDialog(
    val title: String,
    val renameId: Long? = null,
    val initial: String = "",
    var pendingName: String? = null,  // Temporarily holds name while a category is chosen
)

@Composable
internal fun CollectionDetailStep(
    collection: GameCollection,
    gamesFlow: () -> kotlinx.coroutines.flow.Flow<List<Game>>,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onRemoveGame: (Game) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val games by remember(collection.id) { gamesFlow() }.collectAsState(initial = emptyList())

    SettingsPageScaffold(subtitle = collection.name, onBack = onBack, modifier = modifier) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup("Collection")
            SettingsRow(label = "Rename", onClick = onRename)
            SettingsRow(
                label    = "Change Icon",
                sublabel = collection.iconKey?.let { categoryIconFor(it).label } ?: "Default (Memory Card)",
                onClick  = onChangeIcon,
            )
            SettingsRow(label = "Move Up", onClick = onMoveUp)
            SettingsRow(label = "Move Down", onClick = onMoveDown)
            SettingsRow(label = "Delete Collection", sublabel = "Removes the collection; games are kept", onClick = onDelete)

            SettingsGroup("Games (${games.size})")
            if (games.isEmpty()) {
                SettingsRow(
                    label    = "No games in this collection",
                    sublabel = "Add games from their Options menu.",
                )
            } else {
                games.forEach { game ->
                    SettingsRow(
                        label    = game.displayTitle,
                        sublabel = "${game.platformId.uppercase()}  ·  tap to remove from collection",
                        onClick  = { onRemoveGame(game) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun CollectionTextDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    SettingsTextPromptOverlay(
        title = title,
        value = text,
        onValueChange = { text = it },
        onConfirm = { onConfirm(text) },
        onCancel = onCancel,
        placeholder = "Collection name",
    )
}

@Composable
internal fun CollectionCategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    SettingsChoiceOverlay(
        title = "Add to Category",
        options = categories.map { it.name },
        selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId },
        onPick = { onCategorySelected(categories[it].id) },
        onCancel = onCancel,
    )
}

// Icon picker for a collection: a "Default (Memory Card)" option plus the shared category icon
// catalog. Picking null resets to the default art. Mirrors the category icon picker.
@Composable
internal fun CollectionIconPickerDialog(
    selectedIconKey: String?,
    onPick: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    // A grid of fifty-odd icons, picked by eye. The list overlays would turn that into a long
    // scroll of names, which is worse for the one job this has, so it keeps the grid and uses the
    // shared card for the chrome -- and carries its own two-dimensional cursor, because a grid
    // you can only escape from is not a picker.
    //
    // The column count is measured rather than assumed: the cells are adaptive, so the same
    // arithmetic has to use whatever number the layout actually produced. One source of truth for
    // "how many columns", used by both the grid and the cursor.
    val gridState = rememberLazyGridState()
    var cursor by remember { mutableIntStateOf(GRID_CURSOR_HEADER) }
    var columns by remember { mutableIntStateOf(1) }

    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.SELECT ->
                if (cursor == GRID_CURSOR_HEADER) onPick(null)
                else CATEGORY_ICON_CATALOG.getOrNull(cursor)?.let { onPick(it.key) }
            GamepadAction.BACK -> onCancel()
            else -> cursor = gridCursorStep(cursor, columns, CATEGORY_ICON_CATALOG.size, action)
        }
    }
    // Keep the focused icon on screen. The grid is taller than the card shows, so without this
    // the cursor walks off the bottom and the user is moving something they cannot see.
    LaunchedEffect(cursor) {
        if (cursor >= 0) runCatching { gridState.animateScrollToItem(cursor) }
    }

    PfpOverlayCard(onScrimTap = onCancel) {
        PfpOverlayTitle("Collection Icon")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (cursor == GRID_CURSOR_HEADER) Color(0x33FFFFFF) else Color.Transparent)
                .clickable { onPick(null) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                "Default (Memory Card)",
                color = if (selectedIconKey == null) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            val cell = 56.dp
            val gap = 8.dp
            // The same formula LazyVerticalGrid's Adaptive uses, so the cursor and the layout
            // cannot disagree about where a row ends.
            val measured = ((maxWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)
            LaunchedEffect(measured) { columns = measured }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(measured),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                itemsIndexed(CATEGORY_ICON_CATALOG, key = { _, icon -> icon.key }) { index, icon ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == cursor) Color(0x33FFFFFF) else Color.Transparent)
                            .then(
                                if (icon.key == selectedIconKey) {
                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        Image(
                            painter = painterResource(icon.resId),
                            contentDescription = icon.label,
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = icon.key == selectedIconKey,
                                    onClick = { onPick(icon.key) },
                                ),
                        )
                    }
                }
            }
        }
    }
}
