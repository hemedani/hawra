package ir.hawraa.quran.data.repository

import ir.hawraa.quran.data.local.PresetSessionDao
import ir.hawraa.quran.data.local.PresetSessionEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.local.newClientId
import ir.hawraa.quran.data.preset.PresetCodec
import ir.hawraa.quran.data.sync.SyncScheduler
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetEventKind
import ir.hawraa.quran.domain.preset.PresetSitting
import ir.hawraa.quran.domain.preset.PresetSittingStatus
import ir.hawraa.quran.domain.preset.PresetSittingTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the preset sitting lifecycle and turns tracker results into Room rows.
 *
 * The state machine is pure ([PresetSittingTracker]); this class is the only place that knows
 * about the clock and the database. Two invariants matter:
 *
 *  - **at most one sitting is open**, so the state is a single field behind a [Mutex] rather
 *    than a map — a second reader of the same preset would be a different screen, not a
 *    second open row;
 *  - **an event never inserts.** [record] updates the open row's counters and timeline. That
 *    is the product rule, enforced where it cannot be bypassed.
 */
@Singleton
class PresetSittingRepository @Inject constructor(
    private val dao: PresetSessionDao,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) {
    private val mutex = Mutex()
    private var sitting: PresetSitting? = null

    /** The open sitting, if any — what the reader's transport controls act on. */
    val current: PresetSitting? get() = sitting

    /**
     * Closes a sitting left open by a killed process. Runs once at launch, before the reader
     * can open a new one, so an orphan is never attributed to the next listening burst.
     */
    suspend fun recoverOrphans() = mutex.withLock {
        val now = clock.nowMillis()
        dao.openSittings().forEach { orphan ->
            val recovered = PresetSittingTracker.recover(orphan.toDomain())
            dao.finalize(
                clientId = recovered.clientId,
                endedAt = recovered.endedAt ?: orphan.lastEventAt,
                activeSec = recovered.activeSec,
                status = PresetSittingStatus.ABANDONED.wire,
                now = now,
            )
        }
        sitting = null
    }

    /**
     * Opens a sitting for [preset] at [surah]:[ayah]. Called by the **first play**, not by the
     * reader screen appearing: opening the mushaf and scrolling is a reading session, not a
     * preset sitting.
     */
    suspend fun start(
        preset: Preset,
        surah: Int,
        ayah: Int,
        page: Int?,
        reciterKey: String?,
        speed: Float,
    ): PresetSitting = mutex.withLock {
        val now = clock.nowMillis()
        val fresh = PresetSittingTracker.start(
            clientId = newClientId(now),
            preset = preset,
            at = now,
            surah = surah,
            ayah = ayah,
            page = page,
            reciterKey = reciterKey,
            speed = speed,
        )
        sitting = fresh
        dao.upsert(fresh.toEntity(now))
        fresh
    }

    /**
     * Records one transport event on the open row. Does nothing when no sitting is open — a
     * stray player callback before the first play must not create history.
     */
    suspend fun record(
        kind: PresetEventKind,
        surah: Int,
        ayah: Int,
        stepIndex: Int = 0,
        page: Int? = null,
    ) = mutex.withLock {
        val open = sitting?.takeIf { it.isOpen } ?: return@withLock

        val now = clock.nowMillis()
        val next = PresetSittingTracker.event(open, kind, now, surah, ayah, stepIndex)
            .let { if (page != null) PresetSittingTracker.withPage(it, page) else it }
        sitting = next

        dao.updateProgress(
            clientId = next.clientId,
            play = if (kind == PresetEventKind.PLAY) 1 else 0,
            pause = if (kind == PresetEventKind.PAUSE) 1 else 0,
            seek = if (kind == PresetEventKind.SEEK) 1 else 0,
            steps = if (kind == PresetEventKind.STEP) 1 else 0,
            ayahs = if (kind == PresetEventKind.AYAH) 1 else 0,
            activeSec = next.activeSec,
            lastEventAt = next.lastEventAt,
            toSurah = next.toSurah,
            toAyah = next.toAyah,
            page = page ?: next.toPage,
            eventsJson = PresetCodec.encodeEvents(next.events),
            now = now,
        )
    }

    /** Closes the open sitting. [completed] is the caller's judgement, never guessed here. */
    suspend fun finish(completed: Boolean) = mutex.withLock {
        val open = sitting?.takeIf { it.isOpen }
        sitting = null
        if (open == null) return@withLock

        val now = clock.nowMillis()
        val closed = PresetSittingTracker.finalize(open, now, completed)
        dao.finalize(
            clientId = closed.clientId,
            endedAt = closed.endedAt ?: now,
            activeSec = closed.activeSec,
            status = closed.status.wire,
            now = now,
        )
        // A sitting is history now; ask for a push immediately. The periodic worker is the
        // safety net if this one fails.
        syncScheduler.requestImmediate()
    }

    /** Newest sittings first, for the History screen. */
    fun observeHistory(): Flow<List<PresetSitting>> =
        dao.observeHistory().map { rows -> rows.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun recent(limit: Int): List<PresetSitting> = dao.recent(limit).map { it.toDomain() }

    // ---- row mapping ----------------------------------------------------------

    private fun PresetSitting.toEntity(now: Long): PresetSessionEntity = PresetSessionEntity(
        clientId = clientId,
        presetClientId = presetClientId,
        presetName = presetName,
        startedAt = startedAt,
        endedAt = endedAt,
        activeSec = activeSec,
        fromSurah = fromSurah,
        fromAyah = fromAyah,
        toSurah = toSurah,
        toAyah = toAyah,
        fromPage = fromPage,
        toPage = toPage,
        ayahsCompleted = ayahsCompleted,
        stepsCompleted = stepsCompleted,
        playCount = playCount,
        pauseCount = pauseCount,
        seekCount = seekCount,
        lastEventAt = lastEventAt,
        status = status.wire,
        reciterKey = reciterKey,
        speed = speed,
        eventsJson = PresetCodec.encodeEvents(events),
        syncStatus = SyncStatus.PENDING.key,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * `running` is false on read: it describes a live sitting, and a row loaded from disk is
     * by definition not playing. `recover`/`finish` only use it to decide whether the final
     * interval bills, which for a persisted row is always "no".
     */
    private fun PresetSessionEntity.toDomain(): PresetSitting = PresetSitting(
        clientId = clientId,
        presetClientId = presetClientId,
        presetName = presetName,
        startedAt = startedAt,
        endedAt = endedAt,
        activeSec = activeSec,
        fromSurah = fromSurah,
        fromAyah = fromAyah,
        toSurah = toSurah,
        toAyah = toAyah,
        fromPage = fromPage,
        toPage = toPage,
        ayahsCompleted = ayahsCompleted,
        stepsCompleted = stepsCompleted,
        playCount = playCount,
        pauseCount = pauseCount,
        seekCount = seekCount,
        lastEventAt = lastEventAt,
        status = PresetSittingStatus.fromWire(status),
        reciterKey = reciterKey,
        speed = speed,
        running = false,
        events = PresetCodec.eventsOf(eventsJson),
    )
}
