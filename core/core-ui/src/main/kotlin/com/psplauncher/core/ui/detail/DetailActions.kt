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
import androidx.compose.ui.graphics.lerp
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

// ── Primary action region ─────────────────────────────────────────────────────
//
// Launch is the page's primary action and its strongest focus treatment; the quick actions beneath
// it are secondary. Both keep the shared focus language (thin bright edge + slight fill lift, no
// scale, no layout shift) so the whole app reads as one cursor.

/** The 196×110 icon frame beside the launch action. */
val DetailIconTileWidth = 196.dp
val DetailIconTileHeight = 110.dp

@Composable
fun PfpDetailLaunchButton(
    label: String,
    icon: ImageVector?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // A caller may tint the RESTING state (the video page marks its lead action this way). The
    // focused state is never tinted: exactly one control is focused, and it always looks the same
    // so the eye can find it without reading anything.
    fill: Color = DetailButtonRest,
) {
    // A pill, not a rounded rectangle: at this height the radius is half the height, which is what
    // makes a tvOS button read as a button rather than as a card.
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clip(shape)
            .background(if (focused) DetailButtonFocusFill else fill)
            // The cursor's bright edge, AFTER the fill so it draws on top of it.
            //
            // The inverted fill alone is unmistakable on artwork, which is where this button was
            // designed. It is not unmistakable on a pale overlay card, and a destructive confirm
            // is exactly that: a light Cancel that happens to be focused next to a pink Remove
            // that is not, where the eye reads the tint as "this is the one that will happen".
            // Reported from the device as "it didn't delete" — the A-press had hit Cancel, and
            // nothing on screen said so. Same edge colour as Modifier.menuCursor, which is the
            // treatment every menu row in the app already uses to mean "this one".
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) menuCursorEdge() else Color.Transparent,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (focused) DetailButtonFocusText else DetailTextPrimary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            color = content,
            fontSize = 19.sp,
            // Semibold, not bold. The focused pill already carries the emphasis; bold on top of
            // the inversion is two shouts for one thing.
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A labelled quick action under Launch.
 *
 * An action that is not [available] stays in its slot (so the page's action set never moves) but is
 * disabled: drained, untappable, and left out of the controller graph by the caller.
 */
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
    // Same pill, same inversion, smaller: the secondary row reads as the same kind of control as
    // Launch rather than as a different family of tile.
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
            // Disabled rather than hidden: TalkBack reads it as a dimmed button, and taps do nothing.
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

/** The game's icon/cover tile. [content] overrides the artwork (e.g. a package icon preview). */
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
