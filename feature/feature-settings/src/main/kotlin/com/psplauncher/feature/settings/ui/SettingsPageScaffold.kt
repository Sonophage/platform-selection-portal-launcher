package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.settingsSectionFor
import com.psplauncher.core.ui.components.ControllerPromptItem

@Composable
fun SettingsPageScaffold(

    subtitle: String,

    heading: String? = null,
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

        lightScrim = true,

        header = {
            val screenId = LocalSettingsScreenId.current
            val section = remember(screenId) {
                screenId?.let { settingsSectionFor(it) }
            }
            SettingsPageTitle(heading ?: section?.title ?: subtitle)
        },

        showDivider = false,
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
            .padding(start = 40.dp, end = 40.dp, top = 10.dp, bottom = 0.dp),
    )
}
