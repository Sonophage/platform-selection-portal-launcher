package com.psplauncher.core.domain.model

data class Platform(
    val id: String,                     // e.g. "ps2", "gba", "android"
    val name: String,                   // e.g. "PlayStation 2"
    val shortName: String,              // e.g. "PS2"
    val iconRes: String?,               // built-in drawable resource name
    val accentColor: Long,              // ARGB color for wave tint
    val isPinnedToBar: Boolean = false, // user promoted to XMB category bar
    val barPosition: Int = -1,          // position on bar when pinned
    val preferredEmulatorPackage: String? = null,
    val romExtensions: List<String> = emptyList(), // e.g. [".iso", ".cso"]
)

/**
 * The platform ids the code has to name, rather than merely carry.
 *
 * Most platform ids are data: they come out of the seeder, the scanner and the database, and
 * nothing in the code says "ps2". A few are different — the code branches on them, so the string
 * is a name it has to know. Those belong here, once.
 *
 * [WINDOWS] had **nine** definitions before this existed: LibraryConsolidation, WindowsLibrarySetup,
 * PcShortcutImporter, PcGameExporter, ManualGameExportSelector, PcGameImportPlanner, PcGameScanner,
 * XMBViewModel and GameDetailViewModel each declared their own `"windows"`. Nine copies of one
 * string that must agree, in five modules, none of them guarded — exactly the pair problem, except
 * with nine sides. They all happened to agree; nothing made them.
 *
 * In core-domain because every module already depends on it, which is what makes one definition
 * reachable from all of them without a new edge.
 */
object PlatformIds {
    /**
     * PC games, however they are launched — Winlator, GameNative, a native port.
     *
     * The code branches on this one more than any other: the Windows Memory Card cannot be removed,
     * it is import-driven rather than scanned, and it has no ROMs to match artwork against.
     */
    const val WINDOWS = "windows"

    /**
     * Installed Android apps promoted into the library as games.
     *
     * Branched on in seven places before this: the seeder decides whether to create the card, the
     * drawer and Library Manager filter on it, Game Detail and the XMB offer "Unmark as Game" only
     * for it, and both shortcut entry points in `app` stamp it onto pinned shortcuts.
     */
    const val ANDROID = "android"
}
