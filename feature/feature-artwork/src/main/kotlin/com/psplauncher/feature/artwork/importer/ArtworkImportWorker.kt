package com.psplauncher.feature.artwork.importer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.artwork.portable.PortableArtworkLibrary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import java.util.UUID

@HiltWorker
class ArtworkImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val executor: ArtworkImportExecutor,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val notifier = BackgroundTaskNotifier(applicationContext)
        val planFile = resolvePlanFile(applicationContext, inputData.getString(KEY_PLAN_FILE_NAME))
            ?: return Result.failure(workDataOf(KEY_ERROR to "Import plan not found"))
        val plan = ImportPlan.parse(planFile.readText())
            ?: return Result.failure(workDataOf(KEY_ERROR to "Import plan unreadable")).also { planFile.delete() }
        val transfer = runCatching {
            PortableArtworkLibrary.Transfer.valueOf(inputData.getString(KEY_TRANSFER) ?: "")
        }.getOrDefault(PortableArtworkLibrary.Transfer.COPY)

        val label = "Importing artwork — ${plan.sourceLabel}"
        notifier.running(TASK_ID, label, null)
        var lastShown = 0

        return try {
            val summary = executor.execute(plan, transfer) { progress ->

                if (progress.done - lastShown >= PROGRESS_STRIDE || progress.done == progress.total) {
                    lastShown = progress.done
                    notifier.running(TASK_ID, label, progress.done.toFloat() / progress.total.coerceAtLeast(1))
                    setProgressAsync(
                        workDataOf(
                            KEY_PROGRESS_DONE to progress.done,
                            KEY_PROGRESS_TOTAL to progress.total,
                            KEY_PROGRESS_LABEL to progress.label,
                        )
                    )
                }
            }
            notifier.complete(
                TASK_ID, "Artwork import finished",
                "${summary.imported} imported, ${summary.skipped} skipped, ${summary.failed} failed",
            )
            planFile.delete()
            Result.success(workDataOf(KEY_IMPORTED to summary.imported, KEY_FAILED to summary.failed))
        } catch (e: CancellationException) {
            notifier.complete(TASK_ID, "Artwork import cancelled", null)
            planFile.delete()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Artwork import failed")
            notifier.failed(TASK_ID, "Artwork import failed", e.message ?: "Unexpected error")
            planFile.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unexpected error")))
        }
    }

    companion object {
        const val UNIQUE_NAME = "pfp_artwork_import"
        const val TASK_ID = "artwork_import"
        const val KEY_PLAN_FILE_NAME = "plan_file_name"
        const val KEY_TRANSFER = "transfer"
        const val KEY_ERROR = "error"
        const val KEY_IMPORTED = "imported"
        const val KEY_FAILED = "failed"
        const val KEY_PROGRESS_DONE = "done"
        const val KEY_PROGRESS_TOTAL = "total"
        const val KEY_PROGRESS_LABEL = "label"
        private const val PROGRESS_STRIDE = 20

        private const val PLANS_DIR = "import_plans"

        private fun resolvePlanFile(context: Context, name: String?): File? {
            if (name.isNullOrBlank()) return null
            val dir = File(context.filesDir, PLANS_DIR)
            val file = File(dir, name)
            val safe = file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
            return if (safe && file.isFile) file else null
        }

        fun enqueue(
            context: Context,
            plan: ImportPlan,
            transfer: PortableArtworkLibrary.Transfer,
        ): UUID {
            val dir = File(context.filesDir, PLANS_DIR).apply { mkdirs() }

            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "plan-${System.currentTimeMillis()}.json")
            file.writeText(ImportPlan.encode(plan))
            val request = OneTimeWorkRequestBuilder<ArtworkImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PLAN_FILE_NAME to file.name,
                        KEY_TRANSFER to transfer.name,
                    )
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
