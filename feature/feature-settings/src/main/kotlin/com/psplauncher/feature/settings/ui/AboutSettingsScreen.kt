package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The real installed version, from PackageManager — a library module's BuildConfig can't
    // know the app's versionName/versionCode (the old hardcoded copy here went stale).
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "unknown"
    // Value rows are focusable, so the cursor walks the list and focus-driven scrolling brings
    // each row into view — no manual scroll interception needed.
    SettingsScaffold(
        title    = "Settings",
        subtitle = "About",
        onBack   = onBack,
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SettingsGroup("PSPLauncher")

            SettingsValueRow(label = "Version",      value = versionName)
            SettingsValueRow(label = "Build",        value = versionCode)
            SettingsValueRow(label = "Min Android",  value = "Android 10 (API 29)")
            SettingsValueRow(label = "Target",       value = "Android 15 (API 35)")

            SettingsGroup("Open Source")

            SettingsValueRow(label = "Jetpack Compose",   value = "UI framework")
            SettingsValueRow(label = "Room",              value = "Local database")
            SettingsValueRow(label = "Hilt",              value = "Dependency injection")
            SettingsValueRow(label = "Ktor",              value = "Network client")
            SettingsValueRow(label = "Coil",              value = "Image loading")
            SettingsValueRow(label = "Media3",            value = "Video snaps & playback")
            SettingsValueRow(label = "WorkManager",       value = "Background tasks")
            SettingsValueRow(label = "Timber",            value = "Logging")

            SettingsGroup("Credits")

            SettingsValueRow(label = "Artwork & Media", value = "ScreenScraper · SteamGridDB")
            SettingsValueRow(label = "Metadata",        value = "ScreenScraper · IGDB")
            SettingsValueRow(label = "Inspired by",     value = "Sony PSP XMB")
            SettingsValueRow(label = "See also",        value = "Settings ▸ Credits")

            SettingsGroup("Legal")

            SettingsRow(
                label    = "PSPLauncher is an independent fan project, not affiliated with Sony Interactive Entertainment.",
                sublabel = "PlayStation, PSP and XMB are trademarks of Sony Interactive Entertainment Inc.",
            )
        }
    }
}

@CombinedPreviews
@Composable
fun AboutSettingsScreenPreview() {
    PfpPreview {
        AboutSettingsScreen(onBack = {})
    }
}
