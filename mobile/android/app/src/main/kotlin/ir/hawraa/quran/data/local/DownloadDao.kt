package ir.hawraa.quran.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(rows: List<DownloadEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE state IN ('queued', 'downloading') ORDER BY requestedAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE editionKey = :editionKey AND fileKey = :fileKey")
    suspend fun byKey(editionKey: String, fileKey: String): DownloadEntity?

    /** What the downloads screen lists: one card per requested scope. */
    @Query(
        """
        SELECT scopeKey, editionKey, reciterKey,
               COUNT(*) AS total,
               SUM(CASE WHEN state = 'done' THEN 1 ELSE 0 END) AS done,
               SUM(CASE WHEN state = 'failed' THEN 1 ELSE 0 END) AS failed,
               SUM(bytes) AS bytes
        FROM downloads
        GROUP BY scopeKey, editionKey, reciterKey
        ORDER BY MAX(requestedAt) DESC
        """,
    )
    fun observeBatches(): Flow<List<DownloadBatch>>

    @Query("SELECT * FROM downloads WHERE scopeKey = :scopeKey AND editionKey = :editionKey")
    suspend fun forBatch(scopeKey: String, editionKey: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'done'")
    suspend fun completed(): List<DownloadEntity>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM downloads WHERE state = 'done'")
    fun observeTotalBytes(): Flow<Long>

    @Query("UPDATE downloads SET state = :state, error = :error, updatedAt = :now WHERE editionKey = :editionKey AND fileKey = :fileKey")
    suspend fun setState(editionKey: String, fileKey: String, state: String, error: String?, now: Long)

    @Query(
        """
        UPDATE downloads
        SET state = 'done', bytes = :bytes, completedAt = :now, error = NULL
        WHERE editionKey = :editionKey AND fileKey = :fileKey
        """,
    )
    suspend fun markDone(editionKey: String, fileKey: String, bytes: Long, now: Long)

    @Query("DELETE FROM downloads WHERE scopeKey = :scopeKey AND editionKey = :editionKey")
    suspend fun deleteBatch(scopeKey: String, editionKey: String)

    @Query("SELECT * FROM downloads WHERE state = 'done' AND editionKey = :editionKey")
    suspend fun completedForEdition(editionKey: String): List<DownloadEntity>

    /**
     * Puts a batch's failed files back in the queue, keeping their rows.
     *
     * This is what "retry" means here: the files that worked are still `done` and are not
     * re-fetched, and the ones that did not are tried again. Deleting and re-requesting the
     * batch would re-download everything.
     */
    @Query(
        """
        UPDATE downloads SET state = 'queued', error = NULL, updatedAt = :now
        WHERE editionKey = :editionKey AND scopeKey = :scopeKey AND state = 'failed'
        """,
    )
    suspend fun requeueFailed(editionKey: String, scopeKey: String, now: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('queued', 'downloading')")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('queued', 'downloading')")
    fun observePendingCount(): Flow<Int>

    /**
     * The file that serves `surah:ayah` for an edition, if any: an exact verse file first, then
     * a whole-surah file. Juz files are excluded — they cover the ayah but cannot start at it.
     */
    @Query(
        """
        SELECT * FROM downloads
        WHERE editionKey = :editionKey AND state = 'done' AND surah = :surah
          AND (ayah = :ayah OR ayah IS NULL)
        ORDER BY CASE WHEN ayah IS NULL THEN 1 ELSE 0 END
        LIMIT 1
        """,
    )
    suspend fun covering(editionKey: String, surah: Int, ayah: Int): DownloadEntity?
}

/**
 * A requested download scope rolled up for the downloads screen — "surah 2, Parhizgar,
 * 48 of 286 files". A projection, not a table.
 */
data class DownloadBatch(
    val scopeKey: String,
    val editionKey: String,
    val reciterKey: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    val bytes: Long,
) {
    val isComplete: Boolean get() = total > 0 && done == total
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}
