package ir.hawraa.quran.domain.preset

/**
 * One **sitting** with a preset: everything the user did with it between pressing play
 * and leaving the reader.
 *
 * This exists because of one product rule, stated plainly: *activity with a preset should
 * be logged as history, but pressing play or pause must not create a new history entry.*
 * So the granular things — a pause, a seek, a repeated step — are **counters and events
 * inside one row**, and a row is opened when the sitting starts and closed when it ends.
 *
 * Pure Kotlin: no Android, no clock, no I/O. Every function takes `at` explicitly so the
 * whole machine is deterministic and testable, exactly like `ReadingSessionTracker`.
 */
data class PresetSitting(
    /** Device-minted id; also the server's idempotency key. */
    val clientId: String,
    /** Which preset produced this sitting. Null if the preset was deleted while open. */
    val presetClientId: String?,
    /** Snapshot of the name, so the history survives a rename or a delete. */
    val presetName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Time actually *playing*, not wall clock. Integer seconds. */
    val activeSec: Int = 0,
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val fromPage: Int? = null,
    val toPage: Int? = null,
    val ayahsCompleted: Int = 0,
    val stepsCompleted: Int = 0,
    val playCount: Int = 0,
    val pauseCount: Int = 0,
    val seekCount: Int = 0,
    val lastEventAt: Long,
    val status: PresetSittingStatus = PresetSittingStatus.ACTIVE,
    val reciterKey: String? = null,
    val speed: Float = 1f,
    /**
     * Was the sitting playing at the last event? Time is billed only while it was, so a
     * user who pauses and walks away for an hour does not come back to a 60-minute session.
     * Part of the value rather than the tracker's memory so the machine stays a pure fold.
     */
    val running: Boolean = false,
    /** Most recent first? No — chronological, capped; see [PresetSittingTracker.MAX_EVENTS]. */
    val events: List<PresetEvent> = emptyList(),
) {
    val isOpen: Boolean get() = status == PresetSittingStatus.ACTIVE

    /** How long the sitting lasted on the wall clock, for the history list. */
    val wallClockSec: Int
        get() = (((endedAt ?: lastEventAt) - startedAt).coerceAtLeast(0L) / 1000L).toInt()

    /**
     * Events are capped, so this is the count of *recorded* events, not of presses. The
     * counters above are exact; only the timeline is trimmed.
     */
    val recordedEvents: Int get() = events.size
}

/** Lifecycle of a sitting. Mirrors `back/models/presetSession.ts`. */
enum class PresetSittingStatus(val wire: String) {
    ACTIVE("active"),
    FINALIZED("finalized"),

    /** The process died mid-sitting; ended at the last event we actually saw. */
    ABANDONED("abandoned"),
    ;

    companion object {
        fun fromWire(value: String?): PresetSittingStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

/** One thing that happened, at millisecond resolution. */
data class PresetEvent(
    val kind: PresetEventKind,
    val at: Long,
    val surah: Int,
    val ayah: Int,
    val stepIndex: Int = 0,
)

/**
 * The kinds the server accepts. Deliberately the same vocabulary, so a stored event can be
 * replayed into this machine without a translation table.
 */
enum class PresetEventKind(val wire: String) {
    PLAY("play"),
    PAUSE("pause"),
    SEEK("seek"),

    /** One preset step finished (a translation read out, a silence elapsed…). */
    STEP("step"),

    /** One ayah finished, recipe and all. */
    AYAH("ayah"),
    ;

    companion object {
        fun fromWire(value: String?): PresetEventKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The state machine that turns button presses into one row.
 *
 * The rule it enforces is the whole point of the design: **`event()` never creates a
 * sitting, it only adds to one.** A play, a pause, a seek and a repeat all land on the
 * same row; only [start] opens one and only [finalize] closes it.
 */
object PresetSittingTracker {

    /** A gap longer than this between two events counts as "walked away". */
    const val IDLE_TIMEOUT_MS = 90_000L

    /**
     * The server caps its stored event array at 200 (`$push` + `$slice: -200`). Capping
     * here too means the wire payload is the same shape the server would keep, so nothing
     * is silently dropped in transit that the client believed it had sent.
     */
    const val MAX_EVENTS = 200

    /**
     * Opens a sitting at [at], positioned at [surah]:[ayah] on [page].
     *
     * The row is opened by the *user pressing play*, not by the screen appearing: a reader
     * who opens the mushaf and scrolls has a reading session, not a preset sitting.
     */
    fun start(
        clientId: String,
        preset: Preset,
        at: Long,
        surah: Int,
        ayah: Int,
        page: Int? = null,
        reciterKey: String? = null,
        speed: Float = 1f,
    ): PresetSitting = PresetSitting(
        clientId = clientId,
        presetClientId = preset.clientId,
        presetName = preset.name,
        startedAt = at,
        fromSurah = surah,
        fromAyah = ayah,
        toSurah = surah,
        toAyah = ayah,
        fromPage = page,
        toPage = page,
        lastEventAt = at,
        status = PresetSittingStatus.ACTIVE,
        reciterKey = reciterKey ?: preset.reciterKey,
        speed = speed,
        running = false,
    )

    /**
     * Records [kind] at [at]. Returns the sitting unchanged when it is already closed —
     * an event arriving after finalize (a late player callback, say) must not reopen or
     * mutate an append-only row.
     */
    fun event(
        sitting: PresetSitting,
        kind: PresetEventKind,
        at: Long,
        surah: Int,
        ayah: Int,
        stepIndex: Int = 0,
    ): PresetSitting {
        if (!sitting.isOpen) return sitting

        val gapMs = (at - sitting.lastEventAt).coerceIn(0L, IDLE_TIMEOUT_MS)
        val billedSec = if (sitting.running) (gapMs / 1000L).toInt() else 0

        val event = PresetEvent(kind = kind, at = at, surah = surah, ayah = ayah, stepIndex = stepIndex)
        val events = (sitting.events + event).let {
            if (it.size > MAX_EVENTS) it.takeLast(MAX_EVENTS) else it
        }

        return sitting.copy(
            activeSec = sitting.activeSec + billedSec,
            lastEventAt = maxOf(at, sitting.lastEventAt),
            playCount = sitting.playCount + if (kind == PresetEventKind.PLAY) 1 else 0,
            pauseCount = sitting.pauseCount + if (kind == PresetEventKind.PAUSE) 1 else 0,
            seekCount = sitting.seekCount + if (kind == PresetEventKind.SEEK) 1 else 0,
            stepsCompleted = sitting.stepsCompleted + if (kind == PresetEventKind.STEP) 1 else 0,
            ayahsCompleted = sitting.ayahsCompleted + if (kind == PresetEventKind.AYAH) 1 else 0,
            // The range only grows. Replaying an earlier ayah never shrinks it, so
            // "how far did I get" cannot go backwards by accident. Pages are set by
            // `withPage`, because an event carries an ayah, not a page.
            toSurah = if (surah > sitting.toSurah) surah else sitting.toSurah,
            toAyah = when {
                surah > sitting.toSurah -> ayah
                surah == sitting.toSurah -> maxOf(ayah, sitting.toAyah)
                else -> sitting.toAyah
            },
            running = when (kind) {
                PresetEventKind.PLAY, PresetEventKind.STEP, PresetEventKind.AYAH -> true
                PresetEventKind.PAUSE -> false
                // A seek while paused leaves the sitting paused; the user is scrubbing.
                PresetEventKind.SEEK -> sitting.running
            },
            events = events,
        )
    }

    /**
     * Records the page the sitting reached. A sitting can straddle a page boundary, so
     * both bounds widen — the same rule the reading tracker uses.
     */
    fun withPage(sitting: PresetSitting, page: Int): PresetSitting = sitting.copy(
        fromPage = sitting.fromPage?.let { minOf(it, page) } ?: page,
        toPage = sitting.toPage?.let { maxOf(it, page) } ?: page,
    )

    /**
     * Closes the sitting at [at].
     *
     * [completed] is the caller's judgement (the run reached its last ayah), never guessed
     * here — the same discipline as the reading tracker. A sitting that never played is
     * still recorded: it explains a row in the history the user can recognise.
     */
    fun finalize(
        sitting: PresetSitting,
        at: Long,
        completed: Boolean,
    ): PresetSitting {
        if (!sitting.isOpen) return sitting
        val gapMs = (at - sitting.lastEventAt).coerceIn(0L, IDLE_TIMEOUT_MS)
        val billedSec = if (sitting.running) (gapMs / 1000L).toInt() else 0
        return sitting.copy(
            endedAt = maxOf(at, sitting.startedAt),
            lastEventAt = maxOf(at, sitting.lastEventAt),
            activeSec = sitting.activeSec + billedSec,
            status = PresetSittingStatus.FINALIZED,
            running = false,
        )
    }

    /**
     * The process died with the sitting open. End it at the last event we actually saw —
     * never at "now", which could be days later — and mark it abandoned so the history is
     * honest about what happened.
     */
    fun recover(sitting: PresetSitting): PresetSitting {
        if (!sitting.isOpen) return sitting
        return sitting.copy(
            endedAt = sitting.lastEventAt,
            status = PresetSittingStatus.ABANDONED,
            running = false,
        )
    }
}
