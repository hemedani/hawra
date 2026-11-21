package ir.hawraa.quran.data.repository

import ir.hawraa.quran.data.local.PresetDao
import ir.hawraa.quran.data.local.PresetEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.local.newClientId
import ir.hawraa.quran.data.preset.PresetCodec
import ir.hawraa.quran.data.sync.SyncScheduler
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single owner of the preset list.
 *
 * Local write first, always: a preset created in a tunnel must exist immediately and sync
 * later. The room for error is in the *stamp*, not the write — [Preset.updatedAtMs] is the
 * last-write-wins token the server compares, so every mutation here bumps it. A mutation
 * that forgot to would look successful locally and then lose to a stale row on the next
 * sync, which is the hardest kind of bug to see.
 */
@Singleton
class PresetRepository @Inject constructor(
    private val dao: PresetDao,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) {

    fun observePresets(): Flow<List<Preset>> =
        dao.observeActive().map { rows -> rows.map(::toDomain) }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun presets(): List<Preset> = dao.all().map(::toDomain)

    suspend fun preset(clientId: String): Preset? = dao.byClientId(clientId)?.let(::toDomain)

    /** The preset the reader should offer first, or null when none exists yet. */
    suspend fun defaultPreset(): Preset? = dao.defaultPreset()?.let(::toDomain)

    /**
     * Creates a preset. The **first** one becomes the default, because a reader with exactly
     * one preset should not have to nominate it before play works.
     */
    suspend fun create(
        name: String,
        steps: List<PresetStep>,
        description: String? = null,
        reciterKey: String? = null,
        autoAdvance: Boolean = true,
        ayahGapMs: Int = 0,
    ): Preset {
        val now = clock.nowMillis()
        val preset = Preset(
            clientId = newClientId(now),
            name = name,
            description = description,
            steps = steps,
            reciterKey = reciterKey,
            autoAdvance = autoAdvance,
            ayahGapMs = ayahGapMs,
            isDefault = dao.defaultPreset() == null,
            updatedAtMs = now,
        )
        dao.upsert(toEntity(preset, now, SyncStatus.PENDING))
        syncScheduler.requestImmediate()
        return preset
    }

    /** Saves an edit. The stamp is taken here, not by the caller, for the reason above. */
    suspend fun update(preset: Preset): Preset {
        val now = clock.nowMillis()
        val saved = preset.copy(revision = preset.revision + 1, updatedAtMs = now)
        // Keep the server id and the original creation time: `upsert` is a REPLACE, so
        // anything not copied across is lost.
        val existing = dao.byClientId(preset.clientId)
        dao.upsert(toEntity(saved, now, SyncStatus.PENDING, existing))
        syncScheduler.requestImmediate()
        return saved
    }

    suspend fun setDefault(clientId: String) {
        val now = clock.nowMillis()
        dao.setDefault(clientId, stamp = now, now = now)
        syncScheduler.requestImmediate()
    }

    /**
     * Removes a preset. Archived rather than deleted when it has history or coverage, so the
     * rows that reference it keep a name to show (backend D13); a preset nothing refers to is
     * deleted outright, because leaving it would make the archive grow forever.
     */
    suspend fun remove(clientId: String) {
        val now = clock.nowMillis()
        if (dao.hasHistory(clientId)) {
            dao.archive(clientId, stamp = now, now = now)
            syncScheduler.requestImmediate()
        } else {
            dao.delete(clientId)
        }
    }

    private fun toDomain(row: PresetEntity): Preset = Preset(
        clientId = row.clientId,
        name = row.name,
        description = row.description,
        steps = PresetCodec.stepsOf(row.stepsJson),
        reciterKey = row.reciterKey,
        autoAdvance = row.autoAdvance,
        ayahGapMs = row.ayahGapMs,
        isDefault = row.isDefault,
        isArchived = row.isArchived,
        revision = row.revision,
        updatedAtMs = row.updatedAtMs,
    )

    private fun toEntity(
        preset: Preset,
        now: Long,
        status: SyncStatus,
        existing: PresetEntity? = null,
    ): PresetEntity = PresetEntity(
        clientId = preset.clientId,
        serverId = existing?.serverId,
        name = preset.name,
        description = preset.description,
        stepsJson = PresetCodec.encodeSteps(preset.steps),
        reciterKey = preset.reciterKey,
        autoAdvance = preset.autoAdvance,
        ayahGapMs = preset.ayahGapMs,
        isDefault = preset.isDefault,
        isArchived = preset.isArchived,
        revision = preset.revision,
        updatedAtMs = preset.updatedAtMs,
        syncStatus = status.key,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}
