package ir.hawraa.quran.data.download

import ir.hawraa.quran.data.local.DownloadBatch
import ir.hawraa.quran.data.local.DownloadDao
import ir.hawraa.quran.data.local.DownloadEntity
import ir.hawraa.quran.data.local.DownloadState
import ir.hawraa.quran.di.AudioRoot
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.download.DownloadManifest
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.LocalAudioEntry
import ir.hawraa.quran.domain.download.LocalAudioIndex
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.download.LocalJuzFile
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a request actually did, so the UI can say "already have 284 of 286". */
data class DownloadRequest(
    val manifest: DownloadManifest,
    val total: Int,
    val alreadyDone: Int,
    val queued: Int,
) {
    val nothingToDo: Boolean get() = queued == 0
}

/** How one pass over the queue went. */
data class DownloadRun(
    val completed: Int,
    val failed: Int,
    /** Files still queued when the pass stopped — non-zero means "run me again". */
    val remaining: Int,
) {
    val hasWorkLeft: Boolean get() = remaining > 0
    val allSucceeded: Boolean get() = failed == 0
}

/**
 * Owns the `downloads` table and the audio files on disk. Nothing else touches either.
 *
 * The shape of the feature follows from one fact: the Quran is 6236 files. That rules out
 * "download in one call and report progress" and forces a **persisted queue** — one row per
 * file, written before any bytes move. Every property below is a consequence:
 *
 *  - **Interruption costs one file.** The queue is in SQLite, so a killed worker, a reboot or
 *    a flight-mode switch resumes from the next `queued` row. Rows are `downloading` while in
 *    flight and are re-picked if a run dies, because the state is a hint, not a lock.
 *  - **Re-requesting is a no-op.** Rows are keyed `(editionKey, fileKey)` and inserted with
 *    `IGNORE`, so asking for surah 2 twice downloads it once.
 *  - **Failure is per file.** A dead URL marks one row `failed` and the run continues; the
 *    batch as a whole is never abandoned because of one ayah.
 *  - **The index is a snapshot, not a query.** Playback asks "is this ayah on disk?" on the
 *    main thread while building a queue, so [index] is rebuilt from `done` rows after each run
 *    and at launch instead of being read from the database per ayah.
 */
@Singleton
class DownloadManager @Inject constructor(
    private val dao: DownloadDao,
    private val catalogueClient: DownloadCatalogueClient,
    private val catalogue: RecitationCatalogueRepository,
    private val fetcher: FileFetcher,
    @AudioRoot private val audioRoot: File,
    private val clock: Clock,
    private val scheduler: DownloadScheduler,
) : LocalAudioSource {

    private val _index = MutableStateFlow(LocalAudioIndex.EMPTY)

    /** What playback consults. Updated after every run and at launch. */
    val index: StateFlow<LocalAudioIndex> = _index.asStateFlow()

    override fun current(): LocalAudioIndex = _index.value

    fun observeBatches(): Flow<List<DownloadBatch>> = dao.observeBatches()

    fun observeTotalBytes(): Flow<Long> = dao.observeTotalBytes()

    fun observePending(): Flow<Int> = dao.observePendingCount()

    /**
     * Asks the server what this edition/scope needs and queues it.
     *
     * The manifest is fetched **before** anything is written: an edition that is not verified,
     * a scope with no ayahs, or a malformed range must fail as one clear error rather than as
     * a queue of rows that will each fail on their own.
     */
    suspend fun request(edition: EditionInfo, scope: DownloadScope): Result<DownloadRequest> {
        if (!edition.isDownloadable) {
            return Result.failure(IllegalStateException("edition ${edition.key} is not verified"))
        }
        val manifest = catalogueClient
            .manifest(edition.key, edition.reciterKey, scope)
            .getOrElse { return Result.failure(it) }
        if (manifest.isEmpty) {
            return Result.failure(IllegalStateException("manifest for ${scope.scopeKey} is empty"))
        }

        val now = clock.nowMillis()
        val rows = manifest.files.map { file ->
            DownloadEntity(
                editionKey = edition.key,
                reciterKey = edition.reciterKey,
                fileKey = file.key,
                scope = scope.wire,
                scopeKey = scope.scopeKey,
                surah = file.surah,
                ayah = file.ayah,
                juz = file.juz,
                url = file.url,
                relativePath = file.relativePath,
                expectedBytes = file.bytes,
                requestedAt = now,
            )
        }
        dao.insertIfAbsent(rows)
        // A file that failed last time is retryable by definition: asking for the scope again
        // is the retry gesture, so it must not need a separate button.
        dao.requeueFailed(edition.key, scope.scopeKey, now)

        val stored = dao.forBatch(scope.scopeKey, edition.key)
        val done = stored.count { it.state == DownloadState.DONE.key }
        val queued = stored.count { it.state != DownloadState.DONE.key }

        scheduler.requestDownload()
        return Result.success(
            DownloadRequest(
                manifest = manifest,
                total = stored.size,
                alreadyDone = done,
                queued = queued,
            ),
        )
    }

    /** Puts a batch's failed files back in the queue and asks for another run. */
    suspend fun retry(batch: DownloadBatch) {
        dao.requeueFailed(batch.editionKey, batch.scopeKey, clock.nowMillis())
        scheduler.requestDownload()
    }

    /**
     * Restarts a queue that a process death or a dead connection left half-done.
     *
     * WorkManager's own retry covers a worker that returned `retry`; this covers the case where
     * the process was killed and the work was never enqueued again. Cheap enough to run at every
     * launch: one count query, and nothing is scheduled when the queue is empty.
     */
    suspend fun resumeIfPending() {
        if (dao.pendingCount() > 0) scheduler.requestDownload()
    }

    /** Deletes a batch: its files first, then its rows, so a crash cannot orphan bytes. */
    suspend fun delete(batch: DownloadBatch) {
        dao.forBatch(batch.scopeKey, batch.editionKey).forEach { row ->
            val file = File(audioRoot, row.relativePath)
            file.delete()
            File(file.path + PART_SUFFIX).delete()
        }
        dao.deleteBatch(batch.scopeKey, batch.editionKey)
        refreshIndex()
    }

    /**
     * Works through the queue, one file at a time, until it is empty or [maxFiles] have been
     * fetched in this pass.
     *
     * The cap exists because a whole-Quran request is 6236 files: a worker that tried to do
     * them all in one execution would be killed by the system halfway with no progress
     * reported. Stopping at a boundary and letting the caller ask again is the difference
     * between a queue that resumes and one that restarts.
     */
    suspend fun runPending(
        maxFiles: Int = MAX_FILES_PER_PASS,
        onProgress: suspend (fetched: Int, total: Int) -> Unit = { _, _ -> },
    ): DownloadRun {
        val total = dao.pendingCount()
        var completed = 0
        var failed = 0

        while (completed + failed < maxFiles) {
            val chunk = dao.nextQueued(CHUNK)
            if (chunk.isEmpty()) break
            for (row in chunk) {
                if (completed + failed >= maxFiles) break
                val outcome = fetchOne(row)
                if (outcome) completed++ else failed++
                onProgress(completed + failed, total)
            }
        }

        refreshIndex()
        return DownloadRun(completed, failed, dao.pendingCount())
    }

    /** Returns true when the file is now on disk. */
    private suspend fun fetchOne(row: DownloadEntity): Boolean {
        val now = clock.nowMillis()
        dao.setState(row.editionKey, row.fileKey, DownloadState.DOWNLOADING.key, null, now)
        return try {
            val target = File(audioRoot, row.relativePath)
            val bytes = fetcher.fetch(row.url, target)

            // The server measured this file, so "it arrived" is not the same as "it is
            // complete". A chunked response declares no length, which makes a connection
            // dropped mid-file look exactly like a finished download; the only way to tell is
            // to compare against the size that was promised. Deleting on mismatch matters as
            // much as the check — leaving a truncated file on disk would let the next attempt
            // resume from the wrong offset and keep the wrong length forever.
            val problem = sizeMismatch(row.expectedBytes, bytes)
            if (problem != null) {
                target.delete()
                throw IOException(problem)
            }

            dao.markDone(row.editionKey, row.fileKey, bytes, clock.nowMillis())
            true
        } catch (cancelled: CancellationException) {
            // The worker was stopped: hand the row back rather than leaving it `downloading`.
            dao.setState(row.editionKey, row.fileKey, DownloadState.QUEUED.key, null, clock.nowMillis())
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            dao.setState(
                row.editionKey,
                row.fileKey,
                DownloadState.FAILED.key,
                message.take(MAX_ERROR_CHARS),
                clock.nowMillis(),
            )
            false
        }
    }

    /**
     * Rebuilds the playback index from `done` rows.
     *
     * The file's *kind* comes from the row itself, not from the scope: a per-ayah edition asked
     * for a whole surah has `scope = "surah"` but 286 per-ayah files, and `ayah != null` is
     * what makes them addressable.
     *
     * Juz rows go into [LocalAudioIndex.juzFiles], not into the verse index: a whole-juz MP3
     * covers an ayah but cannot start at it, so it must never be returned by `find`. It is still
     * indexed, because a juz-granularity reciter's queue addresses files by juz and needs to know
     * that one is on disk.
     *
     * The edition's style comes from the cached catalogue; when the cache has never been
     * filled (first launch, offline) the entry carries a blank style, which matches anything
     * rather than silently disabling playback.
     */
    suspend fun refreshIndex() {
        val styles = catalogue.cached()
            .asSequence()
            .flatMap { it.editions.asSequence() }
            .associate { it.key to it.style }

        val entries = mutableListOf<LocalAudioEntry>()
        val juzFiles = mutableListOf<LocalJuzFile>()

        for (row in dao.completed()) {
            val style = styles[row.editionKey].orEmpty()
            val path = File(audioRoot, row.relativePath).absolutePath
            val juz = row.juz
            if (juz != null) {
                juzFiles += LocalJuzFile(
                    reciterKey = row.reciterKey,
                    style = style,
                    juz = juz,
                    path = path,
                )
                continue
            }
            val surah = row.surah ?: continue
            entries += LocalAudioEntry(
                reciterKey = row.reciterKey,
                style = style,
                kind = if (row.ayah != null) EditionGranularity.AYAH else EditionGranularity.SURAH,
                surah = surah,
                ayah = row.ayah,
                path = path,
            )
        }
        _index.value = LocalAudioIndex(entries, juzFiles)
    }

    /** Absolute path on disk for a downloaded file, used by the downloads screen to play it. */
    fun pathOf(row: DownloadEntity): File = File(audioRoot, row.relativePath)

    suspend fun filesOf(batch: DownloadBatch): List<DownloadEntity> =
        dao.forBatch(batch.scopeKey, batch.editionKey)

    private companion object {
        /** Rows claimed per query. Big enough to be cheap, small enough to resume finely. */
        const val CHUNK = 16
        const val MAX_FILES_PER_PASS = 240
        const val MAX_ERROR_CHARS = 200
        const val PART_SUFFIX = ".part"
    }
}

/**
 * Compares a finished download against the size the server declared.
 *
 * Returns `null` when the file is acceptable, or a message when it is not. Pulled out as a
 * top-level function because it is the one piece of [DownloadManager.fetchOne] whose failure
 * is silent — a short file plays until it stops, with no error anywhere — and it deserves a
 * test that does not need a database, a worker and a fake catalogue to run.
 *
 * `expected == null` means "the server never said", which is the normal case for a
 * CDN-sourced file; that is not a failure, it is an absence of evidence.
 */
internal fun sizeMismatch(expected: Long?, actual: Long): String? =
    if (expected != null && actual != expected) {
        "expected $expected bytes, got $actual"
    } else {
        null
    }
