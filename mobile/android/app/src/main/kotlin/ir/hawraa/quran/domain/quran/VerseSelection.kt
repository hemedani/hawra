package ir.hawraa.quran.domain.quran

/**
 * The independently stateful parts of a verse block.
 *
 * They are separate because the product needs them separate: three translations and one
 * commentary shown at once, each with its own highlight, and the verse itself highlighted
 * on its own. A single `selected: Boolean` on the whole block cannot express that, and
 * every later feature (compare translations, open one commentary, highlight only the
 * Arabic) would have to fight it.
 */
enum class VerseLayer { VERSE, TRANSLATION, COMMENTARY }

/**
 * One addressable element: this layer of this ayah. [key] distinguishes several
 * translations of the same verse (and, later, several commentaries).
 */
data class VerseElement(
    val surah: Int,
    val ayah: Int,
    val layer: VerseLayer,
    val key: String = "",
) {
    companion object {
        fun verse(surah: Int, ayah: Int) = VerseElement(surah, ayah, VerseLayer.VERSE)

        fun translation(surah: Int, ayah: Int, translationKey: String) =
            VerseElement(surah, ayah, VerseLayer.TRANSLATION, translationKey)

        fun commentary(surah: Int, ayah: Int, commentaryKey: String = "") =
            VerseElement(surah, ayah, VerseLayer.COMMENTARY, commentaryKey)
    }
}

/**
 * What the reader has highlighted, and what they touched last.
 *
 * - [selected] is a **set**: several elements can be highlighted at once (three
 *   translations, or a verse plus its commentary). Each toggles on its own.
 * - [active] is the **single** element the last tap landed on. It is what "start playing
 *   from here" and "show me this verse's translation" key off, and it is deliberately not
 *   derived from [selected]: tapping a translation must not make the verse active, and
 *   clearing a highlight must not leave a stale focus behind.
 *
 * Pure Kotlin: the reader passes taps in and asks questions; nothing here knows about
 * Compose, pages or playback.
 */
data class VerseSelection(
    val selected: Set<VerseElement> = emptySet(),
    val active: VerseElement? = null,
) {
    val isEmpty: Boolean get() = selected.isEmpty() && active == null

    fun isSelected(element: VerseElement): Boolean = element in selected

    /** True when *any* key of this layer is selected — used for whole-layer styling. */
    fun isSelected(surah: Int, ayah: Int, layer: VerseLayer): Boolean =
        selected.any { it.surah == surah && it.ayah == ayah && it.layer == layer }

    /** True when this exact translation/commentary is selected. */
    fun isSelected(surah: Int, ayah: Int, layer: VerseLayer, key: String): Boolean =
        VerseElement(surah, ayah, layer, key) in selected

    /**
     * The reader tapped this verse's Arabic text, so its translations may be peeked at
     * even with translations switched off. This is a lookup, not a mode change: nothing
     * is selected, and the setting is untouched.
     */
    fun isFocused(surah: Int, ayah: Int): Boolean =
        active?.let { it.surah == surah && it.ayah == ayah } == true

    /** Toggles one element. Tapping the active element off clears the focus too. */
    fun toggle(element: VerseElement): VerseSelection {
        if (element in selected) {
            return VerseSelection(
                selected = selected - element,
                active = if (active == element) null else active,
            )
        }
        return VerseSelection(selected = selected + element, active = element)
    }

    /** Drops everything about one ayah (e.g. when its page scrolls away). */
    fun clearAyah(surah: Int, ayah: Int): VerseSelection = VerseSelection(
        selected = selected.filterNot { it.surah == surah && it.ayah == ayah }.toSet(),
        active = active?.takeUnless { it.surah == surah && it.ayah == ayah },
    )

    fun clear(): VerseSelection = VerseSelection()
}

/**
 * What "play just this selection" means (D32).
 *
 * The selection is a set of per-layer elements; a playback run needs an *order* and a *recipe*:
 * the verses in mushaf order, whether their audio plays, and which spoken layers follow each
 * one. Deriving that here keeps the promise testable and in one place — the ViewModel turns the
 * plan into a queue or a preset sequence, but never re-decides what the reader selected.
 */
data class SelectionPlan(
    /** Distinct selected verses in mushaf order (`surah to ayah`). */
    val verses: List<Pair<Int, Int>>,
    /** The reciter's own audio plays for the verses (a VERSE layer element was selected). */
    val reciteVerses: Boolean,
    /** Translation keys to speak after each verse, in first-tapped order. */
    val translationKeys: List<String>,
    /** Commentary keys to speak after each verse, in first-tapped order. */
    val commentaryKeys: List<String>,
) {
    /**
     * Whether this plan is more than "play from this ayah": two verses, or any spoken layer,
     * changes what the play button promises, so the UI must say *play selection* instead.
     */
    val isSelectionRun: Boolean
        get() = verses.size > 1 || translationKeys.isNotEmpty() || commentaryKeys.isNotEmpty()
}

/**
 * The playback plan for this selection, or `null` when nothing is selected. Order is defined by
 * the mushaf itself (surah, then ayah) — tap order would make the same three verses play in a
 * different order depending on where a finger landed first, which is not what a recitation is.
 */
fun VerseSelection.playbackPlan(): SelectionPlan? {
    if (selected.isEmpty()) return null
    return SelectionPlan(
        verses = selected.map { it.surah to it.ayah }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second })),
        reciteVerses = selected.any { it.layer == VerseLayer.VERSE },
        translationKeys = selected.filter { it.layer == VerseLayer.TRANSLATION }
            .map { it.key }.filter { it.isNotBlank() }.distinct(),
        commentaryKeys = selected.filter { it.layer == VerseLayer.COMMENTARY }
            .map { it.key }.filter { it.isNotBlank() }.distinct(),
    )
}
