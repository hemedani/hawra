package ir.hawraa.quran.domain.download

/**
 * "Put this verse's audio on disk before I play it."
 *
 * The seam that makes a preset **download-then-play, verse by verse**: the player asks for one
 * verse to be provisioned and suspends until it is on disk, so a reciter the reader has never
 * cached still plays offline — one file at a time, as the run reaches each ayah.
 *
 * It is an interface rather than a direct [DownloadManager] call so the player stays unit-testable
 * (a fake answers with a path, or `null` to mean "stream it") and so the whole "prefer disk, else
 * fetch and wait, else fall back to streaming" policy lives in exactly one production class.
 *
 * Pure Kotlin: the contract speaks in keys and coordinates, never in URLs or files.
 */
fun interface VerseAudioProvisioner {
    /**
     * Ensures `surah:ayah` for [reciterKey]/[style] is on disk and returns its absolute path, or
     * `null` when it cannot be provided (no downloadable edition, a non-per-ayah edition, offline,
     * or the fetch did not land in time). `null` is not an error — the caller streams instead.
     */
    suspend fun provision(reciterKey: String, style: String, surah: Int, ayah: Int): String?
}
