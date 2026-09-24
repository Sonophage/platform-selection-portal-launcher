package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.core.ui.notification.SystemToast
import com.psplauncher.core.ui.notification.ToastKind

// ── The notification bar ──────────────────────────────────────────────────────
//
// What the toast pill became. The pill appeared in the top centre, said its piece for three
// seconds and was gone; anything you were not looking at you never saw. The newest one now sits in
// the status strip's left half, where the live activity is, and pressing that corner pulls the
// rest of them down as a bar — "hitting that area can show those notifications as a bar going
// down".
//
// Everything below the bar stays live. It is a sheet from the top edge, not a modal: the only
// thing it takes is the next press, which closes it.

@Composable
fun XmbNotificationBar(
    open: Boolean,
    items: List<SystemToast>,
    /** The device's own notifications, in their own section. Empty when access is not granted. */
    android: List<AndroidNotice> = emptyList(),
    /** Shown in place of the Android section when the permission has never been granted. */
    androidAccessGranted: Boolean = true,
    onGrantAndroidAccess: () -> Unit = {},
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(RowGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Sheet)
                    // Below the strip: the strip is what you pressed to get here and it stays
                    // legible, the same reason the context rail draws under it rather than over.
                    .padding(top = StripHeight + 8.dp, bottom = 14.dp)
                    .padding(horizontal = 20.dp),
            ) {
                // Two sections, not one interleaved list. They are different KINDS of thing: the
                // launcher's are events that happened and are done, Android's are ongoing and
                // stay until something dismisses them. Sorted together they would be one list
                // where half the rows can be acted on and half can only be read, with nothing
                // saying which is which.
                SectionHeading("This launcher")
                if (items.isEmpty()) {
                    Text(
                        "Nothing has happened yet",
                        color = Muted,
                        fontSize = DetailSize,
                        lineHeight = DetailSize * 1.3f,
                    )
                } else {
                    items.forEach { NotificationRow(it) }
                    Text(
                        "Clear",
                        color = Muted,
                        fontSize = DetailSize,
                        lineHeight = DetailSize * 1.3f,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClear)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                SectionHeading("Android")
                when {
                    !androidAccessGranted -> Text(
                        "Turn on Notification access to see these here",
                        color = Muted,
                        fontSize = DetailSize,
                        lineHeight = DetailSize * 1.3f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onGrantAndroidAccess)
                            .padding(vertical = 2.dp),
                    )
                    android.isEmpty() -> Text(
                        "Nothing from other apps",
                        color = Muted,
                        fontSize = DetailSize,
                        lineHeight = DetailSize * 1.3f,
                    )
                    else -> android.take(AndroidRows).forEach { AndroidRow(it) }
                }
            }
        }
        // The press that closes it, over everything the sheet is not covering. Only while open,
        // so the XMB underneath is untouched the rest of the time.
        if (open) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = DetailSize,
        lineHeight = DetailSize * 1.3f,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * One notification from another app: who it is from, then what it says.
 *
 * The app's name leads, which is the opposite of the launcher's own rows. A launcher report is
 * always from the launcher and says so by being in that section; an Android row is useless until
 * you know which app is talking.
 */
@Composable
private fun AndroidRow(notice: AndroidNotice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GlyphSlot)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Text(
                text = notice.appLabel.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = DetailSize,
                lineHeight = DetailSize * 1.3f,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text(
                listOfNotNull(notice.appLabel, notice.title).joinToString("  ·  "),
                color = Color.White,
                fontSize = TitleSize,
                lineHeight = TitleSize * 1.3f,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            notice.text?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = DetailSize,
                    lineHeight = DetailSize * 1.3f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(toast: SystemToast) {
    val accent = if (toast.kind == ToastKind.ERROR) ErrorTint else SuccessTint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GlyphSlot)
                .clip(RoundedCornerShape(5.dp))
                .background(accent.copy(alpha = 0.18f)),
        ) {
            Text(
                text = if (toast.kind == ToastKind.ERROR) "!" else "✓",
                color = accent,
                fontSize = DetailSize,
                lineHeight = DetailSize * 1.3f,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text(
                toast.title,
                color = Color.White,
                fontSize = TitleSize,
                lineHeight = TitleSize * 1.3f,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            toast.message?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = DetailSize,
                    lineHeight = DetailSize * 1.3f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val Sheet = Color(0xF2140902)
private val Muted = Color(0x99FFFFFF)
private val SuccessTint = Color(0xFF6FD08C)
private val ErrorTint = Color(0xFFE2606A)
private val GlyphSlot = 22.dp

/**
 * How many Android rows the sheet draws.
 *
 * It is a live set, not a history — a device with forty notifications would push the sheet off the
 * bottom of the screen, and the sheet is a glance, not a shade.
 */
private const val AndroidRows = 6
private val RowGap = 8.dp
private val TitleSize = NotificationBarStyle.TitleSp.sp
private val DetailSize = NotificationBarStyle.DetailSp.sp

/**
 * The bar's type sizes, against the bundle's own legibility floor.
 *
 * The floor — "No text below 28 px at native res" — was written for the toast card this bar
 * replaces, and it applies here for the same reason: these are sentences a user reads, not chrome.
 * The status strip's own 8-10sp is deliberately under it and always was; a clock you glance at and
 * a report you read are not the same kind of text.
 *
 * Kept as plain numbers so a JVM test can check them without a Compose runtime, which is how the
 * toast's floor was guarded before it.
 */
object NotificationBarStyle {
    /** The design's stated floor, in pixels on its own 1920x1080 frame. */
    const val LegibilityFloorPx = 28f

    /** This panel: 374dpi. 1080 physical pixels over 462dp. */
    const val PanelDensity = 2.3375f

    const val TitleSp = 13f
    const val DetailSp = 12f
}
