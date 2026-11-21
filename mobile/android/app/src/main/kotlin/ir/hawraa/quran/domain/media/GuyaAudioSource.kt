package ir.hawraa.quran.domain.media

/**
 * "Is there spoken audio for this verse's translation/tafsir, and where do I play it from?"
 *
 * The seam that lets a preset's `translation`/`tafsir` step play **real guya audio** (ترجمه‌گویا /
 * تفسیرگویا) downloaded or streamed verse by verse, instead of always falling back to on-device TTS.
 * It is an interface so the preset player stays unit-testable (a fake answers a URL, or `null` to mean
 * "no audio — use TTS") and so the whole "find a verified edition → disk first → download → stream →
 * give up to TTS" policy lives in one production class.
 *
 * Pure Kotlin: the contract speaks in keys and coordinates, never in URLs it builds itself.
 */
fun interface GuyaAudioSource {
    /**
     * An absolute, playable URL for this verse's guya audio — a `file://` path when it is on disk,
     * or a stream URL when the server can serve it now — or `null` when there is no verified,
     * available edition, in which case the caller speaks the text aloud instead.
     *
     * `null` is a normal outcome, not an error: guya editions are seeded unverified until the owner
     * drops the audio, and a verse with no narration must still be heard (via TTS), never skipped.
     */
    suspend fun resolveUrl(
        contentType: MediaContentType,
        textKey: String?,
        surah: Int,
        ayah: Int,
    ): String?
}
