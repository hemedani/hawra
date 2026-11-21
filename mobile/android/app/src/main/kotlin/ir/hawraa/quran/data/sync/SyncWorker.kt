package ir.hawraa.quran.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background sync to the Lesan backend: reading sessions, presets, preset sittings and
 * coverage.
 *
 * Everything is already safe in Room before this runs, so the worker is pure catch-up: a
 * failure only means "try again later", never data loss.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sync: ReadingSessionSync,
    private val presetSync: PresetSync,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Both streams always run: a preset failure must not block session upload, and vice
        // versa. The result is "retry" only when something actually failed.
        val sessions = sync.syncPending()
        val presets = presetSync.syncPending()
        return if (sessions.isSuccess && presets.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "hawraa-sync-periodic"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "hawraa-sync-now"
        const val PERIOD_HOURS = 6L
    }
}

/**
 * Owns the WorkManager schedule. Kept out of the worker so callers (app start, after a
 * session finalizes) ask for "sync please" without knowing about constraints.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val networkOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Coalesced periodic catch-up; KEEP so the existing window is not reset. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncWorker.PERIOD_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(networkOnly)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Asks for an immediate push, e.g. right after a session is finalized. Unique work
     * with KEEP: if a push is already queued there is nothing to add.
     */
    fun requestImmediate() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkOnly)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
