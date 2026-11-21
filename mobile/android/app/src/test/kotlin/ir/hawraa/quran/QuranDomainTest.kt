package ir.hawraa.quran

import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.SurahInfo
import ir.hawraa.quran.domain.quran.showsStandaloneBasmala
import ir.hawraa.quran.domain.quran.toPersianDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure Quran domain logic: number formatting and the basmala rule. */
class QuranDomainTest {

    private fun verse(surah: Int, ayah: Int) = QuranVerse(
        key = "$surah:$ayah",
        surah = surah,
        ayah = ayah,
        textUthmani = "",
        juz = 1,
        hizb = 1,
        rubElHizb = 1,
        sajdah = null,
    )

    private fun surah(number: Int, bismillahPre: Boolean) = SurahInfo(
        number = number,
        nameArabic = "",
        nameFa = "",
        nameEn = "",
        ayahCount = 7,
        revelationPlace = "makkah",
        bismillahPre = bismillahPre,
    )

    @Test
    fun `persian digits`() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", (1234567890).toPersianDigits())
        assertEquals("۷", 7.toPersianDigits())
        assertEquals("۶۰۴", 604.toPersianDigits())
    }

    @Test
    fun `basmala is shown before the first ayah of a surah that has one`() {
        assertTrue(verse(2, 1).showsStandaloneBasmala(surah(2, bismillahPre = true)))
    }

    @Test
    fun `basmala is not repeated inside the surah`() {
        assertFalse(verse(2, 2).showsStandaloneBasmala(surah(2, bismillahPre = true)))
    }

    @Test
    fun `al-fatiha basmala is ayah 1 so no extra line`() {
        // The API reports bismillahPre = false for Al-Fatiha; even if it did not,
        // the basmala there is a numbered ayah and must not be drawn twice.
        assertFalse(verse(1, 1).showsStandaloneBasmala(surah(1, bismillahPre = true)))
        assertFalse(verse(1, 1).showsStandaloneBasmala(surah(1, bismillahPre = false)))
    }

    @Test
    fun `at-tawbah has no basmala`() {
        assertFalse(verse(9, 1).showsStandaloneBasmala(surah(9, bismillahPre = true)))
    }

    @Test
    fun `no surah means no basmala`() {
        assertFalse(verse(2, 1).showsStandaloneBasmala(null))
    }
}
