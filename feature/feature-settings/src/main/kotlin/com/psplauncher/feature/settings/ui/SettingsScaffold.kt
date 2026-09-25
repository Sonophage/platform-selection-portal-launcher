package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.drawBehind
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

// ── CompositionLocals — provided by SettingsNavHost, consumed by SettingsScaffold ──

/**
 * Which settings screen is on, so the scaffold can draw the rail of its siblings. Null for a
 * screen opened from somewhere other than the settings tree (a deep link, the wizard), which
 * simply gets no rail.
 */
val LocalSettingsScreenId = compositionLocalOf<String?> { null }

/** Opens a sibling screen from the rail. A no-op by default so previews and tests need nothing. */
val LocalSettingsOpenScreen = compositionLocalOf<(String) -> Unit> { {} }

val LocalSettingsPendingAction = compositionLocalOf<GamepadAction?> { null }
val LocalSettingsActionConsumed = compositionLocalOf<() -> Unit> { {} }

/** Reports pointer input so the host can hide controller-only cursor decoration. */
val LocalSettingsTouchInput = compositionLocalOf<() -> Unit> { {} }
/** Host-level touch callback used by the fullscreen settings hint gate. */
val LocalSettingsHostTouchInput = compositionLocalOf<() -> Unit> { {} }
val LocalSettingsShowControllerHint = compositionLocalOf { false }

/**
 * Runs a tapped prompt on the settings footer, supplied once by SettingsNavHost.
 *
 * Ambient for the same reason [LocalControllerPromptStyle] is: the footer is chrome the scaffold
 * draws, and threading a dispatcher through every settings screen's signature to reach it would
 * put the parameter on dozens of screens that never mention the footer.
 */
val LocalSettingsPromptAction = compositionLocalOf<((GamepadAction) -> Unit)?> { null }
/**
 * Settings ▸ Controller ▸ Left Backs Out. When on, D-pad LEFT on a row that has no inline actions
 * leaves the screen instead of doing nothing. Defaults to true, matching the stored preference, so
 * previews and tests behave like the app.
 */
val LocalSettingsLeftBacksOut = compositionLocalOf { true }

/**
 * Whether the user's most recent input anywhere in the app was touch (mirrored from
 * `XMBUiState.lastInputWasTouch`).
 *
 * A settings screen used to open with the controller cursor showing no matter how it was reached,
 * because each scaffold starts its own [cursorVisible] at true and only clears it on the NEXT touch
 * down — so a touch user saw the cursor flash onto every screen they opened, having never touched a
 * controller. Seeding from the host's input mode is what makes "I have always been in touch mode"
 * actually hold across a screen change.
 */
val LocalSettingsLastInputWasTouch = compositionLocalOf { false }
val LocalSettingsCursorVisible = compositionLocalOf { true }

/**
 * The handler of the in-window overlay a settings screen currently has open, if any.
 *
 * A settings screen that puts a prompt on top of itself has to take the pad while it is up, or
 * the cursor keeps walking the list underneath and Confirm fires the row behind the prompt. The
 * scaffold checks this before anything else and hands the action straight over.
 *
 * It is a single slot rather than a stack on purpose: two overlays open at once on one screen is
 * not a state this app has, and a stack would make "who owns the pad" a question with a history.
 * Screens register through [SettingsOverlayInput], which clears the slot when the overlay leaves
 * the composition -- so an overlay cannot keep the pad after it is gone.
 */
internal val LocalSettingsOverlayInput =
    compositionLocalOf<MutableState<((GamepadAction) -> Unit)?>?> { null }

/**
 * Gives [onAction] the pad for as long as this composable is in the tree.
 *
 * Call it from inside the `if (showing)` that draws the overlay, so registration and the overlay
 * arrive and leave together.
 */
@Composable
fun SettingsOverlayInput(onAction: (GamepadAction) -> Unit) {
    // Loud on absence, not silent. A missing slot means this overlay is composed outside
    // SettingsNavHost, and the consequence would be that it draws perfectly and takes no input at
    // all -- the exact trap the AlertDialogs had. A crash here is a better outcome than that.
    val slot = LocalSettingsOverlayInput.current
        ?: error("SettingsOverlayInput must be composed inside SettingsNavHost")
    val current by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        slot.value = { action -> current(action) }
        onDispose { slot.value = null }
    }
}

// Internal tracker: rows register their onClick when they gain focus so the scaffold
// can invoke the right action on a controller SELECT press.
internal val LocalSettingsFocusTracker =
    compositionLocalOf<((() -> Unit)?) -> Unit> { {} }

// Internal registry: rows that declare a focusKey register a FocusRequester here so the
// scaffold can restore focus to a specific row (the one that opened a child screen) when
// returning, instead of always snapping back to the first row.
internal val LocalSettingsFocusRegistry =
    compositionLocalOf<SnapshotStateMap<String, FocusRequester>> { mutableStateMapOf() }

// Internal registrar: the first interactive row to compose reports its FocusRequester here so
// the scaffold can place initial focus on a real, laid-out row. (The old 0dp bootstrap box
// never reliably gained focus, leaving menus opening with nothing selected.)
internal val LocalSettingsRegisterFirstFocusable =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

// Explicit vertical navigation. Rows register (FocusRequester, ControllerNavItem) pairs in
// composition order; ControllerNavigationState owns movement and selection, and the scaffold
// requests focus for the model's focused key. Coordinates are a presentation concern only
// (scroll-into-view, reseed fallback) — never directional moveFocus, which escapes into the
// XMB's focusable items behind the overlay (proven by logs: canFocus inheritance does not
// reach the XMB's LazyColumn across subcompositions).
internal val LocalSettingsRowPositions =
    compositionLocalOf<SnapshotStateMap<FocusRequester, Float>?> { null }
// On-screen HEIGHT of each row, used for keep-in-view clamping by the row's bottom edge. Kept
// separate from [LocalSettingsRowPositions] (row tops) because the engine's geometry and the
// reseed fallback both consume the top-Y map as-is.
internal val LocalSettingsRowSizes =
    compositionLocalOf<SnapshotStateMap<FocusRequester, Float>?> { null }
internal val LocalSettingsNavigationOrder =
    compositionLocalOf<SnapshotStateList<Pair<FocusRequester, ControllerNavItem>>?> { null }

// Controller-reachable inline actions (e.g. a root row's Replace/Remove buttons). Keyed by the
// owning row's navigation key; each entry is (actionKey, FocusRequester) in visual order. They
// are reached via LEFT/RIGHT and never participate in vertical traversal.
internal val LocalSettingsRowActions =
    compositionLocalOf<SnapshotStateMap<String, SnapshotStateList<Pair<String, FocusRequester>>>?> { null }

// The scrollable Column inside each screen is the real scroll owner. SettingsScaffold registers
// it here so controller boundary navigation resets that exact state rather than an unrelated
// scaffold-local ScrollState.
internal val LocalSettingsScrollStateRegistrar =
    compositionLocalOf<(ScrollState) -> Unit> { {} }

internal val LocalSettingsScrollToTop =
    compositionLocalOf<() -> Unit> { {} }
internal val LocalSettingsReportFocused =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

// Slider nodes (SettingsSliderRow): a normal navigable row that becomes value-adjustable after
// SELECT. The scaffold holds the node of the slider currently in adjust mode and hands LEFT/RIGHT
// to it; SELECT/BACK exit adjust (BACK is consumed, never popping the screen); UP/DOWN exit adjust
// and then move normally. Null while no slider is being adjusted.
internal val LocalSettingsEnterSliderMode =
    compositionLocalOf<(SettingsSliderNode) -> Unit> { {} }
internal val LocalSettingsSliderAdjusting = compositionLocalOf { false }

// Rows report leaving composition. If the FOCUSED row is removed (a list item deleted, a
// section re-rendered), Compose silently clears focus and the menu goes dead until the user
// presses a direction — the scaffold uses this signal to refocus the nearest surviving row.
internal val LocalSettingsReportRemoved =
    compositionLocalOf<(FocusRequester) -> Unit> { {} }

// Focus was lost (its row left composition): land on the row nearest the last focused Y so the
// cursor reappears where the user was, not at the top of the screen.
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

// ── Colors ────────────────────────────────────────────────────────────────────

// Accent is a composable GETTER for the same reason the two text roles below are: it must follow
// the user's chosen theme. It read PfpPalette.Accent until 2026-09-19, which is a CONSTANT — a
// hardcoded blue evaluated once at class load — so every accent in Settings stayed that blue while
// the XMB next door repainted. Fifteen files read this.
val SettingsAccent: Color
    @Composable get() = LocalPFPColors.current.accentColor

// The two text roles are composable GETTERS, not constants: they read the resolved palette out of
// LocalPfpTextColors, so a user font colour (or a backdrop-driven clamp) repaints ~60 call sites
// with no edit at any of them. Both still carry white / PfpPalette.Subtext today.
//
// A read from outside composition will not compile. That is the feature — the compiler enumerates
// the sites that need an explicit parameter instead of leaving us to grep for them.
val SettingsText: Color
    @Composable get() = LocalPfpTextColors.current.primary
val SettingsSubtext: Color
    @Composable get() = LocalPfpTextColors.current.secondary

// Directional drop shadow for text over the translucent backdrop: the settings scrim is a
// translucent theme gradient (the wallpaper reads through BY DESIGN), so flat gray helper text
// washes out wherever the wallpaper is bright. The repo's standard black drop shadow
// (PspContextMenu, ControllerHintBar, DetailContextMenu) restores separation without hiding
// the wallpaper behind a heavier scrim.
val SettingsTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)
// Divider stays neutral: it separates rows rather than signalling anything, and a tinted hairline
// reads as decoration at every accent.
val SettingsDivider = com.psplauncher.core.ui.theme.PfpPalette.Divider

/**
 * How wide the settings column is allowed to get.
 *
 * Chosen against the device this is built for: 1920x1080 at 374dpi is 821dp of landscape width,
 * so this leaves roughly a third of the screen showing the wallpaper, which is the XMB's own
 * proportion. Wider than any phone in portrait, so it never constrains a small screen.
 */
val SETTINGS_COLUMN_MAX_WIDTH = 560.dp

/** The section rail's width, including its left margin. */
private val SETTINGS_RAIL_WIDTH = 216.dp

/**
 * The focused row's explanation, shown ONCE at the bottom of the screen instead of under every
 * row.
 *
 * This is how a PS3 settings list works, and it is not only a style choice: with 212 helper
 * lines in this app, drawing each one under its own row produced a page that was mostly small
 * grey text, and the thing you were actually on had no more presence than the eleven things you
 * were not. One band, for the row you are on, restores the ratio -- and roughly doubles how many
 * settings fit on a screen, because rows become a single line.
 *
 * A MutableState rather than a value, because the writer is the focused ROW, deep inside
 * content(), and the reader is the scaffold that composed it.
 */
val LocalSettingsHelp = compositionLocalOf { mutableStateOf<String?>(null) }

// The focused row's plate: a neutral lift, no hue. See SettingsRow for why it is colourless.
private val SETTINGS_ROW_SHAPE = RoundedCornerShape(10.dp)
/**
 * The focused row's fill — almost nothing, on purpose.
 *
 * Measured off the PS5 reference rather than chosen: inside the focused row's outline the image
 * reads 30-45 luminance against 25-37 just outside it. Five points. There is no glass there, and
 * 0.10 white was reading as a lit panel instead of a cursor.
 */
private val SETTINGS_ROW_SELECTED_FILL = Color.White.copy(alpha = 0.035f)

/**
 * The focused row's outline, dim on the left and bright on the right.
 *
 * Also measured: the same border in that capture peaks at 114 on its left edge and 125 on its
 * right, about 2px either side. A single flat colour is what made ours look heavier than the
 * reference — the weight is in the gradient, not in the thickness.
 */
private val SETTINGS_ROW_SELECTED_EDGE_START = Color.White.copy(alpha = 0.16f)
private val SETTINGS_ROW_SELECTED_EDGE_END = Color.White.copy(alpha = 0.38f)

/** The outline as a left-to-right brush. */
private val SettingsRowSelectedEdgeBrush: Brush
    get() = Brush.horizontalGradient(
        listOf(SETTINGS_ROW_SELECTED_EDGE_START, SETTINGS_ROW_SELECTED_EDGE_END),
    )

/**
 * The focused row's plate: the one definition of what "the cursor is here" looks like.
 *
 * Pulled out of [SettingsRow] when the Icon Color swatch strip -- a focusable row that is not a
 * SettingsRow -- turned out to draw no plate at all. Controller focus DID reach it; there was
 * simply nothing to see, and since the cursor lands on the first swatch, which is also the
 * selected one, nothing on screen changed when it arrived. It read as an unreachable row.
 *
 * Anything focusable that is not a SettingsRow uses this rather than copying the three
 * modifiers, so the plate can only ever look like one thing.
 */
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

/**
 * The picker's own shape and edges, squarer and flatter than a settings row's.
 *
 * A dropdown is a panel that opens ON something, not a card that sits in a list, and the
 * reference draws it as a near-rectangle with one hairline. The row plate's 10dp radius and
 * gradient edge belong to rows; reusing them here made the panel read as a very large row.
 */
private val PICKER_SHAPE = RoundedCornerShape(4.dp)
private val PICKER_EDGE = Color.White.copy(alpha = 0.22f)
private val PICKER_FOCUS_EDGE = Color.White.copy(alpha = 0.55f)

/** One choice offered by a [SettingsPickerRow]. */
data class SettingsPickerOption(val label: String, val help: String? = null)

/**
 * An open picker. Null when none is.
 *
 * The scaffold owns this, not the row that opened it, for the same reason it owns slider adjust
 * mode: while a picker is up it must take EVERY controller action before navigation sees any of
 * them, and the only thing in the tree that can do that is the scaffold's action handler.
 */
internal class SettingsPickerRequest(
    val title: String,
    val options: List<SettingsPickerOption>,
    val selectedIndex: Int,
    val onPick: (Int) -> Unit,
    /** Root-space Y of the row that opened it, so the list appears where the value was. */
    val anchorY: Float,
)

internal val LocalSettingsPicker =
    compositionLocalOf { mutableStateOf<SettingsPickerRequest?>(null) }

/** Two lines at [SETTINGS_HELP_TEXT_SP], reserved whether or not there is anything to say. */
private const val SETTINGS_HELP_TEXT_SP = 13

/**
 * Margin kept between a focused row and either edge of the content viewport, and — the same value
 * on purpose — the height of the fade at the bottom of that viewport. Tying them together is what
 * makes the fade safe: keep-in-view parks a focused row's bottom edge exactly where the fade
 * starts, so a row the cursor is on is never dimmed, while a row scrolled under the fold dissolves
 * instead of being sliced by the helper footer's divider.
 */
internal val CONTENT_EDGE_MARGIN = 16.dp

/**
 * Test handle for the scrolling content viewport.
 *
 * The navigation tests used to derive the viewport's bottom edge from the footer prompt's
 * position minus a hand-written 20.dp. Both halves were wrong: the prompt is chrome and moved
 * when the chrome did, and the real inset is [CONTENT_EDGE_MARGIN], which is 16.dp. The tests
 * passed on the slack between the two. They measure this box now, and subtract the constant.
 */
internal const val SettingsContentViewportTag = "settings_content_viewport"

// ── Standard helper footer ────────────────────────────────────────────────────

val SettingsDefaultHelperItems = listOf(
    ControllerPromptItem(GamepadAction.SELECT, "Enter"),
    ControllerPromptItem(GamepadAction.BACK, "Back"),
)

/**
 * The button-hint pill every ordinary fullscreen settings screen carries, at the right-hand end
 * of the help band.
 *
 * It had a full-width strip of its own below that band, with a divider and centred prompts, and
 * the strip stayed measured whether or not the hint was showing: the hint faded in on an idle
 * pause, and a strip that collapsed would have shifted every row above it. The hints do not wait
 * for a pause any more, and sharing the band that was already reserved means there is no height
 * to collapse in the first place.
 *
 * The wizard still supplies its own themed footer through the scaffold's chrome override, and
 * this is not drawn when it does.
 */


// ── Scaffold ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // When set, focus is restored to the row whose focusKey matches (used when returning
    // from a child screen). When null, focus starts at the first interactive row.
    restoreFocusKey: String? = null,
    // When set, called with every incoming action BEFORE normal navigation handling.
    // Return true to consume the action (suppresses back/select/focus movement).
    // Used by ControllerSettingsScreen to capture button presses during remap mode.
    onInterceptAction: ((GamepadAction) -> Boolean)? = null,
    onTouchInput: () -> Unit = {},
    // ── Chrome overrides — the first-run wizard's PSP skin (see WizardScaffold) ──
    // Replaces the ◀ breadcrumb header (the wizard draws a step badge + title instead).
    header: (@Composable () -> Unit)? = null,
    // Hides the divider under the header (the wizard header has its own bottom rule).
    showDivider: Boolean = true,
    // Light scrim: the XMB wave reads through instead of sitting behind a dark overlay.
    lightScrim: Boolean = false,
    // Replaces the scrim entirely — the screen brings its own background rather than tinting
    // whatever the XMB happens to be showing. Only the wizard uses it, because only the wizard
    // is a place you arrive at rather than a panel over the crossbar: a first run must not
    // depend on the wallpaper behind it, and the wizard's own black + wave is that independence.
    // [lightScrim] is ignored when this is set.
    backdrop: (@Composable () -> Unit)? = null,
    // The section rail, off for a screen that is a flow rather than a place in the catalog. The
    // wizard is listed in the catalog (Settings ▸ System ▸ Setup Wizard) and therefore gets a
    // rail by default — which on a first run is a column offering Logs and Credits to someone
    // who has not finished telling the launcher where their games are.
    showRail: Boolean = true,
    // Pinned footer override for the first-run wizard's themed Enter/Back chrome. Ordinary
    // fullscreen settings screens leave this null and receive the standard helper section.
    footer: (@Composable () -> Unit)? = null,
    // Contextual prompts may replace the standard Enter/Back pair (Audio uses this for the
    // focused sound assignment); the footer section and its reserved height remain shared.
    helperFooterItems: List<ControllerPromptItem> = SettingsDefaultHelperItems,
    // Identity of what [content] currently shows. Ordinary settings screens leave this null —
    // their content is fixed for the life of the scaffold, so mount-time focus is the whole story.
    // The wizard passes its SetupStep: each page is a fresh screen inside one scaffold, and a
    // change here re-runs initial focus so the new page's own first row takes the cursor.
    contentKey: Any? = null,
    content: @Composable () -> Unit,
) {
    // Settings ▸ Controller ▸ Left Backs Out, supplied by SettingsNavHost from the XMB's state.
    val leftBacksOut = LocalSettingsLeftBacksOut.current
    val focusManager = LocalFocusManager.current
    // The screen content owns the actual verticalScroll state. All focus visibility and boundary
    // operations use this registered state so touch scrolling and controller navigation share one
    // scroll owner.
    val contentScrollState = remember { mutableStateOf<ScrollState?>(null) }

    val bootstrapFR = remember { FocusRequester() }
    val pendingAction = LocalSettingsPendingAction.current
    val onConsumed = LocalSettingsActionConsumed.current
    // The host callback is provided once by SettingsNavHost so every screen reports touch input;
    // an explicit callback remains available for direct previews/tests and specialized screens.
    val hostTouchInput = LocalSettingsHostTouchInput.current
    val notifyTouchInput = {
        onTouchInput()
        hostTouchInput()
    }
    // Menu backdrop is tinted by the user's chosen color scheme (the same background anchors
    // the XMB wave uses), so settings screens match the theme instead of a flat black panel.
    val pfpColors = LocalPFPColors.current

    // Solved once per theme, not per recomposition: each anchor is bisected 12 times. The App
    // Drawer draws the same two anchors (via storefrontColorsFor), which is why they are derived
    // in one place rather than repeated here.
    val (scrimTop, scrimBottom) = remember(pfpColors.backgroundTop, pfpColors.backgroundBottom) {
        xmbScrimAnchors(pfpColors.backgroundTop, pfpColors.backgroundBottom)
    }

    // Tracks the onclick of whichever row currently has controller focus
    val focusedRowClick = remember { mutableStateOf<(() -> Unit)?>(null) }
    // Declarative navigation model: owns the focused key, ordered movement and selection.
    // Rows feed it items via the ordered registration list below.
    val navigationState = remember { ControllerNavigationState() }
    // Settings made no sound at all. Every other surface in the launcher ticks as the cursor
    // moves and clicks when it opens something; this one dispatch is shared by every settings
    // screen, so the noises belong here rather than in each of them.
    val menuSounds = com.psplauncher.core.ui.sound.LocalMenuSounds.current
    // The slider currently in adjust mode (see LocalSettingsEnterSliderMode). Written by the
    // focused row's SELECT and read by the action handler below + the adjusting flag provided
    // down to rows so the active slider can paint itself.
    val sliderNodeState = remember { mutableStateOf<SettingsSliderNode?>(null) }
    // Seeded from the host's input mode: opening a screen by touch must not summon the cursor.
    val lastInputWasTouch = LocalSettingsLastInputWasTouch.current
    val cursorVisible = remember { mutableStateOf(!lastInputWasTouch) }
    // The focused row's explanation. Owned here because the band that shows it is chrome, a
    // sibling of the scrolling body, and cannot read anything the body composed.
    val helpText = remember { mutableStateOf<String?>(null) }
    // The open picker and the cursor inside it. Cursor lives beside the request rather than in it
    // so re-opening the same setting always starts on its current value.
    // The section rail: the current section's screens, and whether the cursor is in it. The
    // section itself is the page title; the shoulders move between sections.
    //
    // Only for a screen that IS one of the catalog's screens. A deep link or the wizard has no
    // siblings to show, and an empty rail would be a column of nothing holding the content in.
    val screenId = LocalSettingsScreenId.current
    val openScreen = LocalSettingsOpenScreen.current
    val railEntries = remember(screenId, showRail) {
        if (showRail) com.psplauncher.core.domain.model.settingsRailRows(screenId) else emptyList()
    }
    val railFocused = remember { mutableStateOf(false) }
    // Filled by SettingsOverlayInput while a screen has a prompt on top of itself. Provided by
    // SettingsNavHost rather than created here: the prompts are siblings of this scaffold, so a
    // slot created here would not be in scope where they register.
    val overlayInput = LocalSettingsOverlayInput.current
    val railCursor = remember(screenId) {
        mutableIntStateOf(railEntries.indexOfFirst { it.id == screenId }.coerceAtLeast(0))
    }

    val pickerState = remember { mutableStateOf<SettingsPickerRequest?>(null) }
    val pickerCursor = remember { mutableIntStateOf(0) }
    // Root-space centre of the visible content viewport. Touch scrolling hides the cursor;
    // the next controller action reanchors focus to the closest visible node instead of resuming
    // the previously focused (possibly off-screen) row.
    val touchScrolled = remember { mutableStateOf(false) }

    // Per-row FocusRequesters keyed by focusKey, for focus-restoration on child return.
    val focusRegistry = remember { mutableStateMapOf<String, FocusRequester>() }

    // FocusRequester of the first interactive row — the reliable initial-focus target.
    val firstRowFocus = remember { mutableStateOf<FocusRequester?>(null) }

    // A new [contentKey] means the whole content was swapped (a wizard page turn): re-open the
    // first-focusable slot so the incoming page's own first row claims it, instead of the cursor
    // staying latched to the outgoing page's now-disposed requester.
    //
    // Deliberately a `remember` and not a LaunchedEffect: this must clear BEFORE the new rows run
    // their registration effects, and it does — the scaffold's own composition runs ahead of
    // content()'s, whereas a LaunchedEffect body is dispatched and can land after those effects,
    // wiping the latch the new page just set.
    remember(contentKey) { firstRowFocus.value = null; contentKey }

    // On-screen Y of every interactive row — presentation only (scroll-into-view, reseed
    // fallback). Movement and selection live in ControllerNavigationState.
    val rowPositions = remember { mutableStateMapOf<FocusRequester, Float>() }
    // On-screen HEIGHT of every interactive row — presentation only, for the keep-in-view
    // clamp to align a row's bottom edge (rather than its top) at the lower viewport edge.
    val rowSizes = remember { mutableStateMapOf<FocusRequester, Float>() }
    // Ordered navigation: rows register (FocusRequester, ControllerNavItem) pairs in composition
    // order; the model consumes them and the scaffold requests focus for the focused key.
    val navigationOrder =
        remember { androidx.compose.runtime.mutableStateListOf<Pair<FocusRequester, ControllerNavItem>>() }
    // Row key -> its inline action (key, FocusRequester) pairs, for LEFT/RIGHT navigation.
    val rowActionFrs = remember {
        mutableStateMapOf<String, SnapshotStateList<Pair<String, FocusRequester>>>()
    }

    // Keep the model's item list in lockstep with what rows register. Rows add/remove/refresh
    // their pairs; snapshotFlow observes any change and the model preserves the focused key
    // (recovering to the nearest survivor if the focused item disappears).
    //
    // The list is ordered by on-screen Y — NOT registration order. Rows register on first
    // composition, so when data loads asynchronously and rows insert mid-list (a fresh Library
    // Manager open: placeholder rows first, then root paths and console cards), the registration
    // list ends up scrambled and the cursor would jump past the inserted rows. Every row in the
    // composed Column has a known Y, so sorting by it makes traversal follow what the user sees.
    // Unpositioned rows (not yet laid out) keep registration order via the stable sort.
    LaunchedEffect(navigationOrder, rowPositions) {
        snapshotFlow {
            // Reading rowPositions subscribes the flow to layout changes, so the order re-sorts
            // the moment a late row lands (or everything scrolls by the same delta — a no-op).
            // The geometry map is emitted alongside so position changes re-trigger the flow even
            // when the visual order itself is unchanged (rows already in order) — the engine's
            // touch re-anchor needs the Y positions, not just the ordering.
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
    // Absolute content position at the top of the scroll viewport. Used as the stable anchor
    // when clamping at the first item; unlike the focused row's moving Y it remains valid while
    // the column is scrolled.
    val firstVisibleContentY = remember { mutableStateOf<Float?>(null) }
    // Height of the visible content viewport (px), measured from the content Box. Keep-in-view
    // and re-anchoring use this REAL on-screen area — not the full configured screen height,
    // which includes system bars and would let the cursor walk past the visible edge.
    val contentViewportHeight = remember { mutableStateOf<Float?>(null) }
    var focusedRow by remember { mutableStateOf<FocusRequester?>(null) }
    // Last known Y of the focused row — the anchor for re-focusing when that row is removed
    // from composition (imported list items, sections that re-render away).
    var lastFocusedY by remember { mutableStateOf<Float?>(null) }
    var refocusTick by remember { mutableIntStateOf(0) }

    // Set true once any row has actually received focus (the menu is no longer "dead").
    var focusRedirected by remember { mutableStateOf(false) }

    // Assign controller focus the moment the menu mounts. Rather than a single delayed
    // attempt (which silently fails if the focusable subtree isn't laid out yet, leaving the
    // menu visible with nothing focused), we re-issue requestFocus() every frame until a row
    // actually gains focus. This guarantees focus + visible highlight appear immediately with
    // no directional input. When restoreFocusKey is set we target that specific row (returning
    // from a child); otherwise we redirect Down from the top so a fresh open always starts at
    // the first item with no stale focus restored.
    LaunchedEffect(Unit) {
        Timber.d("Settings focus: screen opened ($title / $subtitle) restoreKey=$restoreFocusKey")
        var attempts = 0
        // Keep trying until a row actually takes focus. The target is the row we're restoring
        // to (returning from a child) or the first interactive row; both are real, laid-out
        // FocusRequesters. They may not be registered on the very first frame, so we retry —
        // falling back to the 0dp bootstrap only until the real target appears.
        while (!focusRedirected && attempts < 30) {
            withFrameNanos { /* wait for this frame's layout pass */ }
            val target =
                if (restoreFocusKey != null) focusRegistry[restoreFocusKey] else firstRowFocus.value
            if (target != null) {
                runCatching { target.requestFocus() }
            } else {
                runCatching { bootstrapFR.requestFocus() }
            }
            attempts++
        }
        // Last resort if nothing took focus: first row, then bootstrap.
        if (!focusRedirected) {
            (firstRowFocus.value ?: bootstrapFR).let { runCatching { it.requestFocus() } }
        }
        Timber.d("Settings focus: default focus assigned=$focusRedirected after $attempts frame(s) ($subtitle)")
    }

    // Companion to the latch reset above: a frame later the new page's rows have registered, so
    // hand its first row the cursor. Only for content-swapping scaffolds — everything else keeps
    // the mount-only path above, which the null default leaves untouched.
    LaunchedEffect(contentKey) {
        if (contentKey == null) return@LaunchedEffect
        withFrameNanos { }
        firstRowFocus.value?.let { runCatching { it.requestFocus() } }
    }

    // The focused row left composition (e.g. a "Found Games" item just imported away, or a
    // section re-rendered): the model recovers the focused key to the nearest surviving item
    // by list order, and we request focus there so the cursor never silently disappears.
    // Runs a frame later so the new layout has settled and the row's onDispose has run.
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

    // Handle UP / DOWN / SELECT forwarded from XMBViewModel via pendingSettingsAction
    // Keep the focused row inside the REAL visible content viewport and scroll it into view.
    // The bounds come from the measured content Box (root-space Y), so the cursor can never
    // walk past the visible edge — even on devices with system bars. A small margin keeps the
    // row below the header (breadcrumb stays visible) and above the bottom edge.
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
        // Bring the WHOLE focused row inside the visible area, symmetric for both edges: the
        // row's TOP clears the top margin and its BOTTOM clears the bottom margin. Aligning by
        // the row's own relevant edge is what keeps it fully on-screen — scrolling so the TOP
        // just clears the bottom edge would leave the lower part of the cursor clipped below
        // the fold (the bug fixed here; going UP was already correct because a row extends
        // downward from its top).
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

    // Request Compose focus for the row or inline action registered under [key]; fall back to
    // reseeding by last-known Y when nothing is registered (e.g. an empty or loading screen).
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

    // A picker opens ON its current value, not at the top: the first thing you should see is
    // where you already are, and for a setting you are not changing, BACK then costs nothing.
    LaunchedEffect(pickerState.value) {
        pickerState.value?.let { pickerCursor.intValue = it.selectedIndex.coerceAtLeast(0) }
    }

    LaunchedEffect(pendingAction) {
        if (pendingAction == null) return@LaunchedEffect
        // Every controller action is a source transition, including actions intercepted by a
        // screen-specific modal/editor. Reconcile the existing stable-key focus before showing it.
        cursorVisible.value = true
        navigationState.markControllerInput()
        // Revival press: the first controller press after a touch drag. It re-anchors the
        // cursor to the row nearest the viewport centre (the content the user was looking at)
        // and shows it there — it must NOT also move. Only the NEXT directional press moves,
        // so the cursor can never step past the visible area while it is re-appearing.
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
        // Give the screen a chance to consume the action first (e.g. remap capture mode).
        // If the interceptor returns true the action is fully consumed — no navigation fires.
        if (onInterceptAction?.invoke(pendingAction) == true) {
            onConsumed()
            return@LaunchedEffect
        }
        // Revival presses are consumed by the re-anchor itself: no directional movement.
        if (revivalPress && pendingAction.isDirectional) {
            onConsumed()
            return@LaunchedEffect
        }
        // ── An overlay the screen has open owns everything ──────────────────
        // Checked before the rail and before the list: while a prompt is up, a press belongs to
        // the prompt, full stop. Nothing underneath may move.
        overlayInput?.value?.let { handle ->
            handle(pendingAction)
            onConsumed()
            return@LaunchedEffect
        }

        // ── Section switch ──────────────────────────────────────────────────
        // The shoulders step between SECTIONS, landing on the first screen of the next one.
        //
        // Ahead of the rail block, not inside it, because this has to work wherever the cursor
        // is: the rail lists one section's screens now, so with this bound only while the rail
        // has focus there would be no way out of a section from the content column.
        //
        // Nothing was displaced. The XMB's dispatcher dropped both shoulder actions on the floor
        // for settings screens (`else -> Unit`), so they had no meaning here at all.
        if (pendingAction == GamepadAction.PREV_CATEGORY || pendingAction == GamepadAction.NEXT_CATEGORY) {
            val delta = if (pendingAction == GamepadAction.NEXT_CATEGORY) 1 else -1
            com.psplauncher.core.domain.model.settingsSectionStepTarget(screenId, delta)
                ?.let(openScreen)
            onConsumed()
            return@LaunchedEffect
        }

        // ── Section rail ────────────────────────────────────────────────────
        // While the cursor is in the rail it owns vertical movement and Confirm, exactly as the
        // content list does when the cursor is there. RIGHT returns; BACK leaves the screen, so
        // the rail never becomes a place you can get stuck.
        if (railFocused.value) {
            when (pendingAction) {
                GamepadAction.NAVIGATE_UP ->
                    railCursor.intValue = (railCursor.intValue - 1).coerceAtLeast(0)
                GamepadAction.NAVIGATE_DOWN ->
                    railCursor.intValue = (railCursor.intValue + 1).coerceAtMost(railEntries.lastIndex)
                GamepadAction.NAVIGATE_RIGHT -> railFocused.value = false
                GamepadAction.SELECT -> {
                    val target = railEntries.getOrNull(railCursor.intValue)
                    // Confirming the screen you are already on just puts the cursor back in it,
                    // rather than reloading the page you can see.
                    if (target != null && target.id != screenId) openScreen(target.id)
                    else railFocused.value = false
                }
                GamepadAction.BACK -> onBack()
                else -> Unit
            }
            onConsumed()
            return@LaunchedEffect
        }

        // ── Picker panel ────────────────────────────────────────────────────
        // Fully modal: while one is open every action belongs to it, including BACK, which closes
        // the picker rather than the screen. Listed ABOVE slider adjust because a picker cannot
        // be opened from inside a slider, but a slider row can sit under an open picker.
        val openPicker = pickerState.value
        if (openPicker != null) {
            val count = openPicker.options.size
            when (pendingAction) {
                // Wrapping, like every PSP list: the options are few and a wall at each end
                // costs more presses than it saves.
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

        // ── Slider adjust mode ──────────────────────────────────────────────
        // A focused slider node (SettingsSliderRow) captures input after SELECT: LEFT/RIGHT step
        // its value (the gamepad layer supplies auto-repeat for held buttons); SELECT or BACK
        // exit back to row navigation — BACK is consumed here, so it never pops the settings
        // screen while a slider is being adjusted. UP/DOWN exit adjust and fall through to the
        // ordinary vertical movement below.
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
            // Explicit, clamped vertical navigation: focus the nearest registered row above/below
            // the current one by screen-Y. At the first/last row there is no neighbour, so focus
            // simply stays — it can NEVER wander into the XMB because we only ever requestFocus()
            // a registered settings row, never call directional moveFocus. If the current row's
            // geometry isn't known yet, re-seed on the first row rather than risk an escape.
            GamepadAction.NAVIGATE_UP -> {
                val previous = navigationState.focusedKey
                val target = navigationState.move(-1)
                // Only when the cursor actually moved. At the top boundary the list scrolls back
                // and focus stays, and a tick there would say something happened that did not.
                if (target != null && target != previous) menuSounds(MenuSound.SCROLL)
                // Clamped at the first navigable item: stay put but scroll back to the top.
                if (target != null && target == previous) {
                    // Up at the first logical item is a deliberate top-boundary action. Always
                    // return the whole scrollable settings column to offset zero, including
                    // screens whose first visible content is a section header.
                    // Bind the state to a local first: a suspend call behind `?.` becomes the
                    // lambda's boxed `Unit?` result, and the resume value (a Float from the
                    // animation) then fails the checkcast to Unit at runtime.
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
            // Inline trailing actions (e.g. a root row's Replace/Remove buttons) are reached
            // horizontally. On a row without them moveHorizontal returns null — LEFT was a silent
            // no-op there — so that null is the signal the fallthrough wants: LEFT leaves the
            // screen (or, in the wizard, steps back a page), the direction the drill-in implies.
            //
            // Everything that already uses LEFT runs EARLIER and never reaches here: slider adjust
            // mode above, and a screen's own onInterceptAction (remap capture, Themes, Sound).
            GamepadAction.NAVIGATE_LEFT -> {
                val target = navigationState.moveHorizontal(-1)
                when {
                    target != null -> { menuSounds(MenuSound.SCROLL); requestFocusFor(target) }
                    // LEFT at the left edge of the content now steps INTO the rail, which is
                    // where the eye already is: the sibling screens are drawn there. Leaving the
                    // screen is one more LEFT, or BACK, from inside it.
                    // Stepping into the rail is a change of column, not a step down a list --
                    // the same thing the crossbar calls SYSTEM_BROWSE.
                    railEntries.isNotEmpty() -> { menuSounds(MenuSound.SYSTEM_BROWSE); railFocused.value = true }
                    leftBacksOut -> { menuSounds(MenuSound.BACK); onBack() }
                }
            }

            GamepadAction.NAVIGATE_RIGHT -> {
                navigationState.moveHorizontal(1)?.let { menuSounds(MenuSound.SCROLL); requestFocusFor(it) }
            }

            GamepadAction.SELECT -> {
                menuSounds(MenuSound.SELECT)
                // The model dispatches to the focused item; the registered-click fallback only
                // fires when the model has nothing to dispatch (e.g. no rows composed yet) and
                // stays fresh through the focus tracker.
                if (!navigationState.select()) focusedRowClick.value?.invoke()
            }
            // One-level-up navigation: invoke this screen's back handler. For multi-step
            // screens that's "collapse a sub-step (else close)"; for leaf screens it closes
            // the overlay back to the XMB. Mirrors the on-screen Back button exactly.
            GamepadAction.BACK -> {
                menuSounds(MenuSound.BACK)
                onBack()
            }

            else -> Unit
        }
        onConsumed()
    }

    CompositionLocalProvider(
        // `&& !railFocused` is the whole two-cursors fix.
        //
        // The rail draws its cursor as a filled plate with a border, and so does a content row.
        // Nothing stopped both from being on screen at once, so stepping LEFT into the rail left
        // the page wearing two identical cursors and no way to tell which one the D-pad moved.
        // Verified on the device: Settings > Library Manager, LEFT once.
        //
        // Exactly one control on screen is focused, and it always looks the same. The content
        // keeps its Compose focus and its scroll position -- only the drawing stands down -- so
        // RIGHT brings the cursor back to the row it left.
        //
        // The help band is deliberately NOT affected: it reads cursorVisible directly, so it goes
        // on describing the screen you are standing in while you pick a sibling out of the rail.
        LocalSettingsCursorVisible provides (cursorVisible.value && !railFocused.value),
        // Marking focusRedirected here means ANY row gaining focus stops the bootstrap loop,
        // covering both the first-row redirect and the restore-to-key path.
        LocalSettingsFocusTracker provides { click ->
            focusedRowClick.value = click; focusRedirected = true
        },
        LocalSettingsTouchInput provides {
            cursorVisible.value = false
            touchScrolled.value = true
            navigationState.markTouchInput()
            // Any pointer activity hands control back to touch: a finger on the slider (or
            // anywhere else) ends controller adjust mode.
            sliderNodeState.value = null
            notifyTouchInput()
        },
        LocalSettingsHelp provides helpText,
        LocalSettingsPicker provides pickerState,
        LocalSettingsFocusRegistry provides focusRegistry,
        // First clickable row to compose wins the initial-focus slot.
        LocalSettingsRegisterFirstFocusable provides { fr ->
            if (firstRowFocus.value == null) firstRowFocus.value = fr
        },
        LocalSettingsRowPositions provides rowPositions,
        LocalSettingsRowSizes provides rowSizes,
        LocalSettingsNavigationOrder provides navigationOrder,
        LocalSettingsReportFocused provides { fr ->
            focusedRow = fr
            rowPositions[fr]?.let { lastFocusedY = it }
            // Keep the model's focused key aligned with real Compose focus (initial focus,
            // restore-to-key, touch): movement and selection both read from the model.
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
                // Any touch anywhere on the screen marks the input source as touch (hides the
                // controller cursor, re-anchors focus) — a pure probe, consuming nothing, in the
                // same form the detail screens use (GameDetailScreen / VideoDetailScreen /
                // AppDetailScreen).
                //
                // This used to consume every moved change. That never actually killed a drag —
                // PointerEventPass.Main dispatches child-first, so the scrolling bodies had already
                // claimed their drags before this loop saw them — but with dragToScroll now
                // attached to the scaffold's chrome, "who consumed what on which pass" stops being
                // academic, and a root-level blanket consume is the kind of thing that makes
                // modifier ordering load-bearing later.
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
                // Semi-transparent scrim so the XMB wave/wallpaper background stays visible behind
                // Settings (the XMB foreground is hidden by XMBShell while a Settings screen is up).
                //
                // The anchors are SOLVED, not chosen. The bright band that made "Reset Sound to
                // Defaults" measure 1.82:1 was mostly this scrim, not the wallpaper: at ~0.90 alpha
                // only ~11% of the wallpaper reaches the eye, and the theme's own
                // backgroundBottom = lighten(wave, 0.28) is light enough that white text on it
                // fails AA. solveScrimColor darkens each anchor along its own hue by the least
                // amount that keeps white above 4.5:1 even over a pure-white wallpaper — so the
                // gradient stays the theme's colour and the failure cannot come back if
                // ColorCascade.lighten is retuned later (TextLegibilityTest pins both anchors).
                //
                // The wizard skin's lighter scrim is left alone: at 0.45/0.55 alpha no colour can
                // reach white's 0.183 luminance ceiling over a bright wallpaper, so darkening the
                // anchors would only mute the wave without fixing anything. Per-text protection
                // (TextLegibilityStyle) is the instrument for that surface.
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
            // Under everything the scaffold draws, and inside the same Box so it is clipped and
            // sized identically to the scrim it replaces.
            backdrop?.invoke()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The whole screen ends above the soft keyboard.
                    //
                    // MainActivity sets decorFitsSystemWindows false, so without this the window
                    // does not move and the keyboard simply covers whatever is under it. On a
                    // 462 dp-tall screen that is the lower half of every settings list -- which
                    // is where the text fields are, because they sit under the rows that explain
                    // them. Editing a ScreenScraper username meant typing into a field you could
                    // not see.
                    //
                    // On the scaffold rather than on each screen: there are twenty-odd screens
                    // and the ones with fields are not obviously the ones that need it, so a
                    // per-screen fix is a rule nobody will remember. The body scrolls, and the
                    // scaffold already brings the focused row into view, so a shorter viewport is
                    // all it needs to do the rest.
                    .imePadding(),
            ) {
                // Room for the status strip, which the shell draws over this screen. A Spacer
                // rather than top padding on the Column: padding would push the pinned footer up
                // by the same band and leave a gap along the bottom edge. This moves only the
                // header down, and the weighted content Box below shrinks to match -- which is
                // what the measured viewport reads, so keep-in-view still clamps correctly.
                Spacer(Modifier.height(StatusStripHeight))

                // Header band — chrome, and therefore a SIBLING of the scrolling body, which is
                // why a drag here used to die. dragToScroll hands it the body's own scroll state
                // (the one the screen registered for keep-in-view), so the band drags the list.
                // The ◀ breadcrumb inside still taps: a clickable child claims the tap, and the
                // drag past touch slop belongs to this scrollable ancestor — the same division of
                // labour a Button inside a LazyColumn already has.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragToScroll(contentScrollState.value),
                ) {
                if (header != null) {
                    header()
                } else {
                // ── Header — breadcrumb form, matching the detail menus: the ◀ back arrow
                // leads, followed by the title stack. Excluded from focus traversal: the arrow
                // is clickable (touch only — the controller uses the B button), so without this,
                // pressing UP on the first row would jump focus up into the header.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .padding(horizontal = 48.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Arrow AND title stack both trigger back — one tap target, no press highlight.
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
                } // end of the header band

                // Invisible 0dp focus bootstrap element. requestFocus() lands here first;
                // onFocusChanged immediately redirects to the first real interactive row via
                // moveFocus(Down). This avoids needing focusGroup() which is not reliably
                // available across all Compose versions.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .focusRequester(bootstrapFR)
                        .focusable()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                // Hand off to the first interactive row (the default focus target).
                                focusManager.moveFocus(FocusDirection.Down)
                                focusRedirected = true
                                Timber.d("Settings focus: default focus → first item ($subtitle)")
                            }
                        }
                )

                // Content owns the remaining height; with a wizard footer the content Box is
                // weighted so the measured viewport (below) excludes the footer band and
                // keep-in-view clamping never walks a row underneath it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(SettingsContentViewportTag)
                        // NOTE: deliberately NOT dragToScroll'd. This Box is an ANCESTOR of the
                        // body's own verticalScroll, and both would drive the SAME ScrollState.
                        // Modifier.scrollable takes part in nested scrolling, so the two contend
                        // for that state's MutatorMutex: after a fling, the next touch landed
                        // between the two owners and the drag was swallowed instead of catching
                        // the fling. The header and footer bands are SIBLINGS of the body, which
                        // is why they can share the state safely and this cannot.
                        .onGloballyPositioned {
                            firstVisibleContentY.value = it.localToRoot(Offset.Zero).y
                            contentViewportHeight.value = it.size.height.toFloat()
                        }
                        // Bottom edge fade. Content runs flush to the help band, so a row
                        // straddling the fold read as deliberate clipping rather than "there is
                        // more below". Fading the content's own alpha (DstIn over an offscreen
                        // layer, NOT an opaque gradient) is what keeps this correct over the
                        // scaffold's semi-transparent scrim: the partial row dissolves into the
                        // same wallpaper the band behind it shows, instead of into a painted band
                        // that would only match on one theme.
                        //
                        // Draw-only, so the measured viewport above is untouched. The fade shares
                        // [CONTENT_EDGE_MARGIN] with keep-in-view, which is what guarantees a
                        // focused row's bottom edge lands exactly where the fade starts.
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
                    // The settings column is left-anchored and capped, not edge to edge. A PS3
                    // settings list occupies roughly the left half and lets the background hold
                    // the rest; spanning the full width is what made a 22sp label look marooned
                    // in the middle of nothing on a 821dp-wide handheld.
                    //
                    // widthIn, not fillMaxWidth(fraction): on a phone in portrait the cap is
                    // wider than the screen and this is a no-op, so narrow devices keep the full
                    // width they need.
                    Row(Modifier.fillMaxSize()) {
                        // The current section's screens. The section's own name is the page
                        // title, so listing it here too was the duplicate row you could see on
                        // Overview.
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

                // Footer band — the other half of the dead zone, wired to the same scroll owner
                // as the header. The wizard's Enter/Back chrome arrives through the [footer] slot,
                // so it is covered here too and WizardScaffold needs no change of its own.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragToScroll(contentScrollState.value),
                ) {
                    // The prompts and the focused row's explanation, in ONE band.
                    //
                    // This is the shared [PfpHintBar] that the crossbar, the App Drawer, Search
                    // and the detail pages draw, so Settings stops being the last screen with a
                    // black pill on it. The explanation rides in the bar's centre slot, which was
                    // empty space on every other screen.
                    //
                    // A FIXED height, always -- the bar's own. A band that grew and shrank with
                    // each row's text would move the list under the cursor every time the cursor
                    // moved, which is the one thing a settings list must never do. It is 34dp
                    // against the old 44, so the list gains 10dp rather than paying for a second
                    // band: stacking the bar under the explanation would have cost 78dp of a
                    // 462dp screen, which is the strip this band replaced in the first place.
                    //
                    // The explanation is hidden while the cursor is, because then there is no
                    // focused row to explain and the last one's text would be a lie about where
                    // you are. One line now rather than two: the bar is shorter than the band,
                    // and the room between the prompt groups is wide.
                    if (footer == null) {
                        val help = helpText.value?.takeIf { cursorVisible.value && it.isNotBlank() }
                        PfpHintBar(
                            // Empty rather than hidden when the hints are off: the bar still has
                            // the explanation to carry, and an AnimatedVisibility around the whole
                            // band would collapse it and move the list.
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
                        // Footer chrome (Enter / Back prompts) is display-only — never a focus
                        // target, so UP on the first content row cannot land inside it.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = false },
                        ) {
                            footer()
                        }
                    }
                    // Nothing here in the ordinary case. The button-hint pill used to sit in this
                    // slot as a centred band with a divider and a reserved height, because it
                    // faded in and out and a band that collapsed would have shifted every row
                    // above it. It does not fade in and out any more, and it is now drawn bottom
                    // right over the content like the XMB's, which puts it in the same corner on
                    // every screen in the app and hands its reserved height back to the list.
                }
            }

            // The picker, over the whole screen. Last child of the root Box so it paints above
            // the list, the header and the footer; it draws no focusable node of its own because
            // the scaffold's action handler already owns every action while it is open, and a
            // second focus target would fight the list's cursor underneath.
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

/**
 * The open picker: a list that opens ON the row it belongs to, the way a PS5 setting's options
 * do, rather than a panel in the middle of the screen.
 *
 * Anchoring is the whole point. A centred panel made you look away from the row you were
 * changing and then hunt for it again afterwards; this one appears over the value it is
 * replacing, so the thing being chosen never moves.
 */
@Composable
private fun SettingsPickerPanel(picker: SettingsPickerRequest, cursor: Int, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Tapping off the panel closes it, the way BACK does. Without this the scrim was
            // inert and a touch user who opened a picker had no way out of it at all.
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // Light, because the point of anchoring is that the row you came from stays visible.
            // Contrast alone has to say which layer is on top, and the opaque list below does it.
            // Barely a scrim. The reference dims almost nothing -- the panel's own opacity is
            // what separates the layers -- and 0.45 was dark enough to read as a modal dialog
            // rather than as a list opening on a row.
            .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        val panelHeight = PICKER_ROW_HEIGHT * picker.options.size + PICKER_PADDING * 2
        val anchorDp = with(density) { picker.anchorY.toDp() }
        // Clamped, so a row near the bottom opens upward rather than off the screen and a row
        // near the top is not pushed under the header.
        val top = anchorDp.coerceIn(
            PICKER_EDGE_MARGIN,
            (maxHeight - panelHeight - PICKER_EDGE_MARGIN).coerceAtLeast(PICKER_EDGE_MARGIN),
        )
        Column(
            modifier = Modifier
                .padding(start = 48.dp)
                .offset(y = top)
                // Hugs its content. 260dp was a panel wide enough to look like a dialog next to
                // five one-word options; the reference is only as wide as its longest label.
                .widthIn(min = 150.dp, max = SETTINGS_COLUMN_MAX_WIDTH)
                .clip(PICKER_SHAPE)
                // Opaque: this list sits ON the settings list, so the rows underneath must not
                // read through the options the way they did through the full-screen panel.
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
                        // Touch picks an option directly. The controller path drives a cursor and
                        // then confirms it; a finger has no cursor, so the tap IS the choice.
                        .clickable {
                            picker.onPick(index)
                            onDismiss()
                        }
                        .background(
                            if (focused) SETTINGS_ROW_SELECTED_FILL else Color.Transparent,
                            PICKER_SHAPE,
                        )
                        // A flat light hairline, not the row plate's left-to-right gradient. On a
                        // 150dp panel the gradient reads as one lit edge and one missing one.
                        .then(
                            if (focused) Modifier.border(1.dp, PICKER_FOCUS_EDGE, PICKER_SHAPE)
                            else Modifier
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The tick marks what is CURRENTLY set; the plate marks where the cursor is.
                    // Two different facts, both on screen at once, because you stand on one
                    // option while another is the saved one.
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


// ── Reusable row components ───────────────────────────────────────────────────
// (Controller-row registration helpers live in ControllerRowRegistration.kt — shared with the
// first-run wizard's row family so focus behavior can never drift between the two families.)

/**
 * The list of sibling screens down the left of a settings page.
 *
 * Display only as far as the controller is concerned: the scaffold owns which entry the cursor
 * is on, because while the rail holds the cursor it also owns vertical movement and Confirm, and
 * that cannot be decided in two places. [cursorIndex] is null whenever the cursor is elsewhere,
 * which is also how the rail knows to draw nothing as focused.
 */
@Composable
private fun SettingsSectionRail(
    entries: List<com.psplauncher.core.domain.model.SettingsEntry>,
    currentId: String?,
    cursorIndex: Int?,
    onPick: (com.psplauncher.core.domain.model.SettingsEntry) -> Unit,
) {
    // A list, not a Column: two levels of a six-section tree is up to twelve rows, which is
    // taller than the rail on a handheld in landscape. The cursor scrolls it.
    //
    // Two things this used to get wrong, both only visible at 462dp. It followed the CURSOR only,
    // so arriving on a screen any other way — opening Settings, or backing out of a sub-screen —
    // left the rail wherever it happened to be, with the entry you were actually on sometimes
    // off-screen or sliced in half at the edge. And `animateScrollToItem` with no offset puts the
    // target against the TOP of the viewport, which throws away the siblings above it — and the
    // siblings are the entire reason the rail exists.
    //
    // So it follows whichever is live, cursor first and the open screen otherwise, and centres.
    val listState = rememberLazyListState()
    LaunchedEffect(cursorIndex, currentId, entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val target = cursorIndex ?: entries.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }
        target ?: return@LaunchedEffect
        val info = listState.layoutInfo
        val viewport = info.viewportSize.height
        val rowHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0
        // Negative offset scrolls the item DOWN from the top edge by half the leftover viewport,
        // i.e. centres it. Zero while the list has not been measured yet, which top-aligns for one
        // frame rather than jumping to a wrong place.
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
                    // No indentation any more: every row in this column is a screen of the one
                    // section named in the title, so there is no hierarchy left to express.
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title,
                    // The page you are on stays bright whether or not the cursor is in the rail:
                    // it is answering "where am I", not "what am I pointing at". The cursor plate
                    // answers the second question, and the two are allowed to be on different rows.
                    //
                    // Everything else drops to SETTINGS_RAIL_INACTIVE_ALPHA. Measured, not chosen:
                    // in the PS5 Settings reference the active rail item peaks at 241 and the
                    // inactive ones at 113 — a little under half. Secondary text alone was not
                    // enough of a gap, which is why the active row was hard to pick out of a
                    // thirteen-row tree at a glance.
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

/**
 * How far the rail's non-current rows drop.
 *
 * The rail is navigation, not content: its job is to make the page you are on obvious, and a row
 * you are not on is a landmark rather than something to read. WCAG's body-text floor does not
 * apply to it for the same reason it does not apply to a disabled control — but the CURRENT row,
 * the one you actually read, keeps full primary text and is unaffected by this.
 */
private const val SETTINGS_RAIL_INACTIVE_ALPHA = 0.42f

@Composable
fun SettingsGroup(title: String) {
    // Headers are visual landmarks. The scaffold's first-item clamp scrolls the complete
    // settings column to offset zero, ensuring a long screen that starts with this header can
    // always be returned to its true top with UP at the first row.
    // A landmark, not a banner. The XMB has no chrome: a PS3 settings list separates groups with
    // space and a quiet label, never a tinted full-width bar. The header is also deliberately
    // SMALLER than the row labels it introduces now — it used to be the same 15sp, which left
    // nothing establishing hierarchy except capitals.
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

/**
 * A controller-reachable inline action rendered in a [SettingsRow]'s trailing slot. Reached by
 * pressing RIGHT onto the row; LEFT/RIGHT steps between a row's actions and back to the row.
 * SELECT activates the focused action.
 */
class SettingsRowAction(
    val label: String,
    val onClick: () -> Unit,
    val onLongPress: (() -> Unit)? = null,
    // Background color drawn behind the icon when this action holds controller focus.
    val actionFocusBackgroundColor: Color = Color.White.copy(alpha = 0.25f),
    val icon: @Composable () -> Unit,
)

/** How far a disabled row's text fades: still readable, plainly not actionable. */
private const val DISABLED_ROW_ALPHA = 0.4f

@Composable
fun SettingsRow(
    label: String,
    sublabel: String? = null,
    // The row's current setting, right-aligned opposite the label. This is the PS3 shape --
    // "Video Output Settings        HDMI" -- and it is why a value belongs here rather than in
    // [trailing]: only the row knows whether it is selected, and the value has to grow with the
    // label or it reads as a footnote pinned to a heading.
    value: String? = null,
    focusKey: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    // Inline controller-reachable actions (e.g. Replace/Remove buttons), navigated via LEFT/RIGHT.
    actions: List<SettingsRowAction> = emptyList(),
    // Reports controller-focus changes so a screen can track which row is hovered (e.g. to
    // open a per-row context menu on the options button).
    onFocusChangedExternal: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    // When true the row-level cursor fill is suppressed while an inline action has focus,
    // letting the action's own background be the sole highlight indicator.
    hideRowHighlightOnActionFocus: Boolean = false,
    // A disabled row keeps its place in navigation (every row is focusable) but greys out, and
    // SELECT and taps do nothing: [onClick] is dropped rather than guarded at each use.
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

    // EVERY row is controller-focusable — a non-focusable row is a dead zone the cursor can't
    // reach or scroll to (read-only value rows, info footers). Only rows with a real [onClick]
    // claim the initial-focus slot, so a screen still opens on its first ACTION, and only they
    // draw the strong cursor fill; read-only rows get a softer frame and SELECT is a no-op.
    // Registration + geometry reporting are shared with the wizard's row family via
    // rememberControllerRowRegistration so both families navigate identically.
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

    // A Column, not a Row: the plate holds the label line AND, when focused, the explanation
    // under it. The row itself is the inner Row below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(row.focusRequester)
            // Report on-screen Y and height so the scaffold can navigate and keep the row in view.
            .then(row.positionReporting)
            // Observe focus to register onclick with scaffold (for SELECT) and show highlight
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
                    // The explanation is drawn in this row's own plate below, not sent to the
                    // band at the foot. The band still exists — it holds the controller prompts,
                    // and SettingsSliderRow still writes its helper there — so this clears it
                    // rather than leaving the previous row's words stranded under the cursor.
                    help.value = null
                    Timber.d("Settings focus: row=\"$label\" clickable=${click != null}")
                }
            }
            // Selection is a PLATE: a soft rounded fill with a hairline edge behind the whole
            // row. This is the PS5 settings list, which is the reference asked for -- its focused
            // row is a lifted rounded rectangle, not a coloured wash.
            //
            // It replaced a left-edge bloom in the PS3 idiom. Both are console-correct; they come
            // from different consoles, and this is the one whose screenshots are the brief.
            //
            // The plate is COLOURLESS on purpose. A themed one turned every focused row into a
            // bar of the wallpaper's hue, which is the habit this screen has been unlearning.
            .padding(horizontal = 40.dp)
            .settingsSelectedPlate(rowSelected)
            .focusable()
            // 18dp was set when every row carried a second line of helper text and needed the
            // air. With the helper moved to the foot of the screen the rows are one line, and at
            // 18dp a screen held three of them. A console settings list is denser than that.
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
                // One size, whatever the cursor is on. Weight and the plate carry focus instead.
                //
                // The XMB reads these same two spec values and DOES grow its selected row, but it
                // can afford to: it has a fixed ROW_HEIGHT to absorb the difference and applies the
                // delta through graphicsLayer, which never touches layout. A settings row has
                // neither, so the larger text re-laid the list and every row below the cursor
                // shifted as it moved. It also spent height a 462dp screen does not have.
                //
                // The reflow is the part that was verified here. A review of a 26-screen capture
                // sweep additionally counted eleven panes ending below the fold, five of them on a
                // section header with nothing under it — but that was a count off screenshots, not
                // a measurement, and it predates this change and the rail's centring. Panes do
                // scroll and do carry a bottom fade ([CONTENT_EDGE_MARGIN]), so "below the fold"
                // there means content continues, not content lost. Recheck before acting on it.
                fontSize = XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = TextStyle(shadow = SettingsTextShadow),
            )
            // [sublabel] is NOT drawn here any more. It goes to the help band at the foot of
            // the screen, for the focused row only -- see LocalSettingsHelp.
        }
        if (value != null) {
            Spacer(Modifier.width(24.dp))
            Text(
                text = value,
                // Not SettingsAccent. PfpPalette.Accent (#4A90D9) has relative luminance 0.264,
                // which caps it at 3.34:1 on pure white and 6.28:1 on pure black. Accent is a
                // fill/ring/border colour and is structurally incapable of carrying body text; no
                // shadow fixes that, because a shadow changes the edge and not the fill.
                color = (if (rowSelected) Color.White else SettingsSubtext)
                    .let { if (enabled) it else it.copy(alpha = it.alpha * DISABLED_ROW_ALPHA) },
                // Same reason as the label above: no size change on focus.
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
        // The explanation, inside the focused row's plate.
        //
        // It used to go to a band at the foot of the screen, which put the words as far from the
        // row as the screen allows and gave every page a reserved strip whether or not anything
        // was in it. The reference keeps them together: in the PS5 capture only the focused row
        // carries a description, it sits under that row's label inside the same plate, and it is
        // at full brightness rather than treated as a footnote.
        //
        // Only when focused, which is what keeps the list dense — this is the "what can be a
        // single item should be a single item" rule. An unfocused row is one line.
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

/**
 * Wraps arbitrary [content] as a controller-focusable, confirm/tap-activatable settings element,
 * registering with the scaffold's focus system exactly like [SettingsRow]. Use for custom rows that
 * don't fit the label/sublabel layout. [content] receives whether the
 * element currently holds controller focus, so the caller can draw its own highlight.
 */
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
                    // Nothing to say about this one, and saying nothing is the point: without
                    // this the band would still be explaining whichever ROW the cursor came from.
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
        // Row-level click so controller SELECT can toggle it
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

/**
 * A setting with a fixed set of values, chosen from a panel.
 *
 * Reads exactly like [SettingsValueRow] -- label left, current value right -- and SELECT opens
 * the full list instead of stepping blindly to the next one. That is the difference worth the
 * extra press: cycling only ever showed you where you had just landed, never where you could go,
 * so finding a particular value meant pressing A until it came round again.
 *
 * Two-value settings go through here too. A tick list for two options is one press more than a
 * cycle, and it is still the right trade: one rule for every multi-value setting beats a rule
 * that changes at some arbitrary option count, and the panel is where the options explain
 * themselves.
 */
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
    // Where this row sits on screen, so its option list can open ON it. Re-read on every layout
    // pass, because the list scrolls under the cursor between one open and the next.
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

// Confirm-to-edit text field for controller navigation. Navigating onto the field only
// highlights it (read-only, no keyboard); pressing SELECT (A) — or tapping, for touch —
// enters edit mode and opens the keyboard. IME "Done", or focus leaving the field, exits
// edit mode. This keeps the keyboard from popping up just by scrolling past the field.
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
    // An optional prompt shown after [helper] — for rows whose helper text used to spell out a
    // button ("Press A to type"), so the glyph follows the user's pad instead of being baked in.
    helperPrompt: ControllerPromptItem? = null,
    enabled: Boolean = true,
) {
    val focusTracker = LocalSettingsFocusTracker.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reportFocused = LocalSettingsReportFocused.current
    val help = LocalSettingsHelp.current
    var editing by remember { mutableStateOf(false) }

    // Always focusable so this field can be the screen's initial-focus target (a screen that
    // starts with a text field still opens with it highlighted, read-only). Registration is
    // shared with every other row family via rememberControllerRowRegistration; SELECT enters
    // edit mode, and disabled fields are excluded from navigation entirely.
    val row = rememberControllerRowRegistration(
        prefix = "field",
        focusKey = focusKey,
        claimInitialFocus = true,
        selectable = enabled,
        enabled = enabled,
        onSelect = { editing = true },
    )
    val fr = row.focusRequester

    // The keyboard follows edit mode only — focus alone (navigating onto the field) never
    // opens it, because the field stays read-only until SELECT/tap flips `editing`.
    // The readOnly -> editable flip restarts the field's text-input session asynchronously, so
    // show() in the same frame silently no-ops (the field looks dead on a controller). Settle a
    // frame, re-assert focus on the now-editable field, settle again, then show the keyboard.
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
                            // Controller SELECT over the field starts editing (opens the keyboard).
                            focusTracker { editing = true }
                            reportFocused(fr)
                            help.value = helper
                        } else {
                            editing = false
                        }
                    },
            )
            // While not editing, a non-focusable tap layer lets touch users enter edit mode
            // (a read-only field ignores taps). pointerInput adds no focus target, so it never
            // interferes with controller D-pad traversal.
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
