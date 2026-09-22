package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.ui.icons.CategoryIconGlyph
import com.psplauncher.feature.settings.viewmodel.CREATE_CATEGORY_FOCUS_KEY
import com.psplauncher.feature.settings.viewmodel.CategoryManagerUiState
import com.psplauncher.feature.settings.viewmodel.CategoryManagerViewModel
import com.psplauncher.feature.settings.viewmodel.CategoryStep
import com.psplauncher.feature.settings.viewmodel.CollectionsSettingsViewModel
import com.psplauncher.core.domain.model.GameCollection

/**
 * Categories and Collections, on one screen.
 *
 * They were two Settings entries in two different sections -- Collections under Library,
 * Categories under Interface -- for what reads as one idea: the groups the crossbar is made of.
 * You could add a collection to a category from one screen and never find the category from the
 * other.
 *
 * The two flows are still two state machines, owned by two ViewModels, and this only decides
 * which one is on screen. The merged list shows when BOTH are at rest; a category step or an
 * open collection takes over, because each of those is a whole screen of its own. Merging the
 * state machines as well would have been a rewrite of two working things to change where a row
 * is drawn.
 */
@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManagerViewModel = hiltViewModel(),
    collectionsViewModel: CollectionsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val collections by collectionsViewModel.collections.collectAsState()
    val gamingCategories by collectionsViewModel.gamingCategories.collectAsState()

    // Collections' sub-steps, hoisted out of the screen this list used to live on so the merged
    // list can decide which flow is showing. Same variables, same meanings.
    var openCollectionId by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<CollectionDialog?>(null) }
    var pickCategoryForNewCollection by remember { mutableStateOf<String?>(null) }
    var iconPickerFor by remember { mutableStateOf<Long?>(null) }
    val openCollection = collections.firstOrNull { it.id == openCollectionId }

    // One Back, two flows: collapse whatever collection sub-step is open, then let the category
    // ViewModel collapse its own, then leave. Order matters -- a collection dialog opened from
    // the merged list must not fall through to the category machine, which knows nothing of it.
    val handleBack: () -> Unit = {
        when {
            iconPickerFor != null               -> iconPickerFor = null
            pickCategoryForNewCollection != null -> pickCategoryForNewCollection = null
            dialog != null                      -> dialog = null
            openCollectionId != null            -> openCollectionId = null
            viewModel.onBack()                  -> Unit
            else                                -> onBack()
        }
    }

    when {
        openCollection != null -> CollectionDetailStep(
            collection   = openCollection,
            gamesFlow    = { collectionsViewModel.gamesIn(openCollection.id) },
            onRename     = { dialog = CollectionDialog("Rename Collection", openCollection.id, openCollection.name) },
            onChangeIcon = { iconPickerFor = openCollection.id },
            onMoveUp     = { collectionsViewModel.moveUp(openCollection.id) },
            onMoveDown   = { collectionsViewModel.moveDown(openCollection.id) },
            onDelete     = { collectionsViewModel.delete(openCollection.id); openCollectionId = null },
            onRemoveGame = { game -> collectionsViewModel.removeGame(openCollection.id, game.id) },
            onBack       = handleBack,
            modifier     = modifier,
        )
        state.step == CategoryStep.PICK_ICON -> PickIconContent(state, viewModel, handleBack, modifier)
        state.step == CategoryStep.PICK_TYPE -> PickTypeContent(state, viewModel, handleBack, modifier)
        state.step == CategoryStep.DETAIL    -> CategoryDetailContent(state, viewModel, handleBack, modifier)
        else -> CategoryListContent(
            state = state,
            vm = viewModel,
            collections = collections,
            onCreateCollection = { dialog = CollectionDialog("New Collection") },
            onOpenCollection = { openCollectionId = it.id },
            onBack = handleBack,
            modifier = modifier,
        )
    }

    // Name entry for a new or renamed collection. Suppressed while the category picker is up:
    // creating runs name-then-category, and both dialogs at once would stack.
    if (pickCategoryForNewCollection == null) {
        dialog?.let { d ->
            CollectionTextDialog(
                title = d.title,
                initial = d.initial,
                onConfirm = { name ->
                    if (d.renameId != null) {
                        collectionsViewModel.rename(d.renameId, name)
                        dialog = null
                    } else {
                        d.pendingName = name
                        pickCategoryForNewCollection = gamingCategories.firstOrNull()?.id ?: "games"
                    }
                },
                onCancel = { dialog = null },
            )
        }
    }

    dialog?.pendingName?.let { name ->
        if (pickCategoryForNewCollection != null) {
            CollectionCategoryPickerDialog(
                categories = gamingCategories,
                selectedCategoryId = pickCategoryForNewCollection ?: "games",
                onCategorySelected = { categoryId ->
                    collectionsViewModel.create(name, categoryId)
                    pickCategoryForNewCollection = null
                    dialog = null
                },
                onCancel = { pickCategoryForNewCollection = null },
            )
        }
    }

    iconPickerFor?.let { id ->
        CollectionIconPickerDialog(
            selectedIconKey = collections.firstOrNull { it.id == id }?.iconKey,
            onPick = { key -> collectionsViewModel.setIcon(id, key); iconPickerFor = null },
            onCancel = { iconPickerFor = null },
        )
    }

    // Create-name dialog
    if (state.showCreateNameDialog) {
        var text by remember { mutableStateOf("") }
        SettingsTextPromptOverlay(
            title = "New Category",
            value = text,
            onValueChange = { text = it },
            onConfirm = { viewModel.confirmCreateName(text) },
            onCancel = { viewModel.cancelCreateName() },
            placeholder = "Category name",
            confirmLabel = "Next",
        )
    }

    // Rename dialog
    state.renameTargetId?.let { id ->
        val current = state.categories.firstOrNull { it.id == id }?.name ?: ""
        var text by remember(id) { mutableStateOf(current) }
        SettingsTextPromptOverlay(
            title = "Rename Category",
            value = text,
            onValueChange = { text = it },
            onConfirm = { viewModel.confirmRename(text) },
            onCancel = { viewModel.cancelRename() },
        )
    }
}

// ── LIST ────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryListContent(
    state: CategoryManagerUiState,
    vm: CategoryManagerViewModel,
    collections: List<GameCollection>,
    onCreateCollection: () -> Unit,
    onOpenCollection: (GameCollection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    SettingsPageScaffold(
        subtitle = "Categories & Collections",
        onBack = onBack,
        modifier = modifier,
        restoreFocusKey = state.returnFocusKey,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup("Manage")
            SettingsRow(
                label    = "Create Category",
                sublabel = "Add a new category to the XMB bar",
                focusKey = CREATE_CATEGORY_FOCUS_KEY,
                onClick  = { vm.startCreate() },
            )
            SettingsRow(
                label    = "Create Collection",
                sublabel = "e.g. RPGs, Currently Playing, Best PSP Games",
                onClick  = onCreateCollection,
            )

            SettingsGroup("XMB Categories")
            state.categories.forEach { cat ->
                SettingsRow(
                    label    = cat.name + if (!cat.visible) "  (Hidden)" else "",
                    sublabel = "Icon: ${cat.iconKey}" + if (cat.protected) "  ·  Built-in" else "  ·  Custom",
                    focusKey = cat.id,
                    onClick  = { vm.openDetail(cat.id) },
                )
            }

            // Collections sit UNDER the categories they belong to, which is the relationship
            // they actually have: a collection lives inside a gaming category, and the two were
            // being managed from opposite ends of Settings.
            SettingsGroup("Collections")
            if (collections.isEmpty()) {
                SettingsRow(
                    label    = "No collections yet",
                    sublabel = "Create one above, or add a game from its Options menu.",
                )
            } else {
                collections.forEach { collection ->
                    SettingsRow(
                        label    = collection.name,
                        sublabel = "${collection.gameCount} ${if (collection.gameCount == 1) "game" else "games"}",
                        focusKey = "collection_${collection.id}",
                        onClick  = { onOpenCollection(collection) },
                    )
                }
            }
        }
    }
}

// ── PICK ICON ─────────────────────────────────────────────────────────────────────

@Composable
private fun PickIconContent(
    state: CategoryManagerUiState,
    vm: CategoryManagerViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val subtitle = if (state.pickingIconForCreate) "Choose Icon" else "Change Icon"
    SettingsPageScaffold(heading = "Category", subtitle = subtitle, onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup(state.pendingName ?: state.detail?.name ?: "Icon")
            state.iconOptions.forEach { option ->
                SettingsRow(
                    label    = option.label,
                    trailing = { CategoryIconGlyph(option.key, contentDescription = option.label, modifier = Modifier.size(40.dp)) },
                    onClick  = { vm.chooseIcon(option.key) },
                )
            }
        }
    }
}

// ── PICK TYPE ──────────────────────────────────────────────────────────────────────

@Composable
private fun PickTypeContent(
    state: CategoryManagerUiState,
    vm: CategoryManagerViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    SettingsPageScaffold(heading = "Category", subtitle = "Content Type", onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup(state.pendingName ?: "Category Type")
            SettingsRow(
                label    = "Gaming",
                sublabel = "For games and collections",
                onClick  = { vm.chooseType(isGaming = true) },
            )
            SettingsRow(
                label    = "Non-Gaming",
                sublabel = "For apps like Video, Music, Photos",
                onClick  = { vm.chooseType(isGaming = false) },
            )
        }
    }
}

// ── DETAIL ──────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryDetailContent(
    state: CategoryManagerUiState,
    vm: CategoryManagerViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val cat = state.detail
    if (cat == null) { LaunchedEffect(Unit) { vm.onBack() }; return }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    SettingsPageScaffold(heading = "Categories", subtitle = cat.name, onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup("Edit")
            SettingsRow(label = "Rename Category", onClick = { vm.beginRename(cat.id) })
            SettingsValueRow(label = "Change Icon", value = cat.iconKey, onClick = { vm.startChangeIcon() })
            if (cat.canHide) {
                SettingsToggleRow(
                    label    = "Show On Bar",
                    sublabel = "Hide or show this category in the XMB",
                    checked  = cat.visible,
                    onToggle = { vm.toggleVisible(cat.id, it) },
                )
            }
            SettingsToggleRow(
                label    = "Gaming Category",
                sublabel = "Gaming: games & collections · Non-gaming: apps",
                checked  = cat.isGamingCategory,
                onToggle = { vm.setGamingCategory(cat.id, it) },
            )

            SettingsGroup("Order")
            SettingsRow(label = "Move Left",  onClick = { vm.move(cat.id, up = true) })
            SettingsRow(label = "Move Right", onClick = { vm.move(cat.id, up = false) })

            if (!cat.protected) {
                SettingsGroup("Danger Zone")
                SettingsRow(
                    label    = "Delete Category",
                    sublabel = "Removes this custom category. Apps are not uninstalled.",
                    trailing = { Text("Delete", color = SettingsAccent) },
                    onClick  = { showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        SettingsConfirmOverlay(
            title = "Delete ${cat.name}?",
            message = "This removes the category and its app assignments. Apps are not uninstalled.",
            confirmLabel = "Delete",
            onConfirm = { showDeleteConfirm = false; vm.delete(cat.id) },
            onCancel = { showDeleteConfirm = false },
        )
    }
}
