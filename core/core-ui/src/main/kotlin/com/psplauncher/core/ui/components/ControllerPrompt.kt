package com.psplauncher.core.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.GamepadMappings

// ── Controller prompts ───────────────────────────────────────────────────────
//
// One way for any screen to say "this action is on that button". Callers name
// the *action*; which physical button performs it, and which family's art draws
// it, are resolved here from the same GamepadMappings the input handler
// consumes. A footer therefore cannot disagree with the pad — not by
// convention, but because both read one table.
//
// Prompts are ambient chrome rather than screen state, and they are needed by
// footers in four feature modules, so they travel by CompositionLocal instead
// of through every screen signature.

/** The controller identity every prompt on screen renders against. */
@Immutable
data class ControllerPromptStyle(
    /** Which pad's art to draw — Settings ▸ Controller ▸ Type. */
    val family: ControllerDisplayType = ControllerDisplayType.XBOX,
    /** The live bindings, reflecting the Confirm/Back and X/Y layout settings. */
    val mappings: GamepadMappings = GamepadMappings(),
)

/**
 * Ambient controller identity. The default is the stock Xbox layout so
 * `@Preview` and tests render something sensible without a provider; the real
 * value is supplied once at the app root from ControllerLayoutRepository.
 *
 * Deliberately not `staticCompositionLocalOf`: this value always changes at
 * least once per cold start, when DataStore resolves and the default is
 * replaced by the user's real settings. A static local would invalidate the
 * whole subtree — the entire app — at that moment; this one invalidates only
 * the handful of footers that actually read it.
 */
val LocalControllerPromptStyle = compositionLocalOf { ControllerPromptStyle() }

/**
 * A single "button — what it does" prompt.
 *
 * Renders nothing when [action] is not bound to a physical button under the
 * current layout: a prompt that cannot be honoured is worse than no prompt.
 */
/**
 * Whether a physical controller is attached, as far as the shell knows.
 *
 * Defaults to TRUE so a screen that never provides it keeps the compact gamepad-first chrome —
 * the behaviour every caller had before this existed. XMBShell provides the real answer from
 * SystemStatus, which is the only thing that watches for pads arriving and leaving.
 *
 * A CompositionLocal rather than a parameter because the thing that knows (feature-xmb) sits
 * above the thing that needs it (core-ui), and threading a boolean through every prompt list on
 * every screen would be a parameter nobody remembers to pass — which is the same as not having it.
 */
val LocalControllerConnected = compositionLocalOf { true }

@Composable
fun ControllerPrompt(
    action: GamepadAction,
    label: String,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,
) {
    ControllerPrompt(
        actions = listOf(action),
        label = label,
        modifier = modifier,
        style = style,
        labelColor = labelColor,
        labelStyle = labelStyle,
        glyphSize = glyphSize,
        spacing = spacing,
    )
}

/**
 * A prompt naming several inputs under one label — "◀▶ Seek", "L1/R1 Prev / Next".
 *
 * The alternative, one prompt per input, doubles the width of an already tight
 * media footer and reads as two unrelated actions rather than one range.
 *
 * Same contract as the single-action form: [actions] that are not bound to a
 * physical button drop out, and when none of them resolve the prompt renders
 * nothing at all.
 */
@Composable
fun ControllerPrompt(
    actions: List<GamepadAction>,
    label: String,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,
    /** Gap between the glyphs themselves — tighter than [spacing], so a pair reads as one unit. */
    glyphSpacing: Dp = 2.dp,
) {
    ControllerPromptGlyphs(
        icons = style.mappings.iconsFor(actions),
        label = label,
        modifier = modifier,
        family = style.family,
        labelColor = labelColor,
        labelStyle = labelStyle,
        glyphSize = glyphSize,
        spacing = spacing,
        glyphSpacing = glyphSpacing,
    )
}

/**
 * The renderer every prompt form funnels into, taking positions that are already
 * resolved.
 *
 * Public because a few prompts name an input that no setting can remap and that
 * therefore has no action to resolve: the D-pad as a whole ([ControllerIcon.DPAD_ALL]),
 * and a raw-keycode escape hatch such as the PTT capture's cancel button, which
 * reads the physical button before any mapping is applied. Naming the position
 * directly is the truthful thing there; everywhere else, name the action and let
 * the mappings decide.
 *
 * Renders nothing for an empty [icons], so an unresolvable prompt disappears
 * rather than showing a bare label.
 */
@Composable
fun ControllerPromptGlyphs(
    icons: List<ControllerIcon>,
    label: String,
    modifier: Modifier = Modifier,
    family: ControllerDisplayType = LocalControllerPromptStyle.current.family,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,
    glyphSpacing: Dp = 2.dp,
) {
    if (icons.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(glyphSpacing),
        ) {
            for (icon in icons) {
                ControllerIconGlyph(icon = icon, family = family, size = glyphSize)
            }
        }
        Text(
            text = label,
            color = labelColor,
            style = labelStyle,
        )
    }
}

/**
 * One entry in a [ControllerPromptBar]. [actions] names a single input or a set
 * that share a label.
 *
 * [fixedIcons], when set, is drawn instead of resolving [actions] — see
 * [ControllerPromptGlyphs] for when that is the honest choice. Build those with
 * [fixed] rather than by hand.
 */
@Immutable
data class ControllerPromptItem(
    val actions: List<GamepadAction>,
    val label: String,
    val fixedIcons: List<ControllerIcon>? = null,
) {
    constructor(action: GamepadAction, label: String) : this(listOf(action), label)

    /**
     * The action a TAP on this prompt should perform, or null when a tap would have to guess.
     *
     * Only a prompt naming exactly one remappable action is tappable. The two exclusions are the
     * point of this being a function rather than `actions.first()`:
     *
     *  - A [fixedIcons] prompt names a physical position no setting remaps (the D-pad as a whole,
     *    a raw-keycode escape). There is no action behind it to dispatch.
     *  - A multi-action prompt is one label over a range -- "◀▶ Seek", "L1/R1 Prev / Next". A tap
     *    cannot say which end was meant, and silently picking the first would seek backwards when
     *    the user meant forwards. A prompt that does the wrong half of what it says is worse than
     *    one that does nothing.
     *
     * Both still render; they are read-only legends.
     */
    fun tappableAction(): GamepadAction? =
        if (fixedIcons == null && actions.size == 1) actions[0] else null

    companion object {
        /** A prompt for a position no setting remaps (the D-pad, a raw-keycode escape). */
        fun fixed(icon: ControllerIcon, label: String) =
            ControllerPromptItem(emptyList(), label, listOf(icon))
    }
}

/**
 * A row of prompts — the shape every command bar and footer hint wants.
 *
 * [items] is ordered; unbound actions drop out silently, so a bar stays coherent
 * rather than showing a gap. An item may name one input or several, which is what
 * the media footers need: "Seek" is a D-pad pair, "Play" one face button.
 *
 * Takes items rather than action-to-label pairs because a `List<Pair<..>>` and a
 * `List<ControllerPromptItem>` overload erase to the same JVM signature.
 *
 * Pass [onAction] and each prompt naming a single remappable action becomes tappable, firing that
 * action through the same dispatcher the pad uses. A bar that names the buttons is the obvious
 * place a touch user reaches for, and on a handheld the screen is the pad half the time. Which
 * prompts can be tapped is [ControllerPromptItem.tappableAction]'s decision, not this layout's.
 */
// Internal on purpose. Its four look parameters are the reason fifteen screens each ended up
// with their own grey, font size, glyph size and spacing; a public knob is an invitation to
// invent a sixteenth. Feature modules call PfpControllerHints and pick one of two styles.
@Composable
internal fun ControllerPromptBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
    /** Runs the tapped prompt's action. Null leaves the bar a read-only legend. */
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement,
    ) {
        for (item in items) {
            val tapAction = item.tappableAction()?.takeIf { onAction != null }
            ControllerPromptGlyphs(
                icons = item.fixedIcons ?: style.mappings.iconsFor(item.actions),
                label = item.label,
                family = style.family,
                labelColor = labelColor,
                labelStyle = labelStyle,
                glyphSize = glyphSize,
                modifier = if (tapAction == null) Modifier else Modifier
                    // 26dp WITH a pad, 48dp WITHOUT one.
                    //
                    // The trade written here was sound and is kept: minimumInteractiveComponentSize
                    // reserves 48dp of LAYOUT, not just touch area, and it made the pill about
                    // 54dp tall on a 462dp screen, which read as heavy padding around the text. A
                    // prompt is a glyph plus a label, roughly 26dp tall and 90dp wide; the width
                    // is what a thumb lands on and the height is the axis traded away. That is
                    // the right trade on a gamepad-first shell where the tap is the second way in.
                    //
                    // It is the WRONG trade where there is no pad, because then the tap is the
                    // only way in and the guideline exists for exactly that case. The app already
                    // knows which it is — SystemStatus keeps controllerConnected from a live
                    // InputDeviceListener — so this asks rather than assuming. On a handheld with
                    // sticks attached nothing changes; on a keyboard phone or a tablet the bar
                    // gets the height a finger needs.
                    .then(if (LocalControllerConnected.current) Modifier else Modifier.heightIn(min = 48.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = item.label,
                    ) { onAction?.invoke(tapAction) },
            )
        }
    }
}
