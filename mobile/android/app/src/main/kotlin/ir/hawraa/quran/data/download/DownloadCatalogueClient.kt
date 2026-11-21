package ir.hawraa.quran.data.download

import ir.hawraa.quran.data.remote.LesanClient
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.download.DownloadManifest
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.ReciterInfo
import ir.hawraa.quran.di.ServerUrl
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The server side of downloading: the catalogue, and the manifest for one edition over one
 * scope.
 *
 * Read-only and stateless — everything that is remembered (which files are on disk) belongs to
 * [DownloadManager]. Keeping the two apart means a manifest can be re-fetched at any time
 * without touching the download queue, which is what makes "retry the failed files" a simple
 * operation rather than a state migration.
 */
@Singleton
class DownloadCatalogueClient @Inject constructor(
    private val client: LesanClient,
    private val auth: AuthRepository,
    @ServerUrl private val serverUrl: String,
) {

    /**
     * Every active reciter with its editions, straight from `reciter/gets`.
     *
     * Fetched rather than bundled because the catalogue is the one part of this app that
     * changes without a release: an edition is re-verified, a bitrate is added, a source dies.
     * A bundled copy would keep offering a dead URL until the next Play Store update.
     *
     * The `get` projection is not decoration: Lesan's `projectionOrPure` answers a missing
     * projection with the reciter's **pure fields only** — no embedded `editions` — and a
     * catalogue without editions renders a downloads screen with dead pickers. Asking for
     * `editions` explicitly is what puts them in the response.
     */
    suspend fun reciters(): Result<List<ReciterInfo>> {
        val token = auth.token()
            ?: return Result.failure(IllegalStateException("not signed in"))
        val get = buildJsonObject {
            put("key", 1)
            put("nameFa", 1)
            put("nameEn", 1)
            put("countryCode", 1)
            put("sortOrder", 1)
            putJsonObject("editions") {
                put("key", 1)
                put("labelFa", 1)
                put("labelEn", 1)
                put("style", 1)
                put("granularity", 1)
                put("bitrateKbps", 1)
                put("totalFiles", 1)
                put("availability", 1)
                put("sourceName", 1)
                put("licenseNote", 1)
                put("verifiedAt", 1)
                put("isDefault", 1)
            }
        }
        val response = client.callAct(
            model = "reciter",
            act = "gets",
            set = buildJsonObject { },
            get = get,
            token = token,
        )
        return response.mapCatching { body ->
            DownloadCodec.json
                .decodeFromJsonElement(ListSerializer(ReciterDto.serializer()), body)
                .map(DownloadCodec::reciterOf)
                .filter { it.key.isNotBlank() }
                .sortedWith(compareBy({ it.sortOrder }, { it.nameEn }))
        }
    }

    /**
     * The exact files to fetch. The scope decides the ayah set; the edition's granularity
     * decides how that set collapses into files, and both answers come back in one call.
     */
    suspend fun manifest(
        editionKey: String,
        reciterKey: String,
        scope: DownloadScope,
    ): Result<DownloadManifest> {
        val token = auth.token()
            ?: return Result.failure(IllegalStateException("not signed in"))

        val set = buildJsonObject {
            put("editionKey", editionKey)
            put("scope", scope.wire)
            when (scope) {
                is DownloadScope.Ayah -> {
                    put("surah", scope.surah)
                    put("ayah", scope.ayah)
                }

                is DownloadScope.Surah -> put("surah", scope.surah)
                is DownloadScope.Juz -> put("juz", scope.number)
                is DownloadScope.Range -> {
                    put("fromSurah", scope.fromSurah)
                    put("fromAyah", scope.fromAyah)
                    put("toSurah", scope.toSurah)
                    put("toAyah", scope.toAyah)
                }
            }
        }

        val response = client.callAct(
            model = "recitationEdition",
            act = "getDownloadManifest",
            set = set,
            token = token,
        )
        return response.mapCatching { body ->
            val dto = DownloadCodec.json.decodeFromJsonElement(ManifestDto.serializer(), body)
            DownloadCodec.manifestOf(dto, scope, reciterKey, serverUrl)
        }
    }
}
