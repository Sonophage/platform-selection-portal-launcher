package com.psplauncher.feature.xmb.ui.photo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.psplauncher.core.common.format.formatByteSize
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.Photo
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

private val ViewerBg = Color(0xFF000000)

private val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary

private val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary
private val PanelBg = Color(0xF0101018)

@Composable
fun PhotoViewerScreen(
    photoId: String,
    libraryId: String?,
    onBack: () -> Unit,
    openWallpaperPreview: Boolean = false,
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},

    onTouchInput: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(photoId, libraryId, openWallpaperPreview) {
        viewModel.load(photoId, libraryId, openWallpaperPreview)
    }

    LaunchedEffect(state.closed) { if (state.closed) { onBack(); viewModel.onClosedHandled() } }
    LaunchedEffect(pendingGamepadAction) {
        val action = pendingGamepadAction ?: return@LaunchedEffect
        viewModel.handleGamepadAction(action)
        onGamepadActionConsumed()
    }

    if (state.isLoading) {
        Box(modifier.fillMaxSize().background(ViewerBg)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = menuCursorEdge())
        }
        return
    }
    val photo = state.photo ?: run { onBack(); return }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        viewModel.onGesture(zoomChange, panChange.x, panChange.y)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerBg)
            .transformable(transformState)

            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTouchInput(); viewModel.toggleControls() },
            ),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = state.zoom,
                    scaleY = state.zoom,
                    translationX = state.panX,
                    translationY = state.panY,
                    rotationZ = state.rotationDegrees.toFloat(),
                ),
        )

        var titleFlashVisible by remember { mutableStateOf(true) }
        LaunchedEffect(photo.id) {
            titleFlashVisible = true
            kotlinx.coroutines.delay(2200)
            titleFlashVisible = false
        }
        AnimatedVisibility(
            visible = titleFlashVisible && !state.wallpaperPreviewVisible && !state.showOptions,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(600)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        ) {
            Text(
                text = photo.displayName,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .background(Color(0x66000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        if (state.controlsVisible && !state.wallpaperPreviewVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))

                    .padding(horizontal = 70.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    listOfNotNull(
                        "${state.index + 1} / ${state.photos.size}",
                        photo.resolutionLabel,
                        photo.displayDateMs?.let { fmtDate(it) },
                    ).joinToString("  ·  "),
                    color = TextMuted, fontSize = 12.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                PfpControllerHints(
                    items = listOf(
                        ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev"),
                        ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next"),
                        ControllerPromptItem(GamepadAction.SELECT, "Hide Controls"),
                        ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"),
                        ControllerPromptItem(GamepadAction.BACK, "Back"),
                    ),
                    style = ControllerHintStyle.OVERLAY,
                    onAction = { action ->
                        onTouchInput()
                        when (action) {
                            GamepadAction.PREV_CATEGORY -> viewModel.step(-1)
                            GamepadAction.NEXT_CATEGORY -> viewModel.step(+1)
                            GamepadAction.OPEN_CONTEXT_MENU -> viewModel.openOptions()
                            else -> viewModel.handleGamepadAction(action)
                        }
                    },
                )
            }
        }

        if (state.showOptions) {
            PspContextMenuOverlay(
                state = state.optionsMenu,
                onRowActivated = viewModel::onOptionRowActivated,
                onDismiss = viewModel::closeOptions,
            )
        }

        if (state.wallpaperPreviewVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Set as launcher wallpaper?", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("It replaces the XMB wave background.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                PfpControllerHints(
                    items = listOf(
                        ControllerPromptItem(GamepadAction.SELECT, "Apply"),
                        ControllerPromptItem(GamepadAction.BACK, "Cancel"),
                    ),
                    style = ControllerHintStyle.OVERLAY,
                )
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = viewModel::confirmWallpaper, enabled = !state.applyingWallpaper) {
                        Text(if (state.applyingWallpaper) "Applying…" else "Apply", color = menuCursorEdge())
                    }
                    TextButton(onClick = viewModel::cancelWallpaperPreview, enabled = !state.applyingWallpaper) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            }
        }

        if (state.infoVisible) {
            InfoDialog(photo = photo, onDismiss = { viewModel.handleGamepadAction(GamepadAction.BACK) })
        }

        if (state.confirmRemove) {
            PfpConfirmOverlay(
                title = "Remove from library?",
                message = "\"${photo.displayName}\" will be removed from this library. " +
                    "The photo on disk is not deleted.",
                confirmLabel = "Remove",
                cancelLabel = "Cancel",
                confirmFocused = false,
                cancelFocused = true,
                onConfirm = viewModel::confirmRemove,
                onCancel = viewModel::cancelRemove,
            )
        }

        state.actionMessage?.let { msg ->
            Text(
                msg,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .background(PanelBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.dismissMessage() }
        }
    }
}

@Composable
private fun InfoDialog(photo: Photo, onDismiss: () -> Unit) {
    PfpOverlayCard(onScrimTap = onDismiss) {
        PfpOverlayTitle(photo.displayName)
        Spacer(Modifier.height(10.dp))
        photo.resolutionLabel?.let { InfoRow("Resolution", it) }
        photo.dateTaken?.let { InfoRow("Taken", fmtDate(it)) }
        photo.lastModified?.let { InfoRow("Modified", fmtDate(it)) }
        photo.sizeBytes?.let { InfoRow("Size", formatByteSize(it)) }
        photo.mimeType?.let { InfoRow("Type", it) }
        photo.relativePath?.let { InfoRow("Location", it) }
        InfoRow("File", photo.displayName)
        Spacer(Modifier.height(18.dp))
        PfpDetailLaunchButton(
            label = "OK",
            icon = null,
            focused = true,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}

private fun fmtDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

