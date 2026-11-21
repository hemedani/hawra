package ir.hawraa.quran.domain.preset

import ir.hawraa.quran.domain.quran.QuranPage
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.recitation.AyahNumbering

/**
 * One thing to do at one moment of a preset run, fully resolved.
 *
 * A cue is the *flattened* form of a preset: every repeat is its own cue, every gap is
 * attached to the cue that waits for it, and every text is already looked up. Nothing
 * downstream needs the preset, the verse list or a text index to play a cue — which is
 * what makes the player loop trivial and the whole thing testable on the JVM.
 *
 * [gapBeforeMs] rather than `gapAfterMs`: a gap belongs to the moment *before* the next
 * sound, so the inter-ayah pause rides on the first cue of the following ayah and a
 * trailing gap at the very end simply does not exist.
 */
sealed interface PresetCue {
    val stepIndex: Int
    val kind: PresetStepKind
    val surah: Int
    val ayah: Int
    val page: Int
    /** 1-based, so the UI can say "۲ از ۳" without arithmetic. */
    val repeatIndex: Int
    val repeatCount: Int
    val gapBeforeMs: Int
    /** Resolved playback rate: the step's override, else the reader's own speed. */
    val speed: Float

    /** Recite the ayah itself, with a real reciter. */
    data class Recite(
        override val stepIndex: Int,
        override val surah: Int,
        override val ayah: Int,
        override val page: Int,
        override val repeatIndex: Int,
        override val repeatCount: Int,
        override val gapBeforeMs: Int,
        override val speed: Float,
        /** Global ayah number, 1…6236 — how per-ayah CDNs address a file. */
        val globalAyah: Int,
    ) : PresetCue {
        override val kind: PresetStepKind get() = PresetStepKind.AYAH
    }

    /** Read a translation or a tafsir aloud. */
    data class Speak(
        override val stepIndex: Int,
        override val surah: Int,
        override val ayah: Int,
        override val page: Int,
        override val repeatIndex: Int,
        override val repeatCount: Int,
        override val gapBeforeMs: Int,
        override val speed: Float,
        /** Which translation/tafsir this text came from. */
        val textKey: String,
        val text: String,
        val languageCode: String?,
        val tafsir: Boolean,
    ) : PresetCue {
        override val kind: PresetStepKind
            get() = if (tafsir) PresetStepKind.TAFSIR else PresetStepKind.TRANSLATION
    }
}

/**
 * A preset applied to a concrete range of verses: the flat cue list plus what could not
 * be honoured.
 *
 * The failures are *reported*, not hidden. A preset that asks for the Maleki translation
 * when only the Ansarian one is bundled must tell the user why it sounds different,
 * otherwise the app looks broken in a way nobody can diagnose.
 */
data class PresetSequence(
    val cues: List<PresetCue> = emptyList(),
    /** How many verses the range had. */
    val verseCount: Int = 0,
    /** Distinct ayahs that will actually be recited. */
    val ayahCount: Int = 0,
    /** Text keys the preset asked for that the content does not carry. */
    val missingTextKeys: Set<String> = emptySet(),
    /** Verses the mushaf table could not place, so their audio was skipped. */
    val unplaceableVerses: Int = 0,
) {
    val isEmpty: Boolean get() = cues.isEmpty()
    val size: Int get() = cues.size

    fun cueAt(index: Int): PresetCue? = cues.getOrNull(index)

    /** The ayah the cue at [index] belongs to, or null when idle. */
    fun ayahAt(index: Int): Pair<Int, Int>? =
        cues.getOrNull(index)?.let { it.surah to it.ayah }

    val firstCue: PresetCue? get() = cues.firstOrNull()
    val lastCue: PresetCue? get() = cues.lastOrNull()

    /**
     * First cue of the next *different* ayah after [from]. Used by "next ayah", which in a
     * preset means "skip the rest of this ayah's recipe", not "skip one cue".
     */
    fun nextAyahIndex(from: Int): Int? {
        val current = cues.getOrNull(from) ?: return null
        for (i in (from + 1)..cues.lastIndex) {
            val cue = cues[i]
            if (cue.surah != current.surah || cue.ayah != current.ayah) return i
        }
        return null
    }

    /**
     * First cue of the previous ayah. From the middle of an ayah it goes to that ayah's
     * first cue; from the start of one it goes back a whole ayah — the behaviour of a
     * "previous" button on any player.
     */
    fun previousAyahIndex(from: Int): Int? {
        val current = cues.getOrNull(from) ?: return null
        val startOfCurrent = cues.indexOfFirst { it.surah == current.surah && it.ayah == current.ayah }
        if (startOfCurrent < 0) return null
        if (from > startOfCurrent) return startOfCurrent
        if (startOfCurrent == 0) return null
        val previous = cues[startOfCurrent - 1]
        var start = startOfCurrent - 1
        while (start > 0 && cues[start - 1].surah == previous.surah && cues[start - 1].ayah == previous.ayah) {
            start--
        }
        return start
    }

    /** Cue index of the first cue for `surah:ayah`, or null when the run skips it. */
    fun indexOfAyah(surah: Int, ayah: Int): Int? =
        cues.indexOfFirst { it.surah == surah && it.ayah == ayah }.takeIf { it >= 0 }

    /** Distinct ayahs in order, as the run will visit them. */
    fun ayahs(): List<Pair<Int, Int>> = cues.map { it.surah to it.ayah }.distinct()
}

/**
 * Turns a [Preset] into a [PresetSequence]. Pure: no player, no clock, no Android.
 *
 * The rule that shapes this whole file is that a preset describes **one ayah**, so the
 * builder is a fold over the verses of whatever range the reader picked. Nothing here
 * knows about pages, juz or the reader's scroll position.
 *
 * Input is [VerseItem] rather than `QuranVerse` because a cue needs the page it is on
 * (sessions and the history row are page-based) and a verse does not know its own page —
 * the page is the asset's container.
 */
object PresetSequenceBuilder {

    /**
     * @param defaultSpeed the reader's current playback rate; a step's own `speed`
     *   overrides it. Resolved here so no cue ever carries a null speed into the player.
     */
    fun build(
        preset: Preset,
        verses: List<VerseItem>,
        defaultSpeed: Float = 1f,
    ): PresetSequence {
        val steps = preset.playableSteps
        if (steps.isEmpty() || verses.isEmpty()) {
            return PresetSequence(verseCount = verses.size)
        }

        val cues = ArrayList<PresetCue>()
        val missingTextKeys = LinkedHashSet<String>()
        val recitedAyahs = LinkedHashSet<Pair<Int, Int>>()
        var unplaceable = 0

        /**
         * Silence accumulated but not yet attached to a cue: a `silence` step, the gap a
         * step declares after itself, and the preset's inter-ayah pause all land here and
         * are spent by the next cue that makes a sound.
         */
        var pendingGapMs = 0

        verses.forEachIndexed { verseIndex, item ->
            val verse = item.verse
            if (verseIndex > 0) pendingGapMs += preset.ayahGapMs

            steps.forEachIndexed { stepIndex, step ->
                when (step.kind) {
                    PresetStepKind.SILENCE -> {
                        // Nothing to play: a silence step is just more gap.
                        pendingGapMs += step.gapMs
                    }

                    PresetStepKind.AYAH -> {
                        val global = AyahNumbering.globalAyah(verse.surah, verse.ayah)
                        if (global == null) {
                            // The mushaf table cannot place this verse. Skipping is the only
                            // safe move: a guessed global number plays a neighbouring verse
                            // with nothing on screen to reveal the mistake.
                            unplaceable++
                        } else {
                            for (repetition in 1..step.repeat) {
                                cues += PresetCue.Recite(
                                    stepIndex = stepIndex,
                                    surah = verse.surah,
                                    ayah = verse.ayah,
                                    page = item.page,
                                    repeatIndex = repetition,
                                    repeatCount = step.repeat,
                                    gapBeforeMs = pendingGapMs,
                                    speed = step.speed ?: defaultSpeed,
                                    globalAyah = global,
                                )
                                pendingGapMs = step.gapMs
                            }
                            recitedAyahs += verse.surah to verse.ayah
                        }
                    }

                    PresetStepKind.TRANSLATION, PresetStepKind.TAFSIR -> {
                        val key = step.textKey
                        val text = key?.let { wanted ->
                            verse.translations.firstOrNull { it.key == wanted }?.text
                        }
                        if (key == null || text.isNullOrBlank()) {
                            if (key != null) missingTextKeys += key
                        } else {
                            for (repetition in 1..step.repeat) {
                                cues += PresetCue.Speak(
                                    stepIndex = stepIndex,
                                    surah = verse.surah,
                                    ayah = verse.ayah,
                                    page = item.page,
                                    repeatIndex = repetition,
                                    repeatCount = step.repeat,
                                    gapBeforeMs = pendingGapMs,
                                    speed = step.speed ?: defaultSpeed,
                                    textKey = key,
                                    text = text,
                                    languageCode = step.languageCode,
                                    tafsir = step.kind == PresetStepKind.TAFSIR,
                                )
                                pendingGapMs = step.gapMs
                            }
                        }
                    }
                }
            }
        }

        return PresetSequence(
            cues = cues,
            verseCount = verses.size,
            ayahCount = recitedAyahs.size,
            missingTextKeys = missingTextKeys,
            unplaceableVerses = unplaceable,
        )
    }

    /**
     * Convenience for a whole page or a run of pages. Named rather than overloaded because
     * `List<VerseItem>` and `List<QuranPage>` erase to the same JVM signature.
     */
    fun buildForPages(
        preset: Preset,
        pages: List<QuranPage>,
        defaultSpeed: Float = 1f,
    ): PresetSequence = build(
        preset = preset,
        verses = pages.flatMap { page -> page.verses.map { VerseItem(page.page, it) } },
        defaultSpeed = defaultSpeed,
    )
}
