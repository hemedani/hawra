package ir.hawraa.quran.data.repository

import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.local.ReadingSessionEntity
import ir.hawraa.quran.data.sync.SyncScheduler
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.session.FinishedSession
import ir.hawraa.quran.domain.session.ReadingSessionTracker
import ir.hawraa.quran.domain.session.SessionEffect
import ir.hawraa.quran.domain.session.SessionMode
import ir.hawraa.quran.domain.session.SessionRange
import ir.hawraa.quran.domain.session.TrackingResult
import ir.hawraa.quran.domain.session.TrackingState
import ir.hawraa.quran.domain.session.VerseRef
import ir.hawraa.quran.domain.settings.ReadingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Frozen copy of the reading settings in effect when a session opened. */
@Serializable
private data class SettingsSnapshot(
    val fontSizeSp: Int,
    val showTranslation: Boolean,
    val letterInk: String,
    val diacriticInk: String,
    val themeMode: String,
    val languageCode: String,
    /** Set when the session turned into a listening session (mode `listen`). */
    val reciterKey: String? = null,
    val playbackSpeed: Double? = null,
)

/**
 * Owns the reading session lifecycle and turns tracker effects into Room rows.
 *
 * The state machine itself is pure ([ReadingSessionTracker]); this class is the only
 * place that knows about the clock, the database and the frozen settings snapshot.
 *
 * Two invariants matter for correctness:
 *  - at most one session is open at a time, so the tracker state is a single field
 *    guarded by a [Mutex] rather than a map;
 *  - a finalized session is append-only — [finish] only ever writes to a row whose
 *    `endedAt` is still null, so a duplicate call is harmless.
 */
@Singleton
class ReadingSessionRepository @Inject constructor(
    private val dao: ReadingSessionDao,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()

    private var state: TrackingState = TrackingState.Idle
    private var settingsJson: String? = null
    private var snapshot: SettingsSnapshot? = null

    /** The page the open session belongs to, or null when idle. */
    var activePage: Int? = null
        private set

    /**
     * Closes any session left open by a killed process. Must run once at launch, before
     * the reader can open a new session — otherwise the orphan would be attributed to
     * the next reading burst.
     */
    suspend fun recoverOrphans() = mutex.withLock {
        val now = clock.nowMillis()
        dao.openSessions().forEach { orphan ->
            // Trust the persisted progress, not the process: end the session at the last
            // recorded activity and mark it interrupted unless it had already finished.
            dao.finalize(
                clientId = orphan.clientId,
                endedAt = orphan.updatedAt.coerceAtMost(now),
                durationReadSec = orphan.durationReadSec,
                completed = orphan.completed,
                interrupted = !orphan.completed,
                now = now,
            )
        }
        state = TrackingState.Idle
        activePage = null
    }

    /**
     * Opens a session for [page]. Callers must have finished the previous one; if one is
     * still open it is closed as interrupted rather than silently merged.
     *
     * [mode] is `read` or `listen`; a session that starts with the reader pressing play
     * is a listening session from the first second, and one that turns into listening
     * later is upgraded by [markListening].
     */
    suspend fun open(
        start: VerseRef,
        intendedEnd: VerseRef,
        settings: ReadingSettings,
        themeMode: ThemeMode,
        languageCode: String,
        mode: SessionMode = SessionMode.READ,
    ) = mutex.withLock {
        if (state is TrackingState.Active) finishLocked(completed = false)

        val now = clock.nowMillis()
        val clientId = newClientId(now)
        val frozen = SettingsSnapshot(
            fontSizeSp = settings.fontSizeSp,
            showTranslation = settings.showTranslation,
            letterInk = settings.letterInk.key,
            diacriticInk = settings.diacriticInk.key,
            themeMode = themeMode.key,
            languageCode = languageCode,
        )
        snapshot = frozen
        settingsJson = json.encodeToString(frozen)
        state = ReadingSessionTracker.open(clientId, now, start, intendedEnd)
        activePage = start.page

        dao.upsert(
            ReadingSessionEntity(
                clientId = clientId,
                startedAt = now,
                surah = start.surah,
                fromAyah = start.ayah,
                toAyah = start.ayah,
                fromPage = start.page,
                toPage = start.page,
                durationReadSec = 0,
                mode = mode.key,
                settingsJson = settingsJson,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * The reader pressed play: from here on the open session is a listening session.
     *
     * The row is still open, so rewriting its `mode` and snapshot does not break the
     * append-only-after-finalize invariant — but nothing else may change: the ayah range
     * and duration keep accumulating through the normal [progress] path, driven by the
     * audio's own ayah transitions.
     */
    suspend fun markListening(speed: Float, reciterKey: String?) = mutex.withLock {
        val active = (state as? TrackingState.Active)?.session ?: return@withLock
        val current = snapshot ?: return@withLock
        val frozen = current.copy(
            reciterKey = reciterKey,
            playbackSpeed = speed.toDouble(),
        )
        snapshot = frozen
        settingsJson = json.encodeToString(frozen)
        dao.setMode(active.clientId, SessionMode.LISTEN.key, settingsJson!!, clock.nowMillis())
    }

    /** Reports what the reader is looking at now. Drives time and range accumulation. */
    suspend fun progress(from: VerseRef, to: VerseRef) = mutex.withLock {
        val result = ReadingSessionTracker.progress(state, clock.nowMillis(), from, to)
        state = result.state
        applyLocked(result)
    }

    /** Ends the open session. [completed] means the reader moved past the page. */
    suspend fun finish(completed: Boolean) = mutex.withLock {
        finishLocked(completed)
    }

    private suspend fun finishLocked(completed: Boolean) {
        if (state !is TrackingState.Active) {
            state = TrackingState.Idle
            activePage = null
            return
        }
        val result = ReadingSessionTracker.finalize(state, clock.nowMillis(), completed)
        state = TrackingState.Idle
        activePage = null
        applyLocked(result)
    }

    private suspend fun applyLocked(result: TrackingResult) {
        when (result.effect) {
            SessionEffect.NONE -> Unit

            SessionEffect.CHECKPOINT -> {
                val active = (result.state as? TrackingState.Active)?.session ?: return
                val range = active.range
                dao.checkpoint(
                    clientId = active.clientId,
                    surah = range.surah,
                    fromAyah = range.fromAyah,
                    toAyah = range.toAyah,
                    fromPage = range.fromPage,
                    toPage = range.toPage,
                    durationReadSec = active.durationReadSec,
                    completed = active.completed,
                    now = clock.nowMillis(),
                )
            }

            SessionEffect.FINALIZE -> result.finished?.let { finished ->
                writeFinished(finished)
                // The row is durable in Room; asking for a push now is a latency
                // optimisation, and the periodic worker is the safety net.
                syncScheduler.requestImmediate()
            }
        }
    }

    private suspend fun writeFinished(finished: FinishedSession) {
        val now = clock.nowMillis()
        val range: SessionRange = finished.range
        // The row already exists (opened), so this only ever fills in the end state.
        dao.finalize(
            clientId = finished.clientId,
            endedAt = finished.endedAt,
            durationReadSec = finished.durationReadSec,
            completed = finished.completed,
            interrupted = finished.interrupted,
            now = now,
        )
        // Keep the recorded range in sync with what the tracker accumulated.
        dao.checkpoint(
            clientId = finished.clientId,
            surah = range.surah,
            fromAyah = range.fromAyah,
            toAyah = range.toAyah,
            fromPage = range.fromPage,
            toPage = range.toPage,
            durationReadSec = finished.durationReadSec,
            completed = finished.completed,
            now = now,
        )
    }

    /**
     * A client id that is unique without a network round trip — see [newClientId].
     */
    private fun newClientId(now: Long): String = ir.hawraa.quran.data.local.newClientId(now)

}
