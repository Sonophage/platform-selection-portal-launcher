package com.psplauncher.feature.settings.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import com.psplauncher.core.ui.components.MenuRow
import com.psplauncher.core.ui.components.MenuState
import com.psplauncher.feature.settings.viewmodel.LogsSettingsViewModel
import timber.log.Timber
import java.io.File

@Composable
fun LogsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var focusedLog by remember { mutableStateOf<String?>(null) }

    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuIndex by remember { mutableIntStateOf(0) }
    val menuRows = listOf(MenuRow("share", "Share"))

    Box(modifier = modifier) {
    SettingsPageScaffold(
        subtitle = "Logs",
        onBack   = onBack,
        modifier = Modifier.fillMaxSize(),
        onInterceptAction = { action ->
            val m = menuFor
            when {
                m != null -> {
                    when (action) {
                        GamepadAction.NAVIGATE_UP   -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
                        GamepadAction.NAVIGATE_DOWN -> menuIndex = (menuIndex + 1).coerceAtMost(menuRows.size - 1)
                        GamepadAction.SELECT        -> {
                            if (menuIndex == 0) shareLogFile(context, m)
                            menuFor = null
                        }
                        GamepadAction.BACK,
                        GamepadAction.OPEN_CONTEXT_MENU      -> menuFor = null
                        else -> Unit
                    }
                    true
                }

                action == GamepadAction.OPEN_CONTEXT_MENU -> {
                    focusedLog?.let { menuFor = it; menuIndex = 0 }
                    true
                }
                else -> false
            }
        },
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SettingsGroup("Log Files   —   Ⓐ open externally · Ⓨ options")

            if (state.logFiles.isEmpty()) {
                SettingsRow(
                    label    = "No log files yet",
                    sublabel = "A new log starts with every app launch — come back after something happens",
                )
            }
            state.logFiles.forEach { file ->
                SettingsRow(
                    label    = file.name,
                    sublabel = file.sizeKb,
                    onFocusChangedExternal = { focused -> if (focused) focusedLog = file.name },
                    onClick  = { openLogExternally(context, file.name) },
                )
            }

            SettingsRow(
                label   = "Clear All Logs",
                onFocusChangedExternal = { focused -> if (focused) focusedLog = null },
                onClick = {
                    focusedLog = null
                    viewModel.clearLogs()
                },
            )
        }
    }

    menuFor?.let { name ->
        PspContextMenuOverlay(
            state          = MenuState(name, menuRows, selectedIndex = menuIndex),
            onRowActivated = { index ->
                if (index == 0) shareLogFile(context, name)
                menuFor = null
            },
            onDismiss      = { menuFor = null },
        )
    }
    }
}

private fun logFileUri(context: Context, fileName: String): Uri? {
    val file = File(context.filesDir, "logs/$fileName")
    if (!file.exists()) return null
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun openLogExternally(context: Context, fileName: String) {
    runCatching {
        val uri = logFileUri(context, fileName) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Open log with")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Timber.w(it, "Could not open log file externally") }
}

private fun shareLogFile(context: Context, fileName: String) {
    runCatching {
        val uri = logFileUri(context, fileName) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PSPLauncher log — $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share log file")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Timber.w(it, "Could not share log file") }
}
