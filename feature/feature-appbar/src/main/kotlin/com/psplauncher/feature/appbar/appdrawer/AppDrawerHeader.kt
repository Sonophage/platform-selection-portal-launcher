package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.psplauncher.core.ui.components.PfpSearchField
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
// The field itself is core-ui's [PfpSearchField] — this row is the drawer's placement of it, and
// the box, the caret rule and the magnifier are the app's, shared with the installed-app picker.

private val HEADER_HEIGHT = 56.dp

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PfpSearchField(
            query = searchQuery,
            active = searchActive,
            focusRequester = searchFocus,
            placeholder = "Search apps",
            onActivate = { onSearchToggle(true) },
            onQueryChange = onSearchChange,
            onDone = onSearchDone,
            colors = colors,
        )
    }
}
