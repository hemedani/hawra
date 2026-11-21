package ir.hawraa.quran.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * How far one ayah has been covered with one preset — the coverage ledger.
 *
 * Keyed by `(presetClientId, globalAyah)` rather than by `(surah, ayah)` so that "the parts
 * I have heard" is a contiguous numeric range: consecutive ayahs coalesce into runs without
 * a surah-aware comparison at every step.
 *
 * Every counter is a **high-water mark**. The repository merges incoming values with the
 * stored ones, never assigns blindly, which is what makes a retried upload idempotent.
 *
 * No foreign key to `presets`: coverage must outlive a preset being archived or deleted, and
 * the backend keeps the same relationship loose for the same reason.
 */
@Entity(
    tableName = "preset_ayah_progress",
    primaryKeys = ["presetClientId", "globalAyah"],
    indices = [Index("presetClientId"), Index("syncStatus")],
)
data class PresetProgressEntity(
    val presetClientId: String,
    /** 1…6236, Hafs order. */
    val globalAyah: Int,
    val surah: Int,
    val ayah: Int,
    val page: Int? = null,
    val plays: Int = 0,
    val listenedSec: Int = 0,
    val firstAt: Long = 0L,
    val lastAt: Long = 0L,
    val lastKind: String? = null,
    @ColumnInfo(defaultValue = "pending") val syncStatus: String = SyncStatus.PENDING.key,
    val updatedAt: Long,
)
