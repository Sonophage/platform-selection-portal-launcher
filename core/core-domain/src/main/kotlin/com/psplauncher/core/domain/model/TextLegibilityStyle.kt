package com.psplauncher.core.domain.model

enum class TextLegibilityStyle(val label: String) {
    NONE("None"),
    SHADOW("Drop Shadow"),
    OUTLINE("Outline"),
    PLATE("Contrast Plate"),
    AUTO("Automatic");

    companion object {
        val DEFAULT = AUTO

        fun fromName(value: String?): TextLegibilityStyle =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
