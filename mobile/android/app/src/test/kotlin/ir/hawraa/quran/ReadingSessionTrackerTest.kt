package ir.hawraa.quran

import ir.hawraa.quran.domain.session.ReadingSessionTracker
import ir.hawraa.quran.domain.session.SessionEffect
import ir.hawraa.quran.domain.session.TrackingState
import ir.hawraa.quran.domain.session.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading-session contract, pinned. Every case feeds explicit timestamps, so the
 * machine is tested without a clock, a coroutine or a device.
 */
class ReadingSessionTrackerTest {

    private val t0 = 1_700_000_000_000L

    /** `IDLE_TIMEOUT_MS` is a `Long`; durations are `Int` seconds. */
    private val idleTimeoutSec = (ReadingSessionTracker.IDLE_TIMEOUT_MS / 1000).toInt()

    private val pageStart = VerseRef(surah = 2, ayah = 1, page = 2)
    private val pageEnd = VerseRef(surah = 2, ayah = 5, page = 2)

    private fun open(at: Long = t0, start: VerseRef = pageStart, end: VerseRef = pageEnd) =
        ReadingSessionTracker.open("session-1", at, start, end)

    private fun TrackingState.active() = (this as TrackingState.Active).session

    // --- opening ------------------------------------------------------------

    @Test
    fun `open starts an empty active session`() {
        val session = open().active()
        assertEquals("session-1", session.clientId)
        assertEquals(t0, session.startedAt)
        assertEquals(0, session.durationReadSec)
        assertEquals(pageStart.surah, session.range.surah)
        assertEquals(pageStart.ayah, session.range.fromAyah)
        assertEquals(pageStart.ayah, session.range.toAyah)
        assertFalse(session.completed)
    }

    // --- duration accounting ------------------------------------------------

    @Test
    fun `reading time accumulates between events`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 4_000, pageStart, pageStart).state
        val after = ReadingSessionTracker.progress(state, t0 + 9_000, pageStart, pageStart).state
        assertEquals(9, after.active().durationReadSec)
    }

    @Test
    fun `a gap beyond the idle timeout bills only the timeout`() {
        // Reader looks away for 30 minutes, then comes back and reads for 5 s.
        val away = t0 + 30 * 60_000L
        val state = ReadingSessionTracker.progress(open(), away, pageStart, pageStart).state
        assertEquals(idleTimeoutSec, state.active().durationReadSec)

        // The next burst resumes from "now" — the idle minutes are not billed twice.
        val next = ReadingSessionTracker.progress(state, away + 5_000, pageStart, pageStart).state
        assertEquals(idleTimeoutSec + 5, next.active().durationReadSec)
    }

    @Test
    fun `events with a non-monotonic timestamp never rewind the clock`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 8_000, pageStart, pageStart).state
        val rewound = ReadingSessionTracker.progress(state, t0 + 2_000, pageStart, pageStart).state
        assertEquals(8, rewound.active().durationReadSec)
    }

    // --- checkpoint cadence -------------------------------------------------

    @Test
    fun `no checkpoint before ten seconds of activity`() {
        val result = ReadingSessionTracker.progress(open(), t0 + 9_000, pageStart, pageStart)
        assertEquals(SessionEffect.NONE, result.effect)
    }

    @Test
    fun `checkpoint fires at ten seconds and then not again until the next ten`() {
        val first = ReadingSessionTracker.progress(open(), t0 + 10_000, pageStart, pageStart)
        assertEquals(SessionEffect.CHECKPOINT, first.effect)

        // 1 s later: only 11 s of activity, but the last checkpoint was at 10 s.
        val second = ReadingSessionTracker.progress(first.state, t0 + 11_000, pageStart, pageStart)
        assertEquals(SessionEffect.NONE, second.effect)

        val third = ReadingSessionTracker.progress(second.state, t0 + 20_000, pageStart, pageStart)
        assertEquals(SessionEffect.CHECKPOINT, third.effect)
    }

    @Test
    fun `a single long burst checkpoints once and then stays quiet`() {
        // One event covering 45 s of activity must not emit four checkpoints.
        val burst = ReadingSessionTracker.progress(open(), t0 + 45_000, pageStart, pageStart)
        assertEquals(SessionEffect.CHECKPOINT, burst.effect)
        assertEquals(45, burst.state.active().lastCheckpointSec)

        // Checkpoint is anchored at the burst's total, so nothing more is due yet.
        val soon = ReadingSessionTracker.progress(
            burst.state,
            t0 + 50_000,
            pageStart,
            pageStart,
        )
        assertEquals(SessionEffect.NONE, soon.effect)

        val later = ReadingSessionTracker.progress(
            soon.state,
            t0 + 55_000,
            pageStart,
            pageStart,
        )
        assertEquals(SessionEffect.CHECKPOINT, later.effect)
    }

    // --- range growth -------------------------------------------------------

    @Test
    fun `range grows forwards`() {
        val state = ReadingSessionTracker.progress(
            open(),
            t0 + 1_000,
            VerseRef(2, 1, 2),
            VerseRef(2, 3, 2),
        ).state
        assertEquals(1, state.active().range.fromAyah)
        assertEquals(3, state.active().range.toAyah)
    }

    @Test
    fun `re-reading an earlier ayah does not shrink the range`() {
        val forward = ReadingSessionTracker.progress(
            open(),
            t0 + 1_000,
            VerseRef(2, 1, 2),
            VerseRef(2, 4, 2),
        ).state
        val back = ReadingSessionTracker.progress(
            forward,
            t0 + 2_000,
            VerseRef(2, 2, 2),
            VerseRef(2, 2, 2),
        ).state
        assertEquals(4, back.active().range.toAyah)
        assertEquals(1, back.active().range.fromAyah)
    }

    @Test
    fun `a ref from an earlier surah does not rewind the range`() {
        val inBaqarah = ReadingSessionTracker.progress(
            open(),
            t0 + 1_000,
            VerseRef(2, 1, 2),
            VerseRef(2, 3, 2),
        ).state
        val backToFatiha = ReadingSessionTracker.progress(
            inBaqarah,
            t0 + 2_000,
            VerseRef(1, 7, 1),
            VerseRef(1, 7, 1),
        ).state
        val range = backToFatiha.active().range
        assertEquals(2, range.surah)
        assertEquals(3, range.toAyah)
        // The page is still recorded — the reader really did touch page 1.
        assertEquals(1, range.fromPage)
    }

    @Test
    fun `moving into a later surah moves the ayah bounds with it`() {
        val state = ReadingSessionTracker.progress(
            open(),
            t0 + 1_000,
            VerseRef(2, 4, 2),
            VerseRef(3, 2, 3),
        ).state
        val range = state.active().range
        assertEquals(3, range.surah)
        assertEquals(2, range.fromAyah)
        assertEquals(2, range.toAyah)
        assertEquals(3, range.toPage)
    }

    // --- completion ---------------------------------------------------------

    @Test
    fun `reaching the last ayah of the opening page completes the session`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 5_000, pageStart, pageEnd).state
        assertTrue(state.active().completed)
    }

    @Test
    fun `stopping one ayah short does not complete`() {
        val state = ReadingSessionTracker.progress(
            open(),
            t0 + 5_000,
            pageStart,
            VerseRef(2, 4, 2),
        ).state
        assertFalse(state.active().completed)
    }

    @Test
    fun `completion sticks once reached`() {
        val completed = ReadingSessionTracker.progress(open(), t0 + 5_000, pageStart, pageEnd).state
        val back = ReadingSessionTracker.progress(
            completed,
            t0 + 6_000,
            pageStart,
            pageStart,
        ).state
        assertTrue(back.active().completed)
    }

    // --- finalization -------------------------------------------------------

    @Test
    fun `leaving early finalizes as interrupted`() {
        val state = ReadingSessionTracker.progress(
            open(),
            t0 + 5_000,
            pageStart,
            VerseRef(2, 3, 2),
        ).state
        val result = ReadingSessionTracker.finalize(state, t0 + 7_000, completed = false)

        assertEquals(TrackingState.Idle, result.state)
        assertEquals(SessionEffect.FINALIZE, result.effect)
        val finished = assertNotNull(result.finished).let { result.finished!! }
        assertEquals(7, finished.durationReadSec)
        assertEquals(t0 + 7_000, finished.endedAt)
        assertFalse(finished.completed)
        assertTrue(finished.interrupted)
    }

    @Test
    fun `leaving after reaching the end finalizes as completed and not interrupted`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 5_000, pageStart, pageEnd).state
        val finished = ReadingSessionTracker.finalize(state, t0 + 6_000, completed = false)
            .finished!!
        assertTrue(finished.completed)
        assertFalse(finished.interrupted)
    }

    @Test
    fun `the caller can declare completion explicitly`() {
        val finished = ReadingSessionTracker.finalize(open(), t0 + 6_000, completed = true)
            .finished!!
        assertTrue(finished.completed)
        assertFalse(finished.interrupted)
    }

    @Test
    fun `finalize bills at most the idle timeout`() {
        // Reader leaves the app open and walks away for an hour.
        val finished = ReadingSessionTracker.finalize(
            open(),
            t0 + 60 * 60_000L,
            completed = false,
        ).finished!!
        assertEquals(idleTimeoutSec, finished.durationReadSec)
    }

    @Test
    fun `a fast swipe does not count as having read the page`() {
        // Reached the end of the page in under the dwell threshold: not a read page.
        val state = ReadingSessionTracker.progress(open(), t0 + 1_000, pageStart, pageEnd).state
        val finished = ReadingSessionTracker.finalize(state, t0 + 1_500, completed = true)
            .finished!!
        assertFalse(finished.completed)
        assertTrue(finished.interrupted)
    }

    @Test
    fun `dwelling on the page then moving on completes the session`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 5_000, pageStart, pageEnd).state
        val finished = ReadingSessionTracker.finalize(state, t0 + 5_000, completed = true)
            .finished!!
        assertTrue(finished.completed)
        assertFalse(finished.interrupted)
    }

    @Test
    fun `finalizing an idle tracker is a no-op`() {
        val result = ReadingSessionTracker.finalize(TrackingState.Idle, t0, completed = false)
        assertEquals(TrackingState.Idle, result.state)
        assertEquals(SessionEffect.NONE, result.effect)
        assertEquals(null, result.finished)
    }

    @Test
    fun `progress on an idle tracker is a no-op`() {
        val result = ReadingSessionTracker.progress(TrackingState.Idle, t0, pageStart, pageEnd)
        assertEquals(TrackingState.Idle, result.state)
        assertEquals(SessionEffect.NONE, result.effect)
    }

    // --- orphan recovery ----------------------------------------------------

    @Test
    fun `recover ends at the last activity, not at now`() {
        val state = ReadingSessionTracker.progress(open(), t0 + 12_000, pageStart, pageEnd).state
        // Process killed; the app is relaunched two days later.
        val result = ReadingSessionTracker.recover(state, t0 + 2 * 24 * 60 * 60_000L)

        assertEquals(TrackingState.Idle, result.state)
        assertEquals(SessionEffect.FINALIZE, result.effect)
        val finished = result.finished!!
        assertEquals(t0 + 12_000, finished.endedAt)
        assertEquals(12, finished.durationReadSec)
        assertTrue(finished.interrupted)
        // Completion reached before the crash is preserved.
        assertTrue(finished.completed)
    }

    @Test
    fun `recovering an idle tracker is a no-op`() {
        val result = ReadingSessionTracker.recover(TrackingState.Idle, t0)
        assertEquals(SessionEffect.NONE, result.effect)
        assertEquals(null, result.finished)
    }

    // --- intended-end helper ------------------------------------------------

    @Test
    fun `reachedIntendedEnd understands surah and ayah ordering`() {
        val range = open().active().range // surah 2, ayah 1
        assertFalse(
            ReadingSessionTracker.reachedIntendedEnd(range, VerseRef(2, 5, 2)),
        )
        assertTrue(
            ReadingSessionTracker.reachedIntendedEnd(range, VerseRef(2, 1, 2)),
        )
        assertTrue(
            ReadingSessionTracker.reachedIntendedEnd(range, VerseRef(1, 7, 1)),
        )
        assertFalse(
            ReadingSessionTracker.reachedIntendedEnd(range, VerseRef(3, 1, 3)),
        )
    }
}
