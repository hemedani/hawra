package ir.hawraa.quran.domain.quran

/**
 * The reader as one continuous scroll instead of numbered pages.
 *
 * The mushaf flows: a surah banner where a surah starts, the basmala line where the
 * mushaf prints one, then verse after verse, running straight through page boundaries.
 * Pages still exist — sessions, recitation queues and the resume point are page-based —
 * but they are no longer *shown*, so there is nothing to paginate and nothing to count.
 *
 * Pure and list-shaped so the layout can be unit-tested without Compose, and so the
 * mapping "which page am I looking at" stays a lookup rather than a pager side effect.
 */
sealed interface ReaderItem {
    /** Mushaf page this item belongs to. Carried on every item so scrolling can report it. */
    val page: Int

    /** Stable identity for the lazy list; unique across the whole mushaf. */
    val key: String
}

/** Surah plate. Emitted once, where the surah starts. */
data class SurahHeaderItem(
    override val page: Int,
    val surah: SurahInfo,
    val juz: Int,
) : ReaderItem {
    override val key: String get() = "surah-${surah.number}"
}

/** The standalone basmala line that opens a surah (not an ayah, except in al-Fatiha). */
data class BasmalaItem(
    override val page: Int,
    val surah: Int,
) : ReaderItem {
    override val key: String get() = "basmala-$surah"
}

data class VerseItem(
    override val page: Int,
    val verse: QuranVerse,
) : ReaderItem {
    override val key: String get() = "verse-${verse.key}"
}

object ReaderItems {

    /**
     * Flattens [pages] (already in mushaf order) into the scroll.
     *
     * A surah header is emitted when the surah changes, *not* at every page boundary:
     * with continuous scrolling a page break inside al-Baqarah has nothing to announce,
     * and repeating the plate every page would be exactly the pagination we removed.
     */
    fun build(pages: List<QuranPage>, surahs: Map<Int, SurahInfo>): List<ReaderItem> {
        val items = ArrayList<ReaderItem>(pages.sumOf { it.verses.size } + pages.size)
        var currentSurah = 0
        pages.sortedBy { it.page }.forEach { page ->
            page.verses.forEach { verse ->
                val surah = surahs[verse.surah]
                if (verse.surah != currentSurah) {
                    currentSurah = verse.surah
                    if (surah != null) items += SurahHeaderItem(page.page, surah, page.juz)
                }
                if (verse.showsStandaloneBasmala(surah)) {
                    items += BasmalaItem(page.page, verse.surah)
                }
                items += VerseItem(page.page, verse)
            }
        }
        return items
    }

    /** First item belonging to [page], or -1. Used to restore the resume position. */
    fun indexOfPage(items: List<ReaderItem>, page: Int): Int =
        items.indexOfFirst { it.page == page }

    /** Index of a specific verse in the scroll, or -1. Used by follow-along scrolling. */
    fun indexOfVerse(items: List<ReaderItem>, surah: Int, ayah: Int): Int =
        items.indexOfFirst { it is VerseItem && it.verse.surah == surah && it.verse.ayah == ayah }
}
