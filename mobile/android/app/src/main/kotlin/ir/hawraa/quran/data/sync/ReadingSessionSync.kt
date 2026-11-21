package ir.hawraa.quran.data.sync

import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.local.ReadingSessionEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.remote.LesanClient
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pushes finalized reading sessions to the backend.
 *
 * Design rules:
 *  - **never blocks or fails the UI** — every error becomes `failed` rows and a
 *    [Result.failure] the worker turns into a retry, never a crash;
 *  - **idempotent** — the server dedupes on `clientId`, so a batch may be resent after a
 *    lost response; rows are marked `syncing` before the call so a concurrent run skips
 *    them, and `failed` if the call throws;
 *  - **signed-in only** — the token comes from [AuthRepository], which hands out nothing
 *    unless a reader is signed in. Rows written before sign-in are not an error: they stay
 *    queued and go out the first time this runs with a session.
 */
@Singleton
class ReadingSessionSync @Inject constructor(
    private val client: LesanClient,
    private val auth: AuthRepository,
    private val dao: ReadingSessionDao,
    private val clock: Clock,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Pushes every queued session. Returns the number of rows acknowledged. */
    suspend fun syncPending(): Result<Int> {
        var total = 0

        repeat(MAX_BATCHES) {
            val batch = dao.pendingForSync(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success(total)

            // Checked before the rows are marked `syncing`, so a signed-out device leaves the
            // queue exactly as it found it instead of stranding a batch in `syncing`.
            val token = auth.token()
                ?: return Result.failure(IllegalStateException("not signed in"))

            dao.setSyncStatus(batch.map { it.clientId }, SyncStatus.SYNCING.key, clock.nowMillis())

            val attempt = push(token, batch)
            if (attempt.isFailure) {
                fail(batch)
                return Result.failure(
                    attempt.exceptionOrNull() ?: IllegalStateException("sync failed"),
                )
            }

            acknowledge(batch, attempt.getOrThrow())
            total += batch.size
        }
        return Result.success(total)
    }

    private suspend fun push(token: String, batch: List<ReadingSessionEntity>): Result<WireSyncResponse> =
        client.callAct(
            model = "readingSession",
            act = "syncReadingSessions",
            set = buildJsonObject {
                put(
                    "sessions",
                    json.encodeToJsonElement(
                        WireSyncRequest.serializer(),
                        WireSyncRequest(batch.map(::toWire)),
                    ),
                )
            },
            token = token,
        ).mapCatching { element ->
            json.decodeFromJsonElement(WireSyncResponse.serializer(), element)
        }

    private suspend fun acknowledge(batch: List<ReadingSessionEntity>, response: WireSyncResponse) {
        val now = clock.nowMillis()
        val mapped = response.sessions.associateBy { it.clientId }
        batch.forEach { row ->
            val serverId = mapped[row.clientId]?.serverId
            if (serverId != null) {
                dao.markSynced(row.clientId, serverId, now)
            } else {
                // Absent from the mapping means the server already had it.
                dao.setSyncStatus(listOf(row.clientId), SyncStatus.SYNCED.key, now)
            }
        }
    }

    private suspend fun fail(batch: List<ReadingSessionEntity>) {
        dao.setSyncStatus(
            batch.map { it.clientId },
            SyncStatus.FAILED.key,
            clock.nowMillis(),
        )
    }

    private fun toWire(row: ReadingSessionEntity): WireSession = WireSession(
        clientId = row.clientId,
        startedAt = row.startedAt,
        endedAt = row.endedAt,
        surah = row.surah,
        fromAyah = row.fromAyah,
        toAyah = row.toAyah,
        fromPage = row.fromPage,
        toPage = row.toPage,
        durationReadSec = row.durationReadSec,
        completed = row.completed,
        interrupted = row.interrupted,
        mode = row.mode,
        deviceInfo = row.deviceInfo,
        setting = settingOf(row.settingsJson),
    )

    /**
     * The local snapshot carries both the product's own palette choice and the fields the
     * server records; only the latter go over the wire.
     */
    private fun settingOf(snapshotJson: String?): WireSetting? {
        if (snapshotJson == null) return null
        val obj = runCatching { json.parseToJsonElement(snapshotJson).jsonObject }.getOrNull()
            ?: return null
        fun str(key: String): String? =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()
        fun int(key: String): Int? = str(key)?.toIntOrNull()
        fun double(key: String): Double? = str(key)?.toDoubleOrNull()

        val showTranslation = str("showTranslation")?.toBooleanStrictOrNull() ?: true
        return WireSetting(
            languageCode = str("languageCode"),
            theme = str("themeMode"),
            fontSize = int("fontSizeSp"),
            translationKeys = if (showTranslation) listOf(DEFAULT_TRANSLATION) else emptyList(),
            // Present only for listening sessions; null for a read session, which is what
            // the server expects (nothing was played).
            reciterKey = str("reciterKey"),
            playbackSpeed = double("playbackSpeed"),
        )
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val MAX_BATCHES = 20
        const val DEFAULT_TRANSLATION = "ansarian"
    }
}
