package ir.hawraa.quran.domain

/**
 * The language the UI is shown in. [SYSTEM] follows the device; the two explicit values
 * override it so a reader can keep the mushaf in Persian on an English phone (or the
 * other way round) without changing the device language.
 *
 * Persian is the default: this is a Persian-language mushaf app first.
 */
enum class AppLanguage(val key: String, val tag: String?) {
    SYSTEM("system", null),
    PERSIAN("fa", "fa"),
    ENGLISH("en", "en"),
    ;

    /** Layout direction that follows from the choice, or null when the system decides. */
    val isRtl: Boolean? get() = when (this) {
        SYSTEM -> null
        PERSIAN -> true
        ENGLISH -> false
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromKey(key: String?): AppLanguage =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
