package ir.hawraa.quran.domain.recitation

/**
 * The Hafs ʿan ʿAsim surah lengths, in mushaf order, and the mapping between a
 * `(surah, ayah)` pair and the **global ayah number** (1…6236) that verse-by-verse
 * audio CDNs use as their file name.
 *
 * This table is content, not a guess: the per-ayah audio catalogue addresses files as
 * `…/{ayah}.mp3`, so a single wrong count would silently shift every recitation after
 * that surah by one file — the reader would hear the wrong verse with no visible
 * symptom. `AyahNumberingTest` therefore pins the total to 6236 and cross-checks the
 * entries the bundled asset can confirm (al-Fatiha = 7, al-Baqarah = 286).
 *
 * Pure Kotlin on purpose: no Android, no I/O, no catalogue.
 */
object AyahNumbering {

    /** Total number of ayahs in the Hafs mushaf. */
    const val TOTAL_AYAHS = 6236

    const val SURAH_COUNT = 114

    /** Ayah count per surah, index 0 = surah 1. */
    private val AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, // 1–10
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135, // 11–20
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, // 21–30
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85, // 31–40
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, // 41–50
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13, // 51–60
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, // 61–70
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42, // 71–80
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, // 81–90
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11, // 91–100
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, // 101–110
        5, 4, 5, 6, // 111–114
    )

    init {
        check(AYAH_COUNTS.size == SURAH_COUNT) { "surah table must hold 114 entries" }
    }

    fun ayahCount(surah: Int): Int = AYAH_COUNTS.getOrNull(surah - 1) ?: 0

    /**
     * Global index of `surah:ayah`, or `null` when the pair is outside the mushaf.
     * 1:1 → 1, 2:1 → 8, 114:6 → 6236.
     */
    fun globalAyah(surah: Int, ayah: Int): Int? {
        if (surah !in 1..SURAH_COUNT) return null
        if (ayah !in 1..ayahCount(surah)) return null
        var offset = 0
        for (index in 0 until surah - 1) offset += AYAH_COUNTS[index]
        return offset + ayah
    }

    /** Inverse of [globalAyah]; `null` outside 1…[TOTAL_AYAHS]. */
    fun surahAyah(globalAyah: Int): Pair<Int, Int>? {
        if (globalAyah !in 1..TOTAL_AYAHS) return null
        var remaining = globalAyah
        for (index in AYAH_COUNTS.indices) {
            val count = AYAH_COUNTS[index]
            if (remaining <= count) return (index + 1) to remaining
            remaining -= count
        }
        return null
    }

    /** Sum of every surah length; must equal [TOTAL_AYAHS]. */
    val total: Int get() = AYAH_COUNTS.sum()

    // ---- juz -------------------------------------------------------------------------

    /**
     * The first ayah of each juz, index 0 = juz 1.
     *
     * Copied from the server's `content/quranIndex.ts`, which is the copy that decides how a
     * juz-granularity edition is split into files. Two tables would eventually disagree, and the
     * symptom would be a device that downloads juz 1 and plays juz 2 — so the app keeps its own
     * copy only because the reader must answer "which juz is this page in?" offline, and
     * [AyahNumberingTest] pins it against the same invariants the server checks at boot.
     */
    private val JUZ_STARTS = arrayOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, // 1–5
        4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41, // 6–10
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, // 11–15
        18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56, // 16–20
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, // 21–25
        46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1, // 26–30
    )

    const val JUZ_COUNT = 30

    /** Global ayah number each juz begins at, computed once from the table above. */
    private val JUZ_START_GLOBALS: IntArray = JUZ_STARTS.map { (surah, ayah) ->
        globalAyah(surah, ayah) ?: error("juz starts at an invalid ayah $surah:$ayah")
    }.toIntArray()

    init {
        check(JUZ_STARTS.size == JUZ_COUNT) { "juz table must hold $JUZ_COUNT entries" }
        for (index in 1 until JUZ_START_GLOBALS.size) {
            check(JUZ_START_GLOBALS[index] > JUZ_START_GLOBALS[index - 1]) {
                "juz starts must be strictly increasing"
            }
        }
    }

    /**
     * The juz (1…30) containing `surah:ayah`, or `null` outside the mushaf.
     *
     * The reader asks this to pick the right whole-juz file for a page, and to show which juz is
     * sounding. A page can straddle a boundary, which is why the queue builds one track per juz
     * the page touches rather than one per page.
     */
    fun juzOf(surah: Int, ayah: Int): Int? {
        val global = globalAyah(surah, ayah) ?: return null
        // The array is sorted by construction (checked in `init`), so the last start at or
        // before this ayah is its juz.
        val index = JUZ_START_GLOBALS.indexOfLast { it <= global }
        return if (index >= 0) index + 1 else null
    }

    /** First `surah to ayah` of [juz], or `null` outside 1…[JUZ_COUNT]. */
    fun juzStart(juz: Int): Pair<Int, Int>? = JUZ_STARTS.getOrNull(juz - 1)
}
