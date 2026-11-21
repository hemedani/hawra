package ir.hawraa.quran.domain.recitation

import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.quran.toPersianDigits

/**
 * Whether a reciter's audio is published one file per verse or one file per juz.
 *
 * A property of the *source*, not a preference: Shakernejad's tartil is published juz by juz,
 * Parhizgar's murattal verse by verse. The reader has to know which, because the two produce
 * completely different queues — see [RecitationQueue].
 */
enum class ReciterGranularity(val wire: String) {
    AYAH("ayah"),
    JUZ("juz"),
    ;

    companion object {
        fun fromWire(value: String?): ReciterGranularity =
            entries.firstOrNull { it.wire == value } ?: AYAH
    }
}

/**
 * A reciter from the bundled catalogue (`assets/quran/recitations-v1.json`).
 *
 * [urlTemplate] carries the **global** ayah number as `{ayah}` for a per-ayah reciter, or the juz
 * number as `{juz}`/`{juz2}` for a per-juz one — [granularity] says which. It is a template, not a
 * resolved URL, because the catalogue must stay valid for all 6236 ayahs without shipping 6236
 * strings — and because the host may change bitrate or edition without touching code.
 *
 * A template that begins with `/` is server-relative and is resolved against the app's base URL by
 * the repository that loads this file: the server's own mirror is an origin like any other, and it
 * is the only origin that has Shakernejad.
 */
data class Reciter(
    val key: String,
    val nameFa: String,
    val nameEn: String,
    val style: String,
    val bitrate: Int,
    val urlTemplate: String,
    val granularity: ReciterGranularity = ReciterGranularity.AYAH,
) {
    /** Per-ayah audio URL for [globalAyah] (1…6236) — fills only `{ayah}`. */
    fun urlFor(globalAyah: Int): String = urlTemplate.replace(AYAH_PLACEHOLDER, globalAyah.toString())

    /**
     * Per-ayah audio URL that also fills the in-surah placeholders some archives use.
     *
     * `{ayah}` is the global number (1…6236, the AlQuran Cloud CDN scheme); `{surah3}{ayah3}` are
     * the zero-padded surah and in-surah ayah (Inoor names a file `002005` for Baqarah verse 5).
     * A template only substitutes the placeholders it contains, so both schemes coexist on one
     * field. Callers that have the verse (the reader queue, the preset player) use this overload.
     */
    fun urlFor(surah: Int, ayah: Int, globalAyah: Int): String = urlTemplate
        .replace(AYAH_PLACEHOLDER, globalAyah.toString())
        .replace(AYAH3_PLACEHOLDER, ayah.toString().padStart(3, '0'))
        .replace(SURAH3_PLACEHOLDER, surah.toString().padStart(3, '0'))
        .replace(SURAH_PLACEHOLDER, surah.toString())

    /** Whole-juz audio URL for [juz] (1…30). Only meaningful for a [ReciterGranularity.JUZ] source. */
    fun juzUrl(juz: Int): String = urlTemplate
        .replace(JUZ2_PLACEHOLDER, juz.toString().padStart(2, '0'))
        .replace(JUZ_PLACEHOLDER, juz.toString())

    /** Name for the UI locale; falls back to the other script rather than to a key. */
    fun displayName(persian: Boolean): String =
        if (persian) nameFa.ifBlank { nameEn } else nameEn.ifBlank { nameFa }

    /**
     * Whether this reciter can drive the reader's verse-by-verse queue.
     *
     * A whole-juz MP3 has no way to start at verse 17 of al-Baqarah, so a juz reciter gets a
     * one-track-per-juz queue instead — see [RecitationQueue.buildJuz]. The distinction is carried
     * rather than assumed so that "why does this reciter not highlight verses" has an answer in
     * the code and not only in a commit message.
     */
    val servesPerAyahPlayback: Boolean get() = granularity == ReciterGranularity.AYAH

    companion object {
        const val AYAH_PLACEHOLDER = "{ayah}"
        const val AYAH3_PLACEHOLDER = "{ayah3}"
        const val SURAH_PLACEHOLDER = "{surah}"
        const val SURAH3_PLACEHOLDER = "{surah3}"
        const val JUZ_PLACEHOLDER = "{juz}"
        /** Zero-padded form, because the upstream archives name files `Juz-01.mp3`. */
        const val JUZ2_PLACEHOLDER = "{juz2}"
    }
}

/** One playable track: what to fetch, and which verse it belongs to. */
data class RecitationTrack(
    val surah: Int,
    val ayah: Int,
    val globalAyah: Int,
    val page: Int,
    val url: String,
    /**
     * Set when this track is a whole juz rather than one verse. [surah]/[ayah] then carry the
     * juz's **first** ayah, which is a fact about the juz and not a claim about where playback is:
     * the UI reads this field to know that there is no verse to highlight.
     */
    val juz: Int? = null,
) {
    /** Same key the reader uses for its test tags and translation lookup. */
    val key: String get() = "$surah:$ayah"

    /** False for a whole-juz track — nothing on the page corresponds to a position in the file. */
    val servesOneVerse: Boolean get() = juz == null
}

/**
 * Playback speed presets. Deliberately a closed set: anything outside 0.5×–2× sounds
 * wrong for murattal, and a free slider invites 0.1× by accident.
 */
enum class PlaybackSpeed(val value: Float, val key: String) {
    HALF(0.5f, "0.5"),
    THREE_QUARTERS(0.75f, "0.75"),
    NORMAL(1f, "1"),
    ONE_AND_QUARTER(1.25f, "1.25"),
    ONE_AND_HALF(1.5f, "1.5"),
    ;

    /** The next preset, wrapping around — one tap cycles the whole set. */
    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]

    /**
     * Label for a pill: `۱×`, `۰٫۷۵×`. Persian digits and the Arabic decimal separator
     * (U+066B) when [persian] is true, so the label matches the rest of the page.
     */
    fun label(persian: Boolean): String {
        val number = if (persian) key.replace(".", DECIMAL_SEPARATOR_FA) else key
        return (if (persian) number.toPersianDigits() else number) + MULTIPLICATION_SIGN
    }

    companion object {
        /**
         * Tolerates a stored float, but only near a preset: a value from an older build
         * (or a corrupted preference) falls back to 1× rather than silently picking the
         * fastest speed. 1.6 counts as 1.5; 9 does not.
         */
        fun fromValue(value: Float): PlaybackSpeed {
            if (!value.isFinite()) return NORMAL
            val nearest = entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: return NORMAL
            return if (kotlin.math.abs(nearest.value - value) <= MAX_DRIFT) nearest else NORMAL
        }

        fun fromKey(key: String?): PlaybackSpeed =
            entries.firstOrNull { it.key == key } ?: NORMAL

        private const val MAX_DRIFT = 0.15f
        private const val DECIMAL_SEPARATOR_FA = "\u066B"
        private const val MULTIPLICATION_SIGN = "\u00D7"
    }
}

/**
 * Builds and navigates the ayah playlist. Pure: no player, no I/O, no Android.
 *
 * A queue is a run of verses in mushaf order — since D31 the reader's plain play is the **rest of
 * the surah** and a selection play is exactly the chosen verses, so a queue routinely crosses page
 * boundaries. Each [RecitationTrack] therefore carries the page its verse sits on (sessions and
 * follow-along scroll are page-based), not one page for the whole queue.
 */
object RecitationQueue {

    /**
     * One track per verse of [items] (in reading order), addressed by global ayah
     * number. Verses the mushaf table cannot place are skipped rather than silently
     * pointed at a neighbouring file — a wrong ayah is worse than a missing one.
     *
     * [resolveUrl] is the seam for offline playback: it receives the verse and the streaming
     * URL the reciter would use, and returns what to play — typically a `file://` URL when the
     * verse is already downloaded. It is a lambda rather than a download index so this stays
     * free of the data layer, and a test can pass a map.
     */
    fun build(
        items: List<VerseItem>,
        reciter: Reciter,
        resolveUrl: (surah: Int, ayah: Int, remote: String) -> String = { _, _, remote -> remote },
    ): List<RecitationTrack> =
        items.mapNotNull { item ->
            val verse = item.verse
            val global = AyahNumbering.globalAyah(verse.surah, verse.ayah) ?: return@mapNotNull null
            val remote = reciter.urlFor(verse.surah, verse.ayah, global)
            RecitationTrack(
                surah = verse.surah,
                ayah = verse.ayah,
                globalAyah = global,
                page = item.page,
                url = resolveUrl(verse.surah, verse.ayah, remote),
            )
        }

    /**
     * The queue for a **juz-granularity** reciter: one track per juz the items touch.
     *
     * One per juz, not one per page, because a page can straddle a boundary (page 22 does) and a
     * single file cannot cover both sides of it. Two tracks let playback run on into the next juz
     * instead of stopping at the edge of a file that was never going to contain the rest of the
     * page. A surah-wide queue likewise spans every juz the surah passes through (D31).
     *
     * [resolveUrl] receives the juz number and the streaming URL, mirroring [build]'s seam so a
     * downloaded juz is preferred the same way a downloaded verse is.
     */
    fun buildJuz(
        items: List<VerseItem>,
        reciter: Reciter,
        resolveUrl: (juz: Int, remote: String) -> String = { _, remote -> remote },
    ): List<RecitationTrack> =
        items
            .map { it.verse }
            .mapNotNull { AyahNumbering.juzOf(it.surah, it.ayah) }
            .distinct()
            .sorted()
            .mapNotNull { juz ->
                val start = AyahNumbering.juzStart(juz) ?: return@mapNotNull null
                val global = AyahNumbering.globalAyah(start.first, start.second)
                    ?: return@mapNotNull null
                RecitationTrack(
                    surah = start.first,
                    ayah = start.second,
                    globalAyah = global,
                    page = items.firstOrNull { it.verse.surah == start.first && it.verse.ayah == start.second }?.page
                        ?: items.firstOrNull { AyahNumbering.juzOf(it.verse.surah, it.verse.ayah) == juz }?.page
                        ?: 0,
                    url = resolveUrl(juz, reciter.juzUrl(juz)),
                    juz = juz,
                )
            }

    /** Index of `surah:ayah` in [tracks], or `-1`. */
    fun indexOf(tracks: List<RecitationTrack>, surah: Int, ayah: Int): Int =
        tracks.indexOfFirst { it.surah == surah && it.ayah == ayah }

    /** Next track, or `null` at the end of the page — the caller decides what that means. */
    fun next(tracks: List<RecitationTrack>, index: Int): Int? =
        if (index >= 0 && index + 1 <= tracks.lastIndex) index + 1 else null

    /** Previous track, or `null` at the start of the page. */
    fun previous(tracks: List<RecitationTrack>, index: Int): Int? =
        if (index > 0 && index <= tracks.lastIndex) index - 1 else null

    /**
     * Where to start when the reader presses play on a page. Prefers the verse they
     * tapped (as a `surah to ayah` pair, because a page can straddle a surah boundary
     * where ayah numbers restart), otherwise the first ayah of the page.
     *
     * A juz queue is matched by *juz* instead: its tracks carry the juz's first ayah, so
     * comparing verse numbers would only match when the reader happened to tap that verse.
     */
    fun startIndex(tracks: List<RecitationTrack>, selected: Pair<Int, Int>?): Int {
        if (selected == null) return 0
        val (surah, ayah) = selected
        val juz = AyahNumbering.juzOf(surah, ayah)
        val index = tracks.indexOfFirst { track ->
            if (track.juz != null) track.juz == juz else track.surah == surah && track.ayah == ayah
        }
        return if (index >= 0) index else 0
    }
}
