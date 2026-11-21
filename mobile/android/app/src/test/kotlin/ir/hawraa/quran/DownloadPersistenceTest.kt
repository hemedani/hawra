package ir.hawraa.quran

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.local.DownloadDao
import ir.hawraa.quran.data.local.DownloadEntity
import ir.hawraa.quran.data.local.DownloadState
import ir.hawraa.quran.data.local.HawraaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The download queue's SQL, on the JVM against real SQLite.
 *
 * These four behaviours cannot be checked in the pure domain, and each of them is a way the
 * feature fails in the field rather than in a test:
 *
 *  - asking for the same scope twice must not double the rows (or the data bill);
 *  - "retry" must re-queue the failures and leave the successes alone;
 *  - deleting a batch must remove exactly that batch, not the reciter's other downloads;
 *  - an ayah lookup must prefer the verse file over a whole-surah file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DownloadPersistenceTest {

    private lateinit var db: HawraaDatabase
    private lateinit var downloads: DownloadDao

    private val t0 = 1_763_300_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HawraaDatabase::class.java,
        ).allowMainThreadQueries().build()
        downloads = db.downloadDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun asking_for_the_same_scope_twice_keeps_one_row_per_file() = runTest {
        downloads.insertIfAbsent(listOf(row("ayah:2:1"), row("ayah:2:2")))
        downloads.insertIfAbsent(listOf(row("ayah:2:1"), row("ayah:2:2"), row("ayah:2:3")))

        val rows = downloads.forBatch("surah-2", "parhizgar-murattal-ayah-48")
        assertEquals(3, rows.size)
        assertEquals(3, downloads.pendingCount())
    }

    @Test
    fun reinserting_does_not_reset_a_finished_file() = runTest {
        downloads.insertIfAbsent(listOf(row("ayah:2:1")))
        downloads.markDone("parhizgar-murattal-ayah-48", "ayah:2:1", bytes = 41_000, now = t0 + 1)

        downloads.insertIfAbsent(listOf(row("ayah:2:1")))

        val stored = downloads.byKey("parhizgar-murattal-ayah-48", "ayah:2:1")
        assertEquals(DownloadState.DONE.key, stored?.state)
        assertEquals(41_000L, stored?.bytes)
        assertEquals(0, downloads.pendingCount())
    }

    @Test
    fun retry_requeues_failures_and_leaves_successes_done() = runTest {
        downloads.insertIfAbsent(
            listOf(row("ayah:2:1"), row("ayah:2:2"), row("ayah:2:3")),
        )
        downloads.markDone("parhizgar-murattal-ayah-48", "ayah:2:1", bytes = 1, now = t0)
        downloads.setState(
            "parhizgar-murattal-ayah-48",
            "ayah:2:2",
            DownloadState.FAILED.key,
            "HTTP 404",
            t0,
        )

        downloads.requeueFailed("parhizgar-murattal-ayah-48", "surah-2", t0 + 5)

        assertEquals(DownloadState.DONE.key, downloads.byKey(EDITION, "ayah:2:1")?.state)
        assertEquals(DownloadState.QUEUED.key, downloads.byKey(EDITION, "ayah:2:2")?.state)
        // The error is cleared, or the downloads screen would keep showing a failure that is
        // already being retried.
        assertNull(downloads.byKey(EDITION, "ayah:2:2")?.error)
        assertEquals(DownloadState.QUEUED.key, downloads.byKey(EDITION, "ayah:2:3")?.state)
        assertEquals(2, downloads.pendingCount())
    }

    @Test
    fun a_batch_totals_its_files_for_the_downloads_screen() = runTest {
        downloads.insertIfAbsent(listOf(row("ayah:2:1"), row("ayah:2:2"), row("ayah:2:3")))
        downloads.markDone(EDITION, "ayah:2:1", bytes = 1_000, now = t0)
        downloads.markDone(EDITION, "ayah:2:2", bytes = 2_000, now = t0)
        downloads.setState(EDITION, "ayah:2:3", DownloadState.FAILED.key, "HTTP 500", t0)

        val batch = downloads.observeBatches().first()

        assertEquals(1, batch.size)
        assertEquals(3, batch[0].total)
        assertEquals(2, batch[0].done)
        assertEquals(1, batch[0].failed)
        assertEquals(3_000L, batch[0].bytes)
        assertTrue(!batch[0].isComplete)
    }

    @Test
    fun deleting_a_batch_leaves_the_reciters_other_downloads_alone() = runTest {
        downloads.insertIfAbsent(
            listOf(
                row("ayah:2:1"),
                row("ayah:2:2"),
                row("ayah:3:1", scopeKey = "surah-3"),
            ),
        )

        downloads.deleteBatch("surah-2", EDITION)

        assertEquals(0, downloads.forBatch("surah-2", EDITION).size)
        assertEquals(1, downloads.forBatch("surah-3", EDITION).size)
    }

    @Test
    fun a_verse_lookup_prefers_the_verse_file_over_a_whole_surah_file() = runTest {
        downloads.insertIfAbsent(
            listOf(
                row("surah:2", ayah = null),
                row("ayah:2:5", ayah = 5),
            ),
        )
        downloads.markDone(EDITION, "surah:2", bytes = 10, now = t0)
        downloads.markDone(EDITION, "ayah:2:5", bytes = 20, now = t0)

        assertEquals("ayah:2:5", downloads.covering(EDITION, surah = 2, ayah = 5)?.fileKey)
        assertEquals("surah:2", downloads.covering(EDITION, surah = 2, ayah = 6)?.fileKey)
        assertNull(downloads.covering(EDITION, surah = 3, ayah = 1))
    }

    @Test
    fun a_juz_file_is_not_offered_for_an_ayah_lookup() = runTest {
        downloads.insertIfAbsent(listOf(row("juz:1", ayah = null, surah = null, juz = 1)))
        downloads.markDone(EDITION, "juz:1", bytes = 10, now = t0)

        // A whole-juz file covers the ayah but cannot start at it, so the reader must not get it.
        assertNull(downloads.covering(EDITION, surah = 2, ayah = 1))
    }

    @Test
    fun the_declared_size_survives_the_queue() = runTest {
        downloads.insertIfAbsent(
            listOf(row("ayah:2:1", expectedBytes = 67_712), row("ayah:2:2")),
        )

        // Present when the server measured the file, absent when it did not. The absent case is
        // the one that must stay null rather than become 0: 0 would fail every download.
        assertEquals(67_712L, downloads.byKey(EDITION, "ayah:2:1")?.expectedBytes)
        assertNull(downloads.byKey(EDITION, "ayah:2:2")?.expectedBytes)
    }

    private fun row(
        fileKey: String,
        ayah: Int? = fileKey.substringAfterLast(':').toIntOrNull(),
        surah: Int? = fileKey.split(':').getOrNull(1)?.toIntOrNull(),
        juz: Int? = null,
        scopeKey: String = "surah-2",
        expectedBytes: Long? = null,
    ) = DownloadEntity(
        editionKey = EDITION,
        reciterKey = "parhizgar",
        fileKey = fileKey,
        scope = "surah",
        scopeKey = scopeKey,
        surah = surah,
        ayah = ayah,
        juz = juz,
        url = "https://cdn.test/$fileKey.mp3",
        relativePath = "$EDITION/$fileKey.mp3",
        expectedBytes = expectedBytes,
        requestedAt = t0,
    )

    private companion object {
        const val EDITION = "parhizgar-murattal-ayah-48"
    }
}
