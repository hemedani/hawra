package ir.hawraa.quran

import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.DownloadSizing
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.LocalAudioEntry
import ir.hawraa.quran.domain.download.LocalAudioIndex
import ir.hawraa.quran.domain.download.LocalJuzFile
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.download.ReciterInfo
import ir.hawraa.quran.domain.download.SizeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download domain, tested without a device, a network or a database.
 *
 * These are the three places a download feature goes wrong quietly: a scope key that does not
 * survive a round trip (so "delete surah 2" deletes nothing), a size estimate that is off by an
 * order of magnitude (so a 300 MB download looks like 30), and an index that serves the wrong
 * file (so the reader hears the wrong recitation).
 */
class DownloadDomainTest {

    // ---- scopes -------------------------------------------------------------

    @Test
    fun scope_keys_are_stable_and_round_trip() {
        val scopes = listOf(
            DownloadScope.Ayah(2, 255),
            DownloadScope.Surah(2),
            DownloadScope.Juz(1),
            DownloadScope.Juz(30),
            DownloadScope.Range(2, 1, 2, 20),
            DownloadScope.Range(2, 250, 3, 5),
        )
        scopes.forEach { scope ->
            assertEquals("round trip of ${scope.scopeKey}", scope, DownloadScope.parse(scope.scopeKey))
        }
    }

    @Test
    fun scope_keys_are_the_documented_strings() {
        assertEquals("ayah-2:255", DownloadScope.Ayah(2, 255).scopeKey)
        assertEquals("surah-2", DownloadScope.Surah(2).scopeKey)
        assertEquals("juz-01", DownloadScope.Juz(1).scopeKey)
        assertEquals("range-2:1-2:20", DownloadScope.Range(2, 1, 2, 20).scopeKey)
    }

    @Test
    fun scope_wire_names_match_the_act_validator() {
        assertEquals("ayah", DownloadScope.Ayah(1, 1).wire)
        assertEquals("surah", DownloadScope.Surah(1).wire)
        assertEquals("juz", DownloadScope.Juz(1).wire)
        assertEquals("range", DownloadScope.Range(1, 1, 1, 7).wire)
    }

    @Test
    fun a_scope_key_from_a_newer_build_is_unknown_rather_than_misread() {
        assertNull(DownloadScope.parse("page-42"))
        assertNull(DownloadScope.parse("surah-"))
        assertNull(DownloadScope.parse("ayah-2"))
        assertNull(DownloadScope.parse("range-2:1"))
        assertNull(DownloadScope.parse("juz-abc"))
    }

    @Test
    fun a_range_has_a_surah_only_when_it_stays_inside_one() {
        assertEquals(2, DownloadScope.Range(2, 1, 2, 20).surah)
        assertNull(DownloadScope.Range(2, 250, 3, 5).surah)
        assertNull(DownloadScope.Juz(1).surah)
    }

    // ---- granularity --------------------------------------------------------

    @Test
    fun only_ayah_and_surah_files_can_serve_the_reader() {
        assertTrue(EditionGranularity.AYAH.servesPerAyahPlayback)
        assertTrue(EditionGranularity.SURAH.servesPerAyahPlayback)
        // A whole-juz MP3 cannot start at verse 17, so it must never be handed to the queue.
        assertFalse(EditionGranularity.JUZ.servesPerAyahPlayback)
    }

    @Test
    fun an_unknown_granularity_falls_back_to_ayah() {
        assertEquals(EditionGranularity.AYAH, EditionGranularity.fromWire(null))
        assertEquals(EditionGranularity.AYAH, EditionGranularity.fromWire("page"))
        assertEquals(EditionGranularity.JUZ, EditionGranularity.fromWire("juz"))
    }

    // ---- catalogue ----------------------------------------------------------

    @Test
    fun only_verified_editions_are_offered_and_the_default_wins() {
        val reciter = ReciterInfo(
            key = "minshawi",
            nameFa = "منشاوی",
            nameEn = "al-Minshawi",
            editions = listOf(
                edition("minshawi-surah", "surah", 128, available = true),
                edition("minshawi-ayah", "ayah", 64, available = true),
                edition("minshawi-dead", "ayah", 192, available = false),
                edition("minshawi-default", "ayah", 32, available = true, isDefault = true),
            ),
        )
        val downloadable = reciter.downloadable
        // Highest bitrate first; the unverified edition is not offered at all.
        assertEquals(listOf("minshawi-surah", "minshawi-ayah", "minshawi-default"), downloadable.map { it.key })
        // …but the server's default is what gets preselected, regardless of bitrate.
        assertEquals("minshawi-default", reciter.preferredEdition()?.key)
    }

    @Test
    fun without_a_default_the_best_bitrate_is_preselected() {
        val reciter = ReciterInfo(
            key = "parhizgar",
            nameFa = "پرهیزگار",
            nameEn = "Parhizgar",
            editions = listOf(
                edition("low", "ayah", 32, available = true),
                edition("high", "ayah", 128, available = true),
            ),
        )
        assertEquals("high", reciter.preferredEdition()?.key)
    }

    @Test
    fun a_label_falls_back_to_the_other_script_not_to_a_key() {
        val withEmptyPersian = edition("k", "ayah", 48, available = true).copy(labelFa = "")
        assertEquals("Verse by verse", withEmptyPersian.displayLabel(persian = true))
        assertEquals("48 kbps · ayah", withEmptyPersian.technicalSummary())
    }

    // ---- sizing -------------------------------------------------------------

    @Test
    fun the_estimate_is_linear_in_bitrate_and_ayah_count() {
        val oneHundred = DownloadSizing.estimateBytes(48, 100)
        val twoHundred = DownloadSizing.estimateBytes(48, 200)
        val doubleBitrate = DownloadSizing.estimateBytes(96, 100)

        assertEquals(oneHundred * 2, twoHundred)
        assertEquals(oneHundred * 2, doubleBitrate)
        // 48 kbps is 6 kB/s; a nominal 7 s ayah is therefore ~42 kB.
        assertEquals(42_000L, oneHundred / 100)
    }

    @Test
    fun a_whole_quran_estimate_is_in_the_right_order_of_magnitude() {
        // 6236 ayahs at 48 kbps ≈ 262 MB. Not exact, but it must not be 26 MB or 2.6 GB.
        val bytes = DownloadSizing.estimateBytes(48, 6236)
        assertTrue("was $bytes", bytes in 240_000_000L..290_000_000L)
    }

    @Test
    fun zero_or_nonsense_inputs_estimate_nothing() {
        assertEquals(0L, DownloadSizing.estimateBytes(0, 100))
        assertEquals(0L, DownloadSizing.estimateBytes(48, 0))
        assertEquals(0L, DownloadSizing.estimateBytes(-5, -5))
    }

    @Test
    fun byte_sizes_pick_a_readable_unit() {
        assertEquals(SizeUnit.KB, DownloadSizing.human(500).unit)
        assertEquals(SizeUnit.MB, DownloadSizing.human(5L * 1024 * 1024).unit)
        assertEquals(SizeUnit.GB, DownloadSizing.human(3L * 1024 * 1024 * 1024).unit)
        assertEquals(5.0, DownloadSizing.human(5L * 1024 * 1024).rounded(), 0.001)
        // Below ten the decimal matters, above it does not.
        assertEquals(1.4, DownloadSizing.human(1_468_006L).rounded(), 0.001)
        assertEquals(48.0, DownloadSizing.human(48L * 1024 * 1024).rounded(), 0.001)
    }

    // ---- local audio index --------------------------------------------------

    private val index = LocalAudioIndex(
        listOf(
            LocalAudioEntry("parhizgar", "murattal", EditionGranularity.AYAH, 2, 3, "/a/002/003.mp3"),
            LocalAudioEntry("parhizgar", "murattal", EditionGranularity.SURAH, 1, null, "/a/001.mp3"),
            LocalAudioEntry("minshawi", "mujawwad", EditionGranularity.AYAH, 2, 3, "/m/002/003.mp3"),
            // A juz file covers surah 2 but must never be returned: it cannot start at an ayah.
            LocalAudioEntry("shakernejad", "tartil", EditionGranularity.JUZ, 2, null, "/s/juz-01.mp3"),
        ),
    )

    @Test
    fun an_exact_verse_file_is_found() {
        assertEquals("/a/002/003.mp3", index.find("parhizgar", "murattal", 2, 3))
    }

    @Test
    fun a_whole_surah_file_serves_every_verse_in_it() {
        assertEquals("/a/001.mp3", index.find("parhizgar", "murattal", 1, 7))
        assertEquals("/a/001.mp3", index.find("parhizgar", "murattal", 1, 1))
    }

    @Test
    fun an_exact_verse_file_beats_a_whole_surah_file() {
        val both = LocalAudioIndex(
            index.entries + LocalAudioEntry("parhizgar", "murattal", EditionGranularity.SURAH, 2, null, "/a/002.mp3"),
        )
        assertEquals("/a/002/003.mp3", both.find("parhizgar", "murattal", 2, 3))
        assertEquals("/a/002.mp3", both.find("parhizgar", "murattal", 2, 4))
    }

    @Test
    fun a_verse_that_is_not_downloaded_is_not_answered_with_a_neighbour() {
        assertNull(index.find("parhizgar", "murattal", 2, 4))
        assertNull(index.find("parhizgar", "murattal", 3, 1))
        assertNull(index.find("unknown", "murattal", 2, 3))
    }

    @Test
    fun a_different_performance_of_the_same_reciter_is_not_substituted() {
        // The user chose murattal; serving the downloaded mujawwad would be a different
        // recitation, which is worse than streaming the right one.
        assertNull(index.find("minshawi", "murattal", 2, 3))
        assertEquals("/m/002/003.mp3", index.find("minshawi", "mujawwad", 2, 3))
    }

    @Test
    fun a_blank_style_on_either_side_matches_anything() {
        val blanked = LocalAudioIndex(
            listOf(LocalAudioEntry("parhizgar", "", EditionGranularity.AYAH, 2, 3, "/a/002/003.mp3")),
        )
        assertEquals("/a/002/003.mp3", blanked.find("parhizgar", "murattal", 2, 3))
        assertEquals("/a/002/003.mp3", blanked.find("parhizgar", "", 2, 3))
    }

    @Test
    fun a_juz_file_is_never_offered_for_a_verse() {
        assertNull(index.find("shakernejad", "tartil", 2, 1))
        assertTrue(index.hasAny("shakernejad"))
    }

    @Test
    fun a_downloaded_juz_is_found_by_juz_and_never_by_verse() {
        val withJuz = LocalAudioIndex(
            entries = index.entries,
            juzFiles = listOf(LocalJuzFile("shakernejad", "tartil", 1, "/s/juz-01.mp3")),
        )
        assertEquals("/s/juz-01.mp3", withJuz.findJuz("shakernejad", "tartil", 1))
        assertNull(withJuz.findJuz("shakernejad", "tartil", 2))
        // The same file must not answer a verse lookup, whatever the juz.
        assertNull(withJuz.find("shakernejad", "tartil", 2, 1))
        assertTrue(withJuz.hasAny("shakernejad"))
    }

    @Test
    fun a_downloaded_juz_is_preferred_over_the_stream() {
        val withJuz = LocalAudioIndex(
            juzFiles = listOf(LocalJuzFile("shakernejad", "tartil", 3, "/s/juz-03.mp3")),
        )
        val remote = "https://mirror.test/juz/03.mp3"
        assertEquals("file:///s/juz-03.mp3", withJuz.urlForJuz("shakernejad", "tartil", 3, remote))
        assertEquals(remote, withJuz.urlForJuz("shakernejad", "tartil", 4, remote))
        // A different performance is not substituted here either.
        assertEquals(remote, withJuz.urlForJuz("shakernejad", "murattal", 3, remote))
    }

    @Test
    fun an_index_holding_only_juz_files_is_not_empty() {
        val onlyJuz = LocalAudioIndex(juzFiles = listOf(LocalJuzFile("r", "tartil", 1, "/x.mp3")))
        assertFalse(onlyJuz.isEmpty)
        assertTrue(LocalAudioIndex.EMPTY.isEmpty)
    }

    @Test
    fun the_url_prefers_the_file_and_falls_back_to_the_stream() {
        val remote = "https://cdn.test/2.mp3"
        assertEquals("file:///a/002/003.mp3", index.urlFor("parhizgar", "murattal", 2, 3, remote))
        assertEquals(remote, index.urlFor("parhizgar", "murattal", 2, 9, remote))
    }

    @Test
    fun an_empty_index_answers_nothing_and_returns_the_stream() {
        val empty = LocalAudioSource { LocalAudioIndex.EMPTY }.current()
        assertTrue(empty.isEmpty)
        assertEquals("https://cdn.test/1.mp3", empty.urlFor("parhizgar", "murattal", 1, 1, "https://cdn.test/1.mp3"))
    }

    private fun edition(
        key: String,
        granularity: String,
        bitrate: Int,
        available: Boolean,
        isDefault: Boolean = false,
    ) = EditionInfo(
        key = key,
        reciterKey = "r",
        labelFa = "برچسب",
        labelEn = "Verse by verse",
        style = "murattal",
        granularity = EditionGranularity.fromWire(granularity),
        bitrateKbps = bitrate,
        totalFiles = 6236,
        availability = if (available) EditionInfo.VERIFIED else "unverified",
        sourceName = "test",
        isDefault = isDefault,
    )
}
