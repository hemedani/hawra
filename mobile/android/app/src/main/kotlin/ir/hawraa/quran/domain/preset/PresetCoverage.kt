package ir.hawraa.quran.domain.preset

/**
 * How far one ayah has been covered with one preset.
 *
 * Mirrors `back/models/presetAyahProgress.ts`. Every counter is a **high-water mark**, not
 * an increment: the client sends absolute values and both sides keep the larger. That is
 * what makes the merge idempotent, so a retried or out-of-order sync can never walk a
 * user's coverage backwards, and two devices converge without a lock.
 */
data class AyahProgress(
    val surah: Int,
    val ayah: Int,
    /** 1…6236, Hafs order — the dedupe and ordering key. */
    val globalAyah: Int,
    val page: Int? = null,
    val plays: Int = 0,
    val listenedSec: Int = 0,
    val firstAt: Long = 0L,
    val lastAt: Long = 0L,
    val lastKind: String? = null,
) {
    /** Same verse, whichever device or preset reported it. */
    val key: String get() = "$surah:$ayah"
}

/**
 * Merges coverage records. Pure, and deliberately asymmetric: the interesting values take
 * the **larger**, and `firstAt` takes the **smaller** — a first-heard timestamp must stay
 * the first one, not the most recent report's idea of it.
 */
object PresetCoverage {

    /** The whole Quran in Hafs numbering — the denominator of every percentage. */
    const val TOTAL_AYAHS = 6236

    /** Merge [incoming] into [existing], keeping the high-water marks of both. */
    fun merge(existing: AyahProgress?, incoming: AyahProgress): AyahProgress {
        if (existing == null) return incoming
        return AyahProgress(
            surah = incoming.surah,
            ayah = incoming.ayah,
            globalAyah = incoming.globalAyah,
            page = incoming.page ?: existing.page,
            plays = maxOf(existing.plays, incoming.plays),
            listenedSec = maxOf(existing.listenedSec, incoming.listenedSec),
            // 0 means "unknown" on the wire, so it must not win against a real stamp.
            firstAt = earliest(existing.firstAt, incoming.firstAt),
            lastAt = maxOf(existing.lastAt, incoming.lastAt),
            lastKind = if (incoming.lastAt >= existing.lastAt) incoming.lastKind else existing.lastKind,
        )
    }

    /** Fold a batch into a map keyed by [AyahProgress.key]. */
    fun mergeAll(
        existing: Map<String, AyahProgress>,
        incoming: List<AyahProgress>,
    ): Map<String, AyahProgress> {
        val result = existing.toMutableMap()
        incoming.forEach { entry ->
            result[entry.key] = merge(result[entry.key], entry)
        }
        return result
    }

    /** `0.1` for one ayah of 6236, `100.0` for the whole Quran. */
    fun percent(coveredAyahs: Int, total: Int = TOTAL_AYAHS): Double {
        if (total <= 0) return 0.0
        return (coveredAyahs.coerceAtLeast(0).toDouble() / total * 100.0)
    }

    /**
     * Coalesces covered ayahs into contiguous runs — "what exactly have I heard", as
     * ranges rather than 6236 checkboxes. A run may cross a surah boundary, because a
     * reader who finished al-Fatiha and started al-Baqarah did one continuous thing; the
     * range carries both endpoints so the UI can still name them.
     */
    fun ranges(entries: List<AyahProgress>): List<CoverageRange> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.globalAyah }
        val ranges = ArrayList<CoverageRange>()
        var start = sorted.first()
        var previous = sorted.first()

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current.globalAyah == previous.globalAyah + 1) {
                previous = current
                continue
            }
            ranges += CoverageRange.of(start, previous)
            start = current
            previous = current
        }
        ranges += CoverageRange.of(start, previous)
        return ranges
    }

    /** Covered ayahs per surah, for a "which surahs have I done" list. */
    fun bySurah(entries: List<AyahProgress>): Map<Int, Int> =
        entries.groupingBy { it.surah }.eachCount()

    private fun earliest(a: Long, b: Long): Long = when {
        a <= 0L -> b
        b <= 0L -> a
        else -> minOf(a, b)
    }
}

/** An unbroken run of covered ayahs, inclusive at both ends. */
data class CoverageRange(
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val ayahs: Int,
) {
    /** `۱:۱–۱:۷`, or a single `۲:۲۵۵` when the run is one ayah. */
    fun label(persianDigits: (String) -> String): String {
        val from = "${fromSurah}:${fromAyah}"
        if (fromSurah == toSurah && fromAyah == toAyah) return persianDigits(from)
        return "${persianDigits(from)}–${persianDigits("$toSurah:$toAyah")}"
    }

    companion object {
        fun of(start: AyahProgress, end: AyahProgress): CoverageRange = CoverageRange(
            fromSurah = start.surah,
            fromAyah = start.ayah,
            toSurah = end.surah,
            toAyah = end.ayah,
            ayahs = end.globalAyah - start.globalAyah + 1,
        )
    }
}
