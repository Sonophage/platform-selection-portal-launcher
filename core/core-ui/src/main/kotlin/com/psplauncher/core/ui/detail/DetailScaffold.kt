package com.psplauncher.core.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar

// ── Console-style detail page: background, scaffold, header, footer ───────────
//
// The one page frame every full-page library entry (games, apps, videos) shares, in the approved
// PS3-era information-page direction: a see-through breadcrumb header over a thin divider, a scrolling body
// centered at a readable maximum width, and a permanent controller-helper footer pinned in its own
// layout row.
//
// Everything here is accent-derived from the active PFP/XMB theme — no baked-in blue and no fixed
// color asset — so the page follows whichever color scheme (and monthly "Original" hue) is active.
//
// Why this lives in core-ui rather than a feature module: two feature modules render full-page
// entry details (feature-xmb's Game Detail and App Detail), features must not depend on each
// other, and a second copy of these surfaces would drift. Same reasoning as ControllerHintBar.

// Page tones, all read from the theme's detail palette (see DetailPalette.kt: the App Drawer's colors
// with the mockup's layering).
internal val DetailTextPrimary: Color @Composable @ReadOnlyComposable get() = detailPalette().textPrimary
internal val DetailTextMuted: Color @Composable @ReadOnlyComposable get() = detailPalette().textMuted
internal val DetailRowFill: Color @Composable @ReadOnlyComposable get() = detailPalette().rowFill
internal val DetailRowEdge: Color @Composable @ReadOnlyComposable get() = detailPalette().rowEdge
internal val DetailDivider: Color @Composable @ReadOnlyComposable get() = detailPalette().divider
internal val DetailFocusEdge: Color @Composable @ReadOnlyComposable get() = detailPalette().focus

// ── Button tones ──────────────────────────────────────────────────────────────
//
// The detail pages' buttons invert on focus rather than wearing a colour: at rest they are
// translucent glass over the page, and the focused one becomes a near-white slab with dark text.
// That is the tvOS idiom, and it reads at arm's length in a way a coloured fill plus a thin ring
// does not -- the focused control is the brightest thing on the page by a wide margin.
//
// It replaced a fixed green Launch button. The green was the one element on these pages that
// ignored the theme entirely, which mattered more once the pages started taking their colour
// from the game's own artwork: a green slab sat on top of every game's palette.

/** Resting fill: glass, so the page's colour reads through every button equally. */
val DetailButtonRest = Color.White.copy(alpha = 0.13f)

/**
 * The resting fill for a COMPACT launch button — a sixth of the ordinary one.
 *
 * It is enough to hold the shape against artwork and not enough to read as a control. The
 * compact form is used where the button is a legend for the pad, so a plate as solid as a real
 * button's is the wrong promise.
 */
val DetailButtonRestCompact = Color.White.copy(alpha = 0.06f)

/** Focused fill. Not pure white: pure white blooms against a dark page. */
val DetailButtonFocusFill = Color(0xFFEDEDED)

/** Label on the focused fill. Near-black rather than black, to match the fill's softness. */
val DetailButtonFocusText = Color(0xFF101014)

/** Readable maximum width for the page body, plus its side margins. */
val DetailContentMaxWidth: Dp = 920.dp
val DetailContentPadding: Dp = 28.dp

/**
 * The height the helper footer always reserves. Fixed on purpose: the footer's prompts change with
 * context (and fade out entirely for touch input), and neither may move the body's geometry.
 */
val DetailFooterHeight: Dp = 58.dp

internal val DetailTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.72f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

/**
 * The page surface colors: the App Drawer's translucent theme gradient, with see-through header and
 * footer bands, so the XMB wave reads through the page exactly as it does through the drawer.
 */
@Composable
@ReadOnlyComposable
internal fun detailSurfaceTop(): Color = detailPalette().pageTop

@Composable
@ReadOnlyComposable
internal fun detailSurfaceBottom(): Color = detailPalette().pageBottom

@Composable
@ReadOnlyComposable
internal fun detailHeaderSurface(): Color = detailPalette().header

/**
 * The whole-page backdrop: the App Drawer's translucent theme gradient (deep top easing into a
 * midtone), so the XMB wave reads through it exactly as it does through the drawer. Never a texture.
 *
 * [content] is a [BoxScope], so callers can stack modals/overlays on top of the page.
 */
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

/**
 * The detail page frame: pinned breadcrumb header, scrolling body, pinned helper footer.
 *
 * The body scrolls under [scrollState] the caller owns — controller focus and touch must share one
 * scroll owner, so the screen that drives focus-driven scrolling passes its state in here.
 *
 * [overlay] is the top layer of the page's own stack: blocking overlays (context menus, pickers,
 * viewers) belong there rather than inside the scrolling body, so they cover the whole screen and
 * cannot be scrolled away.
 */
@Composable
fun PfpDetailScaffold(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentMaxWidth: Dp = DetailContentMaxWidth,
    horizontalPadding: Dp = DetailContentPadding,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    /**
     * The layer UNDER the page: the entry's own artwork, full-bleed (see [PfpDetailArtBackdrop]).
     * Drawn over the plain page gradient and under everything else, including the header and the
     * footer, so their see-through bands show the art rather than the theme.
     */
    backdrop: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    PfpDetailBackground(modifier = modifier.fillMaxSize()) {
        backdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            header()
            // Hard viewport edge: nothing in the body may paint into the header or footer rows.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                // The body's real viewport, so the page can size its top band to fit above the
                // footer (see detailHeroHeightFor).
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

/** The height of the scrolling body between the header and the footer (unbounded outside a scaffold). */
val LocalDetailViewportHeight = staticCompositionLocalOf { Dp.Infinity }

/**
 * The breadcrumb header: `◀` + title over a small subtitle (e.g. `Nintendo DS` / `ROM`), the same
 * shape as the App Drawer's header, over a thin divider like the drawer's.
 *
 * Deliberately non-focusable for the controller — Back is a button, not a page node — but the arrow
 * and the title stack are one touch target that returns off the page, and it is the only header
 * chrome, so touch users always have a way back even when the controller cursor is hidden.
 */
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
            // Deliberately unweighted: a Row measures unweighted children first, in order, so the
            // breadcrumb claims its full width before [trailing] and is never cut short by it. (It
            // used to share a weight with the spacer below, which handed it only half the leftover
            // space.) Past the screen width it still ellipsizes against the Row's own bound.
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
                // 48dp touch target (Android's minimum) around a 16sp glyph, so the arrow stays easy to
                // hit without a visible chip.
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
                // Gets whatever the breadcrumb leaves; clipped rather than pushing into it.
                Box(modifier = Modifier.clipToBounds(), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DetailDivider))
    }
}

/**
 * The permanent controller-helper footer: a real layout row below the scrolling body, never an
 * overlay.
 *
 * Geometry is reserved whether or not the prompts are showing or resolving, so fading the hints
 * (existing PFP behaviour: hints fade while the last input was touch) never moves content.
 */
@Composable
fun PfpDetailHelperFooter(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "pfpDetailHelperFooter",
    )
    // The shared [PfpHintBar], in a slot that reserves [DetailFooterHeight] whether the bar is
    // showing or not. The fill and the divider rule that used to be here are gone with the pill:
    // the bar brings its own scrim, and a detail page's body clips to its own viewport, so nothing
    // scrolls under it either way.
    Box(
        modifier = modifier.fillMaxWidth().height(DetailFooterHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PfpHintBar(items = items, modifier = Modifier.alpha(alpha))
    }
}

/** A small all-caps label that names a band of the page (e.g. `MEDIA PREVIEW`). */
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

/**
 * The focus ring every controller-focusable detail surface shares: a thin bright edge plus a lift in
 * fill, applied inside the node's own bounds so focus never changes layout.
 *
 * Deliberately not a scale animation: a scaled focus target moves its neighbours, which is exactly
 * the layout shift the approved design forbids.
 */
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

/** Arrangement for the footer's own prompt rows (helpers keep call sites terse). */
internal val DetailHelperArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp)
