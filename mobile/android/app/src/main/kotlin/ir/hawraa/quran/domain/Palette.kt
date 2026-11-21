package ir.hawraa.quran.domain

/**
 * Deterministic aurora gradient ramp index derived from any identity seed
 * (surah, juz, reciter, or title), so the same tile paints the same ramp everywhere, offline.
 */
const val AURORA_COUNT: Int = 8

/** FNV-1a 32-bit, returned as the unsigned value. */
fun hashString(value: String): Long {
    var hash = 0x811c9dc5.toInt()
    for (ch in value) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    return hash.toUInt().toLong()
}

data class TileSeed(val contentHash: String? = null, val title: String? = null, val id: String? = null)

private fun seedKey(seed: TileSeed?): String =
    (seed?.contentHash ?: seed?.title ?: seed?.id ?: "").ifEmpty { "hawraa" }

fun paletteIndexFor(seed: TileSeed?): Int =
    Integer.remainderUnsigned(hashString(seedKey(seed)).toInt(), AURORA_COUNT)

fun paletteIndexFor(key: String): Int =
    Integer.remainderUnsigned(hashString(key.ifEmpty { "hawraa" }).toInt(), AURORA_COUNT)
