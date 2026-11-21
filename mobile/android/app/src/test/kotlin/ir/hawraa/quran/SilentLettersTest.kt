package ir.hawraa.quran

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.quran.SilentLetters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Silent-letter detection, checked against the real Uthmani asset rather than invented
 * strings, because the rules are claims about how this specific text is written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SilentLettersTest {

    /** The muted substrings, in order, joined — the assertion reads like the mushaf. */
    private fun muted(text: String): String =
        SilentLetters.ranges(text).joinToString("|") { text.substring(it.first, it.last + 1) }

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    private fun verse(surah: Int, ayah: Int): String =
        content.pages.flatMap { it.verses }.first { it.surah == surah && it.ayah == ayah }.textUthmani

    // --- the definite article before a sun letter -------------------------------------

    @Test
    fun the_article_before_a_sun_letter_is_silent() {
        // ٱللَّه, ٱلرَّحْمَٰن, ٱلرَّحِيم: lām assimilated, connective alif unread.
        assertEquals("ٱل|ٱل|ٱل", muted(verse(1, 1)))
    }

    @Test
    fun the_article_before_a_moon_letter_is_pronounced() {
        // ٱلْحَمْدُ and ٱلْعَٰلَمِينَ both keep their lām: ح and ع are moon letters, so
        // "al-ḥamdu" and "al-ʿālamīn" are recited as written.
        assertEquals("", muted(verse(1, 2)))
    }

    @Test
    fun only_the_sun_letter_article_is_muted_in_one_verse() {
        // ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ — ص is a sun letter, م is not.
        assertEquals("ٱل", muted(verse(1, 6)))
    }

    @Test
    fun the_article_behind_a_proclitic_is_still_found() {
        assertEquals("ٱل", muted("وَٱلشَّمْسِ"))
        assertEquals("ٱل", muted("بِٱللَّهِ"))
    }

    @Test
    fun disconnected_letters_are_not_silent() {
        // الٓمٓ is recited alif-lām-mīm: the lām is pronounced, and م is a moon letter.
        assertEquals("", muted(verse(2, 1)))
    }

    @Test
    fun a_moon_letter_word_is_untouched() {
        assertEquals("", muted("ٱلْكِتَٰبُ"))
    }

    // --- the Uthmani silent marker ----------------------------------------------------

    @Test
    fun the_silent_waw_of_ulaika_is_muted() {
        // أُو۟لَٰٓئِكَ twice; ٱلْمُفْلِحُونَ is a moon letter, so it stays.
        assertEquals("و|و", muted(verse(2, 5)))
    }

    @Test
    fun the_silent_alif_of_the_plural_ending_is_muted() {
        // ٱلَّذِينَ (sun letter) then كَفَرُوا۟ — the alif after the wāw is not recited.
        assertEquals("ٱل|ا", muted(verse(2, 6)))
    }

    // --- the lexicon, for orthographies that leave the letter unmarked -----------------

    @Test
    fun classic_silent_letters_are_muted_without_a_marker() {
        assertEquals("و", muted("أُولَٰئِكَ")) // wāw: ulā'ika
        assertEquals("ا", muted("مِائَةٌ")) // alif: mi'ah
        assertEquals("و", muted("عَمْرٌو")) // wāw: Amr
    }

    // --- invariants over the whole bundled text ---------------------------------------

    @Test
    fun ranges_are_ordered_in_bounds_and_cover_only_letters() {
        val verses = content.pages.flatMap { it.verses }
        assertTrue(verses.isNotEmpty())
        var total = 0
        for (verse in verses) {
            val text = verse.textUthmani
            val ranges = SilentLetters.ranges(text)
            var previousEnd = -1
            for (range in ranges) {
                assertTrue("range starts before the previous one", range.first > previousEnd)
                assertTrue("range starts out of bounds", range.first in text.indices)
                assertTrue("range ends out of bounds", range.last < text.length)
                assertTrue("range is inverted", range.first <= range.last)
                for (index in range) {
                    assertFalse(
                        "mark muted in ${verse.surah}:${verse.ayah}",
                        SilentLetters.isMark(text[index]),
                    )
                    assertFalse("whitespace muted", text[index].isWhitespace())
                }
                previousEnd = range.last
                total += range.count()
            }
        }
        // The first five pages really do contain silent letters; a silent regression to
        // "nothing is muted" must fail here rather than pass silently.
        assertTrue("expected silent letters across pages 1-5, found $total", total >= 40)
    }

    @Test
    fun empty_and_plain_text_produce_nothing() {
        assertEquals(emptyList<IntRange>(), SilentLetters.ranges(""))
        assertEquals(emptyList<IntRange>(), SilentLetters.ranges("بِسْمِ"))
    }
}
