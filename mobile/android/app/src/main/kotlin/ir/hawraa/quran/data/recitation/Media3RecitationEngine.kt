package ir.hawraa.quran.data.recitation

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Media3/ExoPlayer playback of verse-by-verse recitation.
 *
 * **Stream, don't download** (decision D12): every ayah is fetched on demand through a
 * [CacheDataSource] backed by a size-capped [SimpleCache], so an ayah played once is
 * replayed from disk and a whole page listened to twice costs one page of data. A
 * separate "download this page" flow is not needed for the five-page reader and would
 * have to solve storage management for 6236 files.
 *
 * **One queue = one page.** The queue is a list of one-item media sources, so the
 * current item index *is* the current ayah: follow-along highlighting is driven by
 * `onMediaItemTransition`, never by a position poll. That is also why [PlaybackState]
 * has no position field.
 *
 * All methods must be called from the main thread — ExoPlayer is single-threaded by
 * contract, and the only caller is the reader's ViewModel.
 */
@Singleton
@UnstableApi
class Media3RecitationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: SimpleCache,
) : RecitationEngine {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: -1
            if (index < 0) return
            _state.update { it.copy(index = index, ended = false, failed = false) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { it.copy(buffering = true) }
                Player.STATE_READY -> _state.update { it.copy(buffering = false, failed = false) }
                Player.STATE_ENDED -> _state.update {
                    it.copy(ended = true, isPlaying = false, buffering = false)
                }

                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Surfaced as state, not thrown: a dropped connection must not crash the
            // reader, and the reader can simply press play again.
            _state.update { it.copy(failed = true, buffering = false, isPlaying = false) }
        }
    }

    /**
     * Reads through the disk cache first, then the network. `FLAG_IGNORE_CACHE_ON_ERROR`
     * matters on a device with a corrupted cache: playback degrades to streaming instead
     * of failing outright.
     */
    private val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent(USER_AGENT)
                    .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                    .setReadTimeoutMs(READ_TIMEOUT_MS)
                    .setAllowCrossProtocolRedirects(true),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * The media source factory: cache-then-network for streamed audio, direct disk reads for
     * downloaded verses.
     *
     * A downloaded ayah arrives as a `file://` URI (the local audio index prefers it over the
     * network). [SchemeRoutingDataSourceFactory] sends those to `FileDataSource`; without it the
     * HTTP loader would receive a file path and every kept verse would die with
     * `ERROR_CODE_IO_UNSPECIFIED`.
     */
    private val mediaSourceFactory: DefaultMediaSourceFactory by lazy {
        DefaultMediaSourceFactory(SchemeRoutingDataSourceFactory(cacheDataSourceFactory))
    }

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        // Speech, not music: this is what makes the system duck instead of mixing when
        // a notification arrives, and it is what the recitation should sound like.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // Pause when headphones are unplugged rather than blasting the speaker.
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { created ->
            created.addListener(listener)
            player = created
            applyRepeat(created)
            created.setPlaybackSpeed(_state.value.speed.value)
        }

    override fun setQueue(tracks: List<RecitationTrack>, startIndex: Int, playWhenReady: Boolean) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        val target = startIndex.coerceIn(0, tracks.lastIndex)
        val exo = ensurePlayer()
        exo.setMediaItems(
            tracks.map { track ->
                MediaItem.Builder()
                    .setUri(track.url)
                    .setMediaId(track.key)
                    .build()
            },
            target,
            0L,
        )
        exo.playWhenReady = playWhenReady
        // Prepare explicitly. `setMediaItems` + `playWhenReady = true` is documented to
        // auto-prepare, and on Media3 1.11.1 it reports STATE_BUFFERING synchronously — but
        // the internal thread then sits idle and the source is never loaded: nothing plays
        // and nothing errors. A real `prepare()` is the one call that demonstrably works;
        // calling it after the items are set is harmless when auto-prepare does fire.
        exo.prepare()
        _state.update {
            it.copy(
                queueSize = tracks.size,
                index = target,
                ended = false,
                failed = false,
                isPlaying = playWhenReady,
            )
        }
    }

    override fun play() {
        ensurePlayer().play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekToIndex(index: Int, play: Boolean) {
        val exo = player ?: return
        if (index !in 0 until _state.value.queueSize) return
        exo.seekToDefaultPosition(index)
        exo.playWhenReady = play
        _state.update { it.copy(index = index, ended = false, failed = false) }
    }

    override fun setSpeed(speed: PlaybackSpeed) {
        player?.setPlaybackSpeed(speed.value)
        _state.update { it.copy(speed = speed) }
    }

    override fun setRepeatAyah(repeat: Boolean) {
        _state.update { it.copy(repeatAyah = repeat) }
        player?.let(::applyRepeat)
    }

    override fun clear() {
        val exo = player
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        _state.update {
            PlaybackState(speed = it.speed, repeatAyah = it.repeatAyah)
        }
    }

    override fun release() {
        player?.removeListener(listener)
        player?.release()
        player = null
        _state.value = PlaybackState()
    }

    private fun applyRepeat(exo: ExoPlayer) {
        exo.repeatMode = if (_state.value.repeatAyah) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    // Reads the player directly, so they must run on the main thread — which is exactly where the
    // player's progress ticker lives (a leaf composable). `0` when there is no player.
    override fun currentPositionMs(): Long = player?.currentPosition ?: 0L

    override fun currentDurationMs(): Long {
        val duration = player?.duration ?: 0L
        // ExoPlayer reports C.TIME_UNSET (a huge negative) until the media is prepared.
        return if (duration > 0) duration else 0L
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val USER_AGENT = "Hawraa/1.0 (Android)"
    }
}
