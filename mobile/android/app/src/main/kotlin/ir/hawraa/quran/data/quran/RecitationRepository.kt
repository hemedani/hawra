package ir.hawraa.quran.data.quran

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.hawraa.quran.di.ServerUrl
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.recitation.ReciterGranularity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The bundled reciter catalogue, resolved. */
data class RecitationCatalog(
    val reciters: List<Reciter>,
    val defaultReciterKey: String,
) {
    /** Reciter for [key], falling back to the catalogue default, then to the first. */
    fun reciter(key: String?): Reciter? =
        reciters.firstOrNull { it.key == key }
            ?: reciters.firstOrNull { it.key == defaultReciterKey }
            ?: reciters.firstOrNull()
}

/**
 * Single owner of `assets/quran/recitations-v1.json`.
 *
 * The catalogue ships in the APK and is never refreshed from the network: the audio
 * itself is streamed, but *which* audio belongs to which ayah must not change under
 * the reader's feet mid-session. Adding a reciter is an asset change, not a release
 * of a remote config.
 *
 * One exception to "the asset holds absolute URLs": a template that begins with `/` is
 * **server-relative** and is resolved here against [serverUrl]. Shakernejad's tartil exists only as
 * juz files on this project's own mirror, so his entry points at `/uploads/recitation/…` rather
 * than hard-coding a host that differs between a laptop on the LAN and an emulator on loopback.
 */
@Singleton
class RecitationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ServerUrl private val serverUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cached: RecitationCatalog? = null

    suspend fun catalog(): RecitationCatalog =
        cached ?: mutex.withLock { cached ?: load().also { cached = it } }

    private suspend fun load(): RecitationCatalog = withContext(Dispatchers.IO) {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val dto = json.decodeFromString<RecitationCatalogDto>(raw)
        RecitationCatalog(
            reciters = dto.reciters.map { r ->
                Reciter(
                    key = r.key,
                    nameFa = r.nameFa,
                    nameEn = r.nameEn,
                    style = r.style,
                    bitrate = r.bitrate,
                    urlTemplate = absolute(r.urlTemplate),
                    granularity = ReciterGranularity.fromWire(r.granularity),
                )
            },
            defaultReciterKey = dto.defaultReciter,
        )
    }

    /** Prefixes a server-relative template with the configured origin; leaves absolute ones alone. */
    private fun absolute(template: String): String =
        if (template.startsWith("/")) serverUrl.trimEnd('/') + template else template

    private companion object {
        const val ASSET_PATH = "quran/recitations-v1.json"
    }
}
