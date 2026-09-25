package com.psplauncher.core.ui.components

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

// ── The search box ────────────────────────────────────────────────────────────
//
// Full width, pill-shaped, magnifier INSIDE it where a modern search box puts it, and the whole
// row is the touch target. The App Drawer's header arrived at this shape first; it is here for
// the same reason [PfpHintBar] is — the drawer is not the only screen that searches a grid, and
// the installed-app picker cannot see feature-appbar to borrow it.
//
// It is the caret rule that makes this worth sharing rather than copying. A plain String value
// leaves the selection at 0 while text arrives around it, so a query seeded from outside the
// field — type-to-search hands the first character in before the box has focus — takes every
// character after it at position zero: typing C then L produces "lc" on the device. The drawer
// and SearchScreen each learned that separately. A third copy would have learned it a third time.

private val FIELD_HEIGHT = 40.dp
private val FIELD_CORNER = FIELD_HEIGHT / 2
private val FIELD_BORDER = 1.dp

@Composable
fun PfpSearchField(
    query: String,
    active: Boolean,
    focusRequester: FocusRequester,
    placeholder: String,
    onActivate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    colors: StorefrontColors,
    modifier: Modifier = Modifier,
) {
    // The edge brightens when the field is live rather than switching outright, so the box does
    // not blink between two looks every time the keyboard comes and goes.
    val edge by animateColorAsState(
        targetValue = if (active) colors.searchBorder else colors.searchBorder.copy(alpha = 0.35f),
        animationSpec = tween(160),
        label = "pfpSearchFieldEdge",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .background(colors.searchField, RoundedCornerShape(FIELD_CORNER))
            .border(FIELD_BORDER, edge, RoundedCornerShape(FIELD_CORNER))
            // No ripple and no indication: this is a text box, and a box that flashes when you
            // touch it reads as a button that did something other than take the caret.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onActivate() }
            .padding(horizontal = 16.dp),
    ) {
        SearchGlyph(colors)
        Spacer(Modifier.width(10.dp))

        // A TextFieldValue, not a String, with the caret set explicitly to the end — see the
        // note at the top of this file for what a plain String does to a seeded query.
        val field = remember(query) { TextFieldValue(query, selection = TextRange(query.length)) }
        BasicTextField(
            value = field,
            onValueChange = { onQueryChange(it.text) },
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.searchBorder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(
                        placeholder,
                        color = colors.textSecondary.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                    )
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}

/** The magnifier, hand-drawn rather than an icon font — this app has never shipped one. */
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
