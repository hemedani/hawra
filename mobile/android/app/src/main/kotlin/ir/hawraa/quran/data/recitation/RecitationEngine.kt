package ir.hawraa.quran.data.recitation

import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI and the session tracker need to know about playback. Deliberately small:
 * position is *not* here, because per-ayah tracks make the current item index the only
 * thing that changes — which is what lets follow-along highlighting work without a
 * polling loop (see the "no timers" rule in `AGENTS.md`).
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    /** Index into the queue passed to [RecitationEngine.setQueue]; `-1` when idle. */
    val index: Int = -1,
    val queueSize: Int = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val repeatAyah: Boolean = false,
    /** True while the current track is being fetched (first play of an ayah). */
    val buffering: Boolean = false,
    /** The queue played through to the end and stopped. */
    val ended: Boolean = false,
    /** The last track failed to load. Recoverable by pressing play again. */
    val failed: Boolean = false,
) {
    val hasQueue: Boolean get() = queueSize > 0
}

/**
 * Playback boundary. An interface so the reader's ViewModel can be unit-tested without
 * an audio stack, and so swapping Media3 for something else stays a data-layer change.
 */
interface RecitationEngine {

    val state: StateFlow<PlaybackState>

    /**
     * Replaces the queue and starts at [startIndex]. [playWhenReady] = false prepares
     * the first track without making a sound (used when the reader swipes pages while
     * paused, so pressing play is instant).
     */
    fun setQueue(tracks: List<RecitationTrack>, startIndex: Int, playWhenReady: Boolean)

    fun play()

    fun pause()

    fun seekToIndex(index: Int, play: Boolean = true)

    /**
     * Seeks within the current track. Used by the full-screen player's scrubber.
     *
     * Position is otherwise deliberately absent from [PlaybackState] (see [currentPositionMs]); a
     * user-initiated seek is an explicit command, not a poll. Defaults to a no-op so a fake engine
     * and a non-audio cue need not implement it.
     */
    fun seekToPosition(positionMs: Long) = Unit

    fun setSpeed(speed: PlaybackSpeed)

    fun setRepeatAyah(repeat: Boolean)

    /** Drops the queue and stops. Keeps the player (and its cache) alive. */
    fun clear()

    /** Frees the player. Called when the screen that owns playback goes away. */
    fun release()

    /**
     * Playback position in ms, for the full-screen player's progress bar only.
     *
     * Deliberately a pull, not part of [PlaybackState]: the session tracker and follow-along are
     * driven by real events (item transitions), never a poll (the "no timers" rule). A progress bar
     * is the one place a position genuinely is the fact, and it is read on a while-visible ticker in
     * a leaf composable (`docs/DESIGN.md` §15), so the dock's title row never recomposes per second.
     * Defaults to `0` so a fake engine and a non-Audio cue need not implement it.
     */
    fun currentPositionMs(): Long = 0L

    /** Duration of the current track in ms; `0` when unknown or idle. See [currentPositionMs]. */
    fun currentDurationMs(): Long = 0L
}
