package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.psplauncher.core.ui.theme.menuCursorEdge
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel

val DetailIconTileWidth = 196.dp
val DetailIconTileHeight = 110.dp

@Composable
fun PfpDetailLaunchButton(
    label: String,
    icon: ImageVector?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,

    fill: Color = DetailButtonRest,

    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (compact) 32.dp else 58.dp)
            .clip(shape)

            .background(
                when {
                    focused -> DetailButtonFocusFill
                    compact && fill == DetailButtonRest -> DetailButtonRestCompact
                    else -> fill
                }
            )

            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) menuCursorEdge() else Color.Transparent,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                vertical = if (compact) 6.dp else 15.dp,
                horizontal = if (compact) 12.dp else 26.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (focused) DetailButtonFocusText else DetailTextPrimary
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(if (compact) 16.dp else 24.dp),
            )
            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        }
        Text(
            text = label,
            color = content,
            fontSize = if (compact) 13.sp else 19.sp,

            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PfpDetailQuickAction(
    label: String,
    icon: ImageVector?,
    focused: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    val content = if (focused) DetailButtonFocusText else DetailTextPrimary
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .clip(shape)
            .background(if (focused) DetailButtonFocusFill else DetailButtonRest, shape)
            .semantics(mergeDescendants = true) {
                contentDescription?.let { this.contentDescription = it }
            }

            .clickable(enabled = available, role = Role.Button, onClick = onClick)
            .alpha(if (available) 1f else 0.38f)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            color = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PfpDetailIconTile(
    uri: String?,
    title: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(DetailIconTileWidth)
            .height(DetailIconTileHeight)
            .clip(shape)
            .background(DetailRowFill)
            .border(1.dp, DetailRowEdge, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            content != null -> content()
            uri != null -> AsyncImage(
                model = rememberArtworkModel(uri),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                text = title.take(1).uppercase(),
                color = DetailTextMuted,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
