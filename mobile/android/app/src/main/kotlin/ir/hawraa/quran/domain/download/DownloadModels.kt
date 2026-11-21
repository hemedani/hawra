package ir.hawraa.quran.domain.download

/**
 * The catalogue and the download scopes, mirrored from the backend
 * (`back/models/reciter.ts`, `back/models/recitationEdition.ts`,
 * `back/src/recitationEditions/buildManifest.ts`).
 *
 * Nothing here invents a URL: the *server* owns which edition is per-ayah, per-surah or
 * per-juz and how its file names are built, and the app only asks for a manifest and
 * downloads what it is told. That is why these types carry identity and shape, never a
 * template — a template on the client would be a second source of truth for the same
 * arithmetic, and the two would drift.
 *
 * Pure Kotlin: no Android, no I/O, no Room.
 */

/** One file per ayah, per surah, or per juz — a property of the source, not a preference. */
enum class EditionGranularity(val wire: String) {
    AYAH("ayah"),
    SURAH("surah"),
    JUZ("juz"),
    ;

    /**
     * Only per-ayah files can be addressed from the reader: a whole-juz MP3 has no way to
     * start at verse 17 of al-Baqarah. Surah and juz editions are still worth downloading —
     * they are how continuous offline listening works — but they cannot serve the reader's
     * per-verse queue, and pretending otherwise would play the wrong thing.
     */
    val servesPerAyahPlayback: Boolean get() = this == AYAH || this == SURAH

    companion object {
        fun fromWire(value: String?): EditionGranularity =
            entries.firstOrNull { it.wire == value } ?: AYAH
    }
}

/**
 * One audio edition of one reciter. `availability` is carried verbatim: `verified` means the
 * server probed every file, and only those may be offered for download.
 */
data class EditionInfo(
    val key: String,
    val reciterKey: String,
    val labelFa: String,
    val labelEn: String,
    val style: String,
    val granularity: EditionGranularity,
    val bitrateKbps: Int,
    val totalFiles: Int,
    val availability: String,
    val sourceName: String,
    val licenseNote: String? = null,
    val verifiedAt: String? = null,
    val isDefault: Boolean = false,
) {
    /** The server refuses to build a manifest for anything else, so the UI must not offer it. */
    val isDownloadable: Boolean get() = availability == VERIFIED

    /** Label for the UI locale, falling back to the other script rather than to a key. */
    fun displayLabel(persian: Boolean): String =
        if (persian) labelFa.ifBlank { labelEn } else labelEn.ifBlank { labelFa }

    /** e.g. `48 kbps · ayah` — the two facts that decide what a download costs. */
    fun technicalSummary(): String = "$bitrateKbps kbps · ${granularity.wire}"

    companion object {
        const val VERIFIED = "verified"
    }
}

/** A reciter with the editions the server knows about. */
data class ReciterInfo(
    val key: String,
    val nameFa: String,
    val nameEn: String,
    val countryCode: String? = null,
    val sortOrder: Int = 100,
    val editions: List<EditionInfo> = emptyList(),
) {
    fun displayName(persian: Boolean): String =
        if (persian) nameFa.ifBlank { nameEn } else nameEn.ifBlank { nameFa }

    /** Editions that may be downloaded, best first (verified, then highest bitrate). */
    val downloadable: List<EditionInfo>
        get() = editions
            .filter { it.isDownloadable }
            .sortedWith(compareByDescending<EditionInfo> { it.bitrateKbps }.thenBy { it.key })

    /** The edition to preselect: the server's default, else the best available. */
    fun preferredEdition(): EditionInfo? =
        downloadable.firstOrNull { it.isDefault } ?: downloadable.firstOrNull()
}

/**
 * What the user asked to download.
 *
 * [scopeKey] is the batch identity: it is stored on every file row, it is what "delete this
 * download" means, and it must round-trip through [parse] because the downloads screen
 * rebuilds its labels from the database rather than from memory.
 */
sealed interface DownloadScope {

    /** The `scope` field of `recitationEdition/getDownloadManifest`. */
    val wire: String

    /** Stable batch identity, e.g. `surah-2`, `juz-01`, `ayah-2:255`, `range-2:1-2:20`. */
    val scopeKey: String

    /** The surah a batch belongs to, when it has exactly one. */
    val surah: Int?

    /** One verse. The only scope that is meaningful for a per-ayah edition. */
    data class Ayah(override val surah: Int, val ayah: Int) : DownloadScope {
        override val wire: String get() = "ayah"
        override val scopeKey: String get() = "ayah-$surah:$ayah"
    }

    /** A whole surah — 286 files on a per-ayah edition, 1 on a per-surah one. */
    data class Surah(override val surah: Int) : DownloadScope {
        override val wire: String get() = "surah"
        override val scopeKey: String get() = "surah-$surah"
    }

    /** A whole juz (1…30). Spans several surahs, so it has no single [surah]. */
    data class Juz(val number: Int) : DownloadScope {
        override val wire: String get() = "juz"
        override val scopeKey: String get() = "juz-${number.toString().padStart(2, '0')}"
        override val surah: Int? get() = null
    }

    /** An inclusive range, which may straddle a surah boundary. */
    data class Range(
        val fromSurah: Int,
        val fromAyah: Int,
        val toSurah: Int,
        val toAyah: Int,
    ) : DownloadScope {
        override val wire: String get() = "range"
        override val scopeKey: String get() = "range-$fromSurah:$fromAyah-$toSurah:$toAyah"
        override val surah: Int? get() = if (fromSurah == toSurah) fromSurah else null
    }

    companion object {
        /**
         * Inverse of [scopeKey]. Returns `null` for anything unrecognised — a batch written by
         * a newer build is shown as "unknown scope" rather than mislabelled as a surah.
         */
        fun parse(scopeKey: String): DownloadScope? {
            val parts = scopeKey.split('-', limit = 2)
            if (parts.size != 2) return null
            val body = parts[1]
            return when (parts[0]) {
                "surah" -> body.toIntOrNull()?.let(::Surah)
                "juz" -> body.toIntOrNull()?.let(::Juz)
                "ayah" -> {
                    val pair = body.split(':')
                    if (pair.size != 2) return null
                    val surah = pair[0].toIntOrNull() ?: return null
                    val ayah = pair[1].toIntOrNull() ?: return null
                    Ayah(surah, ayah)
                }

                "range" -> {
                    val ends = body.split('-')
                    if (ends.size != 2) return null
                    val from = ends[0].split(':')
                    val to = ends[1].split(':')
                    if (from.size != 2 || to.size != 2) return null
                    Range(
                        fromSurah = from[0].toIntOrNull() ?: return null,
                        fromAyah = from[1].toIntOrNull() ?: return null,
                        toSurah = to[0].toIntOrNull() ?: return null,
                        toAyah = to[1].toIntOrNull() ?: return null,
                    )
                }

                else -> null
            }
        }
    }
}

/** One file to fetch, exactly as the server described it. */
data class ManifestFile(
    /** Identity within the edition: `ayah:2:3`, `surah:2`, `juz:30`. */
    val key: String,
    val kind: EditionGranularity,
    val surah: Int? = null,
    val ayah: Int? = null,
    val juz: Int? = null,
    val globalAyah: Int? = null,
    val url: String,
    /** Where to store it, relative to the app's audio root. Server-decided. */
    val relativePath: String,
    /**
     * The size the server measured for this file, when it hosts a copy.
     *
     * `null` is the normal case for a CDN-sourced file. When present it is the only way to
     * tell a finished download from a truncated one, because a chunked response carries no
     * length of its own.
     */
    val bytes: Long? = null,
)

/**
 * The server's answer to "what exactly do I fetch?".
 *
 * [ayahCount] and [fileCount] differ on purpose: a surah edition asked for surah 2 returns
 * 286 ayahs in **3 files**, and the download sheet must say so before spending the user's
 * data.
 */
data class DownloadManifest(
    val editionKey: String,
    val reciterKey: String,
    val scope: DownloadScope,
    val ayahCount: Int,
    val fileCount: Int,
    val files: List<ManifestFile>,
) {
    val isEmpty: Boolean get() = files.isEmpty()
}
