package com.psplauncher.core.domain.model

data class PFPTheme(
    val id: String,
    val name: String,
    val author: String? = null,
    val version: String = "1.0",
    val waveColor: Long,
    val waveOpacity: Float = 0.7f,
    val waveSpeed: Float = 1.0f,
    val waveAmplitude: Float = 1.0f,
    val accentColor: Long,
    val textColor: Long,
    val backgroundUri: String? = null,
    val fontKey: String = "system_default",
    val hasBootAnimation: Boolean = false,
    val bootAnimationUri: String? = null,
    val soundPackUri: String? = null,
    val packagePath: String? = null,
    val isBuiltIn: Boolean = false,
)

enum class ThemeSoundEvent {
    NAVIGATE_HORIZONTAL,
    NAVIGATE_VERTICAL,
    SELECT,
    BACK,
    CATEGORY_CHANGE,
    BOOT,
}
