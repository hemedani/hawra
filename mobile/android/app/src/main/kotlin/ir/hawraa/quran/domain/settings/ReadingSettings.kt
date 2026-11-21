package ir.hawraa.quran.domain.settings

/**
 * Reading preferences. Pure data + persistence keys; the UI resolves the presets
 * to concrete colors per theme (`ui/theme/QuranColors.kt`), so a preset key stays
 * stable across light/dark and across future palette tweaks.
 */
data class ReadingSettings(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val showTranslation: Boolean = true,
    val letterInk: LetterInk = LetterInk.INK,
    val diacriticInk: DiacriticInk = DiacriticInk.GOLD,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 22
        const val MAX_FONT_SIZE_SP = 44
        const val FONT_SIZE_STEP_SP = 2
        const val DEFAULT_FONT_SIZE_SP = 32
    }
}

/** Color of the letters (حروف). */
enum class LetterInk(val key: String) {
    INK("ink"),
    GREEN("green"),
    NAVY("navy"),
    ;

    companion object {
        fun fromKey(key: String?): LetterInk = entries.firstOrNull { it.key == key } ?: INK
    }
}

/** Color of the diacritics (اعراب). */
enum class DiacriticInk(val key: String) {
    GOLD("gold"),
    GREEN("green"),
    GREY("grey"),
    BURGUNDY("burgundy"),
    ;

    companion object {
        fun fromKey(key: String?): DiacriticInk = entries.firstOrNull { it.key == key } ?: GOLD
    }
}
