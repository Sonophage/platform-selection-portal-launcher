package com.psplauncher.feature.library.scanner

import android.content.Context
import android.net.Uri
import com.psplauncher.core.domain.model.Game
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class M3uPlaylistReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(game: Game): List<String>? {
        val path = game.romPath ?: return null
        if (!path.endsWith(".m3u", ignoreCase = true)) return null
        return try {
            if (!game.romUri.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(game.romUri))
                    ?.bufferedReader()?.readLines()
            } else {
                File(path).takeIf { it.isFile }?.readLines()
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read playlist $path — discs keep their own identity")
            null
        }
    }
}
