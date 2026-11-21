package ir.hawraa.quran

import ir.hawraa.quran.domain.session.SessionStats
import ir.hawraa.quran.domain.session.SessionSummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History statistics. All dates are pinned, so "today" never depends on when the test
 * happens to run.
 */
class SessionStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tehran")
    private val today: LocalDate = LocalDate.of(2026, 11, 14)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(day, java.time.LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

    private fun session(
        startedAt: Long,
        durationSec: Int = 60,
        completed: Boolean = true,
        surah: Int = 1,
        from: Int = 1,
        to: Int = 7,
        page: Int? = 1,
    ) = SessionSummary(
        clientId = "c-$startedAt",
        startedAt = startedAt,
        endedAt = startedAt + durationSec * 1000L,
        durationReadSec = durationSec,
        surah = surah,
        fromAyah = from,
        toAyah = to,
        page = page,
        completed = completed,
        interrupted = !completed,
    )

    // --- totals -------------------------------------------------------------

    @Test
    fun `totals add up across sessions`() {
        val sessions = listOf(
            session(at(today, 9), durationSec = 120),
            session(at(today, 20), durationSec = 30, completed = false),
            session(at(today.minusDays(1), 8), durationSec = 60, page = 2),
        )
        assertEquals(210, SessionStats.totalSeconds(sessions))
        assertEquals(2, SessionStats.pagesTouched(sessions))
        assertEquals(2, SessionStats.completedCount(sessions))
    }

    @Test
    fun `empty history has zero totals`() {
        assertEquals(0, SessionStats.totalSeconds(emptyList()))
        assertEquals(0, SessionStats.pagesTouched(emptyList()))
        assertEquals(0, SessionStats.streakDays(emptyList(), today, zone))
    }

    // --- streak -------------------------------------------------------------

    @Test
    fun `a streak counts consecutive days ending today`() {
        val sessions = listOf(
            session(at(today, 9)),
            session(at(today.minusDays(1), 9)),
            session(at(today.minusDays(2), 9)),
        )
        assertEquals(3, SessionStats.streakDays(sessions, today, zone))
    }

    @Test
    fun `several sessions in one day count as one streak day`() {
        val sessions = listOf(
            session(at(today, 8)),
            session(at(today, 12)),
            session(at(today, 22)),
            session(at(today.minusDays(1), 8)),
        )
        assertEquals(2, SessionStats.streakDays(sessions, today, zone))
    }

    @Test
    fun `a streak is still alive if the reader last read yesterday`() {
        val sessions = listOf(
            session(at(today.minusDays(1), 9)),
            session(at(today.minusDays(2), 9)),
        )
        assertEquals(2, SessionStats.streakDays(sessions, today, zone))
    }

    @Test
    fun `a gap of a full day ends the streak`() {
        val sessions = listOf(
            session(at(today.minusDays(2), 9)),
            session(at(today.minusDays(3), 9)),
        )
        assertEquals(0, SessionStats.streakDays(sessions, today, zone))
    }

    @Test
    fun `a gap in the middle stops counting there`() {
        val sessions = listOf(
            session(at(today, 9)),
            session(at(today.minusDays(1), 9)),
            // no reading on day -2
            session(at(today.minusDays(3), 9)),
            session(at(today.minusDays(4), 9)),
        )
        assertEquals(2, SessionStats.streakDays(sessions, today, zone))
    }

    // --- day bucketing ------------------------------------------------------

    @Test
    fun `a session just before midnight belongs to that local day`() {
        val late = session(at(today.minusDays(1), 23, 50))
        assertEquals(today.minusDays(1), SessionStats.dayOf(late.startedAt, zone))
    }

    @Test
    fun `byDay groups, sums and orders newest first`() {
        val sessions = listOf(
            session(at(today, 9), durationSec = 60),
            session(at(today, 21), durationSec = 40),
            session(at(today.minusDays(1), 9), durationSec = 100),
        )
        val days = SessionStats.byDay(sessions, zone)

        assertEquals(2, days.size)
        assertEquals(today, days[0].day)
        assertEquals(100, days[0].durationReadSec)
        assertEquals(2, days[0].sessions.size)
        // Newest session first inside the day.
        assertEquals(at(today, 21), days[0].sessions.first().startedAt)
        assertEquals(today.minusDays(1), days[1].day)
    }

    // --- display ------------------------------------------------------------

    @Test
    fun `ayahSpan collapses a single verse and shows a range otherwise`() {
        assertEquals("2:255", session(0, surah = 2, from = 255, to = 255).ayahSpan)
        assertEquals("2:1\u20135", session(0, surah = 2, from = 1, to = 5).ayahSpan)
    }

    @Test
    fun `page counting ignores sessions with no page`() {
        val sessions = listOf(session(0, page = null), session(1, page = 3))
        assertTrue(SessionStats.pagesTouched(sessions) == 1)
    }
}
