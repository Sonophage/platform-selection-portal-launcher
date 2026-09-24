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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
// seconds and was gone; anything you were not looking at you never saw. The newest report sits in
// the status strip's left half now, and pressing that corner pulls the rest of them down.
//
// TWO ROWS, one per source: the device's own notifications, and this launcher's. Each runs
// sideways from its label, the way every other list on this screen does. They are not interleaved
// because they are different kinds of thing — the launcher's are events that happened and are
// done, the system's are ongoing and stay until something dismisses them. One list sorted by time
// is a list where half the rows can be acted on and half can only be read, saying nothing about
// which is which.
//
// The wash is the context rail's, turned a quarter: same XmbScrim, ramped top to bottom off the
// edge it drops from rather than left to right off the edge the rail hugs.

@Composable
fun XmbNotificationBar(
    open: Boolean,
    items: List<SystemToast>,
    /** The device's own notifications. Empty when access is not granted. */
    android: List<AndroidNotice> = emptyList(),
    /** Shown in place of the system row when the permission has never been granted. */
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
                    .background(Brush.verticalGradient(0f to XmbScrim, 1f to Color.Transparent))
                    // Clear of the strip: it is what you pressed to get here and it stays legible,
                    // the same way the context rail draws under it rather than over.
                    .padding(top = StripHeight + 10.dp, bottom = ScrimTail),
            ) {
                NoticeRow(label = "System") {
                    when {
                        !androidAccessGranted -> EmptyNote(
                            "Turn on Notification access to see these here",
                            onClick = onGrantAndroidAccess,
                        )
                        android.isEmpty() -> EmptyNote("Nothing from other apps")
                        else -> LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(CardGap),
                            contentPadding = PaddingValues(horizontal = EdgeGap),
                        ) {
                            items(android, key = { it.key }) { notice ->
                                NoticeCard(
                                    lead = notice.appLabel,
                                    title = notice.title ?: notice.appLabel,
                                    detail = notice.text,
                                    accent = null,
                                )
                            }
                        }
                    }
                }

                NoticeRow(label = "Launcher") {
                    if (items.isEmpty()) {
                        EmptyNote("Nothing has happened yet")
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(CardGap),
                            contentPadding = PaddingValues(horizontal = EdgeGap),
                        ) {
                            items(items, key = { it.id }) { toast ->
                                NoticeCard(
                                    lead = if (toast.kind == ToastKind.ERROR) "!" else "✓",
                                    title = toast.title,
                                    detail = toast.message,
                                    accent = if (toast.kind == ToastKind.ERROR) ErrorTint else SuccessTint,
                                )
                            }
                        }
                        Text(
                            "Clear",
                            color = Muted,
                            fontSize = DetailSize,
                            lineHeight = DetailSize * 1.3f,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = EdgeGap, top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onClear)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        // The press that closes it, over everything the sheet is not covering.
        if (open) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        }
    }
}

/** One labelled row: the source's name, then whatever it has to say, running sideways. */
@Composable
private fun NoticeRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = Muted,
            fontSize = DetailSize,
            lineHeight = DetailSize * 1.3f,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = EdgeGap),
        )
        content()
    }
}

@Composable
private fun EmptyNote(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        color = Muted,
        fontSize = DetailSize,
        lineHeight = DetailSize * 1.3f,
        modifier = Modifier
            .padding(start = EdgeGap)
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
    )
}

/**
 * One notification, as a card in its row.
 *
 * [lead] is what goes in the badge — an app's initial for a system notice, a tick or a bang for
 * one of the launcher's. [accent] tints it where the kind means something; a system notification
 * has no kind, so it takes the neutral badge every monogram in this app wears.
 */
@Composable
private fun NoticeCard(lead: String, title: String, detail: String?, accent: Color?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = CardMaxWidth)
            .clip(RoundedCornerShape(RailCorner))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GlyphSlot)
                .clip(RoundedCornerShape(5.dp))
                .background((accent ?: Color.White).copy(alpha = if (accent != null) 0.18f else 0.12f)),
        ) {
            Text(
                text = lead.trim().firstOrNull()?.uppercase() ?: "?",
                color = accent ?: Color.White,
                fontSize = DetailSize,
                lineHeight = DetailSize * 1.3f,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                title,
                color = Color.White,
                fontSize = TitleSize,
                lineHeight = TitleSize * 1.3f,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
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

private val Muted = Color(0x99FFFFFF)
private val SuccessTint = Color(0xFF6FD08C)
private val ErrorTint = Color(0xFFE2606A)
private val GlyphSlot = 22.dp
private val RowGap = 12.dp
private val CardGap = 8.dp
private val EdgeGap = 20.dp
private val CardMaxWidth = 260.dp

/** How far past the last row the wash keeps fading, so it ends on nothing rather than an edge. */
private val ScrimTail = 40.dp

private val TitleSize = NotificationBarStyle.TitleSp.sp
private val DetailSize = NotificationBarStyle.DetailSp.sp

/**
 * The bar's type sizes, against the bundle's own legibility floor.
 *
 * The floor — "No text below 28 px at native res." — was written for the toast card this replaces,
 * and it applies here for the same reason: these are sentences a user reads, not chrome. The
 * status strip's own 8-10sp is deliberately under it and always was; a clock you glance at and a
 * report you read are not the same kind of text.
 *
 * Kept as plain numbers so a JVM test can check them without a Compose runtime.
 */
object NotificationBarStyle {
    /** The design's stated floor, in pixels on its own 1920x1080 frame. */
    const val LegibilityFloorPx = 28f

    /** This panel: 374dpi. 1080 physical pixels over 462dp. */
    const val PanelDensity = 2.3375f

    const val TitleSp = 13f
    const val DetailSp = 12f
}
