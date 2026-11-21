package ir.hawraa.quran.domain.download

/**
 * How big a download will be, and how to say it.
 *
 * The server does not store file sizes, and the app cannot know an ayah's duration without
 * fetching it, so the pre-download figure is an **estimate** derived from the bitrate and the
 * number of ayahs in the scope. That is honest as long as it is presented as an estimate: the
 * downloads screen shows *real* bytes once files land, and the confirmation sheet shows this
 * number so nobody starts a 300 MB download believing it is 30.
 *
 * [NOMINAL_AYAH_SECONDS] is the one invented constant in the feature. It is the average length
 * of a Hafs ayah at a measured murattal pace (about 7 s across al-Baqarah and the short
 * surahs), and it is deliberately only used for an order-of-magnitude figure.
 */
object DownloadSizing {

    const val NOMINAL_AYAH_SECONDS = 7.0

    /**
     * 6236 ayahs over 30 juz. Individual juz range from roughly 100 to 250 ayahs, so this is
     * only good enough for an estimate — and an estimate is all the confirmation sheet needs.
     */
    const val NOMINAL_AYAHS_PER_JUZ = 208

    /**
     * Seconds of audio in one juz.
     *
     * Needed because a juz-granularity edition breaks the per-ayah formula: juz 1 is 148 ayahs
     * and about an hour, so `estimateBytes(24, 148)` — which assumes 7 s per ayah — comes out four
     * times too small. The unit for that edition is the *juz*, so the estimate is built from the
     * juz.
     *
     * Calibrated against the one file that can be measured: the mirrored Shakernejad juz 1 is
     * 12.65 MB at 24 kbps, which is ~70 minutes. Sixty is used rather than seventy because juz 1
     * is one of the longest in the mushaf and juz 30 is roughly a third of it — the figure is an
     * average, and the UI says "about".
     */
    const val NOMINAL_JUZ_SECONDS = 3_600.0

    /** Bytes a scope of [ayahCount] ayahs is likely to occupy at [bitrateKbps]. */
    fun estimateBytes(bitrateKbps: Int, ayahCount: Int): Long {
        if (bitrateKbps <= 0 || ayahCount <= 0) return 0L
        val bitsPerSecond = bitrateKbps.toDouble() * 1_000.0
        return (bitsPerSecond / 8.0 * NOMINAL_AYAH_SECONDS * ayahCount).toLong()
    }

    /** Bytes [juzCount] whole juz files are likely to occupy at [bitrateKbps]. */
    fun estimateJuzBytes(bitrateKbps: Int, juzCount: Int): Long {
        if (bitrateKbps <= 0 || juzCount <= 0) return 0L
        val bitsPerSecond = bitrateKbps.toDouble() * 1_000.0
        return (bitsPerSecond / 8.0 * NOMINAL_JUZ_SECONDS * juzCount).toLong()
    }

    /**
     * A byte count as a number and a unit, so the UI can format the number in the reader's own
     * digits and the unit in the reader's own language. Returning a formatted string here
     * would put Persian text in `domain/`.
     */
    fun human(bytes: Long): ByteSize {
        val safe = bytes.coerceAtLeast(0L).toDouble()
        return when {
            safe >= GIGABYTE -> ByteSize(safe / GIGABYTE, SizeUnit.GB)
            safe >= MEGABYTE -> ByteSize(safe / MEGABYTE, SizeUnit.MB)
            else -> ByteSize(safe / KILOBYTE, SizeUnit.KB)
        }
    }

    private const val KILOBYTE = 1024.0
    private const val MEGABYTE = KILOBYTE * 1024.0
    private const val GIGABYTE = MEGABYTE * 1024.0
}

/** A magnitude with a unit; see [DownloadSizing.human]. */
data class ByteSize(val value: Double, val unit: SizeUnit) {
    /** One decimal below 10 (`1.4 MB`), none above (`48 MB`) — no false precision. */
    fun rounded(): Double = if (value < 10.0) kotlin.math.round(value * 10) / 10.0 else kotlin.math.round(value)
}

enum class SizeUnit { KB, MB, GB }
