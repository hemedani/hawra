package ir.hawraa.quran.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One downloadable audio file, and whether it is on disk.
 *
 * Rows are created from a **server-built manifest**, not from a URL the client invents: the
 * backend owns the knowledge of which edition is per-ayah, per-surah or per-juz and how its
 * file names are built (`back/src/recitationEditions/buildManifest.ts`). The client's job is
 * only to fetch what it is told and remember what it got, which is why [fileKey] and [url]
 * are both stored rather than derived.
 *
 * Keyed by `(editionKey, fileKey)` so re-requesting an already-downloaded scope is a no-op
 * instead of a second copy. [bytes] is what the storage accounting adds up.
 */
@Entity(
    tableName = "downloads",
    primaryKeys = ["editionKey", "fileKey"],
    indices = [Index("state"), Index("scopeKey")],
)
data class DownloadEntity(
    /** e.g. `parhizgar-murattal-ayah-48`. */
    val editionKey: String,
    val reciterKey: String,
    /** Server-assigned identity of the file within the edition (a path, in practice). */
    val fileKey: String,
    /** `ayah` | `surah` | `juz` | `range` — what the user asked for. */
    val scope: String,
    /** Human-meaningful grouping key, e.g. `surah-2` or `juz-01`; used to delete a batch. */
    val scopeKey: String,
    val surah: Int? = null,
    val ayah: Int? = null,
    val juz: Int? = null,
    val url: String,
    /** Path relative to the app's audio directory. Stable, so playback can resolve offline. */
    val relativePath: String,
    val bytes: Long = 0L,
    /**
     * The size the manifest promised, when it knew one.
     *
     * `null` means "unverified" — the file came from a source whose length was not declared
     * up front. A non-null value is checked after the fetch, because a chunked response has no
     * length of its own and a dropped connection is indistinguishable from a short file.
     */
    val expectedBytes: Long? = null,
    /** `queued` | `downloading` | `done` | `failed`. */
    @ColumnInfo(defaultValue = "queued") val state: String = DownloadState.QUEUED.key,
    val error: String? = null,
    val requestedAt: Long,
    val completedAt: Long? = null,
    val updatedAt: Long = requestedAt,
) {
    val isDone: Boolean get() = state == DownloadState.DONE.key
}

/**
 * The download state machine. `failed` is retryable and keeps its row, so a half-finished
 * surah does not silently vanish from the downloads screen.
 */
enum class DownloadState(val key: String) {
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    DONE("done"),
    FAILED("failed"),
    ;

    companion object {
        fun fromKey(key: String): DownloadState = entries.firstOrNull { it.key == key } ?: QUEUED
    }
}
