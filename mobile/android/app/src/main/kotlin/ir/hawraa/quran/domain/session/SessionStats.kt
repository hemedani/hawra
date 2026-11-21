package ir.hawraa.quran.domain.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A finalized session as the History screen needs it: no Room, no sync state, no
 * settings blob. Mapped from storage at the edge so the statistics stay pure.
 */
data class SessionSummary(
    val clientId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationReadSec: Int,
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val page: Int?,
    val completed: Boolean,
    val interrupted: Boolean,
) {
    /** "2:1–5", or a single ayah when the span is one verse. ASCII digits. */
    val ayahSpan: String
        get() = if (fromAyah == toAyah) "$surah:$fromAyah" else "$surah:$fromAyah\u2013$toAyah"
}

/** One calendar day's worth of reading, newest day first when grouped. */
data class DayBucket(
    val day: LocalDate,
    val sessions: List<SessionSummary>,
    val durationReadSec: Int,
)

/**
 * Statistics over recorded sessions. Pure and deterministic: the "today" that a streak
 * is measured against is always passed in, never read from the system clock.
 */
object SessionStats {

    fun totalSeconds(sessions: List<SessionSummary>): Int =
        sessions.sumOf { it.durationReadSec }

    /** Distinct pages the reader has touched, counting only pages we actually know. */
    fun pagesTouched(sessions: List<SessionSummary>): Int =
        sessions.mapNotNull { it.page }.toSet().size

    /** Sessions the reader finished rather than abandoned. */
    fun completedCount(sessions: List<SessionSummary>): Int =
        sessions.count { it.completed }

    /**
     * Consecutive days of reading ending at [today] or, leniently, at yesterday — a streak
     * stays alive until a whole day passes with no reading at all.
     *
     * Sessions are bucketed in [zone] so a session at 23:50 belongs to the day the reader
     * experienced, not to a UTC boundary.
     */
    fun streakDays(
        sessions: List<SessionSummary>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val days = sessions.map { dayOf(it.startedAt, zone) }.distinct().sortedDescending()
        if (days.isEmpty()) return 0

        val first = days.first()
        // The streak may start today or yesterday, but not earlier than that.
        var cursor = when {
            first == today -> today
            first == today.minusDays(1) -> first
            else -> return 0
        }

        var streak = 0
        for (day in days) {
            // The set is distinct and descending, so the only continuation is the exact
            // previous calendar day; anything else ends the streak.
            if (day != cursor) return streak
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Groups by calendar day in [zone], newest day first, sessions newest first within. */
    fun byDay(
        sessions: List<SessionSummary>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayBucket> = sessions
        .groupBy { dayOf(it.startedAt, zone) }
        .map { (day, daySessions) ->
            val ordered = daySessions.sortedByDescending { it.startedAt }
            DayBucket(
                day = day,
                sessions = ordered,
                durationReadSec = ordered.sumOf { it.durationReadSec },
            )
        }
        .sortedByDescending { it.day }

    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
