package ir.hawraa.quran.data.sync

import ir.hawraa.quran.data.local.PresetDao
import ir.hawraa.quran.data.local.PresetEntity
import ir.hawraa.quran.data.local.PresetProgressDao
import ir.hawraa.quran.data.local.PresetSessionDao
import ir.hawraa.quran.data.local.PresetSessionEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.preset.PresetCodec
import ir.hawraa.quran.data.preset.PresetDto
import ir.hawraa.quran.data.preset.PresetSyncResponse
import ir.hawraa.quran.data.preset.ProgressEntryDto
import ir.hawraa.quran.data.preset.SyncProgressRequest
import ir.hawraa.quran.data.preset.SyncProgressResponse
import ir.hawraa.quran.data.remote.LesanClient
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Pushes presets, sittings and coverage to the backend.
 *
 * Same discipline as [ReadingSessionSync]: local-first, never blocks or fails the UI, every
 * error becomes `failed` rows plus a [Result.failure] the worker turns into a retry.
 *
 * The three streams have different merge rules, and that is the whole point:
 *
 *  - **presets** are last-write-wins on `updatedAtMs`. The server answers with the rows it
 *    *kept*, and the client adopts them — including when the client's edit lost. That is what
 *    makes editing the same preset on two devices converge instead of flapping.
 *  - **sittings** are append-only by `clientId`; a re-sent sitting is acknowledged, not merged.
 *  - **coverage** is a high-water-mark merge, so a retry is always safe.
 */
@Singleton
class PresetSync @Inject constructor(
    private val client: LesanClient,
    private val auth: AuthRepository,
    private val presetDao: PresetDao,
    private val sessionDao: PresetSessionDao,
    private val progressDao: PresetProgressDao,
    private val clock: Clock,
) {
    private val json = PresetCodec.json

    /** Runs all three streams. Returns how many rows were acknowledged in total. */
    suspend fun syncPending(): Result<Int> {
        val token = auth.token() ?: return Result.failure(IllegalStateException("not signed in"))
        var total = 0

        val presets = syncPresets(token)
        if (presets.isFailure) return Result.failure(presets.exceptionOrNull()!!)
        total += presets.getOrThrow()

        val sittings = syncSittings(token)
        if (sittings.isFailure) return Result.failure(sittings.exceptionOrNull()!!)
        total += sittings.getOrThrow()

        val coverage = syncCoverage(token)
        if (coverage.isFailure) return Result.failure(coverage.exceptionOrNull()!!)
        total += coverage.getOrThrow()

        return Result.success(total)
    }

    // ---- presets: last-write-wins --------------------------------------------

    private suspend fun syncPresets(token: String): Result<Int> {
        var acked = 0
        repeat(MAX_BATCHES) {
            val batch = presetDao.pendingForSync(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success(acked)

            val now = clock.nowMillis()
            presetDao.setSyncStatus(batch.map { it.clientId }, SyncStatus.SYNCING.key, now)

            val request = buildJsonObject {
                put(
                    "presets",
                    json.encodeToJsonElement(
                        ListSerializer(PresetDto.serializer()),
                        batch.map(::toWire),
                    ),
                )
            }
            val response = client.callAct(
                model = "preset",
                act = "syncPresets",
                set = request,
                token = token,
            )
            if (response.isFailure) {
                presetDao.setSyncStatus(batch.map { it.clientId }, SyncStatus.FAILED.key, clock.nowMillis())
                return Result.failure(response.exceptionOrNull() ?: IllegalStateException("preset sync failed"))
            }

            val parsed = runCatching {
                json.decodeFromJsonElement(PresetSyncResponse.serializer(), response.getOrThrow())
            }.getOrElse {
                presetDao.setSyncStatus(batch.map { it.clientId }, SyncStatus.FAILED.key, clock.nowMillis())
                return Result.failure(it)
            }

            // The server's copy wins, including when it beat ours. Adopting it is the only way
            // two devices converge: keeping the local row would re-send the losing edit forever.
            val kept = parsed.presets.associateBy { it.clientId }
            val stamp = clock.nowMillis()
            batch.forEach { row ->
                val winner = kept[row.clientId]
                if (winner == null) {
                    // Absent means "already had it" — the row is stored, just not echoed.
                    presetDao.setSyncStatus(listOf(row.clientId), SyncStatus.SYNCED.key, stamp)
                } else {
                    presetDao.upsert(adopt(winner, row, stamp))
                }
            }
            acked += batch.size
        }
        return Result.success(acked)
    }

    /**
     * Rewrites a stored row from the server's version. `serverId` and `createdAt` come from the
     * local row because the server does not know about them; everything else is the winner's.
     */
    private fun adopt(dto: PresetDto, row: PresetEntity, now: Long): PresetEntity = PresetEntity(
        clientId = dto.clientId,
        serverId = dto._id ?: row.serverId,
        name = dto.name,
        description = dto.description,
        stepsJson = PresetCodec.encodeSteps(PresetCodec.presetOf(dto).steps),
        reciterKey = dto.reciterKey,
        autoAdvance = dto.autoAdvance,
        ayahGapMs = dto.ayahGapMs,
        isDefault = dto.isDefault,
        isArchived = dto.isArchived,
        revision = dto.revision ?: row.revision,
        updatedAtMs = dto.updatedAtMs,
        syncStatus = SyncStatus.SYNCED.key,
        createdAt = row.createdAt,
        updatedAt = now,
    )

    // ---- sittings: append-only ------------------------------------------------

    private suspend fun syncSittings(token: String): Result<Int> {
        var acked = 0
        repeat(MAX_BATCHES) {
            val batch = sessionDao.pendingForSync(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success(acked)

            sessionDao.setSyncStatus(batch.map { it.clientId }, SyncStatus.SYNCING.key, clock.nowMillis())

            val failure = batch.firstNotNullOfOrNull { row -> pushSitting(token, row)?.let { row to it } }
            if (failure != null) {
                sessionDao.setSyncStatus(
                    batch.map { it.clientId },
                    SyncStatus.FAILED.key,
                    clock.nowMillis(),
                )
                return Result.failure(failure.second)
            }

            sessionDao.setSyncStatus(batch.map { it.clientId }, SyncStatus.SYNCED.key, clock.nowMillis())
            acked += batch.size
        }
        return Result.success(acked)
    }

    /**
     * A sitting is written with the same three calls the live player uses — `start`, one
     * `logEvent` per recorded event, `finalize` — so the server's counters are rebuilt from the
     * same rules rather than trusted from the client. The alternative (one act that accepts a
     * finished row) would need a second set of merge rules on the server for the same data.
     */
    private suspend fun pushSitting(token: String, row: PresetSessionEntity): Throwable? {
        val sitting = PresetCodec.sittingOf(
            ir.hawraa.quran.data.preset.PresetSittingDto(
                clientId = row.clientId,
                presetClientId = row.presetClientId,
                presetName = row.presetName,
                startedAt = row.startedAt,
                endedAt = row.endedAt,
                activeSec = row.activeSec,
                fromSurah = row.fromSurah,
                fromAyah = row.fromAyah,
                toSurah = row.toSurah,
                toAyah = row.toAyah,
                fromPage = row.fromPage,
                toPage = row.toPage,
                ayahsCompleted = row.ayahsCompleted,
                stepsCompleted = row.stepsCompleted,
                playCount = row.playCount,
                pauseCount = row.pauseCount,
                seekCount = row.seekCount,
                lastEventAt = row.lastEventAt,
                status = row.status,
                reciterKey = row.reciterKey,
                speed = row.speed,
                events = PresetCodec.eventsOf(row.eventsJson).map(PresetCodec::toDto),
            ),
        )

        val start = buildJsonObject {
            put("clientId", sitting.clientId)
            sitting.presetClientId?.let { put("presetClientId", it) }
            put("presetName", sitting.presetName)
            put("startedAt", sitting.startedAt)
            put("surah", sitting.fromSurah)
            put("ayah", sitting.fromAyah)
            sitting.fromPage?.let { put("page", it) }
            sitting.reciterKey?.let { put("reciterKey", it) }
            put("speed", sitting.speed)
        }
        client.callAct("presetSession", "start", start, token = token)
            .onFailure { return it }

        // Counters are cumulative on the server (`$max`), so replaying the timeline in order
        // reproduces the same end state whether this is the first attempt or the third.
        var steps = 0
        var ayahs = 0
        sitting.events.forEach { event ->
            when (event.kind.wire) {
                "step" -> steps++
                "ayah" -> ayahs++
            }
            val body = buildJsonObject {
                put("clientId", sitting.clientId)
                put("kind", event.kind.wire)
                put("at", event.at)
                put("surah", event.surah)
                put("ayah", event.ayah)
                put("stepIndex", event.stepIndex)
                put("ayahsCompleted", ayahs)
                put("stepsCompleted", steps)
                put("toSurah", event.surah)
                put("toAyah", event.ayah)
            }
            client.callAct("presetSession", "logEvent", body, token = token)
                .onFailure { return it }
        }

        val finalize = buildJsonObject {
            put("clientId", sitting.clientId)
            put("endedAt", sitting.endedAt ?: sitting.lastEventAt)
            put("activeSec", sitting.activeSec)
            put("ayahsCompleted", sitting.ayahsCompleted)
            put("stepsCompleted", sitting.stepsCompleted)
            if (sitting.status.wire == "abandoned") put("status", "abandoned")
        }
        client.callAct("presetSession", "finalize", finalize, token = token)
            .onFailure { return it }
        return null
    }

    // ---- coverage: high-water marks -------------------------------------------

    private suspend fun syncCoverage(token: String): Result<Int> {
        var acked = 0
        repeat(MAX_BATCHES) {
            val batch = progressDao.pendingForSync(BATCH_SIZE * 4)
            if (batch.isEmpty()) return Result.success(acked)

            // The act takes one preset at a time, so the batch is grouped by preset. Sending
            // two presets in one call is not possible and would be a silent data loss.
            batch.groupBy { it.presetClientId }.forEach { (presetClientId, rows) ->
                val body = buildJsonObject {
                    put("presetClientId", presetClientId)
                    putJsonArray("entries") {
                        rows.forEach { row ->
                            addJsonObject {
                                put("surah", row.surah)
                                put("ayah", row.ayah)
                                put("globalAyah", row.globalAyah)
                                row.page?.let { put("page", it) }
                                put("plays", row.plays)
                                put("listenedSec", row.listenedSec)
                                put("firstAt", row.firstAt)
                                put("lastAt", row.lastAt)
                                row.lastKind?.let { put("lastKind", it) }
                            }
                        }
                    }
                }
                val response = client.callAct(
                    model = "presetAyahProgress",
                    act = "syncProgress",
                    set = body,
                    token = token,
                )
                if (response.isFailure) {
                    progressDao.setSyncStatus(
                        presetClientId,
                        rows.map { it.globalAyah },
                        SyncStatus.FAILED.key,
                        clock.nowMillis(),
                    )
                    return Result.failure(
                        response.exceptionOrNull() ?: IllegalStateException("progress sync failed"),
                    )
                }
                val parsed = runCatching {
                    json.decodeFromJsonElement(SyncProgressResponse.serializer(), response.getOrThrow())
                }.getOrNull()
                if (parsed == null) {
                    progressDao.setSyncStatus(
                        presetClientId,
                        rows.map { it.globalAyah },
                        SyncStatus.FAILED.key,
                        clock.nowMillis(),
                    )
                    return Result.failure(IllegalStateException("unreadable progress response"))
                }
                progressDao.setSyncStatus(
                    presetClientId,
                    rows.map { it.globalAyah },
                    SyncStatus.SYNCED.key,
                    clock.nowMillis(),
                )
                acked += rows.size
            }
        }
        return Result.success(acked)
    }

    private fun toWire(row: PresetEntity): PresetDto = PresetDto(
        clientId = row.clientId,
        name = row.name,
        description = row.description,
        steps = PresetCodec.stepsOf(row.stepsJson).map(PresetCodec::toDto),
        reciterKey = row.reciterKey,
        autoAdvance = row.autoAdvance,
        ayahGapMs = row.ayahGapMs,
        isDefault = row.isDefault,
        isArchived = row.isArchived,
        updatedAtMs = row.updatedAtMs,
    )

    private companion object {
        const val BATCH_SIZE = 50
        const val MAX_BATCHES = 20
    }
}
