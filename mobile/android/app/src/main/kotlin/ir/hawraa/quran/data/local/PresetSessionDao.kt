package ir.hawraa.quran.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PresetSessionEntity)

    @Query("SELECT * FROM preset_sessions WHERE clientId = :clientId")
    suspend fun byClientId(clientId: String): PresetSessionEntity?

    /** The sitting currently open, if any. At most one: the reader has one preset run. */
    @Query("SELECT * FROM preset_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSitting(): PresetSessionEntity?

    @Query("SELECT * FROM preset_sessions WHERE endedAt IS NULL")
    suspend fun openSittings(): List<PresetSessionEntity>

    /** History for the History screen, newest first. */
    @Query("SELECT * FROM preset_sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<PresetSessionEntity>>

    @Query("SELECT * FROM preset_sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PresetSessionEntity>

    /**
     * Counters are incremented **in place**. This one statement is the whole "no new history
     * entry per press" rule, expressed where it can actually be enforced.
     */
    @Query(
        """
        UPDATE preset_sessions
        SET playCount = playCount + :play, pauseCount = pauseCount + :pause, seekCount = seekCount + :seek,
            stepsCompleted = stepsCompleted + :steps, ayahsCompleted = ayahsCompleted + :ayahs,
            activeSec = :activeSec, lastEventAt = :lastEventAt,
            toSurah = :toSurah, toAyah = :toAyah,
            fromPage = COALESCE(fromPage, :page), toPage = MAX(COALESCE(toPage, :page), :page),
            eventsJson = :eventsJson, syncStatus = 'pending', updatedAt = :now
        WHERE clientId = :clientId AND endedAt IS NULL
        """,
    )
    suspend fun updateProgress(
        clientId: String,
        play: Int,
        pause: Int,
        seek: Int,
        steps: Int,
        ayahs: Int,
        activeSec: Int,
        lastEventAt: Long,
        toSurah: Int,
        toAyah: Int,
        page: Int?,
        eventsJson: String,
        now: Long,
    )

    /** Append-only after this point; guarded by `endedAt IS NULL` like every in-flight update. */
    @Query(
        """
        UPDATE preset_sessions
        SET endedAt = :endedAt, activeSec = :activeSec, status = :status,
            lastEventAt = MAX(lastEventAt, :endedAt), syncStatus = 'pending', updatedAt = :now
        WHERE clientId = :clientId AND endedAt IS NULL
        """,
    )
    suspend fun finalize(clientId: String, endedAt: Long, activeSec: Int, status: String, now: Long)

    /** Finalized sittings still awaiting upload, oldest first. */
    @Query(
        """
        SELECT * FROM preset_sessions
        WHERE endedAt IS NOT NULL AND syncStatus IN ('pending', 'failed')
        ORDER BY startedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForSync(limit: Int): List<PresetSessionEntity>

    @Query("UPDATE preset_sessions SET syncStatus = :status, updatedAt = :now WHERE clientId IN (:clientIds)")
    suspend fun setSyncStatus(clientIds: List<String>, status: String, now: Long)

    @Query("UPDATE preset_sessions SET serverId = :serverId, syncStatus = 'synced', updatedAt = :now WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String?, now: Long)

    @Query("SELECT COUNT(*) FROM preset_sessions WHERE endedAt IS NOT NULL")
    fun observeCount(): Flow<Int>
}
