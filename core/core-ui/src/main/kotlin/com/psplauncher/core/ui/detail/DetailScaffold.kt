package com.psplauncher.core.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.StatusStripHeight

internal val DetailTextPrimary: Color @Composable @ReadOnlyComposable get() = detailPalette().textPrimary
internal val DetailTextMuted: Color @Composable @ReadOnlyComposable get() = detailPalette().textMuted
internal val DetailRowFill: Color @Composable @ReadOnlyComposable get() = detailPalette().rowFill
internal val DetailRowEdge: Color @Composable @ReadOnlyComposable get() = detailPalette().rowEdge
internal val DetailDivider: Color @Composable @ReadOnlyComposable get() = detailPalette().divider
internal val DetailFocusEdge: Color @Composable @ReadOnlyComposable get() = detailPalette().focus

val DetailButtonRest = Color.White.copy(alpha = 0.13f)

val DetailButtonRestCompact = Color.White.copy(alpha = 0.06f)

val DetailButtonFocusFill = Color(0xFFEDEDED)

val DetailButtonFocusText = Color(0xFF101014)

val DetailContentMaxWidth: Dp = 920.dp
val DetailContentPadding: Dp = 28.dp

val DetailFooterHeight: Dp = 58.dp

internal val DetailTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.72f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

@Composable
@ReadOnlyComposable
internal fun detailSurfaceTop(): Color = detailPalette().pageTop

@Composable
@ReadOnlyComposable
internal fun detailSurfaceBottom(): Color = detailPalette().pageBottom

@Composable
@ReadOnlyComposable
internal fun detailHeaderSurface(): Color = detailPalette().header

@Composable
fun PfpDetailBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(0f to detailSurfaceTop(), 1f to detailSurfaceBottom()),
        ),
        content = content,
    )
}

@Composable
fun PfpDetailScaffold(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentMaxWidth: Dp = DetailContentMaxWidth,
    horizontalPadding: Dp = DetailContentPadding,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},

    backdrop: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    PfpDetailBackground(modifier = modifier.fillMaxSize()) {
        backdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(StatusStripHeight))
            header()

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                CompositionLocalProvider(LocalDetailViewportHeight provides maxHeight) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(start = horizontalPadding, end = horizontalPadding, bottom = 22.dp),
                        content = content,
                    )
                }
            }
            footer()
        }
        overlay()
    }
}

val LocalDetailViewportHeight = staticCompositionLocalOf { Dp.Infinity }

@Composable
fun PfpDetailBreadcrumb(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().background(detailHeaderSurface())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DetailContentPadding, end = DetailContentPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onBack,
                    ),
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(text = "◀", color = DetailTextMuted, fontSize = 16.sp)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = title,
                        color = DetailTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = DetailTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                Spacer(Modifier.weight(1f))

                Box(modifier = Modifier.clipToBounds(), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DetailDivider))
    }
}

@Composable
fun PfpDetailHelperFooter(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,

    onAction: ((GamepadAction) -> Unit)? = null,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "pfpDetailHelperFooter",
    )

    Box(
        modifier = modifier.fillMaxWidth().height(DetailFooterHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PfpHintBar(items = items, modifier = Modifier.alpha(alpha), onAction = onAction)
    }
}

@Composable
fun PfpDetailSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = DetailTextMuted.copy(alpha = 0.75f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
internal fun Modifier.detailFocusRing(
    focused: Boolean,
    edge: Color,
    fill: Color,
    shape: RoundedCornerShape,
    strong: Boolean = false,
): Modifier = this
    .background(if (focused) fill else Color.Transparent, shape)
    .border(
        width = if (focused) (if (strong) 2.dp else 1.5.dp) else 1.dp,
        color = if (focused) edge else DetailRowEdge,
        shape = shape,
    )

