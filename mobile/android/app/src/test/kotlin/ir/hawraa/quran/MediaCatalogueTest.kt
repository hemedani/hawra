package ir.hawraa.quran

import ir.hawraa.quran.data.media.MediaCodec
import ir.hawraa.quran.data.media.MediaEditionDto
import ir.hawraa.quran.data.media.chooseMediaEdition
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.media.MediaEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The media catalogue's two pure decisions: how a wire row becomes a domain edition, and which
 * verified edition a step asking for a content type + text key should use. Both run without a
 * client or a coroutine, so the policy that decides what guya audio plays is pinned here.
 */
class MediaCatalogueTest {

    private fun edition(
        key: String,
        type: MediaContentType = MediaContentType.TRANSLATION,
        textKey: String? = null,
        availability: String = "verified",
        isDefault: Boolean = false,
    ) = MediaEdition(
        key = key,
        contentType = type,
        labelFa = key,
        labelEn = key,
        granularity = EditionGranularity.AYAH,
        urlTemplate = "/uploads/recitation/$key/ayah/{ayah}.mp3",
        fileExtension = "mp3",
        language = "fa",
        textKey = textKey,
        narrator = null,
        availability = availability,
        isDefault = isDefault,
    )

    @Test
    fun a_matching_default_beats_everything() {
        val exactDefault = edition("exact-default", textKey = "fa.ansarian", isDefault = true)
        val other = edition("other", textKey = "fa.maleki")
        val pick = chooseMediaEdition(listOf(other, exactDefault), textKey = "fa.ansarian")
        assertEquals("exact-default", pick?.key)
    }

    @Test
    fun a_matching_non_default_beats_a_default_of_another_text() {
        val matching = edition("matching", textKey = "fa.ansarian")
        val otherDefault = edition("other-default", textKey = "fa.maleki", isDefault = true)
        val pick = chooseMediaEdition(listOf(otherDefault, matching), textKey = "fa.ansarian")
        assertEquals("matching", pick?.key)
    }

    @Test
    fun with_no_text_match_the_content_default_is_used() {
        val default = edition("default", textKey = "fa.maleki", isDefault = true)
        val other = edition("other", textKey = "fa.other")
        val pick = chooseMediaEdition(listOf(other, default), textKey = "fa.ansarian")
        assertEquals("default", pick?.key)
    }

    @Test
    fun with_no_default_any_verified_is_used() {
        val a = edition("a", textKey = "fa.maleki")
        val b = edition("b", textKey = "fa.other")
        val pick = chooseMediaEdition(listOf(a, b), textKey = "fa.ansarian")
        assertEquals("a", pick?.key)
    }

    @Test
    fun an_empty_verified_set_is_null() {
        assertNull(chooseMediaEdition(emptyList(), textKey = "fa.ansarian"))
    }

    @Test
    fun an_unknown_content_type_decodes_to_null() {
        val dto = MediaEditionDto(key = "x", contentType = "smell")
        assertNull(MediaCodec.editionOf(dto))
    }

    @Test
    fun a_known_content_type_and_fields_round_trip() {
        val dto = MediaEditionDto(
            key = "tafsir-guya-qaraati-ayah",
            contentType = "tafsir",
            labelFa = "تفسیرگویا",
            labelEn = "Spoken tafsir",
            granularity = "ayah",
            urlTemplate = "/uploads/recitation/tafsir-guya-qaraati-ayah/ayah/{ayah}.mp3",
            language = "fa",
            textKey = "tafsir-guya",
            narrator = "Qaraati",
            availability = "verified",
            isDefault = true,
        )
        val e = MediaCodec.editionOf(dto)
        assertEquals(MediaContentType.TAFSIR, e?.contentType)
        assertEquals("tafsir-guya", e?.textKey)
        assertEquals(true, e?.isVerified)
    }
}
