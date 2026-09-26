package com.psplauncher.core.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

@Composable
fun PfpTextPromptOverlay(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
    confirmLabel: String = "Save",
    cancelLabel: String = "Cancel",
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    PfpOverlayCard(onScrimTap = onCancel, modifier = modifier) {
        PfpOverlayTitle(title)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = DetailTextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = DetailTextMuted) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = detailPalette().focus,
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedTextColor = DetailTextPrimary,
                unfocusedTextColor = DetailTextPrimary,
                cursorColor = detailPalette().focus,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),

            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                if (onReset != null) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onReset != null && resetLabel != null) {
                TextButton(onClick = onReset) {
                    Text(resetLabel, color = DetailTextMuted, fontSize = 12.sp)
                }
            }
            Row {
            TextButton(onClick = onCancel) { Text(cancelLabel, color = DetailTextMuted) }
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = detailPalette().focus, fontWeight = FontWeight.SemiBold)
            }
            }
        }
    }
}
