package ir.hawraa.quran

import ir.hawraa.quran.domain.quran.VerseElement
import ir.hawraa.quran.domain.quran.VerseLayer
import ir.hawraa.quran.domain.quran.VerseSelection
import ir.hawraa.quran.domain.quran.playbackPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verse and its translations are separate state on purpose: a single "this block is
 * selected" flag cannot express "show three translations and a commentary, and let the
 * reader highlight any one of them". These tests pin that independence, because it is the
 * kind of thing that quietly regresses back into one boolean.
 */
class VerseSelectionTest {

    private val verse = VerseElement.verse(surah = 1, ayah = 3)
    private val ansarian = VerseElement.translation(1, 3, "ansarian")
    private val another = VerseElement.translation(1, 3, "ghomshei")

    @Test
    fun selecting_the_verse_does_not_select_its_translation() {
        val selection = VerseSelection().toggle(verse)

        assertTrue(selection.isSelected(verse))
        assertFalse(selection.isSelected(ansarian))
        assertFalse(selection.isSelected(1, 3, VerseLayer.TRANSLATION))
        assertTrue(selection.isSelected(1, 3, VerseLayer.VERSE))
    }

    @Test
    fun selecting_a_translation_does_not_select_the_verse() {
        val selection = VerseSelection().toggle(ansarian)

        assertTrue(selection.isSelected(ansarian))
        assertFalse(selection.isSelected(verse))
        assertFalse(selection.isSelected(1, 3, VerseLayer.VERSE))
    }

    @Test
    fun several_translations_can_be_selected_at_once() {
        val selection = VerseSelection().toggle(ansarian).toggle(another)

        assertTrue(selection.isSelected(ansarian))
        assertTrue(selection.isSelected(another))
        assertEquals(2, selection.selected.size)
        // …and the whole-layer query sees both, which is what styling uses.
        assertTrue(selection.isSelected(1, 3, VerseLayer.TRANSLATION))
    }

    @Test
    fun toggling_off_removes_only_that_element() {
        val selection = VerseSelection().toggle(verse).toggle(ansarian).toggle(verse)

        assertFalse(selection.isSelected(verse))
        assertTrue(selection.isSelected(ansarian))
    }

    @Test
    fun active_follows_the_last_tap_and_clears_with_it() {
        val first = VerseSelection().toggle(verse)
        assertEquals(verse, first.active)

        val second = first.toggle(ansarian)
        assertEquals(ansarian, second.active)

        val third = second.toggle(ansarian)
        assertNull("toggling the active element off must not leave a stale focus", third.active)
        // The verse is still selected — focus and selection are different things.
        assertTrue(third.isSelected(verse))
    }

    @Test
    fun focus_is_per_ayah_not_per_element() {
        val selection = VerseSelection().toggle(ansarian)

        // Drives the "peek at translations" rule, which must fire for the tapped
        // translation's own ayah and for no other.
        assertTrue(selection.isFocused(1, 3))
        assertFalse(selection.isFocused(1, 4))
        assertFalse(selection.isFocused(2, 3))
    }

    @Test
    fun an_empty_selection_is_not_focused_anywhere() {
        val selection = VerseSelection()

        assertTrue(selection.isEmpty)
        assertFalse(selection.isFocused(1, 3))
        assertFalse(selection.isSelected(1, 3, VerseLayer.VERSE))
        assertFalse(selection.isSelected(1, 3, VerseLayer.TRANSLATION))
    }

    @Test
    fun clearing_one_ayah_leaves_the_others_alone() {
        val otherVerse = VerseElement.verse(surah = 2, ayah = 1)
        val selection = VerseSelection()
            .toggle(verse)
            .toggle(ansarian)
            .toggle(otherVerse)

        val cleared = selection.clearAyah(1, 3)

        assertFalse(cleared.isSelected(verse))
        assertFalse(cleared.isSelected(ansarian))
        assertTrue(cleared.isSelected(otherVerse))
        assertTrue(cleared.isFocused(2, 1))
    }

    @Test
    fun clear_drops_everything() {
        val selection = VerseSelection().toggle(verse).toggle(ansarian).clear()

        assertTrue(selection.isEmpty)
        assertTrue(selection.selected.isEmpty())
        assertNull(selection.active)
    }

    @Test
    fun commentary_is_its_own_layer() {
        val commentary = VerseElement.commentary(1, 3, "nemuneh")
        val selection = VerseSelection().toggle(commentary)

        assertTrue(selection.isSelected(commentary))
        assertFalse(selection.isSelected(verse))
        assertTrue(selection.isSelected(1, 3, VerseLayer.COMMENTARY))
        assertFalse(selection.isSelected(1, 3, VerseLayer.VERSE))
    }

    // ---- playback plan (D32) --------------------------------------------------------

    @Test
    fun an_empty_selection_has_no_playback_plan() {
        assertNull(VerseSelection().playbackPlan())
    }

    @Test
    fun a_single_verse_is_not_a_selection_run() {
        val plan = VerseSelection().toggle(verse).playbackPlan()!!

        assertEquals(listOf(1 to 3), plan.verses)
        assertTrue(plan.reciteVerses)
        assertFalse("one verse, no spoken layer — keep the 'Play ayah N' label", plan.isSelectionRun)
    }

    @Test
    fun two_verses_order_by_the_mushaf_not_tap_order() {
        val later = VerseElement.verse(surah = 2, ayah = 5)
        val earlier = VerseElement.verse(surah = 1, ayah = 7)
        // Tapped high-then-low; the plan must still read 1:7 then 2:5.
        val plan = VerseSelection().toggle(later).toggle(earlier).playbackPlan()!!

        assertEquals(listOf(1 to 7, 2 to 5), plan.verses)
        assertTrue(plan.isSelectionRun)
    }

    @Test
    fun a_spoken_layer_makes_it_a_selection_run_even_for_one_ayah() {
        val plan = VerseSelection().toggle(ansarian).playbackPlan()!!

        assertEquals(listOf(1 to 3), plan.verses)
        assertFalse("a translation alone does not recite the verse", plan.reciteVerses)
        assertEquals(listOf("ansarian"), plan.translationKeys)
        assertTrue(plan.isSelectionRun)
    }

    @Test
    fun the_plan_collects_each_layer_independently() {
        val tafsir = VerseElement.commentary(1, 3, "nemuneh")
        val plan = VerseSelection()
            .toggle(verse)
            .toggle(ansarian)
            .toggle(another)
            .toggle(tafsir)
            .playbackPlan()!!

        assertTrue(plan.reciteVerses)
        assertEquals(listOf("ansarian", "ghomshei"), plan.translationKeys.sorted())
        assertEquals(listOf("nemuneh"), plan.commentaryKeys)
        assertEquals("one verse, three spoken layers", listOf(1 to 3), plan.verses)
        assertTrue(plan.isSelectionRun)
    }
}
