package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.theme.StorefrontColors

// ── Header ────────────────────────────────────────────────────────────────────
//
// One search field, the width of the screen. Nothing else.
//
// It was `‹ Android › Recently Used` on the left, a 220dp box in the middle and a magnifier
// labelled "Search" on the right — four things saying two, and both of them said twice:
//
//  * the category tabs directly below this row already name the active filter AND underline it,
//    so the breadcrumb's tail was a second copy of the one thing that cannot be missed;
//  * the magnifier and the word "Search" sat beside a box whose own placeholder reads "Search",
//    and the bottom bar names the button that opens it.
//
// Back went with them. It was the arrow and the word "Android", neither of which is where B goes,
// and B is on the bar at the bottom of every screen now.
//
// What is left is the field, so the field gets the room: full width, pill-shaped, the magnifier
// INSIDE it where a modern search box puts it. The whole row is the touch target — tapping
// anywhere on it focuses the field and raises the keyboard, which is what the button used to do.

private val HEADER_HEIGHT = 56.dp
private val FIELD_HEIGHT = 40.dp
private val FIELD_CORNER = FIELD_HEIGHT / 2
private val FIELD_BORDER = 1.dp

@Composable
internal fun AppDrawerHeader(
    searchQuery: String,
    searchActive: Boolean,
    searchFocus: FocusRequester,
    onSearchToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchDone: () -> Unit,
    colors: StorefrontColors,
) {
    // The edge brightens when the field is live rather than switching outright, so the box does
    // not blink between two looks every time the keyboard comes and goes.
    val edge by animateColorAsState(
        targetValue = if (searchActive) colors.searchBorder else colors.searchBorder.copy(alpha = 0.35f),
        animationSpec = tween(160),
        label = "appDrawerSearchEdge",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(FIELD_HEIGHT)
                .background(colors.searchField, RoundedCornerShape(FIELD_CORNER))
                .border(FIELD_BORDER, edge, RoundedCornerShape(FIELD_CORNER))
                // No ripple and no indication: this is a text box, and a box that flashes when
                // you touch it reads as a button that did something other than take the caret.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSearchToggle(true) }
                .padding(horizontal = 16.dp),
        ) {
            SearchGlyph(colors)
            Spacer(Modifier.width(10.dp))

            // A TextFieldValue, not a String, and the caret is set explicitly to the end.
            //
            // With a plain String the selection stays at 0 while text arrives around it, so a
            // query seeded from outside the field — type-to-search hands the first character in
            // before the box has focus — takes every character after it at position zero. Typing
            // C then L produced "lc" on the device. SearchScreen learned this the same way.
            val field = remember(searchQuery) {
                TextFieldValue(searchQuery, selection = TextRange(searchQuery.length))
            }
            BasicTextField(
                value = field,
                onValueChange = { onSearchChange(it.text) },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.searchBorder),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchDone() }, onDone = { onSearchDone() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) Text(
                            "Search apps",
                            color = colors.textSecondary.copy(alpha = 0.55f),
                            fontSize = 15.sp,
                        )
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocus),
            )
        }
    }
}

/** The magnifier, hand-drawn rather than an icon font — the drawer has never shipped one. */
@Composable
private fun SearchGlyph(colors: StorefrontColors) {
    val tint = colors.textSecondary.copy(alpha = 0.75f)
    Canvas(modifier = Modifier.size(16.dp)) {
        val strokeW = 1.6f.dp.toPx()
        val cx = size.width * 0.42f
        val cy = size.height * 0.42f
        val r = size.width * 0.32f
        drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(strokeW))
        drawLine(
            color = tint,
            start = Offset(cx + r * 0.70f, cy + r * 0.70f),
            end = Offset(cx + r * 0.70f + size.width * 0.24f, cy + r * 0.70f + size.height * 0.24f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
