package com.psplauncher.feature.settings.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.feature.settings.ui.LocalSettingsScrollStateRegistrar
import com.psplauncher.feature.settings.ui.SettingsScaffold

// ── Wizard chrome ─────────────────────────────────────────────────────────────
//
// This screen used to wear a PSP skin taken from the "Create New …" captures: a green-ringed
// navy step badge, blue and red prompt labels, a centred heading, and a "Press the ◀▶ buttons"
// note. Every other settings screen has since moved to the PlayStation 5 language — large
// heading top left, content in a column beneath it, one white prompt row at the foot — and the
// first thing a new user saw was the one screen still speaking the old one.
//
// What is left here is the progress rule. Everything else now comes from the settings scaffold,
// which is the point: the wizard is Settings before you have any, not a separate app.

/** The filled part of the step rule. Accent rather than a fixed colour: the wizard is themed too. */
private val WizardProgressTrack = Color.White.copy(alpha = 0.16f)

/** Amber status/validation text (the wizard's transient messages). */
internal val WizardAmber = Color(0xFFFFC857)

/**
 * The first-run wizard's PSP skin, layered on [SettingsScaffold] — the same controller focus
 * engine, focus restoration, touch re-anchoring, and keep-in-view clamping, but with the
 * mockup's chrome: a green-ringed step badge + title header, a centered task heading with an
 * optional constraint hint, and the Enter / Back prompt footer pinned under the content. The scrim
 * is light so the XMB wave reads through, like the PSP original's rich blue backdrop.
 *
 * Strongly controller driven: BACK steps to the previous page, SELECT activates the
 * focused row to advance/confirm. Touch works everywhere — rows tap, fields tap to edit, and
 * pages may expose their own ▶ affordance.
 */
@Composable
fun WizardScaffold(
    /** 1-based page number; null hides the progress rule entirely. */
    stepNumber: Int?,
    /** How many pages this run has. The denominator — see InitialSetupUiState.reachableSteps. */
    stepCount: Int = 0,
    title: String,
    /** Centered task heading, e.g. "Choose your ROM folders." */
    heading: String,
    /** Centered constraint/hint line under the heading, e.g. "Add one or more root folders." */
    hint: String? = null,
    onBack: () -> Unit,
    /** Dimmed, inert Back on the first page (no earlier step exists). */
    backEnabled: Boolean = true,
    /** Transient wizard message — rendered as an amber row under the heading. */
    message: String? = null,
    onDismissMessage: (() -> Unit)? = null,
    /** Overrides the footer's guidance line (defaults to the PSP ◀▶/▶ wording). */
    footerNote: String? = null,
    /** The page [content] currently shows. Changing it returns the page to the top. */
    contentKey: Any? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SettingsScaffold(
        title = title,
        subtitle = "",
        onBack = onBack,
        modifier = modifier,
        // The settings scrim, not a lighter one. The wizard reading brighter than the screens it
        // is about to hand you over to made it look like a different app, which for a first-run
        // flow is precisely the wrong impression.
        lightScrim = false,
        header = { WizardHeader(stepNumber, stepCount, title) },
        footer = { WizardFooter(backEnabled, footerNote) },
        contentKey = contentKey,
    ) {
        // The wizard owns the shared scrollable column (registered with the scaffold so
        // controller boundary navigation and keep-in-view share one scroll owner).
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        // One scroll state serves all eleven pages, so without this a tall page's offset carries
        // into the short page after it and opens it scrolled past its own content. Every page
        // starts at the top, going forward and back alike.
        //
        // scrollTo, not animateScrollTo: a page turn is a cut, not a movement, and animating it
        // would race the scaffold's keep-in-view clamp as the new page's focus lands.
        LaunchedEffect(contentKey) { scrollState.scrollTo(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            WizardHeading(heading, hint)
            if (message != null && onDismissMessage != null) {
                WizardMessageRow(message, onDismissMessage)
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardHeader(stepNumber: Int?, stepCount: Int, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Header chrome is display-only — UP on the first row must never land here.
            .focusProperties { canFocus = false }
            .padding(start = WizardGutter, end = WizardGutter, top = 18.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Normal,
        )
        if (stepNumber != null && stepCount > 0) {
            Spacer(Modifier.height(10.dp))
            WizardProgress(stepNumber, stepCount)
        }
    }
}

/**
 * Where you are, as a line rather than a badge.
 *
 * The badge said which page this was and nothing else; a first-run flow's real question is how
 * much of it is left. The rule answers that at a glance and the count answers it exactly, and
 * neither needs a colour of its own to do it.
 */
@Composable
private fun WizardProgress(stepNumber: Int, stepCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "STEP $stepNumber OF $stepCount",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .width(WizardProgressWidth)
                .height(2.dp)
                .clip(CircleShape)
                .background(WizardProgressTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stepNumber.toFloat() / stepCount.toFloat())
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(menuCursorEdge()),
            )
        }
    }
}

@Composable
private fun WizardHeading(heading: String, hint: String?) {
    // Left-aligned, like every other settings page. Centred text reads as a splash screen, and
    // the eye has to find the start of each line again on a page that is mostly a form.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WizardGutter, end = WizardGutter, top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = heading,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/**
 * The same prompt row every other settings screen carries.
 *
 * It used to be a bespoke band: a sentence of instructions and two prompts in PSP blue and red.
 * The sentence said what the glyphs beside it already showed, and the colours said this was not
 * a settings screen — on the one screen whose whole job is to introduce the settings screens.
 *
 * On the first page there is no earlier step, so Back is not listed at all. Dimming the row was
 * the first attempt and it was wrong twice over: a prompt naming a button that does nothing is
 * worse than no prompt, and the alpha applied to the ROW, so Enter — which works perfectly well
 * on page one — came out greyed beside it.
 */
@Composable
private fun WizardFooter(backEnabled: Boolean, note: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (note != null) {
            Text(
                text = note,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        PfpControllerHints(
            items = listOfNotNull(
                ControllerPromptItem(GamepadAction.SELECT, "Enter"),
                ControllerPromptItem(GamepadAction.BACK, "Back").takeIf { backEnabled },
            ),
            style = ControllerHintStyle.INLINE,
        )
    }
}

/** The settings gutter. Header, heading and every page's rows start on the same line. */
private val WizardGutter = 48.dp

/** How wide the step rule runs. Long enough to read as progress, short enough to stay chrome. */
private val WizardProgressWidth = 160.dp
