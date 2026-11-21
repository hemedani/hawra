package ir.hawraa.quran.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.ReciterInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/**
 * The server catalogue, with an offline copy.
 *
 * Two jobs, both about not needing the network at the wrong moment:
 *
 *  - The **downloads screen** must open and show "surah 2 · Parhizgar" while the phone is in
 *    airplane mode — which is exactly when someone is looking at their downloads.
 *  - The **local audio index** needs each edition's `style` to decide whether a downloaded
 *    file may serve the reciter the reader chose. Reading that from a cache keeps the index a
 *    pure in-memory lookup instead of a query per ayah.
 *
 * The cache is a snapshot of reference data, never of user activity, so it is written only
 * after a successful fetch and a failure to read it is simply "no cache".
 */
@Singleton
class RecitationCatalogueRepository @Inject constructor(
    private val client: DownloadCatalogueClient,
    private val dataStore: DataStore<Preferences>,
) {
    private val cacheKey = stringPreferencesKey("recitation_catalogue_v1")
    private val mutex = Mutex()

    @Volatile
    private var memory: List<ReciterInfo>? = null

    /** Cached catalogue, or an empty list when the app has never been online. */
    suspend fun cached(): List<ReciterInfo> =
        memory ?: mutex.withLock { memory ?: read().also { memory = it } }

    /** Fetches the catalogue and replaces the cache. Falls back to the cache on failure. */
    suspend fun refresh(): Result<List<ReciterInfo>> {
        val fetched = client.reciters()
        val list = fetched.getOrNull()
        if (list != null && list.isNotEmpty()) {
            memory = list
            write(list)
            return fetched
        }
        val fallback = cached()
        return if (fallback.isNotEmpty()) {
            // A stale catalogue is better than an empty picker; the next refresh fixes it.
            Result.success(fallback)
        } else {
            Result.failure(fetched.exceptionOrNull() ?: IllegalStateException("empty catalogue"))
        }
    }

    /** The edition, if the cached catalogue knows it. */
    suspend fun edition(editionKey: String): EditionInfo? =
        cached().asSequence().flatMap { it.editions.asSequence() }.firstOrNull { it.key == editionKey }

    private suspend fun read(): List<ReciterInfo> {
        val raw = dataStore.data.first()[cacheKey] ?: return emptyList()
        return runCatching {
            DownloadCodec.json
                .decodeFromString(ListSerializer(ReciterDto.serializer()), raw)
                .map(DownloadCodec::reciterOf)
        }.getOrDefault(emptyList())
    }

    private suspend fun write(list: List<ReciterInfo>) {
        val dto = list.map { reciter ->
            ReciterDto(
                key = reciter.key,
                nameFa = reciter.nameFa,
                nameEn = reciter.nameEn,
                countryCode = reciter.countryCode,
                sortOrder = reciter.sortOrder,
                editions = reciter.editions.map { edition ->
                    EditionDto(
                        key = edition.key,
                        labelFa = edition.labelFa,
                        labelEn = edition.labelEn,
                        style = edition.style,
                        granularity = edition.granularity.wire,
                        bitrateKbps = edition.bitrateKbps,
                        totalFiles = edition.totalFiles,
                        availability = edition.availability,
                        sourceName = edition.sourceName,
                        licenseNote = edition.licenseNote,
                        verifiedAt = edition.verifiedAt,
                        isDefault = edition.isDefault,
                    )
                },
            )
        }
        val encoded = DownloadCodec.json.encodeToString(ListSerializer(ReciterDto.serializer()), dto)
        dataStore.edit { prefs -> prefs[cacheKey] = encoded }
    }
}
