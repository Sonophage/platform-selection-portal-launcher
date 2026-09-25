package com.psplauncher.core.domain.model

data class PFPTheme(
    val id: String,
    val name: String,
    val author: String? = null,
    val version: String = "1.0",
    val waveColor: Long,                // ARGB
    val waveOpacity: Float = 0.7f,
    val waveSpeed: Float = 1.0f,
    val waveAmplitude: Float = 1.0f,
    val accentColor: Long,
    val textColor: Long,
    val backgroundUri: String? = null,  // path to background image
    val fontKey: String = "system_default",
    val hasBootAnimation: Boolean = false,
    val bootAnimationUri: String? = null,
    val soundPackUri: String? = null,
    val packagePath: String? = null,    // path to .xmbtheme file
    val isBuiltIn: Boolean = false,
)

/**
 * Sound events a theme can provide — and a HALF-BUILT feature, kept rather than deleted.
 *
 * Nothing reads this enum. But `XmbThemeLoader.extractSoundPack` does unpack a theme's sound pack
 * and `soundPackUri` is persisted on the theme row, so a pack is carried all the way to the
 * database and then never played: the live vocabulary is core-ui's `MenuSound`, which the user
 * assigns per slot in Settings ▸ Media.
 *
 * Deleting this would leave the extraction and the column with nothing naming the contract they
 * were written against, which makes the remaining half harder to finish rather than easier. It
 * stays until the pack is either played or the extraction goes with it.
 */
enum class ThemeSoundEvent {
    NAVIGATE_HORIZONTAL,
    NAVIGATE_VERTICAL,
    SELECT,
    BACK,
    CATEGORY_CHANGE,
    BOOT,
}
