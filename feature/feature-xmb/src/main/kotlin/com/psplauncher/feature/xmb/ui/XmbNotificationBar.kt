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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.feature.xmb.viewmodel.NoticeFocus
import com.psplauncher.core.ui.notification.SystemToast
import com.psplauncher.core.ui.notification.ToastKind

// ── The notification bar ──────────────────────────────────────────────────────
//
// What the toast pill became. The pill appeared in the top centre, said its piece for three
// seconds and was gone; anything you were not looking at you never saw. The newest report sits in
// the status strip's left half now, and pressing that corner pulls the rest of them down.
//
// TWO COLUMNS, one per source: the device's own notifications on the left, this launcher's on the
// right. They are not interleaved because they are different kinds of thing — the launcher's are
// events that happened and are done, the system's are ongoing and stay until something dismisses
// them. One list sorted by time is a list where half the rows can be acted on and half can only be
// read, saying nothing about which is which.
//
// The wash is the context rail's shape turned a quarter — ramped top to bottom off the edge it
// drops from rather than left to right off the edge the rail hugs — but DARKER than the rail's.
// Two different colours on purpose: the rail puts short labels beside an edge and wants the
// wallpaper to keep showing, and this puts sentences across the middle of the screen over whatever
// art happens to be behind them.

/**
 * What the sheet's top row is showing, or null when it has nothing to show.
 *
 * One row, two tenants — a playing track or the last game you were in — so the drawing is one
 * shape and the difference is in what the primary does. [progress] is null for the game, which
 * has no position to report: a bar at zero would be a claim, not an absence.
 */
data class NoticeMedia(
    val title: String,
    val detail: String?,
    val artUri: String?,
    /** 0..1, or null when there is nothing to scrub. */
    val progress: Float?,
    /** "1:04 / 3:58", already formatted by whoever knows the clock. */
    val elapsed: String?,
    val isPlaying: Boolean,
    /** Skip is a music idea; the game row has one control and it is Resume. */
    val hasTransport: Boolean,
    val primaryLabel: String,
)

@Composable
fun XmbNotificationBar(
    open: Boolean,
    items: List<SystemToast>,
    /** The device's own notifications. Empty when access is not granted. */
    android: List<AndroidNotice> = emptyList(),
    /** Shown in place of the system row when the permission has never been granted. */
    androidAccessGranted: Boolean = true,
    /** The row across the top. Null when nothing is playing and nothing has been played. */
    media: NoticeMedia? = null,
    /** The row the cursor is on, so the controller and the finger see the same sheet. */
    focus: NoticeFocus? = null,
    onGrantAndroidAccess: () -> Unit = {},
    onNoticeTapped: (String) -> Unit = {},
    onNoticeDismissTapped: (String) -> Unit = {},
    onMediaPrimary: () -> Unit = {},
    onMediaPrev: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // THE CATCHER GOES FIRST, which is to say UNDERNEATH.
        //
        // It was declared last and therefore drawn on top of the sheet, which was harmless while
        // nothing in the sheet could be pressed: it caught the press that lands anywhere else and
        // there was no anywhere else. Now the rows open notifications and the media row has a
        // transport, and a full-screen clickable over all of them takes every one of those presses
        // and closes the sheet instead. It also obscured them: uiautomator reported a sheet with
        // no contents at all, because an opaque clickable covering a node prunes it.
        if (open) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // The wash covers the WHOLE screen, not the sheet's own height: it ended where the
            // content ended, which drew a horizontal edge across the middle of the wallpaper with
            // the rows floating above it. Full height, solid across the rows, gone by the bottom.
            Column(
                verticalArrangement = Arrangement.spacedBy(RowGap),
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to SheetScrim,
                            ScrimHold to SheetScrim,
                            1f to Color.Transparent,
                        ),
                    )
                    // Clear of the strip: it is what you pressed to get here and it stays legible,
                    // the same way the context rail draws under it rather than over.
                    .padding(top = StripHeight + 10.dp),
            ) {
                // Full width, above both columns: it is one thing about the whole device, where
                // the columns below are two lists from two sources.
                media?.let {
                    MediaRow(
                        media = it,
                        focused = focus == NoticeFocus.Media,
                        onPrimary = onMediaPrimary,
                        onPrev = onMediaPrev,
                        onNext = onMediaNext,
                        modifier = Modifier.padding(horizontal = EdgeGap),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                    modifier = Modifier.padding(horizontal = EdgeGap),
                ) {
                    NoticeColumn(label = "System", modifier = Modifier.weight(1f)) {
                        when {
                            !androidAccessGranted -> EmptyNote(
                                "Turn on Notification access to see these here",
                                onClick = onGrantAndroidAccess,
                            )
                            android.isEmpty() -> EmptyNote("Nothing from other apps")
                            else -> android.take(ColumnRows).forEach { notice ->
                                NoticeCard(
                                    lead = notice.appLabel,
                                    title = notice.title ?: notice.appLabel,
                                    detail = notice.text,
                                    accent = null,
                                    focused = (focus as? NoticeFocus.Notice)?.key == notice.key,
                                    // Only where it goes somewhere. A row that takes a press and
                                    // does nothing is the fault the keyboard prompts had.
                                    onClick = if (notice.canOpen) ({ onNoticeTapped(notice.key) }) else null,
                                    onDismiss = if (notice.canDismiss) ({ onNoticeDismissTapped(notice.key) }) else null,
                                )
                            }
                        }
                    }

                    NoticeColumn(label = "Launcher", modifier = Modifier.weight(1f)) {
                        if (items.isEmpty()) {
                            EmptyNote("Nothing has happened yet")
                        } else {
                            // No focus and no press: these are reports of finished work. The
                            // cursor does not stop here, so nothing draws as though it could.
                            items.take(ColumnRows).forEach { toast ->
                                NoticeCard(
                                    lead = if (toast.kind == ToastKind.ERROR) "!" else "\u2713",
                                    title = toast.title,
                                    detail = toast.message,
                                    accent = if (toast.kind == ToastKind.ERROR) ErrorTint else SuccessTint,
                                )
                            }
                            Text(
                                "Clear",
                                color = Muted,
                                fontSize = DetailSize,
                                lineHeight = DetailSize * 1.3f,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onClear)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One labelled column: the source's name, then what it has to say, down the page. */
@Composable
private fun NoticeColumn(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(
            label,
            color = Muted,
            fontSize = DetailSize,
            lineHeight = DetailSize * 1.3f,
            fontWeight = FontWeight.Bold,
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
private fun NoticeCard(
    lead: String,
    title: String,
    detail: String?,
    accent: Color?,
    focused: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RailCorner))
            // The focused card takes the rail's white capsule rather than a ring: this sheet and
            // the context rail are the same idea in two directions, and a second way of saying
            // "you are here" is a second thing to keep in step.
            .background(
                if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
        onDismiss?.let {
            Spacer(Modifier.weight(1f))
            Text(
                "\u00d7",
                color = if (focused) Color.White else Muted,
                fontSize = TitleSize,
                lineHeight = TitleSize * 1.3f,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = it)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * The row across the top of the sheet: what is playing, how far through, and its controls.
 *
 * Its progress is a hairline under the text rather than a bar beside it, the same shape the
 * battery took across the top of the screen — a line that is part of the thing it describes reads
 * as a property of it, where a bar next to it reads as a second control.
 */
@Composable
private fun MediaRow(
    media: NoticeMedia,
    focused: Boolean,
    onPrimary: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RailCorner))
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            media.artUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(MediaArt).clip(RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    media.title,
                    color = Color.White,
                    fontSize = TitleSize,
                    lineHeight = TitleSize * 1.3f,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                (media.elapsed ?: media.detail)?.let {
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
            if (media.hasTransport) {
                TransportKey("\u23ee", onPrev)
                Spacer(Modifier.width(4.dp))
            }
            TransportKey(media.primaryLabel, onPrimary, wide = true)
            if (media.hasTransport) {
                Spacer(Modifier.width(4.dp))
                TransportKey("\u23ed", onNext)
            }
        }
        media.progress?.let { fraction ->
            Spacer(Modifier.padding(top = 7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

/** One transport control, sized like the keycaps the hint bar draws rather than like a button. */
@Composable
private fun TransportKey(label: String, onClick: () -> Unit, wide: Boolean = false) {
    Text(
        label,
        color = Color.White,
        fontSize = DetailSize,
        lineHeight = DetailSize * 1.3f,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 12.dp else 8.dp, vertical = 4.dp),
    )
}

private val Muted = Color(0x99FFFFFF)
private val SuccessTint = Color(0xFF6FD08C)
private val ErrorTint = Color(0xFFE2606A)
private val GlyphSlot = 22.dp
private val MediaArt = 34.dp
private val RowGap = 8.dp
private val ColumnGap = 22.dp
private val EdgeGap = 20.dp

/** How many each column shows. It is a glance, not a shade — forty would run off the screen. */
private const val ColumnRows = 5

/**
 * Darker than the rail's [XmbScrim], deliberately.
 *
 * The rail lays short labels along an edge and wants the wallpaper to keep showing through; this
 * lays sentences across the middle of the screen over whatever art is behind them, and at the
 * rail's 77% they were legible against a dark wallpaper and not against a bright one.
 */
private val SheetScrim = Color(0xF2050200)

/**
 * How far down the SCREEN the wash stays solid before it starts to go.
 *
 * The rows live in the top third, so this holds well past them and then has the rest of the height
 * to disappear over — a short fade at the bottom of a full-screen wash is a band, not a gradient.
 */
private const val ScrimHold = 0.34f

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
