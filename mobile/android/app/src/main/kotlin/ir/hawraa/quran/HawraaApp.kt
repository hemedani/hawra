package ir.hawraa.quran

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.hawraa.quran.data.download.DownloadManager
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.data.repository.PresetSittingRepository
import ir.hawraa.quran.data.repository.ReadingSessionRepository
import ir.hawraa.quran.data.sync.SyncScheduler
import ir.hawraa.quran.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt injects the WorkManager factory so SyncWorker
 * can receive dependencies; the default WorkManager initializer is disabled in
 * the manifest so this custom Configuration is the one used.
 */
@HiltAndroidApp
class HawraaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var readingSessionRepository: ReadingSessionRepository

    @Inject
    lateinit var presetSittingRepository: PresetSittingRepository

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Resolve the session before anything that needs a token. The cache is published
            // immediately and the server check runs behind it, so this is a disk read in the
            // common case — and it is what keeps the gate closed for a returning reader.
            authRepository.restore()
            // A session left open by a killed process must be closed before the reader
            // can open a new one, otherwise its duration lands on the wrong session.
            readingSessionRepository.recoverOrphans()
            // Same reason, for preset sittings: an orphan closed now is history, one left
            // open would have the next listening burst appended to it.
            presetSittingRepository.recoverOrphans()
            syncScheduler.schedulePeriodic()
            // The playback index is a snapshot, so it has to be rebuilt after a process start
            // or the reader would stream verses that are sitting on disk.
            downloadManager.refreshIndex()
            // A download that was cut short (reboot, force-stop, no network) is still queued;
            // the constraints will hold it until there is a connection.
            downloadManager.resumeIfPending()
        }
    }
}
