package ir.hawraa.quran.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches queued audio files in the background.
 *
 * The queue lives in Room, so this worker is deliberately dumb: it asks
 * [DownloadManager.runPending] for one pass, and asks again while work remains and the system
 * has not stopped it. Everything that makes a download survive — the per-file rows, the resume
 * offsets, the failure marks — is already on disk before the first byte moves, which is why a
 * killed worker is an inconvenience rather than a loss.
 *
 * Progress is reported as `fetched`/`total` files, not bytes: the queue knows how many files it
 * has and does not know how big they are until they land.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manager: DownloadManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var run = manager.runPending { fetched, total ->
            setProgress(workDataOf(KEY_FETCHED to fetched, KEY_TOTAL to total))
        }

        // Keep going while the queue has work and the system still allows it. A whole-Quran
        // request is thousands of files; returning `retry` between passes would add backoff
        // delays to a download that is going perfectly well.
        while (run.hasWorkLeft && !isStopped) {
            run = manager.runPending { fetched, total ->
                setProgress(workDataOf(KEY_FETCHED to fetched, KEY_TOTAL to total))
            }
            if (!run.allSucceeded && !run.hasWorkLeft) break
        }

        // Failures are recorded per file, so they are not a reason to retry the *work*: the
        // files that failed will fail again until the user asks for them again or the source
        // changes. Only a stopped worker (no network mid-run) is worth another attempt.
        return if (isStopped) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_FETCHED = "fetched"
        const val KEY_TOTAL = "total"
    }
}

/**
 * Owns the WorkManager schedule for downloads. Kept apart from the worker so callers ask for
 * "download please" without knowing about constraints or policies.
 *
 * One unique chain, appended to rather than replaced: a second request while the first is
 * running must not cancel it (that would abandon a partly-fetched surah) and must not be
 * dropped (that would leave new rows untouched until the next app launch).
 */
@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val networkOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun requestDownload() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(networkOnly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "hawraa-download"
    }
}
