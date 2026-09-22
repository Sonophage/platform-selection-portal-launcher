package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem

/**
 * An ordinary settings screen wearing the first-run wizard's skin.
 *
 * The wizard is the look the launcher is going to: the wave reading through a light scrim, a
 * centred heading that says what the page is for, and air around the rows. It was never a separate
 * system — [com.psplauncher.feature.settings.ui.wizard.WizardScaffold] is a set of arguments to
 * [SettingsScaffold], which already takes `header`, `footer` and `lightScrim`. So this is the same
 * trick pointed at the other twenty-eight screens, not a second scaffold to keep in step.
 *
 * What it adds over a bare [SettingsScaffold]:
 *
 *  - **The light scrim**, so the wallpaper and the wave are part of the page instead of behind it.
 *  - **A centred heading and a line saying what the page is for.** That line is the
 *    [com.psplauncher.core.domain.model.SettingsEntry.subtitle] the catalog has always carried and
 *    nothing has ever drawn — 36 written strings with no reader. This is the reader.
 *
 * What it deliberately does NOT change: the breadcrumb header, the focus engine, the rail, the
 * footer prompts. Those are shared with every screen and with the wizard, and a skin that forked
 * them would be a second navigation model.
 */
@Composable
fun SettingsPageScaffold(
    /** The breadcrumb's second line — the screen's name, as the rail lists it. */
    subtitle: String,
    /** The centred heading. Defaults to [subtitle]; pass a fuller sentence where one reads better. */
    heading: String = subtitle,
    /**
     * The centred line under the heading: what this page is for.
     *
     * Pass the catalog's subtitle for the screen. Null draws no line, which is right for a page
     * whose heading already says everything.
     */
    hint: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    restoreFocusKey: String? = null,
    onInterceptAction: ((GamepadAction) -> Boolean)? = null,
    helperFooterItems: List<ControllerPromptItem> = SettingsDefaultHelperItems,
    content: @Composable () -> Unit,
) {
    SettingsScaffold(
        title = "Settings",
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
        restoreFocusKey = restoreFocusKey,
        onInterceptAction = onInterceptAction,
        helperFooterItems = helperFooterItems,
        // The one visual switch that does the most work: at 0.45/0.55 alpha the wave is part of
        // the page. TextLegibilityStyle is what protects the text there — see the note in
        // SettingsScaffold's scrim block on why these anchors are NOT solved like the dark ones.
        lightScrim = true,
    ) {
        Column(Modifier.fillMaxWidth()) {
            SettingsPageHeading(heading, hint)
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The centred heading block.
 *
 * `canFocus = false` for the same reason the wizard's header carries it: this is display chrome,
 * and UP from the first row must go to the rail, never into a paragraph the cursor cannot use.
 */
@Composable
private fun SettingsPageHeading(heading: String, hint: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .padding(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
