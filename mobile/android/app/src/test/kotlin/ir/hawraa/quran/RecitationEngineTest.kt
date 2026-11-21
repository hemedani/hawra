package ir.hawraa.quran

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.recitation.Media3RecitationEngine
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationTrack
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Media3 engine on the JVM.
 *
 * This cannot hear anything, but it *can* catch the failures that would otherwise only
 * appear on a device: a `SimpleCache` opened twice, a missing database provider, a player
 * built off the main thread, or a queue that reports the wrong index. Playback itself
 * (and the audio-focus behaviour) stays the owner's on-device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@UnstableApi
class RecitationEngineTest {

    private lateinit var cache: SimpleCache
    private lateinit var engine: Media3RecitationEngine
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cacheDir = File(context.cacheDir, "recitation-test").apply { mkdirs() }
        cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(8L * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        )
        engine = Media3RecitationEngine(context, cache)
    }

    @After
    fun tearDown() {
        engine.release()
        cache.release()
        cacheDir.deleteRecursively()
    }

    private fun tracks(count: Int) = (1..count).map { ayah ->
        RecitationTrack(
            surah = 1,
            ayah = ayah,
            globalAyah = ayah,
            page = 1,
            url = "https://example.invalid/$ayah.mp3",
        )
    }

    @Test
    fun starts_idle() {
        val state = engine.state.value
        assertFalse(state.hasQueue)
        assertFalse(state.isPlaying)
        assertEquals(-1, state.index)
        assertEquals(PlaybackSpeed.NORMAL, state.speed)
    }

    @Test
    fun setting_a_queue_reports_the_start_index_without_playing() {
        engine.setQueue(tracks(5), startIndex = 2, playWhenReady = false)

        val state = engine.state.value
        assertTrue(state.hasQueue)
        assertEquals(5, state.queueSize)
        assertEquals(2, state.index)
        assertFalse(state.isPlaying)
        assertFalse(state.ended)
    }

    @Test
    fun start_index_is_clamped_into_the_queue() {
        engine.setQueue(tracks(3), startIndex = 99, playWhenReady = false)
        assertEquals(2, engine.state.value.index)

        engine.setQueue(tracks(3), startIndex = -4, playWhenReady = false)
        assertEquals(0, engine.state.value.index)
    }

    @Test
    fun speed_and_repeat_survive_a_cleared_queue() {
        engine.setSpeed(PlaybackSpeed.ONE_AND_HALF)
        engine.setRepeatAyah(true)
        engine.setQueue(tracks(2), startIndex = 0, playWhenReady = false)

        engine.clear()

        val state = engine.state.value
        assertFalse(state.hasQueue)
        assertEquals(-1, state.index)
        // Preferences, not queue state: clearing must not reset the reader's choice.
        assertEquals(PlaybackSpeed.ONE_AND_HALF, state.speed)
        assertTrue(state.repeatAyah)
    }

    @Test
    fun an_empty_queue_clears_instead_of_crashing() {
        engine.setQueue(tracks(2), startIndex = 0, playWhenReady = false)
        engine.setQueue(emptyList(), startIndex = 0, playWhenReady = true)
        assertFalse(engine.state.value.hasQueue)
    }

    @Test
    fun seeking_outside_the_queue_is_ignored() {
        engine.setQueue(tracks(2), startIndex = 0, playWhenReady = false)
        engine.seekToIndex(7, play = false)
        assertEquals(0, engine.state.value.index)
    }

    @Test
    fun release_is_idempotent() {
        engine.setQueue(tracks(2), startIndex = 0, playWhenReady = false)
        engine.release()
        engine.release()
        assertFalse(engine.state.value.hasQueue)
        // A released engine can be brought back: the singleton outlives the screen.
        engine.setQueue(tracks(2), startIndex = 1, playWhenReady = false)
        assertEquals(1, engine.state.value.index)
    }
}
