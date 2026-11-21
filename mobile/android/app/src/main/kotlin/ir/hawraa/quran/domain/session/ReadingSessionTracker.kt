package ir.hawraa.quran.domain.session

/**
 * The reading-session state machine — pure Kotlin, no Android, no clock, no I/O.
 *
 * Product rules it encodes (see root `AGENTS.md`):
 *  - a session is opened when the reader shows a page, and finalized when the reader
 *    leaves it; it is append-only after finalization;
 *  - the recorded duration is **time actually spent reading**, not wall clock: a gap
 *    longer than [IDLE_TIMEOUT_MS] between two events means the reader walked away, so
 *    only the first [IDLE_TIMEOUT_MS] of that gap counts;
 *  - the ayah range only grows; re-reading an earlier ayah never shrinks it;
 *  - `completed` means the reader reached the last ayah of the page the session opened
 *    on; `interrupted` is its negation (leaving early, or recovering a session whose
 *    process was killed);
 *  - progress is checkpointed at most every [CHECKPOINT_INTERVAL_MS] **of activity**,
 *    driven by real UI events — never by a timer or polling loop.
 *
 * Every function takes `at` explicitly, so the whole machine is deterministic and
 * unit-testable (see `ReadingSessionTrackerTest`).
 */
object ReadingSessionTracker {

    /** A gap longer than this between two events counts as "not reading". */
    const val IDLE_TIMEOUT_MS = 90_000L

    /** Persist progress at most this often (measured in accumulated activity). */
    const val CHECKPOINT_INTERVAL_MS = 10_000L

    /**
     * A page is only "read" once the reader has actually spent time on it. Without this,
     * swiping through five pages in two seconds would record five completed sessions
     * with a zero duration and poison the history.
     */
    const val MIN_COMPLETION_SEC = 3

    /**
     * Opens a session at [at]. [start] is the first verse the reader saw; [intendedEnd]
     * is the last verse of the page they opened, i.e. what "finishing" means here.
     */
    fun open(
        clientId: String,
        at: Long,
        start: VerseRef,
        intendedEnd: VerseRef,
    ): TrackingState = TrackingState.Active(
        ActiveSession(
            clientId = clientId,
            startedAt = at,
            range = SessionRange.of(start),
            durationReadSec = 0,
            lastActivityAt = at,
            intendedEnd = intendedEnd,
            completed = false,
            lastCheckpointSec = 0,
        ),
    )

    /**
     * The reader is looking at [from]..[to] at [at]. Accumulates reading time, grows the
     * range, and asks for a checkpoint when enough new activity has built up.
     */
    fun progress(
        state: TrackingState,
        at: Long,
        from: VerseRef,
        to: VerseRef,
    ): TrackingResult {
        val session = state.activeOrNull() ?: return TrackingResult(state)
        val durationSec = accumulatedSeconds(session, at)
        val range = session.range
            .merge(from)
            .merge(to)
        val completed = session.completed || reachedIntendedEnd(range, session.intendedEnd)

        val due = durationSec - session.lastCheckpointSec >= CHECKPOINT_INTERVAL_MS / 1000L
        val next = session.copy(
            range = range,
            durationReadSec = durationSec,
            lastActivityAt = at,
            completed = completed,
            lastCheckpointSec = if (due) durationSec else session.lastCheckpointSec,
        )
        return TrackingResult(
            state = TrackingState.Active(next),
            effect = if (due) SessionEffect.CHECKPOINT else SessionEffect.NONE,
        )
    }

    /**
     * The reader left the screen. [completed] lets the caller mark a deliberate finish
     * (moved past the page) versus leaving early; the tracker never guesses. A finish
     * only counts when at least [MIN_COMPLETION_SEC] of reading was accumulated.
     */
    fun finalize(
        state: TrackingState,
        at: Long,
        completed: Boolean,
    ): TrackingResult {
        val session = state.activeOrNull() ?: return TrackingResult(TrackingState.Idle)
        val durationSec = accumulatedSeconds(session, at)
        val isComplete = (completed || session.completed) && durationSec >= MIN_COMPLETION_SEC
        val finished = FinishedSession(
            clientId = session.clientId,
            startedAt = session.startedAt,
            endedAt = at,
            durationReadSec = durationSec,
            range = session.range,
            completed = isComplete,
            interrupted = !isComplete,
        )
        return TrackingResult(TrackingState.Idle, SessionEffect.FINALIZE, finished)
    }

    /**
     * The process died while a session was open. End it at the last moment we actually
     * saw activity (never at "now", which could be days later) and mark it interrupted.
     */
    fun recover(state: TrackingState, at: Long): TrackingResult {
        val session = state.activeOrNull() ?: return TrackingResult(TrackingState.Idle)
        val finished = FinishedSession(
            clientId = session.clientId,
            startedAt = session.startedAt,
            endedAt = session.lastActivityAt,
            durationReadSec = session.durationReadSec,
            range = session.range,
            completed = session.completed,
            interrupted = true,
        )
        return TrackingResult(TrackingState.Idle, SessionEffect.FINALIZE, finished)
    }

    /** True when [range] has reached (or passed) the last verse of the opening page. */
    fun reachedIntendedEnd(range: SessionRange, intendedEnd: VerseRef): Boolean = when {
        range.surah != intendedEnd.surah -> range.surah > intendedEnd.surah
        else -> range.toAyah >= intendedEnd.ayah
    }

    /**
     * Reading time to bill for the event at [at]. The delta since the previous event is
     * clamped to [IDLE_TIMEOUT_MS], so a long absence adds at most the timeout — and the
     * caller's next event resumes from "now", never from the stale stamp.
     */
    private fun accumulatedSeconds(session: ActiveSession, at: Long): Int {
        val deltaMs = (at - session.lastActivityAt).coerceIn(0L, IDLE_TIMEOUT_MS)
        return session.durationReadSec + (deltaMs / 1000L).toInt()
    }

    /**
     * Grows [this] range so it covers [ref]. Monotonic by construction:
     *  - [SessionRange.surah] is the furthest surah reached and never moves backwards;
     *  - [SessionRange.fromAyah]/[SessionRange.toAyah] are the bounds **within** that
     *    surah, so a ref from an earlier surah cannot widen them;
     *  - pages widen towards both extremes, because a session that straddles a page
     *    boundary has genuinely touched both pages.
     */
    private fun SessionRange.merge(ref: VerseRef): SessionRange = when {
        ref.surah > surah -> copy(
            surah = ref.surah,
            fromAyah = ref.ayah,
            toAyah = ref.ayah,
            fromPage = minPage(fromPage, ref.page),
            toPage = maxPage(toPage, ref.page),
        )

        // Already past this surah: keep the ayah bounds, still record the page.
        ref.surah < surah -> copy(fromPage = minPage(fromPage, ref.page))

        else -> copy(
            fromAyah = minOf(fromAyah, ref.ayah),
            toAyah = maxOf(toAyah, ref.ayah),
            fromPage = minPage(fromPage, ref.page),
            toPage = maxPage(toPage, ref.page),
        )
    }

    private fun minPage(a: Int?, b: Int): Int? = if (a == null) b else minOf(a, b)

    private fun maxPage(a: Int?, b: Int): Int? = if (a == null) b else maxOf(a, b)
}

private fun TrackingState.activeOrNull(): ActiveSession? =
    (this as? TrackingState.Active)?.session
