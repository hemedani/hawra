package ir.hawraa.quran.data.download

import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.VerseAudioProvisioner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The production [VerseAudioProvisioner]: queue the verse, wait for it to land, hand back its path.
 *
 * The policy is deliberately ordered so the common cases cost nothing and the failure cases cost
 * one stream:
 *
 *  1. **On disk already** → return the path. No network, no wait. This is every verse after the
 *     first play.
 *  2. **No downloadable per-ayah edition** → `null` immediately. A surah- or juz-granularity
 *     edition has no file that starts at this verse, so waiting would be waiting for a file that
 *     will never be written; streaming (or, for a juz voice in a per-ayah preset, the caller's own
 *     fallback) is the honest answer.
 *  3. **Not yet fetched** → [AudioAutoCache.ensure] the single-verse scope (which schedules the
 *     worker) and suspend until the playback index reports the verse, bounded by a timeout so a
 *     dead network degrades to streaming rather than a stuck player.
 *
 * It depends on the write side ([DownloadManager] for the index, [AudioAutoCache] for the queue)
 * rather than the read-only [ir.hawraa.quran.domain.download.LocalAudioSource], because provisioning
 * is by definition an action, not a lookup — the player still uses `LocalAudioSource` for the
 * cheap "already here?" check before it ever calls in here.
 */
@Singleton
class AutoCacheVerseProvisioner @Inject constructor(
    private val autoCache: AudioAutoCache,
    private val downloads: DownloadManager,
) : VerseAudioProvisioner {

    override suspend fun provision(reciterKey: String, style: String, surah: Int, ayah: Int): String? {
        downloads.current().find(reciterKey, style, surah, ayah)?.let { return it }

        val edition = autoCache.editionFor(reciterKey, style) ?: return null
        if (edition.granularity != EditionGranularity.AYAH) return null

        when (autoCache.ensure(reciterKey, style, DownloadScope.Ayah(surah, ayah))) {
            is AutoCacheOutcome.NoEdition, is AutoCacheOutcome.Failed -> return null
            AutoCacheOutcome.NothingToDo, is AutoCacheOutcome.Queued -> Unit
        }

        val index = withTimeoutOrNull(TIMEOUT_MS) {
            downloads.index.first { it.find(reciterKey, style, surah, ayah) != null }
        } ?: return null
        return index.find(reciterKey, style, surah, ayah)
    }

    private companion object {
        /** Long enough for a worker to start and fetch one short verse; short enough to not feel frozen. */
        const val TIMEOUT_MS = 15_000L
    }
}
