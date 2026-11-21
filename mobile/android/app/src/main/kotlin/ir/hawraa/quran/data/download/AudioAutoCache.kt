package ir.hawraa.quran.data.download

import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.ReciterInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What an automatic cache attempt did, so the caller can stay silent unless something went wrong. */
sealed interface AutoCacheOutcome {
    /** Files were put on the queue. */
    data class Queued(val files: Int) : AutoCacheOutcome

    /** Everything the scope needed was already on disk, or already queued. */
    data object NothingToDo : AutoCacheOutcome

    /** The reader's reciter has no downloadable edition on the server — nothing to fetch. */
    data object NoEdition : AutoCacheOutcome

    /** The manifest could not be fetched. Never fatal: playback streams instead. */
    data class Failed(val reason: String?) : AutoCacheOutcome
}

/**
 * The "keep what you are listening to" rule: when playback starts on a page, its audio is queued
 * for download in the background.
 *
 * ## Why this exists at all
 *
 * The reader already streams, so nothing here is required for audio to work. What it buys is the
 * *second* listen: the page the reader actually played is the page they are most likely to open
 * again, and on a train that second time has no network. Caching on play is the cheapest possible
 * prediction of what someone will want offline, and it costs one manifest call per page.
 *
 * ## The two things it refuses to do
 *
 *  - **It never picks the wrong file.** The scope is chosen from the reciter's granularity: a
 *    per-ayah edition caches the page's verse range, a per-juz edition caches the juz (or juzes)
 *    the page falls in, because a whole-juz MP3 is the only thing that reciter publishes.
 *  - **It never blocks playback.** Every failure is a [AutoCacheOutcome], not an exception; the
 *    queue is written before any bytes move, so a dead network costs a queued row and nothing
 *    else.
 *
 * ## Why the `ensured` set
 *
 * Pressing play, pausing and pressing play again must not hit the manifest endpoint three times.
 * The database already makes the *rows* idempotent, but the round trip is not free, so a scope
 * that has been asked for in this process is not asked for again. A failed download is retried
 * from the downloads screen, which is an explicit gesture — not from re-pressing play, which is
 * not.
 */
@Singleton
class AudioAutoCache @Inject constructor(
    private val manager: DownloadManager,
    private val catalogue: RecitationCatalogueRepository,
) {
    private val mutex = Mutex()

    /** `editionKey|scopeKey` pairs already requested since launch. */
    private val ensured = mutableSetOf<String>()

    /** Set after the first attempt to fill an empty catalogue, so an offline reader does not retry. */
    private var refreshAttempted = false

    /**
     * Queues whatever [scope] still needs on the edition matching [reciterKey] and [style].
     *
     * The style is part of the match because two editions of one reciter can be different
     * performances: caching a `mujawwad` file for a reader listening to `murattal` would put the
     * wrong recitation on disk under the right name.
     */
    suspend fun ensure(
        reciterKey: String,
        style: String,
        scope: DownloadScope,
    ): AutoCacheOutcome {
        val edition = editionFor(reciterKey, style) ?: return AutoCacheOutcome.NoEdition

        val token = "${edition.key}|${scope.scopeKey}"
        mutex.withLock {
            if (!ensured.add(token)) return AutoCacheOutcome.NothingToDo
        }

        return manager.request(edition, scope).fold(
            onSuccess = { request -> outcomeOf(request) },
            onFailure = { error ->
                // Let a failure be retried by the next play: the token was only added optimistically.
                mutex.withLock { ensured.remove(token) }
                AutoCacheOutcome.Failed(error.message)
            },
        )
    }

    /**
     * The server edition for a reciter, preferring the one whose style matches.
     *
     * Falls back to the reciter's best downloadable edition when no style matches, which is what
     * makes this work for a reciter whose catalogue entry and server row disagree on spelling
     * (`tartil` vs `murattal`) — better a correct-voice download than none.
     */
    suspend fun editionFor(reciterKey: String, style: String): EditionInfo? {
        val reciters = catalogueForAutoCache()
        val reciter = reciters.firstOrNull { it.key == reciterKey } ?: return null
        val downloadable = reciter.downloadable
        return downloadable.firstOrNull { it.style == style }
            ?: downloadable.firstOrNull()
            ?: reciter.preferredEdition()
    }

    /**
     * The cached catalogue, fetched once if it has never been filled.
     *
     * The reader can be the first screen after the gate, so "the downloads screen has populated
     * the cache" is not a safe assumption. One attempt per process, and only when the cache is
     * empty — after that a missing catalogue means offline, and the reader should not pay a
     * timeout on every play.
     */
    private suspend fun catalogueForAutoCache(): List<ReciterInfo> {
        val cached = catalogue.cached()
        if (cached.isNotEmpty()) return cached

        val alreadyTried = mutex.withLock {
            val wasTried = refreshAttempted
            refreshAttempted = true
            wasTried
        }
        if (alreadyTried) return emptyList()
        return catalogue.refresh().getOrNull().orEmpty()
    }

    private fun outcomeOf(request: DownloadRequest): AutoCacheOutcome =
        if (request.queued == 0) AutoCacheOutcome.NothingToDo else AutoCacheOutcome.Queued(request.queued)
}
