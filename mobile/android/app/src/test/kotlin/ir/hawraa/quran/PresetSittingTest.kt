package ir.hawraa.quran

import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetEventKind
import ir.hawraa.quran.domain.preset.PresetSittingStatus
import ir.hawraa.quran.domain.preset.PresetSittingTracker
import ir.hawraa.quran.domain.preset.PresetStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The requirement these tests defend, in the owner's own words: *"activity associated with
 * these presets should be logged as history; however, this should not create a new history
 * entry every time the user presses play or pause."*
 *
 * So the assertions are about **one row**: presses become counters, the timeline is capped,
 * and only `start`/`finalize` change the row count. Time is billed only while playing, and a
 * long absence is not billed at all.
 */
class PresetSittingTest {

    private val preset = Preset(
        clientId = "p1",
        name = "آیه، ترجمه، آیه",
        steps = listOf(PresetStep.ayah(), PresetStep.translation("ansarian")),
    )

    private val t0 = 1_763_200_000_000L

    private fun start(at: Long = t0) = PresetSittingTracker.start(
        clientId = "s1",
        preset = preset,
        at = at,
        surah = 1,
        ayah = 1,
        page = 1,
        reciterKey = "parhizgar-murattal-ayah-48",
        speed = 1f,
    )

    @Test
    fun six_presses_are_one_sitting_not_six() {
        var sitting = start()

        // Exactly the sequence the backend smoke test used: play, step, ayah, pause, play, pause.
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 1_000, 1, 1)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.STEP, t0 + 6_000, 1, 1, stepIndex = 0)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.AYAH, t0 + 9_000, 1, 1)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PAUSE, t0 + 12_000, 1, 1)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 20_000, 1, 1)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PAUSE, t0 + 25_000, 1, 1)

        assertEquals(2, sitting.playCount)
        assertEquals(2, sitting.pauseCount)
        assertEquals(1, sitting.stepsCompleted)
        assertEquals(1, sitting.ayahsCompleted)
        assertEquals(6, sitting.recordedEvents)
        assertEquals(PresetSittingStatus.ACTIVE, sitting.status)
    }

    @Test
    fun paused_time_is_not_billed() {
        var sitting = start()

        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 10_000, 1, 1)
        // 10 s elapsed *before* play was pressed: the sitting was not running, so nothing counts.
        assertEquals(0, sitting.activeSec)

        // 20 s of playing.
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PAUSE, t0 + 30_000, 1, 1)
        assertEquals(20, sitting.activeSec)

        // An hour paused, then play again: the hour is not listening.
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 3_630_000, 1, 1)
        assertEquals(20, sitting.activeSec)
    }

    @Test
    fun a_gap_longer_than_the_idle_timeout_is_billed_at_most_once() {
        var sitting = start()
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 1_000, 1, 1)
        // Ten minutes later, with the player still nominally running (a stalled stream, say).
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PAUSE, t0 + 601_000, 1, 1)

        assertEquals((PresetSittingTracker.IDLE_TIMEOUT_MS / 1000).toInt(), sitting.activeSec)
    }

    @Test
    fun the_timeline_is_capped_but_the_counters_stay_exact() {
        var sitting = start()
        val total = PresetSittingTracker.MAX_EVENTS + 25
        repeat(total) { i ->
            sitting = PresetSittingTracker.event(
                sitting = sitting,
                kind = PresetEventKind.PLAY,
                at = t0 + (i + 1) * 1_000L,
                surah = 1,
                ayah = 1,
            )
        }

        assertEquals(PresetSittingTracker.MAX_EVENTS, sitting.recordedEvents)
        // The whole point of keeping counters beside the capped array: history stays true
        // even after the tail is trimmed.
        assertEquals(total, sitting.playCount)
    }

    @Test
    fun the_range_only_grows() {
        var sitting = start()
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.AYAH, t0 + 5_000, 1, 3)
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.AYAH, t0 + 9_000, 1, 2)

        assertEquals(1, sitting.fromSurah)
        assertEquals(1, sitting.toSurah)
        assertEquals(3, sitting.toAyah)

        // Crossing into the next surah resets the ayah bound, as a reading session does.
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.AYAH, t0 + 12_000, 2, 1)
        assertEquals(2, sitting.toSurah)
        assertEquals(1, sitting.toAyah)
    }

    @Test
    fun page_bounds_widen_in_both_directions() {
        var sitting = start()
        sitting = PresetSittingTracker.withPage(sitting, 3)
        sitting = PresetSittingTracker.withPage(sitting, 2)
        sitting = PresetSittingTracker.withPage(sitting, 5)

        assertEquals(1, sitting.fromPage)
        assertEquals(5, sitting.toPage)
    }

    @Test
    fun finalize_closes_the_row_and_later_events_are_ignored() {
        var sitting = start()
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 5_000, 1, 1)
        sitting = PresetSittingTracker.finalize(sitting, at = t0 + 20_000, completed = true)

        assertEquals(PresetSittingStatus.FINALIZED, sitting.status)
        assertFalse(sitting.isOpen)
        assertEquals(t0 + 20_000, sitting.endedAt)

        // A late player callback must not mutate an append-only row.
        val after = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 30_000, 1, 1)
        assertEquals(sitting, after)
    }

    @Test
    fun finalize_bills_the_last_interval() {
        var sitting = start()
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 2_000, 1, 1)
        sitting = PresetSittingTracker.finalize(sitting, at = t0 + 12_000, completed = false)

        // 10 s of playing between the play event and the close.
        assertEquals(10, sitting.activeSec)
    }

    @Test
    fun recovering_a_killed_sitting_ends_it_at_the_last_event_we_saw() {
        var sitting = start()
        sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + 4_000, 1, 2)

        val recovered = PresetSittingTracker.recover(sitting)
        assertEquals(PresetSittingStatus.ABANDONED, recovered.status)
        assertEquals(t0 + 4_000, recovered.endedAt)
    }

    @Test
    fun a_sitting_started_without_a_page_learns_it_from_the_first_ayah() {
        val sitting = PresetSittingTracker.start(
            clientId = "s2",
            preset = preset,
            at = t0,
            surah = 2,
            ayah = 255,
            page = null,
        )

        assertNull(sitting.fromPage)
        assertEquals(255, sitting.toAyah)
        assertEquals(2, sitting.toSurah)
        assertTrue(sitting.reciterKey == null)
    }
}
