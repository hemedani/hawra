package ir.hawraa.quran.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recitation preset, as stored on the device.
 *
 * [stepsJson] holds the ordered step list as JSON rather than a child table. A preset is
 * always read, written and played as a whole — there is no query that wants "step 3 of some
 * preset" — so a child table would buy nothing and cost a join plus ordering bookkeeping on
 * every read. The trade-off is that steps cannot be indexed; nothing needs them to be.
 *
 * [updatedAtMs] is the **last-write-wins stamp** the backend compares (see
 * `back/AGENTS.md` §6), and it is deliberately *not* the same thing as [updatedAt], which is
 * this device's bookkeeping time. Mixing them up would let a device with a slow clock
 * silently win every conflict.
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val name: String,
    /** Optional note shown under the name; kept because the server has it and a round trip
     *  through two devices must not silently erase what one of them wrote. */
    val description: String? = null,
    val stepsJson: String,
    val reciterKey: String? = null,
    val autoAdvance: Boolean = true,
    val ayahGapMs: Int = 0,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val revision: Int = 1,
    val updatedAtMs: Long,
    @ColumnInfo(defaultValue = "pending") val syncStatus: String = SyncStatus.PENDING.key,
    val createdAt: Long,
    val updatedAt: Long,
)
