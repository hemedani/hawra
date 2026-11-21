package ir.hawraa.quran.domain.download

/**
 * "Is this ayah already on disk, and where?"
 *
 * Playback asks this question once per ayah, on the main thread, while building a queue — so
 * it must be a pure in-memory lookup, not a database query. The index is therefore a snapshot
 * of the completed downloads, rebuilt when a download finishes and at app start.
 *
 * Two rules make it correct rather than merely fast:
 *
 *  - **Only per-ayah and per-surah files are indexed.** A whole-juz MP3 covers an ayah but
 *    cannot start at it, so it is stored (for continuous listening) and left out of the index;
 *    pointing the reader at it would play juz 1 from its beginning when verse 17 was requested.
 *  - **Style must match.** Two editions of one reciter can be different performances
 *    (`murattal` vs `mujawwad`); serving one when the reader chose the other would be the
 *    wrong recitation, which is worse than streaming the right one.
 */
data class LocalAudioEntry(
    val reciterKey: String,
    /** `murattal` | `mujawwad` | `tartil` | `muallim`, as the server described the edition. */
    val style: String,
    val kind: EditionGranularity,
    val surah: Int,
    /** `null` for a whole-surah file. */
    val ayah: Int?,
    /** Absolute path on disk. */
    val path: String,
)

/**
 * A whole-juz file that is on disk.
 *
 * Kept in a list of its own rather than as another [LocalAudioEntry] because the two answer
 * different questions: an entry answers "which file starts at this verse", which a juz file cannot
 * answer, and this answers "do I have this juz at all". Folding them together would mean a `find`
 * that has to be careful not to return a juz for a verse — the exact bug the old comment warned
 * about — and a nullable `surah` on every entry.
 */
data class LocalJuzFile(
    val reciterKey: String,
    val style: String,
    val juz: Int,
    /** Absolute path on disk. */
    val path: String,
)

class LocalAudioIndex(
    val entries: List<LocalAudioEntry> = emptyList(),
    /** Whole-juz files, addressed by juz number. Never consulted for verse playback. */
    val juzFiles: List<LocalJuzFile> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty() && juzFiles.isEmpty()

    /**
     * Absolute path of the file that serves `surah:ayah` for [reciterKey], or `null` when it
     * is not downloaded. An exact verse file wins over a whole-surah file: the verse one is
     * the finer-grained answer and the only one that can start at the right place.
     */
    fun find(reciterKey: String, style: String, surah: Int, ayah: Int): String? {
        var surahWide: String? = null
        for (entry in entries) {
            if (entry.reciterKey != reciterKey) continue
            if (entry.surah != surah) continue
            if (entry.kind == EditionGranularity.JUZ) continue
            if (!stylesMatch(entry.style, style)) continue
            when {
                entry.ayah == ayah -> return entry.path
                entry.ayah == null && surahWide == null -> surahWide = entry.path
            }
        }
        return surahWide
    }

    /** Absolute path of the downloaded whole-juz file for [juz], or `null`. */
    fun findJuz(reciterKey: String, style: String, juz: Int): String? =
        juzFiles.firstOrNull {
            it.reciterKey == reciterKey && it.juz == juz && stylesMatch(it.style, style)
        }?.path

    /** Whether anything at all is downloaded for this reciter — drives the "offline" badge. */
    fun hasAny(reciterKey: String): Boolean =
        entries.any { it.reciterKey == reciterKey } || juzFiles.any { it.reciterKey == reciterKey }

    /**
     * What to hand the player: a `file://` URL when the verse is on disk, [remote] otherwise.
     *
     * One definition of "prefer the download", used by both the reader's queue and the preset
     * player — the two places that turn a verse into an audio URL. Two copies of this rule
     * would eventually disagree, and the symptom would be audio that works in one screen and
     * not the other.
     */
    fun urlFor(reciterKey: String, style: String, surah: Int, ayah: Int, remote: String): String =
        find(reciterKey, style, surah, ayah)?.let { FILE_SCHEME + it } ?: remote

    /** The same rule for a whole juz, used by a juz-granularity reciter's queue. */
    fun urlForJuz(reciterKey: String, style: String, juz: Int, remote: String): String =
        findJuz(reciterKey, style, juz)?.let { FILE_SCHEME + it } ?: remote

    /**
     * A blank style on either side matches anything: an edition seeded without a style should
     * not silently disable playback, and a catalogue entry without one should not either.
     */
    private fun stylesMatch(downloaded: String, requested: String): Boolean =
        downloaded.isBlank() || requested.isBlank() || downloaded == requested

    companion object {
        val EMPTY = LocalAudioIndex()

        private const val FILE_SCHEME = "file://"
    }
}

/**
 * Supplies the current index snapshot.
 *
 * The index is rebuilt (never mutated), so a player holding this can always ask for the latest
 * one without caching a stale copy — and a JVM test can answer with a fixed index instead of
 * standing up the download layer.
 */
fun interface LocalAudioSource {
    fun current(): LocalAudioIndex
}
