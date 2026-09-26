package com.psplauncher.feature.xmb.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.icons.CustomIcon
import com.psplauncher.core.ui.icons.CustomIconSurface
import com.psplauncher.core.ui.icons.LocalIconAnimating
import com.psplauncher.feature.xmb.viewmodel.CustomIconSession
import com.psplauncher.themekit.CustomizableIcons
import com.psplauncher.themekit.IconSlot

@Composable
fun CustomIconsOverlay(
    session: CustomIconSession,
    customIcons: Map<String, CustomIcon>,
    themeIcons: Map<String, CustomIcon>,
    onSlotFocused: (Int) -> Unit,
    onIconPicked: (String, android.net.Uri) -> Unit,
    onResetSlot: (String) -> Unit,
    onResetAll: () -> Unit,
    onSaveAsTheme: () -> Unit,
    onGroupMove: (Int) -> Unit,
    onSlotMove: (Int) -> Unit,
    onDone: () -> Unit,
    forwardedAction: GamepadAction? = null,
    onActionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        session.focusedSlot?.let { slot -> uri?.let { onIconPicked(slot.key, it) } }
    }
    val launchPicker = { picker.launch(PICK_MIME) }

    LaunchedEffect(forwardedAction) {
        when (forwardedAction) {
            GamepadAction.SELECT -> launchPicker()
            GamepadAction.OPEN_CONTEXT_MENU -> session.focusedSlot?.let { onResetSlot(it.key) }
            GamepadAction.BACK -> onDone()
            else -> Unit
        }
        if (forwardedAction != null) onActionConsumed()
    }

    val slots = remember(session.groupIndex) { CustomizableIcons.group(session.group) }
    val stripState = rememberLazyListState()
    LaunchedEffect(session.groupIndex, session.slotIndex) {
        if (session.slotIndex in slots.indices) {
            stripState.animateScrollToItem(session.slotIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x22000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {  },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xF20B1220), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Customize XMB Icons",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((index, group) in session.groups.withIndex()) {
                    val selected = index == session.groupIndex
                    Text(
                        text = groupLabel(group),
                        color = if (selected) Color.White else Color(0xFFB9C6DC),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .background(
                                if (selected) Color(0xFF3A82F6) else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onGroupMove(if (index > session.groupIndex) +1 else -1) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            val focused = slots.getOrNull(session.slotIndex)
            if (focused != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompositionLocalProvider(LocalIconAnimating provides true) {
                        SlotPreview(
                            slot = focused,
                            icon = customIcons[focused.key] ?: themeIcons[focused.key],
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Column {
                        Text(focused.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = when {
                                customIcons.containsKey(focused.key) -> "Your pick"
                                themeIcons.containsKey(focused.key) -> "From theme"
                                else -> "Default"
                            },
                            color = Color(0xFFB9C6DC),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            LazyRow(
                state = stripState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(slots, key = { it.key }, contentType = { "slot" }) { slot ->
                    val index = slots.indexOf(slot)
                    val selected = index == session.slotIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .width(84.dp)
                            .background(
                                if (selected) Color(0x33203A5A) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) Color(0xFF3A82F6) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSlotFocused(index) }
                            .padding(8.dp),
                    ) {
                        CompositionLocalProvider(LocalIconAnimating provides selected) {
                            SlotPreview(
                                slot = slot,
                                icon = customIcons[slot.key] ?: themeIcons[slot.key],
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text(
                            text = slot.displayName,
                            color = if (selected) Color.White else Color(0xFFB9C6DC),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            session.message?.let { message ->
                Text(text = message, color = Color(0xFFFFB4A2), fontSize = 12.sp)
            }

            PfpControllerHints(
                items = listOf(
                    ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Move"),
                    ControllerPromptItem(
                        listOf(GamepadAction.PREV_CATEGORY, GamepadAction.NEXT_CATEGORY),
                        "Group",
                    ),
                    ControllerPromptItem(GamepadAction.SELECT, "Pick"),
                    ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Reset"),
                    ControllerPromptItem(GamepadAction.BACK, "Done"),
                ),
                style = ControllerHintStyle.OVERLAY,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = launchPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A82F6)),
                ) { Text("Pick") }

                OutlinedButton(
                    onClick = { focused?.let { onResetSlot(it.key) } },
                    enabled = focused != null && customIcons.containsKey(focused.key),
                ) { Text("Reset") }
                OutlinedButton(onClick = onResetAll, enabled = customIcons.isNotEmpty()) { Text("Reset All") }
                OutlinedButton(onClick = onSaveAsTheme) { Text("Save as Theme…") }
                Box(Modifier.width(1.dp))
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

private val PICK_MIME = arrayOf(
    "image/png",
    "image/jpeg",
    "image/webp",
    "image/gif",
    "image/bmp",
    "image/heif",
)

private fun groupLabel(group: IconSlot.Group): String = when (group) {
    IconSlot.Group.CATEGORY_BAR -> "Category Bar"
    IconSlot.Group.ITEMS -> "Items"
    IconSlot.Group.STATUS -> "Status"
    IconSlot.Group.CONSOLE -> "Consoles"
}

@Composable
private fun SlotPreview(slot: IconSlot, icon: CustomIcon?, modifier: Modifier = Modifier) {
    if (icon != null) {
        CustomIconSurface(icon = icon, contentDescription = slot.displayName, modifier = modifier)
        return
    }
    if (DefaultSlotGlyph(slot = slot, contentDescription = slot.displayName, modifier = modifier)) return

    Box(
        modifier = modifier
            .border(1.dp, Color(0x66B9C6DC), RoundedCornerShape(6.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = slot.displayName.take(1), color = Color(0xFFB9C6DC), fontSize = 18.sp)
    }
}
