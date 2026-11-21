package ir.hawraa.quran

import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetCue
import ir.hawraa.quran.domain.preset.PresetSequenceBuilder
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.quran.VerseTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two recipes from the brief are the acceptance criteria here, verbatim:
 *
 *  - "play a verse, then the Ansarian translation, then the Maleki translation, then the
 *    Al-Mizan tafsir";
 *  - "play the first verse twice, read the Ansarian translation, play the verse once more,
 *    then move to the next verse".
 *
 * Everything else in this file exists because a cue list is the only thing standing between
 * a saved preset and the sound that comes out of the phone.
 */
class PresetSequenceTest {

    private val ansarian = VerseTranslation("ansarian", "ترجمهٔ انصاریان")
    private val maleki = VerseTranslation("maleki", "ترجمهٔ ملکی")

    private fun verse(
        surah: Int,
        ayah: Int,
        page: Int = 1,
        translations: List<VerseTranslation> = listOf(ansarian, maleki),
    ) = VerseItem(
        page = page,
        verse = QuranVerse(
            key = "$surah:$ayah",
            surah = surah,
            ayah = ayah,
            textUthmani = "…",
            translations = translations,
            juz = 1,
            hizb = 1,
            rubElHizb = 1,
            sajdah = null,
        ),
    )

    private fun preset(vararg steps: PresetStep, ayahGapMs: Int = 0, autoAdvance: Boolean = true) =
        Preset(clientId = "p1", name = "آزمون", steps = steps.toList(), ayahGapMs = ayahGapMs, autoAdvance = autoAdvance)

    // ---- the two recipes from the brief ----------------------------------------

    @Test
    fun recipe_one_is_verse_then_ansarian_then_maleki_then_tafsir() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(),
                PresetStep.translation("ansarian"),
                PresetStep.translation("maleki"),
                PresetStep.tafsir("almizan"),
            ),
            verses = listOf(verse(1, 1)),
        )

        // The tafsir is not in the content yet, so it is dropped — but *reported*, because
        // silently reciting three of four steps would look like a bug with no explanation.
        assertEquals(3, sequence.size)
        assertEquals(
            listOf(PresetStepKind.AYAH, PresetStepKind.TRANSLATION, PresetStepKind.TRANSLATION),
            sequence.cues.map { it.kind },
        )
        assertEquals(setOf("almizan"), sequence.missingTextKeys)
        assertEquals(listOf("ansarian", "maleki"), sequence.cues.filterIsInstance<PresetCue.Speak>().map { it.textKey })
    }

    @Test
    fun recipe_two_repeats_the_verse_then_reads_then_recites_again() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(repeat = 2),
                PresetStep.translation("ansarian"),
                PresetStep.ayah(),
            ),
            verses = listOf(verse(1, 1)),
        )

        assertEquals(4, sequence.size)
        assertEquals(
            listOf("ayah", "ayah", "translation", "ayah"),
            sequence.cues.map { it.kind.wire },
        )
        // The repeat index is what lets the UI say "۲ از ۲" and what proves the same ayah
        // really is played twice rather than once with a flag nobody honours.
        assertEquals(listOf(1, 2, 1, 1), sequence.cues.map { it.repeatIndex })
        assertEquals(listOf(2, 2, 1, 1), sequence.cues.map { it.repeatCount })
        // Every cue of the recipe belongs to the same verse.
        assertTrue(sequence.cues.all { it.surah == 1 && it.ayah == 1 })
    }

    @Test
    fun recipe_two_then_moves_to_the_next_verse_and_repeats_the_whole_recipe() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(repeat = 2),
                PresetStep.translation("ansarian"),
                PresetStep.ayah(),
            ),
            verses = listOf(verse(1, 1), verse(1, 2)),
        )

        assertEquals(8, sequence.size)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2), sequence.cues.map { it.ayah })
        assertEquals(listOf(1 to 1, 1 to 2), sequence.ayahs())
    }

    // ---- gaps -------------------------------------------------------------------

    @Test
    fun a_step_gap_rides_on_the_next_cue_not_the_one_it_follows() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(gapMs = 500),
                PresetStep.translation("ansarian", gapMs = 1_000),
            ),
            verses = listOf(verse(1, 1)),
        )

        // First cue starts immediately; the 500 ms after the recitation is spent *before*
        // the translation begins, which is the only place a wait can be honoured.
        assertEquals(listOf(0, 500), sequence.cues.map { it.gapBeforeMs })
    }

    @Test
    fun the_inter_ayah_gap_lands_on_the_first_cue_of_the_next_ayah() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(PresetStep.ayah(), ayahGapMs = 800),
            verses = listOf(verse(1, 1), verse(1, 2)),
        )

        assertEquals(listOf(0, 800), sequence.cues.map { it.gapBeforeMs })
    }

    @Test
    fun a_silence_step_becomes_gap_rather_than_a_cue_of_its_own() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(),
                PresetStep.silence(2_000),
                PresetStep.translation("ansarian"),
            ),
            verses = listOf(verse(1, 1)),
        )

        assertEquals(2, sequence.size)
        assertEquals(listOf(0, 2_000), sequence.cues.map { it.gapBeforeMs })
    }

    @Test
    fun a_trailing_gap_is_not_invented_at_the_end_of_the_run() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(PresetStep.ayah(gapMs = 3_000)),
            verses = listOf(verse(1, 1)),
        )

        assertEquals(1, sequence.size)
        assertEquals(0, sequence.cues.single().gapBeforeMs)
    }

    // ---- speed ------------------------------------------------------------------

    @Test
    fun a_step_speed_overrides_the_readers_speed_and_otherwise_inherits_it() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(speed = 0.75f),
                PresetStep.translation("ansarian"),
            ),
            verses = listOf(verse(1, 1)),
            defaultSpeed = 1.25f,
        )

        assertEquals(listOf(0.75f, 1.25f), sequence.cues.map { it.speed })
    }

    // ---- degenerate presets ------------------------------------------------------

    @Test
    fun a_preset_with_no_playable_step_produces_nothing() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(PresetStep.translation(textKey = "")),
            verses = listOf(verse(1, 1)),
        )

        assertTrue(sequence.isEmpty)
        assertEquals(0, sequence.ayahCount)
        assertEquals(0, sequence.size)
    }

    @Test
    fun a_translation_step_with_no_key_is_not_playable() {
        assertTrue(!PresetStep(PresetStepKind.TRANSLATION, textKey = null).isPlayable)
        assertTrue(!PresetStep(PresetStepKind.TAFSIR, textKey = "  ").isPlayable)
        assertTrue(PresetStep(PresetStepKind.TRANSLATION, textKey = "ansarian").isPlayable)
        assertTrue(PresetStep(PresetStepKind.AYAH).isPlayable)
    }

    @Test
    fun sanitize_clamps_repeat_and_gaps_and_drops_impossible_steps() {
        val sanitized = Preset.sanitized(
            clientId = "p1",
            name = "  ",
            steps = listOf(
                PresetStep(PresetStepKind.AYAH, repeat = 99, gapMs = 99_999),
                PresetStep(PresetStepKind.TRANSLATION, textKey = null),
                PresetStep(PresetStepKind.AYAH, repeat = 0),
            ),
            reciterKey = null,
            autoAdvance = true,
            ayahGapMs = -5,
            isDefault = false,
            isArchived = false,
            revision = 0,
            updatedAtMs = 0,
        )

        assertEquals(1, sanitized.steps.size)
        assertEquals(PresetStep.MAX_REPEAT, sanitized.steps.single().repeat)
        assertEquals(Preset.MAX_GAP_MS, sanitized.steps.single().gapMs)
        assertEquals(0, sanitized.ayahGapMs)
        // A blank name falls back to the id rather than to an empty row in the UI.
        assertEquals("p1", sanitized.name)
        assertEquals(1, sanitized.revision)
    }

    // ---- navigation --------------------------------------------------------------

    @Test
    fun next_ayah_skips_the_rest_of_the_recipe_not_one_cue() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(repeat = 2),
                PresetStep.translation("ansarian"),
                PresetStep.ayah(),
            ),
            verses = listOf(verse(1, 1), verse(1, 2)),
        )

        // From the very first cue, "next ayah" must land on verse 2's first cue (index 4),
        // not on the second repeat of verse 1.
        assertEquals(4, sequence.nextAyahIndex(0))
        assertEquals(4, sequence.nextAyahIndex(2))
        assertNull(sequence.nextAyahIndex(7))
    }

    @Test
    fun previous_ayah_rewinds_to_the_start_of_the_current_ayah_then_the_previous_one() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.ayah(repeat = 2),
                PresetStep.translation("ansarian"),
                PresetStep.ayah(),
            ),
            verses = listOf(verse(1, 1), verse(1, 2)),
        )

        // Mid-recipe: back to the start of *this* ayah.
        assertEquals(0, sequence.previousAyahIndex(2))
        // At the start of verse 2: back to the start of verse 1.
        assertEquals(0, sequence.previousAyahIndex(4))
        // At the very beginning there is nowhere to go.
        assertNull(sequence.previousAyahIndex(0))
    }

    @Test
    fun index_of_ayah_finds_the_first_cue_of_a_verse() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(PresetStep.ayah(repeat = 3)),
            verses = listOf(verse(1, 1), verse(1, 2)),
        )

        assertEquals(0, sequence.indexOfAyah(1, 1))
        assertEquals(3, sequence.indexOfAyah(1, 2))
        assertNull(sequence.indexOfAyah(2, 255))
    }

    // ---- numbering ---------------------------------------------------------------

    @Test
    fun every_recited_cue_carries_the_global_ayah_number() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(PresetStep.ayah()),
            // al-Fatiha has 7 ayahs, so 2:1 is global ayah 8.
            verses = listOf(verse(1, 7), verse(2, 1, page = 2)),
        )

        assertEquals(listOf(7, 8), sequence.cues.filterIsInstance<PresetCue.Recite>().map { it.globalAyah })
        assertEquals(listOf(1, 2), sequence.cues.map { it.page })
    }

    @Test
    fun spoken_cues_carry_the_text_and_the_tafsir_flag() {
        val sequence = PresetSequenceBuilder.build(
            preset = preset(
                PresetStep.translation("ansarian"),
                PresetStep.tafsir("maleki"),
            ),
            verses = listOf(verse(1, 1)),
        )

        val spoken = sequence.cues.filterIsInstance<PresetCue.Speak>()
        assertEquals(2, spoken.size)
        assertEquals(ansarian.text, spoken[0].text)
        assertEquals(false, spoken[0].tafsir)
        assertEquals(true, spoken[1].tafsir)
    }
}
