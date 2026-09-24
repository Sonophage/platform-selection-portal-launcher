package com.psplauncher.feature.backup

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        // The worker reads its own version rather than being told it.
        //
        // It used to take both from inputData, and the one caller that enqueues it passed none,
        // so every backup this app has ever written records appVersionCode 0 and appVersionName
        // "unknown" -- the two fields whose whole job is to say which version wrote the file.
        // Nothing failed, because a default is a perfectly good Int. Reading it here means a
        // caller cannot forget, which is the only fix that stays fixed.
        val pkg = runCatching {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        }.getOrNull()
        // longVersionCode arrived in P and minSdk is Q, so the deprecated branch was unreachable.
        val versionCode = pkg?.longVersionCode?.toInt() ?: 0
        val versionName = pkg?.versionName ?: "unknown"
        return when (
            val result = backupManager.createBackup(
                appVersionCode = versionCode,
                appVersionName = versionName,
                createdAt      = now,
            )
        ) {
            is BackupResult.Success -> Result.success(
                workDataOf(KEY_OUTPUT_PATH to result.displayName)
            )
            is BackupResult.Failure -> Result.failure(
                workDataOf(KEY_ERROR to result.reason)
            )
        }
    }

    companion object {
        const val TAG                    = "pfp_backup"
        const val KEY_OUTPUT_PATH        = "output_path"
        const val KEY_ERROR              = "error"
    }
}
