package com.psplauncher.feature.artwork.portable

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType

object ArtworkKeyFactory {
    fun keyFor(game: Game): String? = when {
        !game.romPath.isNullOrBlank() -> {
            val fileName = game.romPath!!.replace('\\', '/').substringAfterLast('/')
            "rom/${game.platformId}/${ArtworkNaming.slug(ArtworkNaming.fileStem(fileName))}"
        }
        game.contentType != GameContentType.GAME || !game.packageName.isNullOrBlank() -> {
            val pkg = game.packageName ?: return null
            val shortcut = game.shortcutId
            if (shortcut.isNullOrBlank()) "app/$pkg" else "app/$pkg/${ArtworkNaming.slug(shortcut)}"
        }
        game.isManualEntry -> "manual/${ArtworkNaming.slug(game.title)}"
        else -> null
    }

    fun folderPathFor(key: String): String? {
        val parts = key.split('/')
        return when {
            parts.size == 3 && parts[0] == "rom" -> "games/${parts[1]}/${parts[2]}"
            parts.size >= 2 && parts[0] == "app" -> "apps/${parts.drop(1).joinToString("-")}"
            parts.size == 2 && parts[0] == "manual" -> "manual/${parts[1]}"
            else -> null
        }
    }
}
