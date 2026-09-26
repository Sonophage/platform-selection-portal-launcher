package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CARD_MAX_WIDTH: Dp = 520.dp
private val CARD_MIN_WIDTH: Dp = 320.dp

@Composable
fun PfpOverlayCard(
    onScrimTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))

            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onScrimTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val lightCard = DetailTextPrimary.luminance() < 0.5f
        Column(
            modifier = Modifier
                .widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH)
                .clip(RoundedCornerShape(14.dp))
                .background(if (lightCard) Color(0xF2F2F2F6) else Color(0xF21A1A22))
                .border(
                    1.dp,
                    if (lightCard) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(14.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
fun PfpOverlayTitle(text: String) {
    Text(
        text = text,
        color = DetailTextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(shadow = DetailTextShadow),
    )
}

@Composable
fun PfpMessageOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Close",

    dismissFocused: Boolean = true,
) {
    PfpOverlayCard(onScrimTap = onDismiss, modifier = modifier) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = DetailTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PfpDetailLaunchButton(
                label = dismissLabel,
                icon = null,
                focused = dismissFocused,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
