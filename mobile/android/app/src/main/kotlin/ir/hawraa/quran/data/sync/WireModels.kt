package ir.hawraa.quran.data.sync

import kotlinx.serialization.Serializable

/**
 * Wire shapes for the reading-session act the sync engine calls. Kept separate from the
 * Room entity on purpose: the server contract may evolve independently of local
 * storage, and a mapping layer is cheaper than a migration.
 *
 * Field names mirror `back/src/sessions/syncReadingSessions/syncReadingSessions.val.ts`
 * exactly.
 */
@Serializable
data class WireSetting(
    val languageCode: String? = null,
    val theme: String? = null,
    val fontSize: Int? = null,
    val translationKeys: List<String> = emptyList(),
    val reciterKey: String? = null,
    val playbackSpeed: Double? = null,
)

@Serializable
data class WireSession(
    val clientId: String,
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
    val setting: WireSetting? = null,
)

@Serializable
data class WireSyncRequest(val sessions: List<WireSession>)

@Serializable
data class WireSyncMapping(
    val clientId: String,
    val serverId: String? = null,
)

@Serializable
data class WireSyncResponse(
    val synced: Int = 0,
    val sessions: List<WireSyncMapping> = emptyList(),
)
