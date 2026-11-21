package ir.hawraa.quran

import ir.hawraa.quran.domain.preset.AyahProgress
import ir.hawraa.quran.domain.preset.PresetCoverage
import ir.hawraa.quran.domain.quran.toPersianDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Coverage is a *high-water mark* ledger, and these tests exist because that choice is what
 * makes syncing safe: a retried batch, a batch that arrives out of order, or two devices
 * reporting the same ayah must all converge on the same numbers without any coordination.
 */
class PresetCoverageTest {

    private fun ayah(
        surah: Int,
        ayah: Int,
        global: Int,
        plays: Int = 1,
        listenedSec: Int = 30,
        firstAt: Long = 1_000,
        lastAt: Long = 2_000,
    ) = AyahProgress(
        surah = surah,
        ayah = ayah,
        globalAyah = global,
        page = 1,
        plays = plays,
        listenedSec = listenedSec,
        firstAt = firstAt,
        lastAt = lastAt,
        lastKind = "ayah",
    )

    @Test
    fun merge_keeps_the_high_water_mark_of_every_counter() {
        val stored = ayah(1, 1, 1, plays = 5, listenedSec = 120)
        val incoming = ayah(1, 1, 1, plays = 2, listenedSec = 200)

        val merged = PresetCoverage.merge(stored, incoming)
        assertEquals(5, merged.plays)
        assertEquals(200, merged.listenedSec)
    }

    @Test
    fun merge_keeps_the_earliest_first_heard_timestamp() {
        val stored = ayah(1, 1, 1, firstAt = 5_000)
        val incoming = ayah(1, 1, 1, firstAt = 1_000)

        assertEquals(1_000, PresetCoverage.merge(stored, incoming).firstAt)
    }

    @Test
    fun merge_treats_zero_as_unknown_rather_than_as_the_beginning_of_time() {
        val stored = ayah(1, 1, 1, firstAt = 5_000)
        val incoming = ayah(1, 1, 1, firstAt = 0)

        // A zero stamp from a client that never recorded one must not win the `min`.
        assertEquals(5_000, PresetCoverage.merge(stored, incoming).firstAt)
    }

    @Test
    fun merge_is_idempotent_so_a_retried_batch_changes_nothing() {
        val stored = ayah(1, 1, 1, plays = 5, listenedSec = 120)
        val incoming = ayah(1, 1, 1, plays = 3, listenedSec = 90)

        val once = PresetCoverage.merge(stored, incoming)
        val twice = PresetCoverage.merge(once, incoming)

        assertEquals(once, twice)
    }

    @Test
    fun merging_into_an_empty_ledger_stores_the_record_untouched() {
        val incoming = ayah(2, 255, 262)
        assertSame(incoming, PresetCoverage.merge(null, incoming))
    }

    @Test
    fun merge_all_folds_a_batch_by_verse() {
        val ledger = mapOf("1:1" to ayah(1, 1, 1, plays = 1))
        val merged = PresetCoverage.mergeAll(
            ledger,
            listOf(
                ayah(1, 1, 1, plays = 4),
                ayah(1, 2, 2, plays = 2),
                ayah(1, 2, 2, plays = 1),
            ),
        )

        assertEquals(2, merged.size)
        assertEquals(4, merged.getValue("1:1").plays)
        assertEquals(2, merged.getValue("1:2").plays)
    }

    @Test
    fun ranges_coalesce_consecutive_ayahs_into_one_run() {
        val ranges = PresetCoverage.ranges(
            listOf(ayah(1, 1, 1), ayah(1, 2, 2), ayah(1, 3, 3), ayah(1, 4, 4)),
        )

        assertEquals(1, ranges.size)
        assertEquals(1, ranges.single().fromAyah)
        assertEquals(4, ranges.single().toAyah)
        assertEquals(4, ranges.single().ayahs)
    }

    @Test
    fun ranges_split_where_the_reader_skipped() {
        val ranges = PresetCoverage.ranges(
            listOf(ayah(1, 1, 1), ayah(1, 2, 2), ayah(1, 5, 5), ayah(1, 6, 6)),
        )

        assertEquals(2, ranges.size)
        assertEquals(2, ranges[0].ayahs)
        assertEquals(2, ranges[1].ayahs)
        assertEquals(5, ranges[1].fromAyah)
    }

    @Test
    fun a_run_may_cross_a_surah_boundary_and_still_report_both_ends() {
        // al-Fatiha's last ayah is global 7, so 2:1 is global 8 — one continuous run.
        val ranges = PresetCoverage.ranges(listOf(ayah(1, 7, 7), ayah(2, 1, 8)))

        assertEquals(1, ranges.size)
        assertEquals(1, ranges.single().fromSurah)
        assertEquals(2, ranges.single().toSurah)
        assertEquals(1, ranges.single().toAyah)
    }

    @Test
    fun ranges_are_sorted_rather_than_trusting_the_caller() {
        val ranges = PresetCoverage.ranges(
            listOf(ayah(1, 3, 3), ayah(1, 1, 1), ayah(1, 2, 2)),
        )

        assertEquals(1, ranges.size)
        assertEquals(1, ranges.single().fromAyah)
        assertEquals(3, ranges.single().toAyah)
    }

    @Test
    fun percent_is_over_the_whole_quran() {
        assertEquals(0.1, PresetCoverage.percent(7), 0.05)
        assertEquals(100.0, PresetCoverage.percent(PresetCoverage.TOTAL_AYAHS), 0.001)
        assertEquals(0.0, PresetCoverage.percent(0), 0.001)
    }

    @Test
    fun covered_ayahs_are_counted_per_surah() {
        val counts = PresetCoverage.bySurah(
            listOf(ayah(1, 1, 1), ayah(1, 2, 2), ayah(2, 1, 8)),
        )

        assertEquals(2, counts.getValue(1))
        assertEquals(1, counts.getValue(2))
    }

    @Test
    fun a_range_labels_itself_as_one_verse_or_as_a_span() {
        val single = PresetCoverage.ranges(listOf(ayah(2, 255, 262))).single()
        assertEquals("۲:۲۵۵", single.label { it.toPersianDigits() })

        val span = PresetCoverage.ranges(listOf(ayah(1, 1, 1), ayah(1, 2, 2))).single()
        assertEquals("۱:۱–۱:۲", span.label { it.toPersianDigits() })
    }
}
