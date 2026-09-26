package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.psplauncher.feature.settings.viewmodel.RootFolderRow

private val DangerRed = Color(0xFFE55353)

@Composable
fun RootAccessSection(
    groupTitle: String,
    roots: List<RootFolderRow>,
    addLabel: String,
    addSublabel: String,

    emptyLabel: String = "No ROM roots configured",

    rootKindLabel: String = "ROM root",
    onAddRoot: () -> Unit,
    onRelinkRoot: (RootFolderRow) -> Unit,
    onRemoveRoot: (RootFolderRow) -> Unit,
    autoDetectLabel: String? = null,
    autoDetectSublabel: String? = null,
    onAutoDetect: (() -> Unit)? = null,
) {
    SettingsGroup(groupTitle)

    if (roots.isEmpty()) {
        SettingsRow(
            label = emptyLabel,
            sublabel = "Add a folder below to start managing your library",
        )
    } else {
        roots.forEach { root ->
            DirectoryRow(
                label = root.name,
                sublabel = when {
                    !root.linked -> "Access lost — use Edit to re-grant access"
                    root.consoles != null -> "Consoles: ${root.consoles}"
                    else -> rootKindLabel
                },
                onEdit = { onRelinkRoot(root) },
                onRemove = { onRemoveRoot(root) },
            )
        }
    }

    SettingsRow(label = addLabel, sublabel = addSublabel, onClick = onAddRoot)

    if (autoDetectLabel != null && onAutoDetect != null) {
        SettingsRow(label = autoDetectLabel, sublabel = autoDetectSublabel, onClick = onAutoDetect)
    }
}

@Composable
fun DirectoryRow(
    label: String,
    sublabel: String? = null,
    focusKey: String? = null,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    SettingsRow(
        label = label,
        sublabel = sublabel,
        focusKey = focusKey,
        hideRowHighlightOnActionFocus = true,
        onClick = null,
        actions = listOf(
            SettingsRowAction(
                "Edit directory", onEdit,
                actionFocusBackgroundColor = lerp(SettingsAccent, Color.Black, 0.50f),
            ) {
                Icon(
                    Icons.Default.Create,
                    contentDescription = "Edit directory",
                    tint = SettingsAccent,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(4.dp)
                )
            },
            SettingsRowAction(
                "Remove directory", onRemove,
                actionFocusBackgroundColor = lerp(DangerRed, Color.Black, 0.50f),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove directory",
                    tint = SettingsSubtext,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(4.dp)
                )
            },
        ),
    )
}
