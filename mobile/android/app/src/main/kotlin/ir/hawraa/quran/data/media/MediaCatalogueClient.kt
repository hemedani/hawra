package ir.hawraa.quran.data.media

import ir.hawraa.quran.data.remote.LesanClient
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.media.MediaEdition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The read side of the media catalogue: `mediaEdition/gets`.
 *
 * Fetched, not bundled, for the same reason the recitation catalogue is: which guya source is
 * verified changes without a release. The app keeps no URL logic of its own — it asks, then plays
 * what it is told. Stateless: whatever is remembered (which files are on disk) belongs to the
 * download layer, not here.
 */
@Singleton
class MediaCatalogueClient @Inject constructor(
    private val client: LesanClient,
    private val auth: AuthRepository,
) {
    suspend fun editions(contentType: MediaContentType? = null): Result<List<MediaEdition>> {
        val token = auth.token()
            ?: return Result.failure(IllegalStateException("not signed in"))

        val set = buildJsonObject {
            if (contentType != null) put("contentType", contentType.wire)
        }
        val response = client.callAct(
            model = "mediaEdition",
            act = "gets",
            set = set,
            token = token,
        )
        return response.mapCatching { body ->
            MediaCodec.json
                .decodeFromJsonElement(ListSerializer(MediaEditionDto.serializer()), body)
                .mapNotNull(MediaCodec::editionOf)
                .filter { it.key.isNotBlank() }
        }
    }
}
