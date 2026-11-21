package ir.hawraa.quran.domain

/** User's theme preference. Persisted via DataStore; SYSTEM follows the device. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = when (key) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }

    val key: String
        get() = when (this) {
            LIGHT -> "light"
            DARK -> "dark"
            SYSTEM -> "system"
        }
}