package com.psplauncher.core.ui.components

enum class MenuGroup(val label: String?) {
    MAIN(null),
    LIBRARY("Library"),
    METADATA("Metadata"),
    SETTINGS("Settings"),
    CATEGORY("Category"),
    REMOVE("Remove"),
}

data class MenuRow<out T>(
    val action: T?,
    val label: String,
    val group: MenuGroup = MenuGroup.MAIN,
    val isDestructive: Boolean = false,
    val confirms: Boolean = isDestructive,
    val checked: Boolean = false,
    val hidden: Boolean = false,
    val pinnedToRoot: Boolean = false,
    val opensSubmenu: Boolean = false,
)

data class MenuState<T>(
    val title: String,
    val rows: List<MenuRow<T>>,
    val subtitle: String? = null,
    val selectedIndex: Int? = null,
    val parent: MenuState<T>? = null,
    val withheld: Set<T> = emptySet(),
)

sealed interface MenuSelect<out T> {
    data class Run<T>(val action: T) : MenuSelect<T>

    data class Replace<T>(val state: MenuState<T>) : MenuSelect<T>

    data object None : MenuSelect<Nothing>
}

private fun <T> MenuRow<T>.staysAtRoot(): Boolean =
    group == MenuGroup.MAIN || isDestructive || pinnedToRoot

fun <T> List<MenuRow<T>>.foldedIntoGroups(): List<MenuRow<T>> =
    groupBy { it.group }
        .toSortedMap(compareBy { it.ordinal })
        .flatMap { (group, rows) ->
            val pinned = rows.filter { it.staysAtRoot() && !it.isDestructive }
            val bundled = rows.filterNot { it.staysAtRoot() }

            pinned + when (bundled.size) {
                0, 1 -> bundled
                else -> listOf(MenuRow<T>(null, group.label ?: group.name, group, opensSubmenu = true))
            } + rows.filter { it.isDestructive }
        }

fun <T> MenuState<T>.rowsShown(): List<MenuRow<T>> {
    val visible = rows.filterNot { it.hidden || (it.action != null && it.action in withheld) }
    return if (parent == null) visible.foldedIntoGroups() else visible.sortedBy { it.group.ordinal }
}

fun <T> MenuState<T>.moved(delta: Int): MenuState<T> {
    val shown = rowsShown()
    if (shown.isEmpty()) return this
    val next = selectedIndex?.let { (it + delta).coerceIn(0, shown.lastIndex) }
        ?: if (delta > 0) 0 else shown.lastIndex
    return copy(selectedIndex = next)
}

fun <T> MenuState<T>.at(index: Int): MenuState<T> = copy(selectedIndex = index)

fun <T> MenuState<T>.submenuFor(group: MenuGroup): MenuState<T>? {
    val inGroup = rows.filter { it.group == group && !it.staysAtRoot() }
    if (inGroup.isEmpty()) return null
    return copy(subtitle = group.label, rows = inGroup, selectedIndex = 0, parent = this)
}

fun <T> MenuState<T>.confirmFor(row: MenuRow<T>): MenuState<T>? {
    if (!row.confirms) return null
    val action = row.action ?: return null
    return copy(
        subtitle = "${row.label}?",
        rows = listOf(
            MenuRow(null, CONFIRM_CANCEL_LABEL),
            MenuRow(action, row.label, isDestructive = true, confirms = false),
        ),
        selectedIndex = 0,
        parent = this,
    )
}

fun <T> MenuState<T>.chose(index: Int): MenuSelect<T> {
    val row = rowsShown().getOrNull(index) ?: return MenuSelect.None

    if (row.opensSubmenu) {
        return submenuFor(row.group)?.let { MenuSelect.Replace(it) } ?: MenuSelect.None
    }
    val action = row.action ?: return MenuSelect.Replace(back() ?: return MenuSelect.None)

    return confirmFor(row)?.let { MenuSelect.Replace(it) } ?: MenuSelect.Run(action)
}

fun <T> MenuState<T>.back(): MenuState<T>? = parent

const val CONFIRM_CANCEL_LABEL = "Cancel"
