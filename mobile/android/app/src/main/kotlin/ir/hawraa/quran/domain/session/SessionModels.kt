package ir.hawraa.quran.domain.session

/**
 * Where the reader is: surah (1-based), ayah (1-based) and the mushaf page.
 * Small value object so the tracker stays free of Android and Room types.
 */
data class VerseRef(
    val surah: Int,
    val ayah: Int,
    val page: Int,
)

/** What the session was: reading the page, or listening to its recitation. */
enum class SessionMode(val key: String) {
    READ("read"),
    LISTEN("listen"),
}

/** The ayah/page span a session has covered so far. Expands monotonically. */
data class SessionRange(
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val fromPage: Int?,
    val toPage: Int?,
) {
    companion object {
        fun of(ref: VerseRef) = SessionRange(
            surah = ref.surah,
            fromAyah = ref.ayah,
            toAyah = ref.ayah,
            fromPage = ref.page,
            toPage = ref.page,
        )
    }
}

/** A session that is still open. [durationReadSec] is accumulated, not elapsed. */
data class ActiveSession(
    val clientId: String,
    val startedAt: Long,
    val range: SessionRange,
    val durationReadSec: Int,
    val lastActivityAt: Long,
    /** Last verse of the page the session opened on — reaching it means "finished". */
    val intendedEnd: VerseRef,
    val completed: Boolean,
    /**
     * [durationReadSec] at the last checkpoint. Keeps checkpointing to at most once per
     * [ReadingSessionTracker.CHECKPOINT_INTERVAL_MS] of activity instead of re-firing on
     * every subsequent event.
     */
    val lastCheckpointSec: Int = 0,
)

/** A finalized session, ready to be written to Room (and later synced). */
data class FinishedSession(
    val clientId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationReadSec: Int,
    val range: SessionRange,
    val completed: Boolean,
    val interrupted: Boolean,
)

sealed interface TrackingState {
    data object Idle : TrackingState
    data class Active(val session: ActiveSession) : TrackingState
}

/** What the caller should persist after feeding an event to the tracker. */
enum class SessionEffect {
    NONE,

    /** Write the open session's progress (every ~10 s of activity). */
    CHECKPOINT,

    /** The session is over: finalize the row. */
    FINALIZE,
}

data class TrackingResult(
    val state: TrackingState,
    val effect: SessionEffect = SessionEffect.NONE,
    val finished: FinishedSession? = null,
)
