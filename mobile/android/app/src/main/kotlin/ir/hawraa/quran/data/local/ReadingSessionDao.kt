package ir.hawraa.quran.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE clientId = :clientId")
    suspend fun byClientId(clientId: String): ReadingSessionEntity?

    /** Finalized sessions still awaiting sync, oldest first, capped per batch. */
    @Query(
        """
        SELECT * FROM reading_sessions
        WHERE endedAt IS NOT NULL AND syncStatus IN ('pending', 'failed')
        ORDER BY startedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForSync(limit: Int): List<ReadingSessionEntity>

    @Query("UPDATE reading_sessions SET syncStatus = :status, updatedAt = :now WHERE clientId IN (:clientIds)")
    suspend fun setSyncStatus(clientIds: List<String>, status: String, now: Long)

    @Query("UPDATE reading_sessions SET serverId = :serverId, syncStatus = 'synced', updatedAt = :now WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String, now: Long)

    /** Finalize an open session. Append-only after this point. */
    @Query(
        """
        UPDATE reading_sessions
        SET endedAt = :endedAt, durationReadSec = :durationReadSec,
            completed = :completed, interrupted = :interrupted,
            syncStatus = 'pending', updatedAt = :now
        WHERE clientId = :clientId AND endedAt IS NULL
        """,
    )
    suspend fun finalize(
        clientId: String,
        endedAt: Long,
        durationReadSec: Int,
        completed: Boolean,
        interrupted: Boolean,
        now: Long,
    )

    /** Persist in-flight progress for an open session (the ~10 s checkpoint). */    @Query(
        """
        UPDATE reading_sessions
        SET surah = :surah, fromAyah = :fromAyah, toAyah = :toAyah,
            fromPage = :fromPage, toPage = :toPage,
            durationReadSec = :durationReadSec, completed = :completed, updatedAt = :now
        WHERE clientId = :clientId AND endedAt IS NULL
        """,
    )
    suspend fun checkpoint(
        clientId: String,
        surah: Int,
        fromAyah: Int,
        toAyah: Int,
        fromPage: Int?,
        toPage: Int?,
        durationReadSec: Int,
        completed: Boolean,
        now: Long,
    )

    @Query("SELECT * FROM reading_sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<ReadingSessionEntity>>

    /**
     * Upgrade an open session to a listening session: the reader pressed play, so the
     * mode and the playback part of the settings snapshot change while the row is still
     * open. Guarded by `endedAt IS NULL` like every other in-flight update.
     */
    @Query(
        """
        UPDATE reading_sessions
        SET mode = :mode, settingsJson = :settingsJson, updatedAt = :now
        WHERE clientId = :clientId AND endedAt IS NULL
        """,
    )
    suspend fun setMode(clientId: String, mode: String, settingsJson: String, now: Long)
    /** Orphaned open sessions (app killed) — recovered on launch as interrupted. */
    @Query("SELECT * FROM reading_sessions WHERE endedAt IS NULL")
    suspend fun openSessions(): List<ReadingSessionEntity>
}