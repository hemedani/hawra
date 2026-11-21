package ir.hawraa.quran.data.repository

import ir.hawraa.quran.data.local.PresetProgressDao
import ir.hawraa.quran.data.local.PresetProgressEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.sync.SyncScheduler
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.preset.AyahProgress
import ir.hawraa.quran.domain.preset.CoverageRange
import ir.hawraa.quran.domain.preset.PresetCoverage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the coverage card shows: how much, and which parts. */
data class PresetCoverageSummary(
    val presetClientId: String,
    val coveredAyahs: Int,
    val percent: Double,
    val ranges: List<CoverageRange>,
    val bySurah: Map<Int, Int>,
) {
    val isEmpty: Boolean get() = coveredAyahs == 0

    companion object {
        fun of(presetClientId: String, entries: List<AyahProgress>): PresetCoverageSummary =
            PresetCoverageSummary(
                presetClientId = presetClientId,
                coveredAyahs = entries.size,
                percent = PresetCoverage.percent(entries.size),
                ranges = PresetCoverage.ranges(entries),
                bySurah = PresetCoverage.bySurah(entries),
            )
    }
}

/**
 * Owns the per-preset coverage ledger.
 *
 * Writes are **absolute high-water marks**, never deltas, which is what makes the whole thing
 * safe: [recordPlaythrough] reads the stored row, computes the new value, and merges. Sending a
 * delta would double-count on every retry; sending an absolute value cannot.
 *
 * Coverage is deliberately *not* derived from the sitting rows. Sittings answer "what did I do
 * on Tuesday"; coverage answers "how much of the Quran have I heard with this preset", which
 * must survive a preset being edited, a sitting being pruned, or the same ayah being reached
 * by a different route.
 */
@Singleton
class PresetProgressRepository @Inject constructor(
    private val dao: PresetProgressDao,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) {

    /** Coverage for one preset, recomputed from the ledger. */
    fun observeCoverage(presetClientId: String): Flow<PresetCoverageSummary> =
        dao.observeForPreset(presetClientId).map { rows ->
            PresetCoverageSummary.of(presetClientId, rows.map { it.toDomain() })
        }

    suspend fun coverage(presetClientId: String): PresetCoverageSummary =
        PresetCoverageSummary.of(presetClientId, dao.forPreset(presetClientId).map { it.toDomain() })

    suspend fun coveredCount(presetClientId: String): Int = dao.coveredCount(presetClientId)

    /**
     * One completed pass through an ayah: the only thing that grows coverage.
     *
     * [listenedSec] is added to the stored total rather than replacing it, because it measures
     * *time*, not a state — two separate listens are 2× the time. [plays] is likewise
     * incremented. Both are then treated as absolute values by the merge, so the server's
     * `$max` sees a monotonically growing number.
     */
    suspend fun recordPlaythrough(
        presetClientId: String,
        surah: Int,
        ayah: Int,
        globalAyah: Int,
        page: Int?,
        listenedSec: Int,
        lastKind: String = "ayah",
    ) {
        val now = clock.nowMillis()
        val existing = dao.byAyah(presetClientId, globalAyah)?.toDomain()
        val incoming = AyahProgress(
            surah = surah,
            ayah = ayah,
            globalAyah = globalAyah,
            page = page ?: existing?.page,
            plays = (existing?.plays ?: 0) + 1,
            listenedSec = (existing?.listenedSec ?: 0) + listenedSec.coerceAtLeast(0),
            firstAt = existing?.firstAt ?: now,
            lastAt = now,
            lastKind = lastKind,
        )
        val merged = PresetCoverage.merge(existing, incoming)
        dao.upsertAll(listOf(merged.toEntity(presetClientId, now)))
        syncScheduler.requestImmediate()
    }

    /** Clears the upload flag after the server has accepted a batch. */
    suspend fun markSynced(presetClientId: String, globalAyahs: List<Int>) {
        dao.setSyncStatus(presetClientId, globalAyahs, SyncStatus.SYNCED.key, clock.nowMillis())
    }

    private fun AyahProgress.toEntity(presetClientId: String, now: Long) = PresetProgressEntity(
        presetClientId = presetClientId,
        globalAyah = globalAyah,
        surah = surah,
        ayah = ayah,
        page = page,
        plays = plays,
        listenedSec = listenedSec,
        firstAt = firstAt,
        lastAt = lastAt,
        lastKind = lastKind,
        syncStatus = SyncStatus.PENDING.key,
        updatedAt = now,
    )

    private fun PresetProgressEntity.toDomain() = AyahProgress(
        surah = surah,
        ayah = ayah,
        globalAyah = globalAyah,
        page = page,
        plays = plays,
        listenedSec = listenedSec,
        firstAt = firstAt,
        lastAt = lastAt,
        lastKind = lastKind,
    )
}
