package ir.hawraa.quran.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally recorded reading session. Source of truth until synced.
 *
 * [clientId] is a stable UUID minted on the device and doubles as the idempotency
 * key on the server — a retried sync must never create a duplicate. Timestamps
 * are epoch milliseconds; durations are integer seconds. Sessions are
 * append-only once finalized ([endedAt] set).
 */
@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val fromPage: Int? = null,
    val toPage: Int? = null,
    val durationReadSec: Int = 0,
    val completed: Boolean = false,
    val interrupted: Boolean = false,
    val mode: String = "read",
    val deviceInfo: String? = null,
    /** JSON snapshot of the reading settings in effect at open time. */
    val settingsJson: String? = null,
    @ColumnInfo(defaultValue = "pending") val syncStatus: String = SyncStatus.PENDING.key,
    val createdAt: Long,
    val updatedAt: Long,
)