package ir.hawraa.quran.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One sitting with a preset — the unit the History screen shows.
 *
 * This row is opened by the first play and closed when the reader leaves. Play/pause/seek
 * presses **update this row**; they never insert a new one. That is the product rule
 * ("must not create a new history entry every time the user presses play or pause"), and
 * it is why the counters below sit beside a capped [eventsJson] timeline rather than being
 * replaced by it: the counters stay exact after the timeline is trimmed.
 *
 * Append-only once [endedAt] is set.
 */
@Entity(tableName = "preset_sessions")
data class PresetSessionEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    /** Null when the preset was deleted while the sitting was open; [presetName] survives. */
    val presetClientId: String? = null,
    val presetName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val activeSec: Int = 0,
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val fromPage: Int? = null,
    val toPage: Int? = null,
    val ayahsCompleted: Int = 0,
    val stepsCompleted: Int = 0,
    val playCount: Int = 0,
    val pauseCount: Int = 0,
    val seekCount: Int = 0,
    val lastEventAt: Long,
    /** `active` | `finalized` | `abandoned` — see `PresetSittingStatus`. */
    val status: String = "active",
    val reciterKey: String? = null,
    val speed: Float = 1f,
    /** Capped at 200 events; see `PresetSittingTracker.MAX_EVENTS`. */
    val eventsJson: String? = null,
    @ColumnInfo(defaultValue = "pending") val syncStatus: String = SyncStatus.PENDING.key,
    val createdAt: Long,
    val updatedAt: Long,
)
