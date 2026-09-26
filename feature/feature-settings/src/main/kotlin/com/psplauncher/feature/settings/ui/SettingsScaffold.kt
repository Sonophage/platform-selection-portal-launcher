package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import com.psplauncher.themekit.XmbLayoutSpec
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.isDirectional
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.gesture.dragToScroll
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.xmbScrimAnchors
import kotlinx.coroutines.launch
import timber.log.Timber

val LocalSettingsScreenId = compositionLocalOf<String?> { null }

val LocalSettingsOpenScreen = compositionLocalOf<(String) -> Unit> { {} }

val LocalSettingsPendingAction = compositionLocalOf<GamepadAction?> { null }
val LocalSettingsActionConsumed = compositionLocalOf<() -> Unit> { {} }

val LocalSettingsTouchInput = compositionLocalOf<() -> Unit> { {} }

val LocalSettingsHostTouchInput = compositionLocalOf<() -> Unit> { {} }
val LocalSettingsShowControllerHint = compositionLocalOf { false }

val LocalSettingsPromptAction = compositionLocalOf<((GamepadAction) -> Unit)?> { null }

val LocalSettingsLeftBacksOut = compositionLocalOf { true }

val LocalSettingsLastInputWasTouch = compositionLocalOf { false }
val LocalSettingsCursorVisible = compositionLocalOf { true }

internal val LocalSettingsOverlayInput =
    compositionLocalOf<MutableState<((GamepadAction) -> Unit)?>?> { null }

@Composable
fun SettingsOverlayInput(onAction: (GamepadAction) -> Unit) {
    val slot = LocalSettingsOverlayInput.current
        ?: error("SettingsOverlayInput must be composed inside SettingsNavHost")
    val current by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        slot.value = { action -> current(action) }
        onDispose { slot.value = null }
    }
}

internal val LocalSettingsFocusTracker =
    compositionLocalOf<((() -> Unit)?) -> Unit> { {} }

internal val LocalSettingsFocusRegistry =
    compositionLocalOf<SnapshotStateMap<String, FocusRequester>> { mutableStateMapOf() }

internal val LocalSettingsRegisterFirstFocusable =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

internal val LocalSettingsRowPositions =
    compositionLocalOf<SnapshotStateMap<FocusRequester, Float>?> { null }

internal val LocalSettingsRowSizes =
    compositionLocalOf<SnapshotStateMap<FocusRequester, Float>?> { null }
internal val LocalSettingsNavigationOrder =
    compositionLocalOf<SnapshotStateList<Pair<FocusRequester, ControllerNavItem>>?> { null }

internal val LocalSettingsRowActions =
    compositionLocalOf<SnapshotStateMap<String, SnapshotStateList<Pair<String, FocusRequester>>>?> { null }

internal val LocalSettingsScrollStateRegistrar =
    compositionLocalOf<(ScrollState) -> Unit> { {} }

internal val LocalSettingsReportFocused =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

internal val LocalSettingsEnterSliderMode =
    compositionLocalOf<(SettingsSliderNode) -> Unit> { {} }
internal val LocalSettingsSliderAdjusting = compositionLocalOf { false }

internal val LocalSettingsReportRemoved =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

private fun reseedFocus(
    rowPositions: Map<FocusRequester, Float>,
    lastFocusedY: Float?,
    firstRow: FocusRequester?,
) {
    val target = lastFocusedY?.let { anchor ->
        rowPositions.entries.minByOrNull { kotlin.math.abs(it.value - anchor) }?.key
    } ?: firstRow
    target?.let { runCatching { it.requestFocus() } }
}

val SettingsAccent: Color
    @Composable get() = LocalPFPColors.current.accentColor

val SettingsText: Color
    @Composable get() = LocalPfpTextColors.current.primary
val SettingsSubtext: Color
    @Composable get() = LocalPfpTextColors.current.secondary

val SettingsTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

val SettingsDivider = com.psplauncher.core.ui.theme.PfpPalette.Divider

val SETTINGS_COLUMN_MAX_WIDTH = 560.dp

private val SETTINGS_RAIL_WIDTH = 216.dp

val LocalSettingsHelp = compositionLocalOf { mutableStateOf<String?>(null) }

private val SETTINGS_ROW_SHAPE = RoundedCornerShape(10.dp)

private val SETTINGS_ROW_SELECTED_FILL = Color.White.copy(alpha = 0.035f)

private val SETTINGS_ROW_SELECTED_EDGE_START = Color.White.copy(alpha = 0.16f)
private val SETTINGS_ROW_SELECTED_EDGE_END = Color.White.copy(alpha = 0.38f)

private val SettingsRowSelectedEdgeBrush: Brush
    get() = Brush.horizontalGradient(
        listOf(SETTINGS_ROW_SELECTED_EDGE_START, SETTINGS_ROW_SELECTED_EDGE_END),
    )

internal fun Modifier.settingsSelectedPlate(selected: Boolean): Modifier = this
    .clip(SETTINGS_ROW_SHAPE)
    .background(
        color = if (selected) SETTINGS_ROW_SELECTED_FILL else Color.Transparent,
        shape = SETTINGS_ROW_SHAPE,
    )
    .then(
        if (selected) Modifier.border(1.dp, SettingsRowSelectedEdgeBrush, SETTINGS_ROW_SHAPE)
        else Modifier
    )

private val PICKER_SHAPE = RoundedCornerShape(4.dp)
private val PICKER_EDGE = Color.White.copy(alpha = 0.22f)
private val PICKER_FOCUS_EDGE = Color.White.copy(alpha = 0.55f)

data class SettingsPickerOption(val label: String, val help: String? = null)

internal class SettingsPickerRequest(
    val title: String,
    val options: List<SettingsPickerOption>,
    val selectedIndex: Int,
    val onPick: (Int) -> Unit,

    val anchorY: Float,
)

internal val LocalSettingsPicker =
    compositionLocalOf { mutableStateOf<SettingsPickerRequest?>(null) }

private const val SETTINGS_HELP_TEXT_SP = 13

internal val CONTENT_EDGE_MARGIN = 16.dp

internal const val SettingsContentViewportTag = "settings_content_viewport"

val SettingsDefaultHelperItems = listOf(
    ControllerPromptItem(GamepadAction.SELECT, "Enter"),
    ControllerPromptItem(GamepadAction.BACK, "Back"),
)

@Composable
fun SettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,

    restoreFocusKey: String? = null,

    onInterceptAction: ((GamepadAction) -> Boolean)? = null,
    onTouchInput: () -> Unit = {},

    header: (@Composable () -> Unit)? = null,

    showDivider: Boolean = true,

    lightScrim: Boolean = false,

    backdrop: (@Composable () -> Unit)? = null,

    showRail: Boolean = true,

    footer: (@Composable () -> Unit)? = null,

    helperFooterItems: List<ControllerPromptItem> = SettingsDefaultHelperItems,

    contentKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val leftBacksOut = LocalSettingsLeftBacksOut.current
    val focusManager = LocalFocusManager.current

    val contentScrollState = remember { mutableStateOf<ScrollState?>(null) }

    val bootstrapFR = remember { FocusRequester() }
    val pendingAction = LocalSettingsPendingAction.current
    val onConsumed = LocalSettingsActionConsumed.current

    val hostTouchInput = LocalSettingsHostTouchInput.current
    val notifyTouchInput = {
        onTouchInput()
        hostTouchInput()
    }

    val pfpColors = LocalPFPColors.current

    val (scrimTop, scrimBottom) = remember(pfpColors.backgroundTop, pfpColors.backgroundBottom) {
        xmbScrimAnchors(pfpColors.backgroundTop, pfpColors.backgroundBottom)
    }

    val focusedRowClick = remember { mutableStateOf<(() -> Unit)?>(null) }

    val navigationState = remember { ControllerNavigationState() }

    val menuSounds = com.psplauncher.core.ui.sound.LocalMenuSounds.current

    val sliderNodeState = remember { mutableStateOf<SettingsSliderNode?>(null) }

    val lastInputWasTouch = LocalSettingsLastInputWasTouch.current
    val cursorVisible = remember { mutableStateOf(!lastInputWasTouch) }

    val helpText = remember { mutableStateOf<String?>(null) }

    val screenId = LocalSettingsScreenId.current
    val openScreen = LocalSettingsOpenScreen.current
    val railEntries = remember(screenId, showRail) {
        if (showRail) com.psplauncher.core.domain.model.settingsRailRows(screenId) else emptyList()
    }
    val railFocused = remember { mutableStateOf(false) }

    val overlayInput = LocalSettingsOverlayInput.current
    val railCursor = remember(screenId) {
        mutableIntStateOf(railEntries.indexOfFirst { it.id == screenId }.coerceAtLeast(0))
    }

    val pickerState = remember { mutableStateOf<SettingsPickerRequest?>(null) }
    val pickerCursor = remember { mutableIntStateOf(0) }

    val touchScrolled = remember { mutableStateOf(false) }

    val focusRegistry = remember { mutableStateMapOf<String, FocusRequester>() }

    val firstRowFocus = remember { mutableStateOf<FocusRequester?>(null) }

    remember(contentKey) { firstRowFocus.value = null; contentKey }

    val rowPositions = remember { mutableStateMapOf<FocusRequester, Float>() }

    val rowSizes = remember { mutableStateMapOf<FocusRequester, Float>() }

    val navigationOrder =
        remember { androidx.compose.runtime.mutableStateListOf<Pair<FocusRequester, ControllerNavItem>>() }

    val rowActionFrs = remember {
        mutableStateMapOf<String, SnapshotStateList<Pair<String, FocusRequester>>>()
    }

    LaunchedEffect(navigationOrder, rowPositions) {
        snapshotFlow {
            val sorted = navigationOrder.sortedBy { (fr, _) -> rowPositions[fr] ?: Float.MAX_VALUE }
            val geometry = sorted.mapNotNull { (fr, item) ->
                rowPositions[fr]?.let { item.key to it }
            }.toMap()
            sorted to geometry
        }
            .collect { (entries, geometry) ->
                navigationState.updateItems(entries.map { it.second }, geometry)
            }
    }

    val firstVisibleContentY = remember { mutableStateOf<Float?>(null) }

    val contentViewportHeight = remember { mutableStateOf<Float?>(null) }
    var focusedRow by remember { mutableStateOf<FocusRequester?>(null) }

    var lastFocusedY by remember { mutableStateOf<Float?>(null) }
    var refocusTick by remember { mutableIntStateOf(0) }

    var focusRedirected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Timber.d("Settings focus: screen opened ($title / $subtitle) restoreKey=$restoreFocusKey")
        var attempts = 0

        while (!focusRedirected && attempts < 30) {
            withFrameNanos {  }
            val target =
                if (restoreFocusKey != null) focusRegistry[restoreFocusKey] else firstRowFocus.value
            if (target != null) {
                runCatching { target.requestFocus() }
            } else {
                runCatching { bootstrapFR.requestFocus() }
            }
            attempts++
        }

        if (!focusRedirected) {
            (firstRowFocus.value ?: bootstrapFR).let { runCatching { it.requestFocus() } }
        }
        Timber.d("Settings focus: default focus assigned=$focusRedirected after $attempts frame(s) ($subtitle)")
    }

    LaunchedEffect(contentKey) {
        if (contentKey == null) return@LaunchedEffect
        withFrameNanos { }
        firstRowFocus.value?.let { runCatching { it.requestFocus() } }
    }

    LaunchedEffect(refocusTick) {
        if (refocusTick == 0) return@LaunchedEffect
        withFrameNanos { }
        val sortedEntries = navigationOrder.sortedBy { (fr, _) -> rowPositions[fr] ?: Float.MAX_VALUE }
        navigationState.updateItems(
            sortedEntries.map { it.second },
            sortedEntries.mapNotNull { (fr, item) ->
                rowPositions[fr]?.let { item.key to it }
            }.toMap(),
        )
        val target = navigationState.focusedKey
            ?.let { key -> navigationOrder.firstOrNull { it.second.key == key }?.first }
            ?: firstRowFocus.value
        target?.let { runCatching { it.requestFocus() } }
        Timber.d("Settings focus: refocused after row removal (key=${navigationState.focusedKey})")
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(focusedRow) {
        val focused = focusedRow ?: return@LaunchedEffect
        withFrameNanos { }
        val y = rowPositions[focused] ?: return@LaunchedEffect
        val viewportTop = firstVisibleContentY.value ?: return@LaunchedEffect
        val viewportHeight = contentViewportHeight.value ?: return@LaunchedEffect
        val activeScrollState = contentScrollState.value ?: return@LaunchedEffect
        val rowHeight = rowSizes[focused] ?: return@LaunchedEffect
        val margin = with(density) { CONTENT_EDGE_MARGIN.toPx() }
        val viewportBottom = viewportTop + viewportHeight

        val target = when {
            y < viewportTop + margin ->
                (activeScrollState.value.toFloat() - (viewportTop + margin - y)).coerceAtLeast(0f)
            y + rowHeight > viewportBottom - margin ->
                activeScrollState.value.toFloat() + ((y + rowHeight) - (viewportBottom - margin))
            else -> null
        }
        target?.let {
            activeScrollState.animateScrollTo(it.toInt().coerceIn(0, activeScrollState.maxValue))
        }
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    fun requestFocusFor(key: String?) {
        val fr = navigationOrder.firstOrNull { it.second.key == key }?.first
            ?: rowActionFrs.entries.firstOrNull { (_, actions) -> actions.any { it.first == key } }
                ?.value?.firstOrNull { it.first == key }?.second
        if (fr != null) {
            runCatching { fr.requestFocus() }
        } else {
            reseedFocus(rowPositions, lastFocusedY, firstRowFocus.value)
        }
    }

    LaunchedEffect(pickerState.value) {
        pickerState.value?.let { pickerCursor.intValue = it.selectedIndex.coerceAtLeast(0) }
    }

    LaunchedEffect(pendingAction) {
        if (pendingAction == null) return@LaunchedEffect

        cursorVisible.value = true
        navigationState.markControllerInput()

        val revivalPress = touchScrolled.value
        if (revivalPress) {
            val viewportTop = firstVisibleContentY.value
            val viewportHeight = contentViewportHeight.value
            val viewportCenter = if (viewportTop != null && viewportHeight != null) {
                viewportTop + viewportHeight / 2f
            } else {
                null
            }
            if (viewportCenter != null) {
                navigationState.focusNearestTo(viewportCenter)
            }
            touchScrolled.value = false
        }
        val focusedKey = navigationState.focusedKey
        if (focusedKey != null) {
            requestFocusFor(focusedKey)
        }
        Timber.d("Settings focus: action=$pendingAction focusedClick=${focusedRowClick.value != null}")

        if (onInterceptAction?.invoke(pendingAction) == true) {
            onConsumed()
            return@LaunchedEffect
        }

        if (revivalPress && pendingAction.isDirectional) {
            onConsumed()
            return@LaunchedEffect
        }

        overlayInput?.value?.let { handle ->
            handle(pendingAction)
            onConsumed()
            return@LaunchedEffect
        }

        if (pendingAction == GamepadAction.PREV_CATEGORY || pendingAction == GamepadAction.NEXT_CATEGORY) {
            val delta = if (pendingAction == GamepadAction.NEXT_CATEGORY) 1 else -1
            com.psplauncher.core.domain.model.settingsSectionStepTarget(screenId, delta)
                ?.let(openScreen)
            onConsumed()
            return@LaunchedEffect
        }

        if (railFocused.value) {
            when (pendingAction) {
                GamepadAction.NAVIGATE_UP ->
                    railCursor.intValue = (railCursor.intValue - 1).coerceAtLeast(0)
                GamepadAction.NAVIGATE_DOWN ->
                    railCursor.intValue = (railCursor.intValue + 1).coerceAtMost(railEntries.lastIndex)
                GamepadAction.NAVIGATE_RIGHT -> railFocused.value = false
                GamepadAction.SELECT -> {
                    val target = railEntries.getOrNull(railCursor.intValue)

                    if (target != null && target.id != screenId) openScreen(target.id)
                    else railFocused.value = false
                }
                GamepadAction.BACK -> onBack()
                else -> Unit
            }
            onConsumed()
            return@LaunchedEffect
        }

        val openPicker = pickerState.value
        if (openPicker != null) {
            val count = openPicker.options.size
            when (pendingAction) {
                GamepadAction.NAVIGATE_UP ->
                    pickerCursor.intValue = (pickerCursor.intValue - 1 + count) % count
                GamepadAction.NAVIGATE_DOWN ->
                    pickerCursor.intValue = (pickerCursor.intValue + 1) % count
                GamepadAction.SELECT -> {
                    openPicker.onPick(pickerCursor.intValue)
                    pickerState.value = null
                }
                GamepadAction.BACK -> pickerState.value = null
                else -> Unit
            }
            onConsumed()
            return@LaunchedEffect
        }

        val adjustingSlider = sliderNodeState.value
        if (adjustingSlider != null) {
            when (pendingAction) {
                GamepadAction.NAVIGATE_LEFT  -> adjustingSlider.onStep(-1)
                GamepadAction.NAVIGATE_RIGHT -> adjustingSlider.onStep(1)
                GamepadAction.SELECT,
                GamepadAction.BACK -> sliderNodeState.value = null
                GamepadAction.NAVIGATE_UP,
                GamepadAction.NAVIGATE_DOWN -> sliderNodeState.value = null
                else -> Unit
            }
            if (pendingAction != GamepadAction.NAVIGATE_UP &&
                pendingAction != GamepadAction.NAVIGATE_DOWN
            ) {
                onConsumed()
                return@LaunchedEffect
            }
        }
        when (pendingAction) {
            GamepadAction.NAVIGATE_UP -> {
                val previous = navigationState.focusedKey
                val target = navigationState.move(-1)

                if (target != null && target != previous) menuSounds(MenuSound.SCROLL)

                if (target != null && target == previous) {
                    val topScrollState = contentScrollState.value
                    if (topScrollState != null) {
                        coroutineScope.launch { topScrollState.animateScrollTo(0) }
                    }
                }
                requestFocusFor(target)
            }

            GamepadAction.NAVIGATE_DOWN -> {
                val previous = navigationState.focusedKey
                val target = navigationState.move(1)
                if (target != null && target != previous) menuSounds(MenuSound.SCROLL)
                requestFocusFor(target)
            }

            GamepadAction.NAVIGATE_LEFT -> {
                val target = navigationState.moveHorizontal(-1)
                when {
                    target != null -> { menuSounds(MenuSound.SCROLL); requestFocusFor(target) }

                    railEntries.isNotEmpty() -> { menuSounds(MenuSound.SYSTEM_BROWSE); railFocused.value = true }
                    leftBacksOut -> { menuSounds(MenuSound.BACK); onBack() }
                }
            }

            GamepadAction.NAVIGATE_RIGHT -> {
                navigationState.moveHorizontal(1)?.let { menuSounds(MenuSound.SCROLL); requestFocusFor(it) }
            }

            GamepadAction.SELECT -> {
                menuSounds(MenuSound.SELECT)

                if (!navigationState.select()) focusedRowClick.value?.invoke()
            }

            GamepadAction.BACK -> {
                menuSounds(MenuSound.BACK)
                onBack()
            }

            else -> Unit
        }
        onConsumed()
    }

    CompositionLocalProvider(

        LocalSettingsCursorVisible provides (cursorVisible.value && !railFocused.value),

        LocalSettingsFocusTracker provides { click ->
            focusedRowClick.value = click; focusRedirected = true
        },
        LocalSettingsTouchInput provides {
            cursorVisible.value = false
            touchScrolled.value = true
            navigationState.markTouchInput()

            sliderNodeState.value = null
            notifyTouchInput()
        },
        LocalSettingsHelp provides helpText,
        LocalSettingsPicker provides pickerState,
        LocalSettingsFocusRegistry provides focusRegistry,

        LocalSettingsRegisterFirstFocusable provides { fr ->
            if (firstRowFocus.value == null) firstRowFocus.value = fr
        },
        LocalSettingsRowPositions provides rowPositions,
        LocalSettingsRowSizes provides rowSizes,
        LocalSettingsNavigationOrder provides navigationOrder,
        LocalSettingsReportFocused provides { fr ->
            focusedRow = fr
            rowPositions[fr]?.let { lastFocusedY = it }

            val key = navigationOrder.firstOrNull { it.first === fr }?.second?.key
                ?: rowActionFrs.entries.firstOrNull { (_, actions) -> actions.any { it.second === fr } }
                    ?.let { (_, actions) -> actions.firstOrNull { it.second === fr }?.first }
            if (key != null) navigationState.setFocused(key)
        },
        LocalSettingsReportRemoved provides { fr ->
            if (focusedRow == fr) {
                focusedRow = null
                refocusTick++
            }
        },
        LocalSettingsEnterSliderMode provides { node -> sliderNodeState.value = node },
        LocalSettingsSliderAdjusting provides (sliderNodeState.value != null),
        LocalSettingsRowActions provides rowActionFrs,
        LocalSettingsScrollStateRegistrar provides { state -> contentScrollState.value = state },
    ) {
        Box(
            modifier = modifier

                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        cursorVisible.value = false
                        touchScrolled.value = true
                        navigationState.markTouchInput()
                        notifyTouchInput()
                    }
                }
                .fillMaxSize()

                .then(
                    if (backdrop != null) {
                        Modifier
                    } else if (lightScrim) {
                        Modifier.background(
                            Brush.verticalGradient(
                                0f to pfpColors.backgroundTop.copy(alpha = 0.45f),
                                1f to pfpColors.backgroundBottom.copy(alpha = 0.55f),
                            )
                        )
                    } else {
                        Modifier.background(Brush.verticalGradient(0f to scrimTop, 1f to scrimBottom))
                    }
                ),
        ) {
            backdrop?.invoke()
            Column(
                modifier = Modifier
                    .fillMaxSize()

                    .imePadding(),
            ) {
                Spacer(Modifier.height(StatusStripHeight))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragToScroll(contentScrollState.value),
                ) {
                if (header != null) {
                    header()
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .padding(horizontal = 48.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) { onBack() },
                    ) {
                        Text(
                            text = "◀",
                            color = SettingsSubtext,
                            fontSize = 18.sp,
                            style = TextStyle(shadow = SettingsTextShadow),
                            modifier = Modifier.padding(end = 20.dp),
                        )
                        Column {
                            Text(
                                text = title.uppercase(),
                                color = SettingsAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                style = TextStyle(shadow = SettingsTextShadow),
                            )
                            Text(
                                text = subtitle,
                                color = SettingsText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Light,
                                style = TextStyle(shadow = SettingsTextShadow),
                            )
                        }
                    }
                }
                }

                if (showDivider) HorizontalDivider(color = SettingsDivider)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .focusRequester(bootstrapFR)
                        .focusable()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                focusManager.moveFocus(FocusDirection.Down)
                                focusRedirected = true
                                Timber.d("Settings focus: default focus → first item ($subtitle)")
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(SettingsContentViewportTag)

                        .onGloballyPositioned {
                            firstVisibleContentY.value = it.localToRoot(Offset.Zero).y
                            contentViewportHeight.value = it.size.height.toFloat()
                        }

                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fade = CONTENT_EDGE_MARGIN.toPx().coerceAtMost(size.height)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height - fade,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height - fade),
                                size = Size(size.width, fade),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        if (railEntries.isNotEmpty()) {
                            SettingsSectionRail(
                                entries = railEntries,
                                currentId = screenId,
                                cursorIndex = railCursor.intValue.takeIf {
                                    railFocused.value && cursorVisible.value
                                },
                                onPick = { row ->
                                    notifyTouchInput()
                                    if (row.id != screenId) openScreen(row.id)
                                },
                            )
                        }
                        Box(modifier = Modifier.widthIn(max = SETTINGS_COLUMN_MAX_WIDTH)) {
                            content()
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragToScroll(contentScrollState.value),
                ) {
                    if (footer == null) {
                        val help = helpText.value?.takeIf { cursorVisible.value && it.isNotBlank() }
                        PfpHintBar(

                            items = if (LocalSettingsShowControllerHint.current) {
                                helperFooterItems.ifEmpty { SettingsDefaultHelperItems }
                            } else emptyList(),
                            onAction = LocalSettingsPromptAction.current,
                            centre = help?.let {
                                {
                                    Text(
                                        text = it,
                                        color = SettingsSubtext,
                                        fontSize = SETTINGS_HELP_TEXT_SP.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(shadow = SettingsTextShadow),
                                    )
                                }
                            },
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                    if (footer != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = false },
                        ) {
                            footer()
                        }
                    }
                }
            }

            pickerState.value?.let { picker ->
                SettingsPickerPanel(
                    picker = picker,
                    cursor = pickerCursor.intValue,
                    onDismiss = { pickerState.value = null },
                )
            }
        }
    }
}

@Composable
private fun SettingsPickerPanel(picker: SettingsPickerRequest, cursor: Int, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()

            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )

            .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        val panelHeight = PICKER_ROW_HEIGHT * picker.options.size + PICKER_PADDING * 2
        val anchorDp = with(density) { picker.anchorY.toDp() }

        val top = anchorDp.coerceIn(
            PICKER_EDGE_MARGIN,
            (maxHeight - panelHeight - PICKER_EDGE_MARGIN).coerceAtLeast(PICKER_EDGE_MARGIN),
        )
        Column(
            modifier = Modifier
                .padding(start = 48.dp)
                .offset(y = top)

                .widthIn(min = 150.dp, max = SETTINGS_COLUMN_MAX_WIDTH)
                .clip(PICKER_SHAPE)

                .background(Color(0xF21A1A22), PICKER_SHAPE)
                .border(1.dp, PICKER_EDGE, PICKER_SHAPE)
                .padding(PICKER_PADDING),
        ) {
            picker.options.forEachIndexed { index, option ->
                val focused = index == cursor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PICKER_ROW_HEIGHT)
                        .clip(PICKER_SHAPE)

                        .clickable {
                            picker.onPick(index)
                            onDismiss()
                        }
                        .background(
                            if (focused) SETTINGS_ROW_SELECTED_FILL else Color.Transparent,
                            PICKER_SHAPE,
                        )

                        .then(
                            if (focused) Modifier.border(1.dp, PICKER_FOCUS_EDGE, PICKER_SHAPE)
                            else Modifier
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (index == picker.selectedIndex) "\u2713" else " ",
                        color = SettingsText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        text = option.label,
                        color = if (focused) Color.White else SettingsText,
                        fontSize = 15.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val PICKER_ROW_HEIGHT = 42.dp
private val PICKER_PADDING = 8.dp
private val PICKER_EDGE_MARGIN = 24.dp

@Composable
private fun SettingsSectionRail(
    entries: List<com.psplauncher.core.domain.model.SettingsEntry>,
    currentId: String?,
    cursorIndex: Int?,
    onPick: (com.psplauncher.core.domain.model.SettingsEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(cursorIndex, currentId, entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val target = cursorIndex ?: entries.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }
        target ?: return@LaunchedEffect
        val info = listState.layoutInfo
        val viewport = info.viewportSize.height
        val rowHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0

        val centreOffset = if (viewport > 0 && rowHeight in 1 until viewport) -((viewport - rowHeight) / 2) else 0
        listState.animateScrollToItem(target.coerceIn(0, entries.lastIndex), centreOffset)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(SETTINGS_RAIL_WIDTH)
            .fillMaxHeight()
            .padding(start = 40.dp, end = 12.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(entries, key = { _, row -> row.id }) { index, row ->
            val isCurrent = row.id == currentId
            val isCursor = index == cursorIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SETTINGS_ROW_SHAPE)
                    .background(
                        if (isCursor) SETTINGS_ROW_SELECTED_FILL else Color.Transparent,
                        SETTINGS_ROW_SHAPE,
                    )
                    .then(
                        if (isCursor) {
                            Modifier.border(1.dp, SettingsRowSelectedEdgeBrush, SETTINGS_ROW_SHAPE)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onPick(row) }

                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title,

                    color = if (isCurrent) SettingsText
                            else SettingsText.copy(alpha = SETTINGS_RAIL_INACTIVE_ALPHA),
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = SettingsTextShadow),
                )
            }
        }
    }
}

private const val SETTINGS_RAIL_INACTIVE_ALPHA = 0.42f

@Composable
fun SettingsGroup(title: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 28.dp, bottom = 8.dp),
        text = title.uppercase(),
        color = SettingsSubtext,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.4.sp,
        style = TextStyle(shadow = SettingsTextShadow),
    )
}

class SettingsRowAction(
    val label: String,
    val onClick: () -> Unit,
    val onLongPress: (() -> Unit)? = null,

    val actionFocusBackgroundColor: Color = Color.White.copy(alpha = 0.25f),
    val icon: @Composable () -> Unit,
)

private const val DISABLED_ROW_ALPHA = 0.4f

@Composable
fun SettingsRow(
    label: String,
    sublabel: String? = null,

    value: String? = null,
    focusKey: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,

    actions: List<SettingsRowAction> = emptyList(),

    onFocusChangedExternal: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,

    hideRowHighlightOnActionFocus: Boolean = false,

    enabled: Boolean = true,
) {
    val click = onClick?.takeIf { enabled }
    val help = LocalSettingsHelp.current
    val actionFocusCount = remember { mutableIntStateOf(0) }
    val anyActionFocused = actionFocusCount.intValue > 0
    val focusTracker = LocalSettingsFocusTracker.current
    val touchInput = LocalSettingsTouchInput.current
    val cursorVisible = LocalSettingsCursorVisible.current
    val reportFocused = LocalSettingsReportFocused.current
    var isFocused by remember { mutableStateOf(false) }

    val row = rememberControllerRowRegistration(
        prefix = "row",
        focusKey = focusKey,
        claimInitialFocus = click != null,
        selectable = click != null,
        onSelect = click,
        onLongPress = onLongPress,
        trailingActionsFor = { rowKey ->
            actions.mapIndexed { index, action ->
                ControllerNavItem(
                    key = "$rowKey:action:$index",
                    focusable = true,
                    selectable = true,
                    enabled = true,
                    onSelect = action.onClick,
                    onLongPress = action.onLongPress,
                )
            }
        },
    )

    val rowSelected =
        isFocused && cursorVisible && !(hideRowHighlightOnActionFocus && anyActionFocused)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(row.focusRequester)

            .then(row.positionReporting)

            .pointerInput(row.rowKey, click, onLongPress) {
                detectTapGestures(
                    onTap = { touchInput(); click?.invoke() },
                    onLongPress = { touchInput(); onLongPress?.invoke() },
                )
            }
            .onFocusChanged { state ->
                isFocused = state.isFocused
                onFocusChangedExternal?.invoke(state.isFocused)
                if (state.isFocused) {
                    focusTracker(click)
                    reportFocused(row.focusRequester)

                    help.value = null
                    Timber.d("Settings focus: row=\"$label\" clickable=${click != null}")
                }
            }

            .padding(horizontal = 40.dp)
            .settingsSelectedPlate(rowSelected)
            .focusable()

            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = (if (rowSelected) Color.White else SettingsText)
                    .let { if (enabled) it else it.copy(alpha = it.alpha * DISABLED_ROW_ALPHA) },

                fontSize = XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = TextStyle(shadow = SettingsTextShadow),
            )
        }
        if (value != null) {
            Spacer(Modifier.width(24.dp))
            Text(
                text = value,

                color = (if (rowSelected) Color.White else SettingsSubtext)
                    .let { if (enabled) it else it.copy(alpha = it.alpha * DISABLED_ROW_ALPHA) },

                fontSize = XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.End,
                style = TextStyle(shadow = SettingsTextShadow),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                actions.forEachIndexed { index, action ->
                    key(index) {
                        SettingsRowActionButton(
                            rowKey = row.rowKey,
                            index = index,
                            action = action,
                            onFocusedChanged = { focused ->
                                if (focused) actionFocusCount.intValue++
                                else actionFocusCount.intValue--
                            },
                        )
                    }
                }
            }
        }
      }

        if (rowSelected && !sublabel.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = sublabel,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                style = TextStyle(shadow = SettingsTextShadow),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SettingsFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusKey: String? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val focusTracker = LocalSettingsFocusTracker.current
    val touchInput = LocalSettingsTouchInput.current
    val reportFocused = LocalSettingsReportFocused.current
    val help = LocalSettingsHelp.current
    var isFocused by remember { mutableStateOf(false) }

    val row = rememberControllerRowRegistration(
        prefix = "custom",
        focusKey = focusKey,
        claimInitialFocus = true,
        selectable = true,
        onSelect = onClick,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(row.focusRequester)
            .then(row.positionReporting)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    focusTracker(onClick)
                    reportFocused(row.focusRequester)

                    help.value = null
                }
            }
            .pointerInput(onClick) {
                detectTapGestures(onTap = { touchInput(); onClick() })
            }
            .focusable(),
    ) {
        content(isFocused)
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    sublabel: String? = null,
    focusKey: String? = null,
    leading: @Composable (() -> Unit)? = null,
    onFocusChangedExternal: ((Boolean) -> Unit)? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SettingsRow(
        label = label,
        sublabel = sublabel,
        focusKey = focusKey,
        leading = leading,
        onFocusChangedExternal = onFocusChangedExternal,

        onClick = { onToggle(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SettingsAccent,
                    uncheckedThumbColor = SettingsSubtext,
                    uncheckedTrackColor = SettingsDivider,
                ),
            )
        },
    )
}

@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    sublabel: String? = null,
    focusKey: String? = null,
    onFocusChangedExternal: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        label = label,
        sublabel = sublabel,
        value = value,
        focusKey = focusKey,
        onFocusChangedExternal = onFocusChangedExternal,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SettingsPickerRow(
    label: String,
    options: List<SettingsPickerOption>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    sublabel: String? = null,
    focusKey: String? = null,
    enabled: Boolean = true,
) {
    val picker = LocalSettingsPicker.current

    var anchorY by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.onGloballyPositioned { anchorY = it.localToRoot(Offset.Zero).y }) {
        SettingsRow(
            label = label,
            sublabel = sublabel,
            value = options.getOrNull(selectedIndex)?.label ?: "",
            focusKey = focusKey,
            enabled = enabled,
            onClick = {
                picker.value = SettingsPickerRequest(
                    title = label,
                    options = options,
                    selectedIndex = selectedIndex,
                    onPick = onPick,
                    anchorY = anchorY,
                )
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    focusKey: String? = null,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    helper: String? = null,

    helperPrompt: ControllerPromptItem? = null,
    enabled: Boolean = true,
) {
    val focusTracker = LocalSettingsFocusTracker.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reportFocused = LocalSettingsReportFocused.current
    val help = LocalSettingsHelp.current
    var editing by remember { mutableStateOf(false) }

    val row = rememberControllerRowRegistration(
        prefix = "field",
        focusKey = focusKey,
        claimInitialFocus = true,
        selectable = enabled,
        enabled = enabled,
        onSelect = { editing = true },
    )
    val fr = row.focusRequester

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            runCatching { fr.requestFocus() }
            withFrameNanos { }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp)
            .then(row.positionReporting),
    ) {
        Text(
            text = label,
            color = SettingsSubtext,
            fontSize = 12.sp,
            style = TextStyle(shadow = SettingsTextShadow),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = !editing,
                singleLine = singleLine,
                placeholder = { Text(placeholder, color = SettingsSubtext) },
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { editing = false }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SettingsText,
                    unfocusedTextColor = SettingsText,
                    focusedBorderColor = SettingsAccent,
                    unfocusedBorderColor = SettingsDivider,
                    cursorColor = SettingsAccent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fr)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            focusTracker { editing = true }
                            reportFocused(fr)
                            help.value = helper
                        } else {
                            editing = false
                        }
                    },
            )

            if (!editing && enabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { editing = true } },
                )
            }
        }
        if (!helper.isNullOrBlank() || helperPrompt != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!helper.isNullOrBlank()) {
                    Text(
                        text = helper,
                        color = SettingsSubtext.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        style = TextStyle(shadow = SettingsTextShadow),
                    )
                }
                if (helperPrompt != null) {
                    PfpControllerHints(
                        items = listOf(helperPrompt),
                        style = ControllerHintStyle.INLINE,
                    )
                }
            }
        }
    }
}
