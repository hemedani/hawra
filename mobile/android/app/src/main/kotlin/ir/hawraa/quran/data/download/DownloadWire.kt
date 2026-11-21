package ir.hawraa.quran.data.download

import ir.hawraa.quran.domain.download.DownloadManifest
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.ManifestFile
import ir.hawraa.quran.domain.download.ReciterInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire shapes for `reciter/gets`, `recitationEdition/gets` and
 * `recitationEdition/getDownloadManifest`.
 *
 * Every field has a default so one DTO can decode both the catalogue row and the manifest's
 * embedded edition — they are the same document with different projections, and two DTOs
 * would be two places to forget a field. Unknown keys are ignored, so a newer server adding
 * `notesFa` does not break an older build.
 */
@Serializable
data class EditionDto(
    val key: String = "",
    val labelFa: String = "",
    val labelEn: String = "",
    val style: String = "murattal",
    val granularity: String = "ayah",
    val bitrateKbps: Int = 128,
    val totalFiles: Int = 0,
    val availability: String = "unverified",
    val sourceName: String = "",
    val licenseNote: String? = null,
    val verifiedAt: String? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class ReciterDto(
    val key: String = "",
    val nameFa: String = "",
    val nameEn: String = "",
    val countryCode: String? = null,
    val sortOrder: Int = 100,
    val editions: List<EditionDto> = emptyList(),
)

@Serializable
data class ManifestFileDto(
    val key: String = "",
    val kind: String = "ayah",
    val surah: Int? = null,
    val ayah: Int? = null,
    val juz: Int? = null,
    val globalAyah: Int? = null,
    /** The upstream source. Always present. */
    val url: String = "",
    val relativePath: String = "",
    /**
     * Present only when *this* server holds the file. Server-relative (`/uploads/…`), resolved
     * against the same origin the app already talks to — so the manifest stays correct whether
     * the device is on the LAN, on loopback, or behind a tunnel.
     */
    val mirrorPath: String? = null,
    /** Measured size on the server. Absent when the file is not mirrored. */
    val bytes: Long? = null,
)

@Serializable
data class ManifestDto(
    val edition: EditionDto = EditionDto(),
    @SerialName("scope") val scope: String = "ayah",
    val ayahCount: Int = 0,
    val fileCount: Int = 0,
    val files: List<ManifestFileDto> = emptyList(),
)

/** Mapping between the wire DTOs and the domain types. */
object DownloadCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun reciterOf(dto: ReciterDto): ReciterInfo = ReciterInfo(
        key = dto.key,
        nameFa = dto.nameFa,
        nameEn = dto.nameEn,
        countryCode = dto.countryCode,
        sortOrder = dto.sortOrder,
        // The embedded editions do not repeat the reciter key, so it is threaded through
        // here rather than left empty — every later lookup is by reciter.
        editions = dto.editions.map { editionOf(it, dto.key) },
    )

    fun editionOf(dto: EditionDto, reciterKey: String): EditionInfo = EditionInfo(
        key = dto.key,
        reciterKey = reciterKey,
        labelFa = dto.labelFa,
        labelEn = dto.labelEn,
        style = dto.style,
        granularity = EditionGranularity.fromWire(dto.granularity),
        bitrateKbps = dto.bitrateKbps,
        totalFiles = dto.totalFiles,
        availability = dto.availability,
        sourceName = dto.sourceName,
        licenseNote = dto.licenseNote,
        verifiedAt = dto.verifiedAt,
        isDefault = dto.isDefault,
    )

    fun fileOf(dto: ManifestFileDto, serverUrl: String): ManifestFile = ManifestFile(
        key = dto.key,
        kind = EditionGranularity.fromWire(dto.kind),
        surah = dto.surah,
        ayah = dto.ayah,
        juz = dto.juz,
        globalAyah = dto.globalAyah,
        url = resolve(dto, serverUrl),
        relativePath = dto.relativePath,
        bytes = dto.bytes,
    )

    /**
     * The mirror wins when the server has the file, otherwise the upstream URL stands.
     *
     * Resolving here rather than at the call site keeps a single rule in one place: everything
     * downstream — the queue row, the fetcher, the error messages — sees one absolute URL and
     * never has to know that two sources existed.
     */
    private fun resolve(dto: ManifestFileDto, serverUrl: String): String {
        val mirror = dto.mirrorPath?.takeIf { it.isNotBlank() } ?: return dto.url
        val base = serverUrl.trimEnd('/')
        return base + if (mirror.startsWith("/")) mirror else "/$mirror"
    }

    fun manifestOf(
        dto: ManifestDto,
        scope: DownloadScope,
        reciterKey: String,
        serverUrl: String,
    ): DownloadManifest =
        DownloadManifest(
            editionKey = dto.edition.key,
            reciterKey = reciterKey,
            scope = scope,
            ayahCount = dto.ayahCount,
            fileCount = dto.fileCount,
            files = dto.files.map { fileOf(it, serverUrl) },
        )
}
