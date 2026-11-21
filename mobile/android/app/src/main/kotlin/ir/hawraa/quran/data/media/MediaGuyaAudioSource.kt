package ir.hawraa.quran.data.media

import ir.hawraa.quran.di.ServerUrl
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.media.GuyaAudioSource
import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.recitation.AyahNumbering
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [GuyaAudioSource]: find the verified edition for a step, then hand back a URL for
 * this verse — **disk first, then the server's own stream** — or `null` to let the caller speak the
 * text aloud.
 *
 * Why stream is enough for the first cut: the guya audio is served from the backend's own
 * `uploads/` tree (a server-relative template), so "the server can serve it now" is the normal case
 * and needs no separate download. A genuine offline copy (download-then-play, mirroring what
 * recitation does through `VerseAudioProvisioner`) is the next step; until then a previously
 * downloaded file is still preferred here, so the disk-first branch is not dead code — it is the
 * seam the offline path will fill.
 *
 * Only an **ayah-granularity** edition is used: a whole-surah or whole-juz narration cannot start at
 * verse N, exactly the rule that keeps a juz reciter out of a per-ayah preset queue.
 */
@Singleton
class MediaGuyaAudioSource @Inject constructor(
    private val catalogue: MediaCatalogueRepository,
    private val audio: LocalAudioSource,
    @ServerUrl private val serverUrl: String,
) : GuyaAudioSource {

    override suspend fun resolveUrl(
        contentType: MediaContentType,
        textKey: String?,
        surah: Int,
        ayah: Int,
    ): String? {
        val edition = catalogue.editionFor(contentType, textKey) ?: return null
        if (edition.granularity != EditionGranularity.AYAH) return null
        val global = AyahNumbering.globalAyah(surah, ayah) ?: return null

        // A guya file already on disk (keyed by the edition, style-blank) wins over the network.
        audio.current().find(reciterKey = edition.key, style = "", surah = surah, ayah = ayah)
            ?.let { return FILE_SCHEME + it }

        val url = edition.urlTemplate.replace(AYAH_PLACEHOLDER, global.toString())
        return if (url.contains(AYAH_PLACEHOLDER) || url.isBlank()) null else absolute(url)
    }

    /** A server-relative template (`/uploads/…`) resolves against the origin the app already knows. */
    private fun absolute(url: String): String =
        if (url.startsWith("/")) serverUrl.trimEnd('/') + url else url

    private companion object {
        const val FILE_SCHEME = "file://"
        const val AYAH_PLACEHOLDER = "{ayah}"
    }
}
