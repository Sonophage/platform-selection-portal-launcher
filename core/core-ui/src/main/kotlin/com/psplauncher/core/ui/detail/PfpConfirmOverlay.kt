package com.psplauncher.core.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DestructiveConfirmFill = Color(0x33FF6B6B)

@Composable
fun PfpConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    confirmFocused: Boolean,
    cancelFocused: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmFill: Color? = DestructiveConfirmFill,
) {
    PfpOverlayCard(onScrimTap = onCancel, modifier = modifier) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = DetailTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PfpDetailLaunchButton(
                label = cancelLabel,
                icon = null,
                focused = cancelFocused,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
            PfpDetailLaunchButton(
                label = confirmLabel,
                icon = null,
                focused = confirmFocused,
                onClick = onConfirm,

                fill = confirmFill ?: DetailButtonRest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
