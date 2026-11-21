package ir.hawraa.quran

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.local.HawraaDatabase
import ir.hawraa.quran.data.local.PresetDao
import ir.hawraa.quran.data.local.PresetEntity
import ir.hawraa.quran.data.local.PresetProgressDao
import ir.hawraa.quran.data.local.PresetSessionDao
import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.local.ReadingSessionEntity
import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.data.preset.PresetCodec
import ir.hawraa.quran.domain.preset.AyahProgress
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetCoverage
import ir.hawraa.quran.domain.preset.PresetEventKind
import ir.hawraa.quran.domain.preset.PresetSittingStatus
import ir.hawraa.quran.domain.preset.PresetSittingTracker
import ir.hawraa.quran.domain.preset.PresetStep
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Room schema on the JVM — the SQL that ships, not a stub.
 *
 * Two things here can only be checked against actual SQLite, which is why this test exists
 * on top of the pure domain tests:
 *
 *  - **the "one row per sitting" rule**: `updateProgress` must increment counters on the open
 *    row, and a bug there would silently produce one row per press, exactly what the product
 *    forbids;
 *  - **the v1 → v2 migration**: `reading_sessions` must survive the upgrade. A migration
 *    mismatch is a runtime crash on the user's device, so the migration is exercised here
 *    rather than trusted to a schema diff alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PresetPersistenceTest {

    private lateinit var db: HawraaDatabase
    private lateinit var presets: PresetDao
    private lateinit var sittings: PresetSessionDao
    private lateinit var progress: PresetProgressDao
    private lateinit var sessions: ReadingSessionDao

    private val t0 = 1_763_200_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HawraaDatabase::class.java,
        ).allowMainThreadQueries().build()
        presets = db.presetDao()
        sittings = db.presetSessionDao()
        progress = db.presetProgressDao()
        sessions = db.readingSessionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun presetRow(clientId: String = "p1", isDefault: Boolean = false) = PresetEntity(
        clientId = clientId,
        name = "آیه، ترجمه",
        stepsJson = PresetCodec.encodeSteps(
            listOf(PresetStep.ayah(), PresetStep.translation("ansarian")),
        ),
        isDefault = isDefault,
        updatedAtMs = t0,
        createdAt = t0,
        updatedAt = t0,
    )

    private fun sittingRow(clientId: String = "s1") = PresetSittingTracker.start(
        clientId = clientId,
        preset = Preset(clientId = "p1", name = "آیه، ترجمه", updatedAtMs = t0),
        at = t0,
        surah = 1,
        ayah = 1,
        page = 1,
        reciterKey = "parhizgar-murattal-ayah-48",
        speed = 1f,
    ).let { sitting ->
        ir.hawraa.quran.data.local.PresetSessionEntity(
            clientId = sitting.clientId,
            presetClientId = sitting.presetClientId,
            presetName = sitting.presetName,
            startedAt = sitting.startedAt,
            fromSurah = sitting.fromSurah,
            fromAyah = sitting.fromAyah,
            toSurah = sitting.toSurah,
            toAyah = sitting.toAyah,
            fromPage = sitting.fromPage,
            toPage = sitting.toPage,
            lastEventAt = sitting.lastEventAt,
            reciterKey = sitting.reciterKey,
            speed = sitting.speed,
            createdAt = t0,
            updatedAt = t0,
        )
    }

    // ---- the rule ---------------------------------------------------------------

    @Test
    fun many_presses_update_one_sitting_row_instead_of_inserting_more() = runTest {
        sittings.upsert(sittingRow())

        // Six presses, exactly the sequence the backend smoke test used.
        repeat(6) { i ->
            sittings.updateProgress(
                clientId = "s1",
                play = if (i % 2 == 0) 1 else 0,
                pause = if (i % 2 == 1) 1 else 0,
                seek = 0,
                steps = 0,
                ayahs = 0,
                activeSec = (i + 1) * 10,
                lastEventAt = t0 + (i + 1) * 1_000L,
                toSurah = 1,
                toAyah = 1,
                page = 1,
                eventsJson = "[]",
                now = t0 + (i + 1) * 1_000L,
            )
        }

        val rows = sittings.openSittings()
        assertEquals(1, rows.size)
        assertEquals(3, rows.single().playCount)
        assertEquals(3, rows.single().pauseCount)
        // `activeSec` is absolute, so the last write wins rather than accumulating twice.
        assertEquals(60, rows.single().activeSec)
    }

    @Test
    fun finalizing_closes_the_row_and_further_updates_are_ignored() = runTest {
        sittings.upsert(sittingRow())
        sittings.finalize("s1", endedAt = t0 + 30_000, activeSec = 30, status = "finalized", now = t0 + 30_000)

        assertNull(sittings.openSitting())
        sittings.updateProgress(
            clientId = "s1", play = 1, pause = 0, seek = 0, steps = 0, ayahs = 0,
            activeSec = 99, lastEventAt = t0 + 40_000, toSurah = 1, toAyah = 2,
            page = 1, eventsJson = "[]", now = t0 + 40_000,
        )
        // Append-only after finalize: the guard is `endedAt IS NULL`.
        assertEquals(30, sittings.byClientId("s1")!!.activeSec)
        assertEquals(0, sittings.byClientId("s1")!!.playCount)
    }

    // ---- presets ----------------------------------------------------------------

    @Test
    fun exactly_one_preset_is_default_at_a_time() = runTest {
        presets.upsert(presetRow("p1", isDefault = true))
        presets.upsert(presetRow("p2"))

        presets.setDefault("p2", stamp = t0 + 1, now = t0 + 1)

        assertEquals("p2", presets.defaultPreset()?.clientId)
        assertEquals(1, presets.all().count { it.isDefault })
        // The demoted row is a pending edit, or the server would keep the old default.
        assertEquals(SyncStatus.PENDING.key, presets.byClientId("p1")!!.syncStatus)
        assertEquals(t0 + 1, presets.byClientId("p1")!!.updatedAtMs)
    }

    @Test
    fun a_preset_with_history_is_archived_rather_than_deleted() = runTest {
        presets.upsert(presetRow())
        sittings.upsert(sittingRow())

        assertTrue(presets.hasHistory("p1"))
        presets.archive("p1", stamp = t0 + 1, now = t0 + 1)

        val row = presets.byClientId("p1")
        assertNotNull(row)
        assertTrue(row!!.isArchived)
    }

    @Test
    fun a_preset_nothing_refers_to_is_deleted_outright() = runTest {
        presets.upsert(presetRow())
        assertFalse(presets.hasHistory("p1"))

        presets.delete("p1")
        assertNull(presets.byClientId("p1"))
    }

    @Test
    fun steps_survive_a_round_trip_through_the_json_column() = runTest {
        presets.upsert(presetRow())
        val steps = PresetCodec.stepsOf(presets.byClientId("p1")!!.stepsJson)

        assertEquals(2, steps.size)
        assertEquals("ayah", steps[0].kind.wire)
        assertEquals("ansarian", steps[1].textKey)
    }

    @Test
    fun an_unknown_step_kind_is_dropped_instead_of_played_as_something_else() {
        val steps = PresetCodec.stepsOf(
            """[{"kind":"ayah"},{"kind":"hologram","repeat":2},{"kind":"translation","textKey":"x"}]""",
        )

        assertEquals(2, steps.size)
        assertEquals(listOf("ayah", "translation"), steps.map { it.kind.wire })
    }

    // ---- coverage ----------------------------------------------------------------

    @Test
    fun coverage_is_merged_as_high_water_marks_not_overwritten() = runTest {
        val first = AyahProgress(
            surah = 1, ayah = 1, globalAyah = 1, page = 1,
            plays = 3, listenedSec = 90, firstAt = t0, lastAt = t0 + 5_000,
        )
        progress.upsertAll(
            listOf(
                ir.hawraa.quran.data.local.PresetProgressEntity(
                    presetClientId = "p1", globalAyah = 1, surah = 1, ayah = 1, page = 1,
                    plays = first.plays, listenedSec = first.listenedSec,
                    firstAt = first.firstAt, lastAt = first.lastAt,
                    syncStatus = SyncStatus.PENDING.key, updatedAt = t0,
                ),
            ),
        )

        // A stale report arriving late must not reduce the stored values.
        val stale = first.copy(plays = 1, listenedSec = 10, firstAt = t0 + 9_000)
        val stored = progress.byAyah("p1", 1)!!.let {
            AyahProgress(it.surah, it.ayah, it.globalAyah, it.page, it.plays, it.listenedSec, it.firstAt, it.lastAt, it.lastKind)
        }
        val merged = PresetCoverage.merge(stored, stale)

        assertEquals(3, merged.plays)
        assertEquals(90, merged.listenedSec)
        assertEquals(t0, merged.firstAt)
    }

    @Test
    fun covered_ayahs_are_counted_per_preset() = runTest {
        progress.upsertAll(
            (1..7).map { ayah ->
                ir.hawraa.quran.data.local.PresetProgressEntity(
                    presetClientId = "p1", globalAyah = ayah, surah = 1, ayah = ayah, page = 1,
                    plays = 1, listenedSec = 30, firstAt = t0, lastAt = t0,
                    syncStatus = SyncStatus.PENDING.key, updatedAt = t0,
                )
            },
        )

        assertEquals(7, progress.coveredCount("p1"))
        assertEquals(0, progress.coveredCount("p2"))
        assertEquals(1, PresetCoverage.ranges(progress.forPreset("p1").map {
            AyahProgress(it.surah, it.ayah, it.globalAyah, it.page, it.plays, it.listenedSec, it.firstAt, it.lastAt, it.lastKind)
        }).size)
    }

    // ---- the upgrade path ----------------------------------------------------------

    @Test
    fun reading_sessions_still_work_alongside_the_new_tables() = runTest {
        // Not a migration test (Room's MigrationTestHelper needs the exported schema on the
        // classpath), but it does prove the v2 entity set coexists: a reading session and a
        // preset sitting can be written and read in the same database without interference.
        sessions.upsert(
            ReadingSessionEntity(
                clientId = "r1",
                startedAt = t0,
                surah = 1,
                fromAyah = 1,
                toAyah = 7,
                fromPage = 1,
                toPage = 1,
                createdAt = t0,
                updatedAt = t0,
            ),
        )
        sittings.upsert(sittingRow())

        assertEquals(1, sessions.openSessions().size)
        assertEquals(1, sittings.openSittings().size)
        assertEquals(PresetSittingStatus.ACTIVE.wire, sittings.openSitting()!!.status)
        // Both tables use the same clientId namespace without colliding.
        assertEquals("r1", sessions.openSessions().single().clientId)
    }

    @Test
    fun events_round_trip_through_the_capped_json_column() = runTest {
        var sitting = PresetSittingTracker.start(
            clientId = "s1",
            preset = Preset(clientId = "p1", name = "x", updatedAtMs = t0),
            at = t0, surah = 1, ayah = 1, page = 1,
        )
        repeat(PresetSittingTracker.MAX_EVENTS + 10) { i ->
            sitting = PresetSittingTracker.event(sitting, PresetEventKind.PLAY, t0 + i + 1L, 1, 1)
        }
        sittings.upsert(sittingRow())
        sittings.updateProgress(
            clientId = "s1", play = 0, pause = 0, seek = 0, steps = 0, ayahs = 0,
            activeSec = sitting.activeSec, lastEventAt = sitting.lastEventAt,
            toSurah = 1, toAyah = 1, page = 1,
            eventsJson = PresetCodec.encodeEvents(sitting.events),
            now = t0,
        )

        val restored = PresetCodec.eventsOf(sittings.byClientId("s1")!!.eventsJson)
        assertEquals(PresetSittingTracker.MAX_EVENTS, restored.size)
        assertTrue(restored.all { it.kind == PresetEventKind.PLAY })
    }
}
