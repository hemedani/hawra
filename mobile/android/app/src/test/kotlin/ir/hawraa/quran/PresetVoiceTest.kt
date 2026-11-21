package ir.hawraa.quran

import ir.hawraa.quran.data.quran.RecitationCatalog
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.recitation.ReciterGranularity
import ir.hawraa.quran.ui.screens.reader.presetVoice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The voice a preset run picks. The rule under test is the owner's: a preset recites **verse by
 * verse**, so it must land on a per-ayah edition, and the reciter it pins wins — a pinned juz-only
 * voice (Shakernejad) may never be substituted into a verse-level queue.
 */
class PresetVoiceTest {

    private fun ayahReciter(key: String) = Reciter(
        key = key,
        nameFa = key,
        nameEn = key,
        style = "murattal",
        bitrate = 48,
        urlTemplate = "https://cdn.test/$key/{ayah}.mp3",
        granularity = ReciterGranularity.AYAH,
    )

    private fun juzReciter(key: String) = ayahReciter(key).copy(
        style = "tartil",
        urlTemplate = "https://cdn.test/$key/{juz2}.mp3",
        granularity = ReciterGranularity.JUZ,
    )

    private val catalog = RecitationCatalog(
        reciters = listOf(juzReciter("shakernejad"), ayahReciter("parhizgar"), ayahReciter("alafasy")),
        defaultReciterKey = "shakernejad", // the default is juz-only on purpose — the resolver must skip it
    )

    @Test
    fun the_pinned_per_ayah_reciter_wins() {
        val voice = presetVoice("alafasy", catalog, selected = ayahReciter("parhizgar"))
        assertEquals("alafasy", voice?.key)
    }

    @Test
    fun a_pinned_juz_reciter_falls_back_to_the_per_ayah_selection() {
        val voice = presetVoice("shakernejad", catalog, selected = ayahReciter("parhizgar"))
        assertEquals("parhizgar", voice?.key)
    }

    @Test
    fun with_no_pin_the_per_ayah_selection_is_used() {
        val voice = presetVoice(null, catalog, selected = ayahReciter("alafasy"))
        assertEquals("alafasy", voice?.key)
    }

    @Test
    fun when_both_pin_and_selection_are_juz_only_the_per_ayah_default_is_picked() {
        // Default is shakernejad (juz), so the resolver must reach past it to the first per-ayah reciter.
        val voice = presetVoice("shakernejad", catalog, selected = juzReciter("shakernejad"))
        assertEquals("parhizgar", voice?.key)
    }

    @Test
    fun with_no_catalogue_the_selection_is_returned_as_is() {
        val voice = presetVoice("shakernejad", catalog = null, selected = juzReciter("shakernejad"))
        assertEquals("shakernejad", voice?.key)
    }
}
