package ir.hawraa.quran.data.media

import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.media.MediaEdition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The media catalogue with an in-process cache.
 *
 * One fetch per process is enough to answer every guya lookup in a preset run, and a failure to
 * reach the server is simply "no guya yet" — the preset player then speaks the text aloud (TTS),
 * which is the honest degraded behaviour, not an error. (A persisted offline copy, like the
 * recitation catalogue keeps, is a follow-up; the audio itself needs the network or dropped files
 * regardless, so caching only the reference list adds little on the first pass.)
 */
@Singleton
class MediaCatalogueRepository @Inject constructor(
    private val client: MediaCatalogueClient,
) {
    private val mutex = Mutex()

    @Volatile
    private var cache: List<MediaEdition>? = null

    suspend fun cached(): List<MediaEdition> =
        cache ?: mutex.withLock { cache ?: refresh().getOrNull().orEmpty().also { cache = it } }

    suspend fun refresh(): Result<List<MediaEdition>> {
        val result = client.editions()
        result.getOrNull()?.let { cache = it }
        return result
    }

    /**
     * The verified media edition to use for a step asking for [contentType] + [textKey].
     *
     * Preference order, so a step that names a text still plays *some* narration when the exact
     * text has no edition: default-with-matching-text, then any-with-matching-text, then the
     * content type's default, then any verified of that type. Returns `null` when nothing is
     * verified — which is the normal state until the owner drops the guya audio.
     */
    suspend fun editionFor(contentType: MediaContentType, textKey: String?): MediaEdition? {
        val verified = cached().filter { it.contentType == contentType && it.isVerified }
        return chooseMediaEdition(verified, textKey)
    }
}

/**
 * The selection policy, extracted pure so it is testable without a client or a coroutine.
 *
 * [verified] is already filtered to one content type and to verified rows; this only ranks them.
 */
internal fun chooseMediaEdition(
    verified: List<MediaEdition>,
    textKey: String?,
): MediaEdition? {
    if (verified.isEmpty()) return null
    val matching = textKey?.let { key -> verified.filter { it.textKey == key } }.orEmpty()
    return matching.firstOrNull { it.isDefault }
        ?: matching.firstOrNull()
        ?: verified.firstOrNull { it.isDefault }
        ?: verified.firstOrNull()
}
