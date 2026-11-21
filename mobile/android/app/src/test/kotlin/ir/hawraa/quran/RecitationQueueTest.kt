package ir.hawraa.quran

import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.recitation.AyahNumbering
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationQueue
import ir.hawraa.quran.domain.recitation.RecitationTrack
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.recitation.ReciterGranularity
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playlist is what stands between "press play" and "hear the right verse", so the
 * tests here are about *correspondence*: right count, right order, right global number,
 * right URL — including across a surah boundary, where ayah numbers restart at 1.
 */
class RecitationQueueTest {

    private val reciter = Reciter(
        key = "test",
        nameFa = "قاری",
        nameEn = "Reciter",
        style = "murattal",
        bitrate = 48,
        urlTemplate = "https://cdn.example.test/audio/{ayah}.mp3",
    )

    private fun verse(surah: Int, ayah: Int, page: Int = 1) = QuranVerse(
        key = "$surah:$ayah",
        surah = surah,
        ayah = ayah,
        textUthmani = "",
        juz = 1,
        hizb = 1,
        rubElHizb = 1,
        sajdah = null,
    )

    /** A verse in its page container — the queue's input since D31, because a run crosses pages. */
    private fun item(surah: Int, ayah: Int, page: Int = 1) = VerseItem(page, verse(surah, ayah, page))

    @Test
    fun builds_one_track_per_verse_in_reading_order() {
        val items = listOf(item(1, 1), item(1, 2), item(1, 3))
        val tracks = RecitationQueue.build(items, reciter = reciter)

        assertEquals(3, tracks.size)
        assertEquals(listOf(1, 2, 3), tracks.map { it.ayah })
        assertEquals(listOf(1, 2, 3), tracks.map { it.globalAyah })
        assertEquals(
            listOf(
                "https://cdn.example.test/audio/1.mp3",
                "https://cdn.example.test/audio/2.mp3",
                "https://cdn.example.test/audio/3.mp3",
            ),
            tracks.map { it.url },
        )
        assertEquals("1:3", tracks.last().key)
        assertEquals(1, tracks.last().page)
    }

    /** A page that ends al-Fatiha and starts al-Baqarah: ayah numbers restart, globals must not. */
    @Test
    fun page_straddling_a_surah_boundary_keeps_globals_monotonic() {
        val items = listOf(item(1, 6, page = 1), item(1, 7, page = 1), item(2, 1, page = 2))
        val tracks = RecitationQueue.build(items, reciter = reciter)

        assertEquals(listOf(6, 7, 8), tracks.map { it.globalAyah })
        assertEquals(listOf("1:6", "1:7", "2:1"), tracks.map { it.key })
        // D31: a run spans pages, so each track carries the page *its own* verse sits on.
        assertEquals(listOf(1, 1, 2), tracks.map { it.page })
    }

    @Test
    fun navigation_stops_at_the_ends_of_the_page() {
        val tracks = RecitationQueue.build(
            listOf(item(2, 1), item(2, 2), item(2, 3)),
            reciter = reciter,
        )
        assertEquals(1, RecitationQueue.next(tracks, 0))
        assertEquals(2, RecitationQueue.next(tracks, 1))
        assertNull(RecitationQueue.next(tracks, 2))
        assertEquals(1, RecitationQueue.previous(tracks, 2))
        assertEquals(0, RecitationQueue.previous(tracks, 1))
        assertNull(RecitationQueue.previous(tracks, 0))
        // Defensive: an idle engine reports -1, which must not wrap to the last track.
        assertNull(RecitationQueue.next(tracks, -1))
        assertNull(RecitationQueue.previous(tracks, -1))
    }

    @Test
    fun starts_at_the_tapped_verse_or_the_top_of_the_page() {
        val tracks = RecitationQueue.build(
            listOf(item(2, 1), item(2, 2), item(2, 3)),
            reciter = reciter,
        )
        assertEquals(0, RecitationQueue.startIndex(tracks, null))
        assertEquals(2, RecitationQueue.startIndex(tracks, 2 to 3))
        // A selection from another surah cannot hijack the start position.
        assertEquals(0, RecitationQueue.startIndex(tracks, 1 to 3))
        assertEquals(0, RecitationQueue.startIndex(emptyList(), 2 to 3))
    }

    @Test
    fun index_of_is_negative_when_absent() {
        val tracks = RecitationQueue.build(listOf(item(1, 1)), reciter = reciter)
        assertEquals(0, RecitationQueue.indexOf(tracks, 1, 1))
        assertEquals(-1, RecitationQueue.indexOf(tracks, 1, 2))
    }

    /**
     * The real asset: five pages, 36 verses. Every track must carry a contiguous run of
     * global numbers (so the playlist never skips a file) and a distinct URL.
     */
    @Test
    fun bundled_pages_produce_contiguous_playlists() {
        val pages = Json.parseToJsonElement(
            File("src/main/assets/quran/pages-v1.json").readText(),
        ).jsonObject.getValue("pages").jsonArray

        var checked = 0
        pages.forEach { pageElement ->
            val page = pageElement.jsonObject
            val pageNumber = page.getValue("page").jsonPrimitive.int
            val verses = page.getValue("verses").jsonArray.map { element ->
                val obj = element.jsonObject
                QuranVerse(
                    key = obj.getValue("key").jsonPrimitive.content,
                    surah = obj.getValue("surah").jsonPrimitive.int,
                    ayah = obj.getValue("ayah").jsonPrimitive.int,
                    textUthmani = "",
                    juz = 1,
                    hizb = 1,
                    rubElHizb = 1,
                    sajdah = null,
                )
            }

            val tracks = RecitationQueue.build(verses.map { VerseItem(pageNumber, it) }, reciter)
            assertEquals("page $pageNumber lost verses", verses.size, tracks.size)
            assertEquals("page $pageNumber has duplicate urls", tracks.size, tracks.map { it.url }.toSet().size)
            tracks.zipWithNext { a, b ->
                // Global numbers are contiguous across surah boundaries too, so a gap here
                // would mean the playlist skips an audio file.
                assertEquals(
                    "page $pageNumber jumps from ${a.key} to ${b.key}",
                    a.globalAyah + 1,
                    b.globalAyah,
                )
            }
            tracks.forEach { track ->
                assertNotNull(
                    "unplaceable verse ${track.key}",
                    AyahNumbering.globalAyah(track.surah, track.ayah),
                )
            }
            checked += tracks.size
        }
        assertTrue("expected to check the bundled pages", checked >= 36)
    }

    /**
     * D31: a plain play queues the whole surah, not one page. Al-Fatiha is 7 verses on page 1;
     * al-Baqarah's bundled verses run 2:1–2:29 across pages 2–5. A queue built from every verse
     * of a surah must keep reading order and carry each verse's own page.
     */
    @Test
    fun a_surah_wide_run_spans_pages_in_reading_order() {
        val items = listOf(
            item(2, 1, page = 2), item(2, 2, page = 2),
            item(2, 3, page = 3),
            item(2, 4, page = 4),
            item(2, 5, page = 5),
        )
        val tracks = RecitationQueue.build(items, reciter = reciter)

        assertEquals(listOf(1, 2, 3, 4, 5), tracks.map { it.ayah })
        assertEquals(listOf(2, 2, 3, 4, 5), tracks.map { it.page })
        assertEquals("starting at 2:3 lands on the third track", 2, RecitationQueue.startIndex(tracks, 2 to 3))
    }

    @Test
    fun playback_speeds_cycle_and_label() {        assertEquals(PlaybackSpeed.ONE_AND_QUARTER, PlaybackSpeed.NORMAL.next())
        assertEquals(PlaybackSpeed.HALF, PlaybackSpeed.ONE_AND_HALF.next())
        assertEquals("1×", PlaybackSpeed.NORMAL.label(persian = false))
        assertEquals("1.25×", PlaybackSpeed.ONE_AND_QUARTER.label(persian = false))
        // Persian digits with the Arabic decimal separator (U+066B).
        assertEquals("\u06F1\u066B\u06F2\u06F5\u00D7", PlaybackSpeed.ONE_AND_QUARTER.label(persian = true))
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromKey(null))
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromValue(0.98f))
        assertEquals(PlaybackSpeed.ONE_AND_HALF, PlaybackSpeed.fromValue(1.6f))
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromValue(9f))
    }

    @Test
    fun reciter_resolves_urls_and_names() {
        assertEquals("https://cdn.example.test/audio/6236.mp3", reciter.urlFor(6236))
        assertEquals("قاری", reciter.displayName(persian = true))
        assertEquals("Reciter", reciter.displayName(persian = false))
        assertEquals("Reciter", reciter.copy(nameFa = "").displayName(persian = true))
    }

    // ---- juz-granularity reciters --------------------------------------------------

    private val juzReciter = Reciter(
        key = "shakernejad",
        nameFa = "حامد شاکرنژاد",
        nameEn = "Hamed Shakernejad",
        style = "tartil",
        bitrate = 24,
        urlTemplate = "https://mirror.test/juz/{juz2}.mp3",
        granularity = ReciterGranularity.JUZ,
    )

    @Test
    fun a_juz_reciter_gets_one_track_per_juz_not_per_verse() {
        val items = (1..7).map { item(1, it) }
        val tracks = RecitationQueue.buildJuz(items, reciter = juzReciter)

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals(1, track.juz)
        assertEquals("https://mirror.test/juz/01.mp3", track.url)
        // The track carries the juz's first ayah and says so: nothing claims to be verse 4.
        assertEquals("1:1", track.key)
        assertFalse(track.servesOneVerse)
        assertEquals(1, track.page)
    }

    @Test
    fun a_page_that_straddles_a_juz_boundary_gets_two_tracks() {
        // Juz 2 begins at al-Baqarah 142, so a page carrying 141 and 142 touches both.
        val items = listOf(item(2, 141, page = 22), item(2, 142, page = 22), item(2, 143, page = 22))
        val tracks = RecitationQueue.buildJuz(items, reciter = juzReciter)

        assertEquals(listOf(1, 2), tracks.map { it.juz })
        assertEquals(
            listOf("https://mirror.test/juz/01.mp3", "https://mirror.test/juz/02.mp3"),
            tracks.map { it.url },
        )
    }

    @Test
    fun a_juz_queue_starts_at_the_juz_of_the_tapped_verse() {
        val tracks = RecitationQueue.buildJuz(
            listOf(item(2, 141, page = 22), item(2, 142, page = 22)),
            reciter = juzReciter,
        )
        // Tapping a verse in the second half of the page must start the second file, not the first.
        assertEquals(1, RecitationQueue.startIndex(tracks, 2 to 142))
        assertEquals(0, RecitationQueue.startIndex(tracks, 2 to 141))
        assertEquals(0, RecitationQueue.startIndex(tracks, null))
    }

    @Test
    fun a_juz_url_pads_to_two_digits_and_accepts_the_unpadded_placeholder() {
        assertEquals("https://mirror.test/juz/01.mp3", juzReciter.juzUrl(1))
        assertEquals("https://mirror.test/juz/30.mp3", juzReciter.juzUrl(30))
        val unpadded = juzReciter.copy(urlTemplate = "https://mirror.test/juz/{juz}.mp3")
        assertEquals("https://mirror.test/juz/7.mp3", unpadded.juzUrl(7))
    }

    @Test
    fun only_a_per_ayah_reciter_serves_the_verse_queue() {
        assertTrue(reciter.servesPerAyahPlayback)
        assertFalse(juzReciter.servesPerAyahPlayback)
    }

    @Test
    fun track_keys_match_the_reader_test_tags() {
        val track = RecitationTrack(surah = 2, ayah = 255, globalAyah = 262, page = 42, url = "u")
        assertEquals("2:255", track.key)
    }

    /**
     * The Inoor scheme (Shakernejad's verse-by-verse source) names a file by the **in-surah**
     * ayah, zero-padded — `002005` for Baqarah 5, not the global number. The reader's queue must
     * resolve the `{surah3}{ayah3}` template correctly, or every verse after the first would play
     * the wrong file.
     */
    @Test
    fun an_inoor_style_template_fills_the_in_surah_ayah_placeholder() {
        val inoor = reciter.copy(
            urlTemplate = "https://apiquran.inoor.ir/File/Sound/Recitation/1014/{surah3}{ayah3}.mp3",
        )
        val tracks = RecitationQueue.build(listOf(item(2, 5)), reciter = inoor)
        assertEquals(1, tracks.size)
        assertEquals(
            "https://apiquran.inoor.ir/File/Sound/Recitation/1014/002005.mp3",
            tracks[0].url,
        )
    }
}
