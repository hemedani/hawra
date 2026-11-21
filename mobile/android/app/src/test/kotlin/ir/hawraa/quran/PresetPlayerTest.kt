package ir.hawraa.quran

import ir.hawraa.quran.data.preset.PresetPlaybackListener
import ir.hawraa.quran.data.preset.PresetPlayer
import ir.hawraa.quran.data.recitation.PlaybackState
import ir.hawraa.quran.data.recitation.RecitationEngine
import ir.hawraa.quran.data.recitation.SpeechEngine
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.download.LocalAudioIndex
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.download.VerseAudioProvisioner
import ir.hawraa.quran.domain.media.GuyaAudioSource
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetCue
import ir.hawraa.quran.domain.preset.PresetSequenceBuilder
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.quran.VerseTranslation
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationTrack
import ir.hawraa.quran.domain.recitation.Reciter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preset player, driven by fake engines.
 *
 * This is where the recipe language actually becomes behaviour, so the tests assert the thing
 * the user hears: **which source makes which sound, in which order**. The fakes end a cue only
 * when the test says so, which makes the ordering deterministic without a single sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresetPlayerTest {

    // ---- fakes -------------------------------------------------------------------

    private open class FakeEngine : RecitationEngine {
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state.asStateFlow()

        /** Every URL handed to the player, in order — the audible sequence. */
        val played = mutableListOf<String>()
        var currentSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL
        var paused = false
        var cleared = false

        override fun setQueue(tracks: List<RecitationTrack>, startIndex: Int, playWhenReady: Boolean) {
            played += tracks.map { it.url }
            _state.value = PlaybackState(
                isPlaying = playWhenReady,
                index = startIndex,
                queueSize = tracks.size,
                speed = currentSpeed,
            )
        }

        override fun play() {
            paused = false
            _state.update { it.copy(isPlaying = true) }
        }

        override fun pause() {
            paused = true
            _state.update { it.copy(isPlaying = false) }
        }

        override fun seekToIndex(index: Int, play: Boolean) = Unit

        override fun setSpeed(speed: PlaybackSpeed) {
            currentSpeed = speed
            _state.update { it.copy(speed = speed) }
        }

        override fun setRepeatAyah(repeat: Boolean) = Unit

        override fun clear() {
            cleared = true
            _state.value = PlaybackState(speed = currentSpeed)
        }

        override fun release() = Unit

        fun finishCue() = _state.update { it.copy(ended = true, isPlaying = false) }
        fun failCue() = _state.update { it.copy(failed = true, isPlaying = false) }
    }

    private class FakeSpeech : SpeechEngine {
        val spoken = mutableListOf<String>()
        private var pending: (() -> Unit)? = null
        var stopped = false
        override val available: Boolean get() = true

        override suspend fun speak(text: String, languageCode: String?, speed: Float) {
            spoken += text
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                pending = { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) }
            }
        }

        override fun stop() {
            stopped = true
            pending?.invoke()
            pending = null
        }

        override fun release() = Unit

        fun finishUtterance() {
            pending?.invoke()
            pending = null
        }
    }

    private class RecordingListener : PresetPlaybackListener {
        val finished = mutableListOf<PresetCue>()
        var elapsed = mutableListOf<Long>()
        var runFinished = 0

        override fun onCueFinished(cue: PresetCue, elapsedMs: Long) {
            finished += cue
            elapsed += elapsedMs
        }

        override fun onFinished() {
            runFinished++
        }
    }

    // ---- fixtures ----------------------------------------------------------------

    private val reciter = Reciter(
        key = "test",
        nameFa = "قاری",
        nameEn = "Reciter",
        style = "murattal",
        bitrate = 48,
        urlTemplate = "https://cdn.test/{ayah}.mp3",
    )

    private val ansarian = VerseTranslation("ansarian", "ترجمه")

    private fun verse(surah: Int, ayah: Int, page: Int = 1) = VerseItem(
        page = page,
        verse = QuranVerse(
            key = "$surah:$ayah",
            surah = surah,
            ayah = ayah,
            textUthmani = "…",
            translations = listOf(ansarian),
            juz = 1,
            hizb = 1,
            rubElHizb = 1,
            sajdah = null,
        ),
    )

    /** verse ×2, its translation, then verse once more — the second recipe from the brief. */
    private val recipe = Preset(
        clientId = "p1",
        name = "آیه، ترجمه، آیه",
        steps = listOf(
            PresetStep.ayah(repeat = 2),
            PresetStep.translation("ansarian"),
            PresetStep.ayah(),
        ),
    )

    private var now = 1_000_000L

    /**
     * The player's scope must be driven by the test scheduler (so `advanceUntilIdle` runs the
     * cue loop) but must **not** be a child of the test job — a run suspended on an ayah that
     * never ends would otherwise hang `runTest`. `backgroundScope` looks like the right tool
     * and is not: its tasks are not advanced here, which silently produced a player that never
     * started.
     */
    private fun TestScope.playerScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    /**
     * The player hops to this dispatcher for every Media3 call. In production it is the main
     * thread (ExoPlayer is single-threaded); here it is a test dispatcher on the same scheduler,
     * so `advanceUntilIdle` drives it. Passing the real `Dispatchers.Main` would fail on the JVM.
     */
    private fun TestScope.mainDispatcher() = StandardTestDispatcher(testScheduler)

    private fun TestScope.player(
        engine: FakeEngine,
        speech: FakeSpeech,
        listener: PresetPlaybackListener? = null,
    ): PresetPlayer {
        val player = PresetPlayer(
            engine = engine,
            speech = speech,
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> null },
            guya = GuyaAudioSource { _, _, _, _ -> null },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(
            sequence = PresetSequenceBuilder.build(recipe, listOf(verse(1, 1))),
            presetName = recipe.name,
            reciter = reciter,
            speed = PlaybackSpeed.NORMAL,
            listener = listener,
        )
        return player
    }

    // ---- the run -----------------------------------------------------------------

    @Test
    fun a_recipe_alternates_recitation_and_speech_in_order() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val player = player(engine, speech)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        // First repetition of the verse, from the reciter.
        assertEquals(listOf("https://cdn.test/1.mp3"), engine.played)
        assertTrue(speech.spoken.isEmpty())

        engine.finishCue()
        advanceUntilIdle()
        // Second repetition: the same file, queued again.
        assertEquals(2, engine.played.size)

        engine.finishCue()
        advanceUntilIdle()
        // Now the translation is spoken.
        assertEquals(listOf(ansarian.text), speech.spoken)

        speech.finishUtterance()
        advanceUntilIdle()
        // And the verse once more.
        assertEquals(3, engine.played.size)
    }

    @Test
    fun the_run_reports_every_finished_cue_and_then_itself() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val listener = RecordingListener()
        val player = player(engine, speech, listener)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        engine.finishCue(); advanceUntilIdle()
        engine.finishCue(); advanceUntilIdle()
        speech.finishUtterance(); advanceUntilIdle()
        engine.finishCue(); advanceUntilIdle()

        assertEquals(4, listener.finished.size)
        assertEquals(1, listener.runFinished)
        assertTrue(player.state.value.finished)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun elapsed_time_is_reported_per_cue() = runTest {
        val engine = FakeEngine()
        val listener = RecordingListener()
        val player = player(engine, FakeSpeech(), listener)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        now += 3_500
        engine.finishCue()
        advanceUntilIdle()

        assertEquals(3_500L, listener.elapsed.first())
    }

    @Test
    fun a_declared_gap_is_waited_out_before_the_next_sound() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val gapped = recipe.copy(
            steps = listOf(PresetStep.ayah(gapMs = 2_000), PresetStep.translation("ansarian")),
        )
        val player = PresetPlayer(
            engine = engine,
            speech = speech,
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> null },
            guya = GuyaAudioSource { _, _, _, _ -> null },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(
            sequence = PresetSequenceBuilder.build(gapped, listOf(verse(1, 1))),
            presetName = gapped.name,
            reciter = reciter,
            speed = PlaybackSpeed.NORMAL,
        )
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        engine.finishCue()
        // The translation must not start until the gap has elapsed; virtual time proves it
        // without sleeping.
        advanceTimeBy(1_000)
        assertTrue(speech.spoken.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(ansarian.text), speech.spoken)
    }

    // ---- transport ----------------------------------------------------------------

    @Test
    fun pause_holds_the_audio_position_and_play_resumes_without_re_queueing() = runTest {
        val engine = FakeEngine()
        val player = player(engine, FakeSpeech())
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        assertEquals(1, engine.played.size)

        player.pause()
        advanceUntilIdle()
        assertTrue(engine.paused)
        assertFalse(player.state.value.isPlaying)

        player.play()
        advanceUntilIdle()
        // The same ayah continues: no second queue was built.
        assertEquals(1, engine.played.size)
        assertFalse(engine.paused)
    }

    @Test
    fun pause_during_speech_stops_the_voice_and_play_restarts_that_step() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val spokenFirst = recipe.copy(
            steps = listOf(PresetStep.translation("ansarian"), PresetStep.ayah()),
        )
        val player = PresetPlayer(
            engine = engine,
            speech = speech,
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> null },
            guya = GuyaAudioSource { _, _, _, _ -> null },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(
            sequence = PresetSequenceBuilder.build(spokenFirst, listOf(verse(1, 1))),
            presetName = spokenFirst.name,
            reciter = reciter,
            speed = PlaybackSpeed.NORMAL,
        )
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        assertEquals(1, speech.spoken.size)

        player.pause()
        advanceUntilIdle()
        assertTrue(speech.stopped)
        assertFalse(player.state.value.isPlaying)

        // TTS cannot resume mid-utterance, so play re-speaks the same step.
        player.play()
        advanceUntilIdle()
        assertEquals(2, speech.spoken.size)
    }

    @Test
    fun next_skips_the_rest_of_this_ayah_recipe() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val twoVerses = PresetSequenceBuilder.build(
            recipe,
            listOf(verse(1, 1), verse(1, 2)),
        )
        val player = PresetPlayer(
            engine = engine,
            speech = speech,
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> null },
            guya = GuyaAudioSource { _, _, _, _ -> null },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(twoVerses, recipe.name, reciter, PlaybackSpeed.NORMAL)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        player.next()
        advanceUntilIdle()

        // Verse 1 is global ayah 1; verse 2 is global ayah 2. Four cues per verse
        // (two recitations, a translation, a recitation), so verse 2 starts at index 4.
        assertEquals("https://cdn.test/2.mp3", engine.played.last())
        assertEquals(2, player.state.value.cue?.ayah)
        assertEquals(4, twoVerses.indexOfAyah(1, 2))
    }

    @Test
    fun a_failed_ayah_stops_the_run_instead_of_skipping_silently() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val player = player(engine, speech)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        engine.failCue()
        advanceUntilIdle()

        assertTrue(player.state.value.failed)
        assertFalse(player.state.value.isPlaying)
        // Nothing was spoken: a broken recitation must not be papered over by continuing.
        assertTrue(speech.spoken.isEmpty())
        assertEquals(1, engine.played.size)
    }

    @Test
    fun stop_clears_the_queue_and_restores_the_readers_own_speed() = runTest {
        val engine = FakeEngine()
        val player = player(engine, FakeSpeech())
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()
        // The recipe's default speed is 1×, but the reader had chosen 1.5×.
        engine.currentSpeed = PlaybackSpeed.ONE_AND_HALF

        player.stop()
        advanceUntilIdle()

        assertTrue(engine.cleared)
        assertEquals(PlaybackSpeed.NORMAL, engine.currentSpeed)
        assertFalse(player.state.value.active)
        assertNull(player.state.value.cue)
    }

    @Test
    fun an_empty_sequence_does_nothing_at_all() = runTest {
        val engine = FakeEngine()
        val player = PresetPlayer(
            engine,
            FakeSpeech(),
            Clock { now },
            LocalAudioSource { LocalAudioIndex.EMPTY },
            VerseAudioProvisioner { _, _, _, _ -> null },
            GuyaAudioSource { _, _, _, _ -> null },
            mainDispatcher(),
            playerScope(),
        )
        player.load(PresetSequenceBuilder.build(Preset(clientId = "x", name = "x"), emptyList()), "x", reciter, PlaybackSpeed.NORMAL)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()

        assertFalse(player.state.value.active)
        assertTrue(engine.played.isEmpty())
    }

    /**
     * The D26 behaviour: a verse that is not on disk is **provisioned** (downloaded and awaited)
     * and then played from that file, not streamed. The provisioner answers with a path; the player
     * must hand the engine a `file://` URL for it.
     */
    @Test
    fun a_verse_that_is_not_on_disk_is_provisioned_and_played_from_the_file() = runTest {
        val engine = FakeEngine()
        val player = PresetPlayer(
            engine = engine,
            speech = FakeSpeech(),
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> "/data/recitation/test/ayah-1.mp3" },
            guya = GuyaAudioSource { _, _, _, _ -> null },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(
            sequence = PresetSequenceBuilder.build(recipe, listOf(verse(1, 1))),
            presetName = recipe.name,
            reciter = reciter,
            speed = PlaybackSpeed.NORMAL,
        )
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()

        assertEquals(listOf("file:///data/recitation/test/ayah-1.mp3"), engine.played)
    }

    /**
     * The regression the device crash exposed: an engine that throws (a bad URL, a wrong-thread
     * Media3 fault) must end the run as `failed`, never propagate onto the application scope and
     * kill the process. `runTest` fails the test if the exception escapes, so a green test here
     * proves the guard holds.
     */
    @Test
    fun an_engine_that_throws_ends_the_run_as_failed_not_a_crash() = runTest {
        val speech = FakeSpeech()
        val throwing = object : FakeEngine() {
            override fun setQueue(tracks: List<RecitationTrack>, startIndex: Int, playWhenReady: Boolean) {
                throw IllegalStateException("Player accessed on the wrong thread")
            }
        }
        val player = player(throwing, speech)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()

        assertTrue(player.state.value.failed)
        assertFalse(player.state.value.isPlaying)
        // The run stopped at the broken verse rather than continuing into the translation.
        assertTrue(speech.spoken.isEmpty())
    }

    /** A single translation step — the shape a guya-audio cue is built from. */
    private val translationRecipe = Preset(
        clientId = "t",
        name = "ترجمه",
        steps = listOf(PresetStep.translation("ansarian")),
    )

    private fun TestScope.translationPlayer(
        engine: FakeEngine,
        speech: FakeSpeech,
        guyaUrl: String?,
    ): PresetPlayer {
        val player = PresetPlayer(
            engine = engine,
            speech = speech,
            clock = Clock { now },
            audio = LocalAudioSource { LocalAudioIndex.EMPTY },
            provisioner = VerseAudioProvisioner { _, _, _, _ -> null },
            guya = GuyaAudioSource { _, _, _, _ -> guyaUrl },
            main = mainDispatcher(),
            scope = playerScope(),
        )
        player.load(
            sequence = PresetSequenceBuilder.build(translationRecipe, listOf(verse(1, 1))),
            presetName = translationRecipe.name,
            reciter = reciter,
            speed = PlaybackSpeed.NORMAL,
        )
        return player
    }

    /**
     * D27b: a translation step plays the **guya audio** the backend has for the verse, not TTS.
     * The audible difference the owner asked for — a narrated translation, verse by verse.
     */
    @Test
    fun a_translation_step_plays_guya_audio_when_available_instead_of_tts() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val player = translationPlayer(engine, speech, guyaUrl = "https://srv/guya/1.mp3")
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()

        assertEquals(listOf("https://srv/guya/1.mp3"), engine.played)
        assertTrue(speech.spoken.isEmpty())
    }

    /**
     * The honest fallback: with no guya audio for the verse (the normal state until the owner drops
     * files), the step is spoken aloud by TTS rather than skipped.
     */
    @Test
    fun a_translation_step_falls_back_to_tts_when_no_guya_audio_exists() = runTest {
        val engine = FakeEngine()
        val speech = FakeSpeech()
        val player = translationPlayer(engine, speech, guyaUrl = null)
        advanceUntilIdle()

        player.play()
        advanceUntilIdle()

        assertEquals(listOf(ansarian.text), speech.spoken)
        assertTrue(engine.played.isEmpty())
    }
}
