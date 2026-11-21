package ir.hawraa.quran.domain.preset

/**
 * A recitation **preset**: a reusable recipe for how to recite *one* ayah.
 *
 * Pure Kotlin — no Android, no I/O. The shape mirrors `back/models/preset.ts` field for
 * field, because presets travel between devices through the backend and a mapping
 * surprise would silently change a user's recitation on their other phone.
 *
 * The important property is that a preset is **range-independent**. It never names a
 * surah or an ayah; it says "recite, then read the Ansarian translation, then recite
 * again", and the player applies that recipe at every stop of whatever range the reader
 * chose. That is what lets one preset work over a single verse, a page, a juz or the
 * whole Quran without the user maintaining several copies of it.
 */
data class Preset(
    /**
     * Device-minted id and the server's idempotency key. Stable across renames, so a
     * rename is an update rather than a new preset on the other device.
     */
    val clientId: String,
    val name: String,
    /** Optional note shown under the name in the preset list. */
    val description: String? = null,
    val steps: List<PresetStep> = emptyList(),
    /** Pins a voice; null means "whatever the reader currently has selected". */
    val reciterKey: String? = null,
    /** Move to the next ayah by itself when the recipe finishes. */
    val autoAdvance: Boolean = true,
    /** Silence inserted between two ayahs, on top of the last step's own gap. */
    val ayahGapMs: Int = 0,
    val isDefault: Boolean = false,
    /** Deleted but kept because it has coverage or history (backend D13). */
    val isArchived: Boolean = false,
    val revision: Int = 1,
    /** Last-write-wins stamp, epoch millis from the editing device's clock. */
    val updatedAtMs: Long = 0L,
) {
    /** Steps that will actually do something, in order. See [PresetStep.isPlayable]. */
    val playableSteps: List<PresetStep> get() = steps.filter { it.isPlayable }

    /**
     * A preset with no playable step cannot recite anything, so the player must refuse it
     * rather than silently do nothing when the user presses play.
     */
    val isPlayable: Boolean get() = playableSteps.isNotEmpty()

    /** Every text key this preset will ask for — used to warn about missing translations. */
    val requiredTextKeys: Set<String>
        get() = playableSteps.mapNotNull { it.textKey }.toSet()

    /**
     * A local edit: same identity, new content, and a stamp that beats the stored copy on
     * the server (LWW) and on this device. Callers must not hand-roll the stamp — a preset
     * edited with an unchanged `updatedAtMs` would lose its own edit on the next sync.
     */
    fun edited(
        name: String = this.name,
        description: String? = this.description,
        steps: List<PresetStep> = this.steps,
        reciterKey: String? = this.reciterKey,
        autoAdvance: Boolean = this.autoAdvance,
        ayahGapMs: Int = this.ayahGapMs,
        atMs: Long,
    ): Preset = copy(
        name = name.trim().ifBlank { this.name },
        description = description,
        steps = steps,
        reciterKey = reciterKey,
        autoAdvance = autoAdvance,
        ayahGapMs = ayahGapMs.coerceIn(0, MAX_GAP_MS),
        revision = revision + 1,
        updatedAtMs = atMs,
    )

    companion object {
        /** Long enough to breathe, short enough not to feel broken. */
        const val MAX_GAP_MS = 10_000

        /**
         * Keeps a preset usable when it arrives from the server or from an older build.
         * Drops what cannot be played instead of playing something wrong: a `translation`
         * step with no `textKey` has no text to speak, and a `repeat` of 0 would silently
         * skip the verse the preset is about.
         */
        fun sanitized(
            clientId: String,
            name: String,
            description: String? = null,
            steps: List<PresetStep>,
            reciterKey: String?,
            autoAdvance: Boolean,
            ayahGapMs: Int,
            isDefault: Boolean,
            isArchived: Boolean,
            revision: Int,
            updatedAtMs: Long,
        ): Preset = Preset(
            clientId = clientId,
            name = name.trim().ifBlank { clientId },
            description = description,
            steps = steps.filter { it.isPlayable }.map { step ->
                step.copy(
                    repeat = step.repeat.coerceIn(1, PresetStep.MAX_REPEAT),
                    gapMs = step.gapMs.coerceIn(0, MAX_GAP_MS),
                    speed = step.speed?.takeIf { it.isFinite() && it > 0f },
                )
            },
            reciterKey = reciterKey,
            autoAdvance = autoAdvance,
            ayahGapMs = ayahGapMs.coerceIn(0, MAX_GAP_MS),
            isDefault = isDefault,
            isArchived = isArchived,
            revision = revision.coerceAtLeast(1),
            updatedAtMs = updatedAtMs,
        )
    }
}

/**
 * One instruction inside a preset.
 *
 * [textKey] names *which* translation or tafsir, in the same vocabulary as
 * [ir.hawraa.quran.domain.quran.VerseTranslation.key], so adding a second translation to
 * the content asset needs no change here.
 */
data class PresetStep(
    val kind: PresetStepKind,
    val textKey: String? = null,
    val repeat: Int = 1,
    /** Per-step playback rate override; null inherits the reader's speed. */
    val speed: Float? = null,
    /** Silence after this step, every time it runs. */
    val gapMs: Int = 0,
    /** TTS voice hint for a spoken step. */
    val languageCode: String? = null,
) {
    /**
     * Whether this step can produce sound. A spoken step without a text key has nothing to
     * say, and a repeat of zero would skip the ayah the preset exists for — both are
     * dropped rather than played as silence, so a corrupt preset fails visibly.
     */
    val isPlayable: Boolean
        get() = repeat >= 1 && when (kind) {
            PresetStepKind.AYAH, PresetStepKind.SILENCE -> true
            PresetStepKind.TRANSLATION, PresetStepKind.TAFSIR -> !textKey.isNullOrBlank()
        }

    companion object {
        /**
         * Capped so a stuck "repeat" spinner cannot queue 10 000 copies of a verse and make
         * the app look frozen. Ten is past any real recitation habit.
         */
        const val MAX_REPEAT = 10

        fun ayah(repeat: Int = 1, gapMs: Int = 0, speed: Float? = null): PresetStep =
            PresetStep(PresetStepKind.AYAH, repeat = repeat, gapMs = gapMs, speed = speed)

        fun translation(textKey: String, repeat: Int = 1, gapMs: Int = 0): PresetStep =
            PresetStep(PresetStepKind.TRANSLATION, textKey = textKey, repeat = repeat, gapMs = gapMs)

        fun tafsir(textKey: String, repeat: Int = 1, gapMs: Int = 0): PresetStep =
            PresetStep(PresetStepKind.TAFSIR, textKey = textKey, repeat = repeat, gapMs = gapMs)

        fun silence(gapMs: Int): PresetStep =
            PresetStep(PresetStepKind.SILENCE, gapMs = gapMs)
    }
}

/**
 * What a step asks for. The wire names match the backend enum exactly; [fromWire] is
 * deliberately tolerant, because an unknown kind from a newer server must degrade to
 * "skip this step", never to a crash in the reader.
 */
enum class PresetStepKind(val wire: String) {
    AYAH("ayah"),
    TRANSLATION("translation"),
    TAFSIR("tafsir"),
    SILENCE("silence"),
    ;

    /** Whether this kind is read aloud by TTS rather than by the reciter. */
    val isSpoken: Boolean get() = this == TRANSLATION || this == TAFSIR

    companion object {
        fun fromWire(value: String?): PresetStepKind? = entries.firstOrNull { it.wire == value }
    }
}
