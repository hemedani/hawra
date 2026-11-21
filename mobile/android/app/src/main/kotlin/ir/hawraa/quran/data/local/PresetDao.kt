package ir.hawraa.quran.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(presets: List<PresetEntity>)

    @Query("SELECT * FROM presets WHERE clientId = :clientId")
    suspend fun byClientId(clientId: String): PresetEntity?

    /** What the presets screen and the reader's picker show: live presets, default first. */
    @Query(
        """
        SELECT * FROM presets
        WHERE isArchived = 0
        ORDER BY isDefault DESC, updatedAtMs DESC
        """,
    )
    fun observeActive(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY isDefault DESC, updatedAtMs DESC")
    suspend fun all(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE isDefault = 1 AND isArchived = 0 LIMIT 1")
    suspend fun defaultPreset(): PresetEntity?

    @Query("SELECT COUNT(*) FROM presets WHERE isArchived = 0")
    fun observeCount(): Flow<Int>

    /**
     * Exactly one default. Demoting everything first is not laziness — a partial update
     * would leave two defaults for as long as the two statements are apart, and a reader
     * that reads between them would have to guess.
     *
     * Both statements bump `updatedAtMs` and mark the row pending: a demoted preset is an
     * *edit* as far as the server is concerned, and a client that changed `isDefault` without
     * a new stamp would lose that change to its own older copy on the next sync.
     */
    @Transaction
    suspend fun setDefault(clientId: String, stamp: Long, now: Long) {
        clearDefaults(stamp, now)
        markDefault(clientId, stamp, now)
    }

    @Query(
        """
        UPDATE presets
        SET isDefault = 0, updatedAtMs = :stamp, syncStatus = 'pending', updatedAt = :now
        WHERE isDefault = 1
        """,
    )
    suspend fun clearDefaults(stamp: Long, now: Long)

    @Query(
        """
        UPDATE presets
        SET isDefault = 1, updatedAtMs = :stamp, syncStatus = 'pending', updatedAt = :now
        WHERE clientId = :clientId
        """,
    )
    suspend fun markDefault(clientId: String, stamp: Long, now: Long)

    /**
     * Soft delete. A preset with coverage or history is archived rather than removed, so the
     * rows that reference it keep a name to show (backend D13).
     */
    @Query("UPDATE presets SET isArchived = 1, isDefault = 0, updatedAtMs = :stamp, updatedAt = :now WHERE clientId = :clientId")
    suspend fun archive(clientId: String, stamp: Long, now: Long)

    @Query("DELETE FROM presets WHERE clientId = :clientId")
    suspend fun delete(clientId: String)

    /**
     * Whether anything still refers to this preset. Cross-table on purpose: the rule "archive
     * instead of delete when there is history" belongs to the repository that performs the
     * delete, and asking here keeps it a single statement rather than three round trips.
     */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM preset_sessions WHERE presetClientId = :clientId)
            OR EXISTS(SELECT 1 FROM preset_ayah_progress WHERE presetClientId = :clientId)
        """,
    )
    suspend fun hasHistory(clientId: String): Boolean

    @Query(
        """
        SELECT * FROM presets
        WHERE syncStatus IN ('pending', 'failed')
        ORDER BY updatedAtMs ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForSync(limit: Int): List<PresetEntity>

    @Query("UPDATE presets SET syncStatus = :status, updatedAt = :now WHERE clientId IN (:clientIds)")
    suspend fun setSyncStatus(clientIds: List<String>, status: String, now: Long)

    @Query(
        """
        UPDATE presets
        SET serverId = :serverId, syncStatus = 'synced',
            revision = :revision, updatedAtMs = :updatedAtMs, updatedAt = :now
        WHERE clientId = :clientId
        """,
    )
    suspend fun markSynced(clientId: String, serverId: String?, revision: Int, updatedAtMs: Long, now: Long)
}
