package ir.hawraa.quran.data.preset

import ir.hawraa.quran.data.recitation.RecitationEngine
import ir.hawraa.quran.data.recitation.SpeechEngine
import ir.hawraa.quran.di.ApplicationScope
import ir.hawraa.quran.di.Main
import ir.hawraa.quran.domain.Clock
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.download.VerseAudioProvisioner
import ir.hawraa.quran.domain.media.GuyaAudioSource
import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.preset.PresetCue
import ir.hawraa.quran.domain.preset.PresetSequence
import ir.hawraa.quran.domain.recitation.AyahNumbering
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationTrack
import ir.hawraa.quran.domain.recitation.Reciter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the reader and the dock need to know while a preset is running. */
data class PresetPlaybackState(
    val active: Boolean = false,
    /** Name of the preset being played, for the mini-player. */
    val presetName: String = "",
    val index: Int = -1,
    val size: Int = 0,
    val cue: PresetCue? = null,
    val isPlaying: Boolean = false,
    /** A declared gap is elapsing. The UI shows a paused-looking transport, not silence. */
    val waiting: Boolean = false,
    val finished: Boolean = false,
    val failed: Boolean = false,
) {
    val currentAyah: Pair<Int, Int>? get() = cue?.let { it.surah to it.ayah }
    val progress: Float get() = if (size <= 0) 0f else (index + 1).toFloat() / size
}

/** Told about each finished cue, so the caller can bill history and coverage. */
interface PresetPlaybackListener {
    /**
     * One cue completed: a recitation, a spoken translation, or a silence.
     *
     * [elapsedMs] is how long it actually took, which is the only honest way to record
     * listening time — an ayah's length is not derivable from the preset.
     */
    fun onCueFinished(cue: PresetCue, elapsedMs: Long)

    /** The run reached its last cue. */
    fun onFinished()
}

/**
 * Plays a [PresetSequence] — the whole recipe language, over two different sound sources.
 *
 * The design follows from one constraint: a preset alternates between a reciter's audio file
 * and speech, and those cannot share a Media3 queue. So instead of one queue that plays
 * itself, this walks the cue list and hands each cue to whichever engine can play it.
 *
 * Three deliberate choices:
 *
 *  - **Sequential and suspending, not callback-driven.** `awaitAudio` waits on the engine's
 *    state flow and `SpeechEngine.speak` suspends until the utterance ends, so the run reads
 *    top-to-bottom. No position polling and no state machine of its own.
 *  - **Pause keeps its place for audio.** Pausing during a recitation leaves the engine
 *    positioned mid-ayah and leaves the run waiting, so pressing play continues the verse
 *    instead of restarting it. Speech cannot be resumed by the platform, so a paused spoken
 *    step restarts — the one asymmetry the platform forces.
 *  - **Gaps are real waits.** A preset says "half a second between the verse and its
 *    translation", so that is a `delay`, not a poll. It is the only timer-like thing here and
 *    it is a duration, not a sampling loop.
 */
@Singleton
class PresetPlayer @Inject constructor(
    private val engine: RecitationEngine,
    private val speech: SpeechEngine,
    private val clock: Clock,
    private val audio: LocalAudioSource,
    private val provisioner: VerseAudioProvisioner,
    private val guya: GuyaAudioSource,
    @Main private val main: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PresetPlaybackState())
    val state: StateFlow<PresetPlaybackState> = _state.asStateFlow()

    private var sequence: PresetSequence = PresetSequence()
    private var reciter: Reciter? = null
    private var listener: PresetPlaybackListener? = null

    /** The reader's own speed, restored when the preset run ends. */
    private var restoreSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL

    private var job: Job? = null

    /**
     * Index whose audio has actually been handed to the engine. `-1` when the current cue is
     * not playing audio. Pause uses this to decide between "let the engine hold its position"
     * and "cancel the run" — without it, a pause during a gap would either be ignored or
     * resume audio that was never started.
     */
    private var audioStartedFor: Int = -1

    /**
     * Prepares a run. [startIndex] is a cue index — callers resolve "start at this ayah" with
     * [PresetSequence.indexOfAyah], so this class never re-implements that lookup.
     */
    fun load(
        sequence: PresetSequence,
        presetName: String,
        reciter: Reciter?,
        speed: PlaybackSpeed,
        startIndex: Int = 0,
        listener: PresetPlaybackListener? = null,
    ) {
        stop()
        this.sequence = sequence
        this.reciter = reciter
        this.listener = listener
        restoreSpeed = speed
        val start = startIndex.coerceIn(0, (sequence.size - 1).coerceAtLeast(0))
        _state.value = PresetPlaybackState(
            active = !sequence.isEmpty,
            presetName = presetName,
            index = if (sequence.isEmpty) -1 else start,
            size = sequence.size,
            cue = sequence.cueAt(start),
        )
    }

    fun play() {
        if (sequence.isEmpty) return
        if (job?.isActive == true) {
            // Resuming a paused recitation: the engine still holds the queue and its position.
            if (audioStartedFor == _state.value.index) {
                engine.play()
                _state.update { it.copy(isPlaying = true) }
            }
            return
        }
        val from = _state.value.index.takeIf { it >= 0 } ?: 0
        start(from)
    }

    fun pause() {
        val index = _state.value.index
        if (audioStartedFor == index && job?.isActive == true) {
            engine.pause()
        } else {
            // A spoken step or a gap cannot be resumed mid-way, so the run stops here and
            // `play` re-runs this cue from its beginning.
            job?.cancel()
            job = null
            speech.stop()
        }
        _state.update { it.copy(isPlaying = false) }
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    /** Skips the rest of this ayah's recipe and starts the next one. */
    fun next() {
        sequence.nextAyahIndex(_state.value.index)?.let(::seek)
    }

    fun previous() {
        sequence.previousAyahIndex(_state.value.index)?.let(::seek)
    }

    /** Jump to a specific ayah inside the run (follow-along, a tap on a verse). */
    fun seekToAyah(surah: Int, ayah: Int) {
        sequence.indexOfAyah(surah, ayah)?.let(::seek)
    }

    /** Ends the run and hands the reader back their own speed. */
    fun stop() {
        job?.cancel()
        job = null
        speech.stop()
        engine.clear()
        engine.setSpeed(restoreSpeed)
        audioStartedFor = -1
        sequence = PresetSequence()
        listener = null
        _state.value = PresetPlaybackState()
    }

    // ---- the run ----------------------------------------------------------------

    private fun seek(target: Int) {
        val wasPlaying = _state.value.isPlaying
        job?.cancel()
        job = null
        speech.stop()
        engine.clear()
        audioStartedFor = -1
        _state.update {
            it.copy(
                index = target,
                cue = sequence.cueAt(target),
                isPlaying = false,
                finished = false,
                failed = false,
            )
        }
        if (wasPlaying) start(target)
    }

    private fun start(from: Int) {
        job?.cancel()
        job = scope.launch {
            var index = from
            while (index in sequence.cues.indices) {
                val cue = sequence.cues[index]
                _state.update {
                    it.copy(index = index, cue = cue, isPlaying = true, finished = false, failed = false)
                }

                // The whole cue is guarded so a fault — a URL the engine rejects, a TTS error,
                // anything — ends the run as `failed` rather than throwing onto the application
                // scope. An uncaught throw there has no handler and would kill the process, so a
                // reader must never be able to crash the app by choosing a preset.
                val played = try {
                    if (cue.gapBeforeMs > 0) {
                        _state.update { it.copy(waiting = true) }
                        delay(cue.gapBeforeMs.toLong())
                        _state.update { it.copy(waiting = false) }
                    }

                    val startedAt = clock.nowMillis()
                    val ok = when (cue) {
                        is PresetCue.Recite -> awaitAudio(cue, index)
                        is PresetCue.Speak -> playSpeak(cue, index)
                    }
                    if (ok) listener?.onCueFinished(cue, clock.nowMillis() - startedAt)
                    ok
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }

                if (!played) {
                    _state.update { it.copy(isPlaying = false, failed = true) }
                    return@launch
                }
                index++
            }

            _state.update {
                it.copy(isPlaying = false, finished = true, index = sequence.size - 1)
            }
            listener?.onFinished()
        }
    }

    /**
     * Hands one ayah to the audio engine and waits for it to end.
     *
     * `ended`/`failed` are the only two terminal states that matter here; buffering and
     * position are the engine's business. Because the queue is a single item, "the queue
     * ended" and "this ayah ended" are the same event — the same property that makes
     * follow-along highlighting work without a poll.
     */
    private suspend fun awaitAudio(cue: PresetCue.Recite, index: Int): Boolean {
        val voice = reciter ?: return false
        return playUrl(
            url = resolveReciteUrl(voice, cue),
            surah = cue.surah,
            ayah = cue.ayah,
            globalAyah = cue.globalAyah,
            page = cue.page,
            speed = cue.speed,
            index = index,
        )
    }

    /**
     * A translation/tafsir step: play its **guya audio** when the backend has one for this verse,
     * otherwise speak the bundled text aloud (TTS). Audio wins because the owner asked to hear real
     * narrated translation/tafsir verse by verse; TTS is the honest fallback so a step is never
     * skipped silently.
     */
    private suspend fun playSpeak(cue: PresetCue.Speak, index: Int): Boolean {
        val contentType = if (cue.tafsir) MediaContentType.TAFSIR else MediaContentType.TRANSLATION
        val url = guya.resolveUrl(contentType, cue.textKey, cue.surah, cue.ayah)
        if (url == null) {
            speech.speak(cue.text, cue.languageCode, cue.speed)
            return true
        }
        val global = AyahNumbering.globalAyah(cue.surah, cue.ayah) ?: return false
        return playUrl(
            url = url,
            surah = cue.surah,
            ayah = cue.ayah,
            globalAyah = global,
            page = cue.page,
            speed = cue.speed,
            index = index,
        )
    }

    /**
     * Plays one fixed URL through the engine and waits for it to end. Shared by a recited ayah and
     * a spoken step's guya audio — both are just "play this file, then move on."
     *
     * The engine calls run under [withContext] on [main]: ExoPlayer is single-threaded and this
     * loop runs on the IO application scope, so touching the player from here without the hop is
     * what crashed the app the moment a preset started.
     */
    private suspend fun playUrl(
        url: String,
        surah: Int,
        ayah: Int,
        globalAyah: Int,
        page: Int,
        speed: Float,
        index: Int,
    ): Boolean {
        val track = RecitationTrack(
            surah = surah,
            ayah = ayah,
            globalAyah = globalAyah,
            page = page,
            url = url,
        )
        audioStartedFor = index
        withContext(main) {
            engine.setSpeed(PlaybackSpeed.fromValue(speed))
            engine.setQueue(listOf(track), startIndex = 0, playWhenReady = true)
        }
        val terminal = engine.state.first { it.ended || it.failed }
        audioStartedFor = -1
        return !terminal.failed
    }

    /**
     * Disk first, then download-and-wait, then stream — for a recited ayah.
     *
     * The reader chose this reciter to hear verse by verse, so a verse that is not yet on disk is
     * fetched and awaited before it sounds — the "auto-download while playing" behaviour. A verse
     * that cannot be provided (no per-ayah edition, offline, timed out) still plays by streaming
     * rather than stalling the run: provisioning may speed playback up, never break it.
     */
    private suspend fun resolveReciteUrl(voice: Reciter, cue: PresetCue.Recite): String {
        val remote = voice.urlFor(cue.surah, cue.ayah, cue.globalAyah)
        audio.current().find(voice.key, voice.style, cue.surah, cue.ayah)?.let { return FILE_SCHEME + it }
        val provisioned = provisioner.provision(voice.key, voice.style, cue.surah, cue.ayah)
        return provisioned?.let { FILE_SCHEME + it } ?: remote
    }

    private companion object {
        const val FILE_SCHEME = "file://"
    }
}
