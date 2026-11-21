package ir.hawraa.quran

import ir.hawraa.quran.domain.recitation.AyahNumbering
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The global ayah number is the *file name* of every per-ayah audio file, so an error
 * here is silent and awful: the reader would hear a neighbouring verse with nothing on
 * screen to suggest anything is wrong. These tests pin the table against the mushaf
 * total and against the bundled asset's own surah metadata.
 */
class AyahNumberingTest {

    @Test
    fun table_covers_the_whole_mushaf() {
        assertEquals(AyahNumbering.TOTAL_AYAHS, AyahNumbering.total)
        assertEquals(114, AyahNumbering.SURAH_COUNT)
    }

    @Test
    fun offsets_at_surah_boundaries() {
        assertEquals(1, AyahNumbering.globalAyah(1, 1))
        assertEquals(7, AyahNumbering.globalAyah(1, 7))
        // al-Fatiha has 7 ayahs, so al-Baqarah starts at 8.
        assertEquals(8, AyahNumbering.globalAyah(2, 1))
        assertEquals(293, AyahNumbering.globalAyah(2, 286))
        // 7 + 286 = 293.
        assertEquals(294, AyahNumbering.globalAyah(3, 1))
        assertEquals(6231, AyahNumbering.globalAyah(114, 1))
        assertEquals(6236, AyahNumbering.globalAyah(114, 6))
    }

    @Test
    fun inverse_is_exact_for_every_ayah() {
        for (global in 1..AyahNumbering.TOTAL_AYAHS) {
            val (surah, ayah) = requireNotNull(AyahNumbering.surahAyah(global)) {
                "no surah:ayah for global ayah $global"
            }
            assertEquals("round trip failed at $global", global, AyahNumbering.globalAyah(surah, ayah))
        }
    }

    @Test
    fun out_of_range_pairs_have_no_number() {
        assertNull(AyahNumbering.globalAyah(0, 1))
        assertNull(AyahNumbering.globalAyah(115, 1))
        assertNull(AyahNumbering.globalAyah(1, 0))
        assertNull(AyahNumbering.globalAyah(1, 8)) // al-Fatiha has 7
        assertNull(AyahNumbering.globalAyah(114, 7))
        assertNull(AyahNumbering.surahAyah(0))
        assertNull(AyahNumbering.surahAyah(6237))
        assertEquals(0, AyahNumbering.ayahCount(115))
    }

    /**
     * The asset is the authority on how many ayahs each surah has (it came from the
     * source text). Any disagreement means audio would drift, so it fails here.
     */
    @Test
    fun bundled_asset_surah_lengths_agree() {
        val surahs = asset().jsonObject.getValue("surahs").jsonArray
        assertTrue("asset has no surahs", surahs.isNotEmpty())
        surahs.forEach { element ->
            val surah = element.jsonObject
            val number = surah.getValue("number").jsonPrimitive.int
            val declared = surah.getValue("ayahCount").jsonPrimitive.int
            assertEquals(
                "ayah count for surah $number disagrees with the audio table",
                declared,
                AyahNumbering.ayahCount(number),
            )
        }
    }

    /** Every verse in the bundled pages must resolve — no gaps in the playlist. */
    @Test
    fun every_bundled_verse_has_a_global_number() {
        val pages = asset().jsonObject.getValue("pages").jsonArray
        var count = 0
        pages.forEach { page ->
            page.jsonObject.getValue("verses").jsonArray.forEach { verse ->
                val obj = verse.jsonObject
                val surah = obj.getValue("surah").jsonPrimitive.int
                val ayah = obj.getValue("ayah").jsonPrimitive.int
                assertNotNull(
                    "no global number for $surah:$ayah",
                    AyahNumbering.globalAyah(surah, ayah),
                )
                count++
            }
        }
        assertTrue("expected the bundled pages to carry verses", count >= 36)
    }

    // ---- juz ---------------------------------------------------------------------

    @Test
    fun juz_table_holds_thirty_juz_and_starts_where_the_mushaf_does() {
        assertEquals(30, AyahNumbering.JUZ_COUNT)
        assertEquals(1 to 1, AyahNumbering.juzStart(1))
        assertEquals(2 to 142, AyahNumbering.juzStart(2))
        assertEquals(78 to 1, AyahNumbering.juzStart(30))
        assertNull(AyahNumbering.juzStart(0))
        assertNull(AyahNumbering.juzStart(31))
    }

    @Test
    fun juz_boundaries_land_on_the_documented_ayahs() {
        assertEquals(1, AyahNumbering.juzOf(1, 1))
        // Juz 2 begins at al-Baqarah 142, so 141 is the last ayah of juz 1.
        assertEquals(1, AyahNumbering.juzOf(2, 141))
        assertEquals(2, AyahNumbering.juzOf(2, 142))
        assertEquals(29, AyahNumbering.juzOf(67, 1))
        assertEquals(30, AyahNumbering.juzOf(78, 1))
        assertEquals(30, AyahNumbering.juzOf(114, 6))
        assertNull(AyahNumbering.juzOf(115, 1))
        assertNull(AyahNumbering.juzOf(1, 8))
    }

    /**
     * Every ayah belongs to exactly one juz, and the thirty of them add up to the mushaf.
     *
     * This is the check the server performs at boot, repeated here because the app's copy of the
     * table is what decides which whole-juz file the reader hears.
     */
    @Test
    fun every_ayah_belongs_to_exactly_one_juz() {
        val perJuz = IntArray(AyahNumbering.JUZ_COUNT + 1)
        for (global in 1..AyahNumbering.TOTAL_AYAHS) {
            val (surah, ayah) = requireNotNull(AyahNumbering.surahAyah(global))
            val juz = requireNotNull(AyahNumbering.juzOf(surah, ayah)) { "no juz for $surah:$ayah" }
            perJuz[juz]++
        }
        assertEquals(
            "the juz table does not cover the mushaf",
            AyahNumbering.TOTAL_AYAHS,
            perJuz.drop(1).sum(),
        )
        assertTrue("a juz came out empty: ${perJuz.toList()}", perJuz.drop(1).all { it > 0 })
    }

    /**
     * The five bundled pages are all inside juz 1.
     *
     * Not a coincidence to rely on quietly: it is exactly why mirroring *one* juz file gives a
     * juz-granularity reciter the whole of the first five pages.
     */
    @Test
    fun the_first_five_pages_are_all_in_the_first_juz() {
        val pages = asset().jsonObject.getValue("pages").jsonArray
        var verses = 0
        pages.forEach { page ->
            page.jsonObject.getValue("verses").jsonArray.forEach { verse ->
                val obj = verse.jsonObject
                val surah = obj.getValue("surah").jsonPrimitive.int
                val ayah = obj.getValue("ayah").jsonPrimitive.int
                assertEquals("$surah:$ayah is outside juz 1", 1, AyahNumbering.juzOf(surah, ayah))
                verses++
            }
        }
        assertEquals(36, verses)
    }

    private fun asset() = Json.parseToJsonElement(
        File("src/main/assets/quran/pages-v1.json").readText(),
    )
}
