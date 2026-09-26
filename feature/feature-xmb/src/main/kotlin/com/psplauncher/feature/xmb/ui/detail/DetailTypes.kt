package com.psplauncher.feature.xmb.ui.detail

enum class ArtworkType { ICON, HERO, BACKGROUND }

val ArtworkType.displayLabel: String
    get() = when (this) {
        ArtworkType.ICON       -> "Game Icon"
        ArtworkType.HERO       -> "Hero Banner"
        ArtworkType.BACKGROUND -> "Background"
    }

data class DetailMedia(val uri: String, val isVideo: Boolean)

data class ArtPickerItem(
    val url: String,
    val thumbUrl: String? = null,
    val label: String? = null,
)

internal fun mediaStableId(media: DetailMedia): String =
    if (media.isVideo) "v:${media.uri}" else "i:${media.uri}"
