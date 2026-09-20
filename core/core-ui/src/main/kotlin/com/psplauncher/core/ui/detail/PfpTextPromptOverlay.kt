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

// ── In-window text prompt ─────────────────────────────────────────────────────
//
// The text-entry twin of [PfpConfirmOverlay], and it exists for the same measured reason: a
// Material3 AlertDialog renders into its own platform Window, so while it is up the Activity's
// dispatchKeyEvent never runs and the gamepad pipeline never sees a press.
//
// That was verified on a tablet against the "New Collection" prompt: BUTTON_A did nothing,
// BUTTON_B did nothing, the D-pad did nothing, and the system Back key dismissed only the soft
// keyboard, leaving the prompt up. Touch was the only escape, while the helper footer underneath
// promised "A Enter / B Back". On a handheld with no navigation bar that is a dead end.
//
// The ViewModels have always had correct BACK branches for these prompts (onCancelAppRename,
// onCancelCollectionName, onCancelPlaylistName). They were simply unreachable. Drawing the prompt
// in the launcher's own window is the whole fix -- no ViewModel change needed.

/**
 * A single-line text prompt drawn inside the launcher's own window.
 *
 * Confirm is the keyboard's own Done action (and the Save button); cancel is Back, which the
 * caller's ViewModel already answers. The text is owned here because it is transient: nothing
 * outside this prompt needs to see a half-typed name.
 */
@Composable
fun PfpTextPromptOverlay(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Save",
    cancelLabel: String = "Cancel",
) {
    val focusRequester = remember { FocusRequester() }
    // Open with the field focused and the keyboard up. Without this the first thing a user does
    // is reach out and tap the field, which on a controller-first launcher is a dead end.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    PfpOverlayCard(onScrimTap = onCancel, modifier = modifier) {
        PfpOverlayTitle(title)
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
            // The keyboard's own Done key commits. That is how every other text field in this app
            // is confirmed, and it is the only commit path a soft keyboard really offers.
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(cancelLabel, color = DetailTextMuted) }
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = detailPalette().focus, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
