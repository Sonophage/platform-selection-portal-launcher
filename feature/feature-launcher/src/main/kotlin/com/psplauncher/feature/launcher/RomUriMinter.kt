package com.psplauncher.feature.launcher

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.psplauncher.core.data.database.dao.MemoryCardDao
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

object RomSourceAdmission {
    fun isAdmissible(path: String, sources: List<String>): Boolean {
        if (path.isBlank()) return false
        val target = runCatching { File(path).canonicalPath }.getOrNull() ?: return false

        return sources.any { source ->
            if (source.isBlank()) return@any false
            val root = runCatching { File(source).canonicalPath }.getOrNull() ?: return@any false
            target != root && target.startsWith(root + File.separator)
        }
    }
}

@Singleton
class RomUriMinter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryCardDao: MemoryCardDao,
) {
    suspend fun mint(romPath: String): Uri? {
        val sources = configuredSources()
        if (!RomSourceAdmission.isAdmissible(romPath, sources)) {
            Timber.w(
                "Refused to mint a ROM URI outside the configured sources (%d source(s) known)",
                sources.size,
            )
            return null
        }
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(romPath))
        }.onFailure { Timber.w(it, "FileProvider refused a ROM path inside a configured source") }
            .getOrNull()
    }

    private suspend fun configuredSources(): List<String> =
        memoryCardDao.getAll().mapNotNull { it.romDirectory?.takeIf { dir -> dir.isNotBlank() } }
}
