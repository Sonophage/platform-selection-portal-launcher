package com.psplauncher.feature.settings.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val inset = maxWidth * ROOT_LEFT_INSET_FRACTION

            var leavingFor by remember { mutableStateOf<String?>(null) }
            val travel by animateDpAsState(
                targetValue = if (leavingFor != null) -inset else 0.dp,
                animationSpec = tween(ROOT_SLIDE_MS),
                label = "settingsRootSlide",
            )
            val fade by animateFloatAsState(
                targetValue = if (leavingFor != null) 0f else 1f,
                animationSpec = tween(ROOT_SLIDE_MS),
                label = "settingsRootFade",
            )
            LaunchedEffect(leavingFor) {
                val target = leavingFor ?: return@LaunchedEffect
                delay(ROOT_SLIDE_MS.toLong())
                onOpenSection(target)
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = travel)
                .alpha(fade)
                .verticalScroll(scrollState)
                .padding(start = inset, top = ROOT_TOP_DROP),
        ) {
            SettingsSectionId.entries.forEach { section ->

                val opens = settingsEntriesIn(section).firstOrNull()?.id
                SettingsRow(
                    label = section.title,

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

                    onClick = opens?.takeIf { leavingFor == null }?.let { { leavingFor = it } },
                )
            }
        }
        }
    }
}

private const val ROOT_LEFT_INSET_FRACTION = 0.20f

private val ROOT_TOP_DROP = 28.dp

private const val ROOT_SLIDE_MS = 190

private fun SettingsSectionId.icon(): ImageVector = when (this) {
    SettingsSectionId.OVERVIEW -> Icons.Filled.Dashboard
    SettingsSectionId.LIBRARY -> Icons.Filled.VideogameAsset
    SettingsSectionId.EMULATORS -> Icons.Filled.Build
    SettingsSectionId.APPEARANCE -> Icons.Filled.Palette
    SettingsSectionId.INTERFACE -> Icons.Filled.Tune
    SettingsSectionId.MEDIA -> Icons.Filled.PermMedia
    SettingsSectionId.SYSTEM -> Icons.Outlined.Info
}
