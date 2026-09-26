package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.animateFloatAsState
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.components.HintBarHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.psplauncher.feature.xmb.viewmodel.XMBContextMenuItem

@Composable
fun ContextMenuOverlay(
    rows: List<XMBContextMenuItem>,

    selectedIndex: Int?,
    onItemActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()

            .background(Brush.horizontalGradient(0f to Color.Transparent, 1f to XmbScrim)),
    ) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(RailRowGap),
            modifier = Modifier
                .align(Alignment.CenterEnd)

                .padding(top = StatusStripHeight, bottom = HintBarHeight)
                .verticalScroll(rememberScrollState())
                .padding(end = RailEdgeGap),
        ) {
            rows.forEachIndexed { index, row ->

                val target = selectedIndex
                    ?.let { XmbDim.smoothed(kotlin.math.abs(index - it), rows.lastIndex) }
                    ?: XmbDim.smoothed(1, rows.lastIndex)

                val dim by animateFloatAsState(target, tween(DimFadeMs), label = "railDim")
                XmbRailRow(
                    label = row.label,
                    focused = index == selectedIndex,
                    destructive = row.isDestructive,
                    dim = dim,
                    onClick = { onItemActivated(index) },
                )
            }
        }
    }
}

private const val DimFadeMs = 160
