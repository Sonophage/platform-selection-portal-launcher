package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * Measured off the PS5 Settings reference rather than guessed. Sampling peak text luminance in
 * that capture:
 *
 *  - The page title is the brightest thing on screen (254) and sits at the OUTER margin, x=84,
 *    left of and above the rail, whose items start at x=168. It is a title, not a breadcrumb:
 *    there is no eyebrow above it, no back chevron beside it and no rule under it.
 *  - The rail's active item reads 241; its inactive items read 113. Inactive is not "slightly
 *    quieter", it is a little under half, which is what makes the active one findable at a glance.
 *  - A row's description appears for the FOCUSED row only, inside its plate, at full brightness
 *    (240) — every other row in the capture shows a label and a value and nothing else.
 *
 * So what this adds over a bare [SettingsScaffold] is the title treatment and the light scrim. The
 * rail's dimming is in [SettingsScaffold] itself, because the rail is shared.
 *
 * What it deliberately does NOT change: the focus engine, the rail's structure, the footer
 * prompts. Those are shared with every screen and with the wizard, and a skin that forked them
 * would be a second navigation model.
 */
@Composable
fun SettingsPageScaffold(
    /** The page's name, drawn large at the top-left. */
    subtitle: String,
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
        header = { SettingsPageTitle(subtitle) },
        // The reference has no rule under the title. The rail and the content are separated by
        // their own columns; a horizontal line across both only adds a second boundary.
        showDivider = false,
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The page title: one line, top-left, at the outer margin.
 *
 * `canFocus = false` for the same reason the wizard's header carries it: this is display chrome,
 * and UP from the first row must go to the rail, never into a title the cursor cannot use.
 */
@Composable
private fun SettingsPageTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 30.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 10.dp),
    )
}
