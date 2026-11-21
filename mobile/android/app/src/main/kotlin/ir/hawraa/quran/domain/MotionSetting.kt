package ir.hawraa.quran.domain

enum class MotionSetting(val key: String) {
    SYSTEM("system"),
    FULL("full"),
    REDUCED("reduced");

    companion object {
        val DEFAULT = SYSTEM

        fun fromKey(key: String?): MotionSetting =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
