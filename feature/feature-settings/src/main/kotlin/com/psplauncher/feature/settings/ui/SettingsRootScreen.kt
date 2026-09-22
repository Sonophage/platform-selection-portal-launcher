package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.SettingsSectionId
import com.psplauncher.core.domain.model.settingsEntriesIn

/**
 * Where Settings opens: the seven sections, one per row.
 *
 * It exists because the rail stopped being able to reach them. The rail used to list every section
 * with the open one expanded, which made it the whole tree and meant Settings could open straight
 * into a screen. Now it lists one section's screens, the shoulders step sideways between sections,
 * and this is the page that shows the sections themselves — the same shape the PS5 opens Settings
 * with: a full-width list of destinations, no side column, one icon and one label per row.
 *
 * No rail of its own, and it gets one for free: [com.psplauncher.core.domain.model.settingsRailRows]
 * returns nothing for an id outside the catalog, and this id is deliberately not in it. A root is
 * not a sibling of the screens it opens.
 */
@Composable
fun SettingsRootScreen(
    onOpenSection: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(
        subtitle = "Settings",
        onBack = onBack,
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SettingsSectionId.entries.forEach { section ->
                // A section row opens its FIRST screen, which is also the row the rail will land
                // the cursor on — so arriving reads as continuing rather than as a jump.
                val opens = settingsEntriesIn(section).firstOrNull()?.id
                SettingsRow(
                    label = section.title,
                    // The section's own description, which the catalog has always carried and
                    // nothing has drawn. Here it is the difference between "Interface" and
                    // knowing that sound and controls live behind it.
                    sublabel = section.subtitle,
                    focusKey = section.id,
                    leading = {
                        Icon(
                            imageVector = section.icon(),
                            contentDescription = null,
                            tint = SettingsSubtext,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    onClick = opens?.let { { onOpenSection(it) } },
                )
            }
        }
    }
}

/**
 * The row icon for a section.
 *
 * A `when` over the enum rather than a field on it: [SettingsSectionId] lives in core-domain, which
 * has no Compose dependency and should not grow one to hold a picture. The compiler still catches
 * a new section, because this is exhaustive.
 */
private fun SettingsSectionId.icon(): ImageVector = when (this) {
    SettingsSectionId.OVERVIEW -> Icons.Filled.Dashboard
    SettingsSectionId.LIBRARY -> Icons.Filled.VideogameAsset
    SettingsSectionId.EMULATORS -> Icons.Filled.Build
    SettingsSectionId.APPEARANCE -> Icons.Filled.Palette
    SettingsSectionId.INTERFACE -> Icons.Filled.Tune
    SettingsSectionId.MEDIA -> Icons.Filled.PermMedia
    SettingsSectionId.SYSTEM -> Icons.Outlined.Info
}
