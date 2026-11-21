package ir.hawraa.quran.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PresetProgressEntity>)

    @Query("SELECT * FROM preset_ayah_progress WHERE presetClientId = :presetClientId")
    suspend fun forPreset(presetClientId: String): List<PresetProgressEntity>

    /** Coverage for one preset, in mushaf order. */
    @Query(
        """
        SELECT * FROM preset_ayah_progress
        WHERE presetClientId = :presetClientId
        ORDER BY globalAyah ASC
        """,
    )
    fun observeForPreset(presetClientId: String): Flow<List<PresetProgressEntity>>

    @Query("SELECT * FROM preset_ayah_progress WHERE presetClientId = :presetClientId AND globalAyah = :globalAyah")
    suspend fun byAyah(presetClientId: String, globalAyah: Int): PresetProgressEntity?

    @Query("SELECT COUNT(*) FROM preset_ayah_progress WHERE presetClientId = :presetClientId")
    suspend fun coveredCount(presetClientId: String): Int

    /** Everything touched since the last successful upload — the coverage sync queue. */
    @Query(
        """
        SELECT * FROM preset_ayah_progress
        WHERE syncStatus IN ('pending', 'failed')
        ORDER BY presetClientId ASC, globalAyah ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForSync(limit: Int): List<PresetProgressEntity>

    @Query(
        """
        UPDATE preset_ayah_progress
        SET syncStatus = :status, updatedAt = :now
        WHERE presetClientId = :presetClientId AND globalAyah IN (:globalAyahs)
        """,
    )
    suspend fun setSyncStatus(presetClientId: String, globalAyahs: List<Int>, status: String, now: Long)

    @Query("DELETE FROM preset_ayah_progress WHERE presetClientId = :presetClientId")
    suspend fun clearForPreset(presetClientId: String)
}
