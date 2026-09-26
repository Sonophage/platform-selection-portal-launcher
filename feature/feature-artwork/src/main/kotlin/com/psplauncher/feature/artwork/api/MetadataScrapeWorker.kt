package com.psplauncher.feature.artwork.api

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.UUID

@HiltWorker
class MetadataScrapeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val artworkRepository: ArtworkRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val notifier = BackgroundTaskNotifier(applicationContext)
        val mode = inputData.getString(KEY_MODE) ?: MODE_MISSING

        val platformId = inputData.getString(KEY_PLATFORM)
        val label = when {
            platformId != null -> "Scraping missing artwork: ${platformId.uppercase()}"
            mode == MODE_ALL -> "Re-scraping all games"
            else -> "Scraping missing artwork"
        }
        notifier.running(TASK_ID, label, null)
        var lastNotified = 0L

        val onProgress: (ScrapeProgress) -> Unit = { p ->

            val now = System.currentTimeMillis()
            if (now - lastNotified >= 500 || p.current == p.total) {
                lastNotified = now
                notifier.running(
                    TASK_ID, "$label — ${p.current}/${p.total}",
                    if (p.total > 0) p.current.toFloat() / p.total else null,
                )
            }
            setProgressAsync(
                workDataOf(
                    KEY_CURRENT to p.current,
                    KEY_TOTAL to p.total,
                    KEY_SUCCEEDED to p.succeeded,
                    KEY_FAILED to p.failed,
                    KEY_TITLE to p.title,
                    KEY_SOURCE to p.scrapeSource,
                    KEY_ASSET to p.scrapeAsset,
                )
            )
        }

        return try {
            val result = when {
                platformId != null -> artworkRepository.scrapeMissingForPlatform(platformId, onProgress)
                mode == MODE_ALL -> artworkRepository.reScrapeAllGames(onProgress)
                else -> artworkRepository.scrapeMissingOnly(onProgress)
            }

            val counts = "${result.succeeded} succeeded, ${result.failed} failed of ${result.total}"
            notifier.complete(
                TASK_ID, "Artwork scrape finished",
                result.stoppedReason?.let { "$counts. $it" } ?: counts,
            )
            Result.success(
                workDataOf(
                    KEY_SUCCEEDED to result.succeeded,
                    KEY_FAILED to result.failed,
                    KEY_TOTAL to result.total,
                    KEY_MODE to mode,
                    KEY_STOPPED_REASON to result.stoppedReason,
                )
            )
        } catch (e: CancellationException) {
            notifier.complete(TASK_ID, "Artwork scrape cancelled", "Artwork fetched so far is kept")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Scrape batch failed")
            notifier.failed(TASK_ID, "Artwork scrape failed", e.message ?: "Unexpected error")
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unexpected error")))
        }
    }

    companion object {
        const val UNIQUE_NAME = "pfp_metadata_scrape"
        const val TASK_ID = "metadata_scrape"
        const val MODE_ALL = "all"
        const val MODE_MISSING = "missing"
        const val KEY_MODE = "mode"

        const val KEY_PLATFORM = "platform"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_SUCCEEDED = "succeeded"
        const val KEY_FAILED = "failed"
        const val KEY_TITLE = "title"
        const val KEY_SOURCE = "source"
        const val KEY_ASSET = "asset"
        const val KEY_ERROR = "error"

        const val KEY_STOPPED_REASON = "stopped_reason"

        fun enqueue(context: Context, mode: String, platformId: String? = null): UUID {
            val request = OneTimeWorkRequestBuilder<MetadataScrapeWorker>()
                .setInputData(
                    if (platformId == null) workDataOf(KEY_MODE to mode)
                    else workDataOf(KEY_MODE to mode, KEY_PLATFORM to platformId)
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
            return request.id
        }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
