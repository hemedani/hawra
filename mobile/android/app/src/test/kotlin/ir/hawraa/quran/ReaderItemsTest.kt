package ir.hawraa.quran

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.quran.BasmalaItem
import ir.hawraa.quran.domain.quran.ReaderItem
import ir.hawraa.quran.domain.quran.ReaderItems
import ir.hawraa.quran.domain.quran.SurahHeaderItem
import ir.hawraa.quran.domain.quran.VerseItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The continuous scroll is built from the same content the sessions and the audio use, so
 * what matters is the mapping: one item per verse, a surah plate only where a surah
 * starts, a basmala only where the mushaf prints one, and every item knowing its page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReaderItemsTest {

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    private val items by lazy { ReaderItems.build(content.pages, content.surahs) }

    @Test
    fun one_item_per_verse_and_none_lost() {
        val verses = content.pages.sumOf { it.verses.size }
        assertEquals(verses, items.filterIsInstance<VerseItem>().size)
        assertEquals(36, verses)
    }

    @Test
    fun a_surah_plate_only_where_a_surah_starts() {
        val headers = items.filterIsInstance<SurahHeaderItem>()

        // Pages 1–5 cover al-Fatiha and the first 29 ayahs of al-Baqarah.
        assertEquals(listOf(1, 2), headers.map { it.surah.number })
        // …and each plate opens its surah: the first verse after it is that surah's ayah 1,
        // possibly behind a standalone basmala line (al-Baqarah has one, al-Fatiha does not).
        headers.forEach { header ->
            val index = items.indexOf(header)
            var next = items[index + 1]
            if (next is BasmalaItem) {
                assertEquals(header.surah.number, next.surah)
                next = items[index + 2]
            }
            assertTrue("plate for surah ${header.surah.number} is not followed by its verse", next is VerseItem)
            assertEquals(header.surah.number, (next as VerseItem).verse.surah)
            assertEquals(1, next.verse.ayah)
        }
    }

    @Test
    fun no_page_boundary_furniture() {
        // The whole point: nothing in the scroll announces a page number. A plate per page
        // would be pagination in disguise, so al-Baqarah — which runs across pages 2–5 —
        // must carry exactly one plate, sitting on the page where the surah actually starts.
        val baqarahPlates = items.filterIsInstance<SurahHeaderItem>().filter { it.surah.number == 2 }
        assertEquals(1, baqarahPlates.size)
        assertEquals(2, baqarahPlates.single().page)

        // Page 3 starts inside al-Baqarah, so its first item must be a plain verse.
        assertTrue(items.first { it.page == 3 } is VerseItem)
    }

    @Test
    fun basmala_only_where_the_mushaf_prints_one() {
        val basmalas = items.filterIsInstance<BasmalaItem>()

        // al-Fatiha's basmala is ayah 1; at-Tawbah has none; al-Baqarah needs the line.
        assertEquals(listOf(2), basmalas.map { it.surah })
        val index = items.indexOf(basmalas.single())
        assertTrue(items[index + 1] is VerseItem)
        assertEquals(1, (items[index + 1] as VerseItem).verse.ayah)
    }

    @Test
    fun pages_are_monotonic_and_match_their_verse() {
        var lastPage = 0
        items.forEach { item ->
            assertTrue("page went backwards at ${item.key}", item.page >= lastPage)
            lastPage = item.page
            if (item is VerseItem) {
                val declared = content.pages.first { it.page == item.page }
                assertTrue(
                    "verse ${item.verse.key} is listed under page ${item.page}",
                    declared.verses.any { it.key == item.verse.key },
                )
            }
        }
    }

    @Test
    fun keys_are_unique_across_the_whole_mushaf() {
        val keys = items.map(ReaderItem::key)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun lookups_used_by_resume_and_follow_along() {
        assertEquals(0, ReaderItems.indexOfPage(items, 1))
        // al-Fatiha is plate + its 7 ayahs = 8 items, so page 2 opens at index 8.
        assertEquals(8, ReaderItems.indexOfPage(items, 2))
        assertTrue(items[8] is SurahHeaderItem)
        assertEquals(-1, ReaderItems.indexOfPage(items, 99))

        val first = ReaderItems.indexOfVerse(items, 2, 1)
        assertTrue(first > 0)
        assertTrue(items[first] is VerseItem)
        assertEquals(-1, ReaderItems.indexOfVerse(items, 2, 286))
    }

    @Test
    fun empty_content_produces_an_empty_scroll() {
        assertTrue(ReaderItems.build(emptyList(), emptyMap()).isEmpty())
        assertEquals(-1, ReaderItems.indexOfPage(emptyList(), 1))
        assertEquals(-1, ReaderItems.indexOfVerse(emptyList(), 1, 1))
    }
}
