package ir.hawraa.quran.ui.recitation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the dock's player half needs to know, published by whoever owns playback.
 *
 * The dock lives in the app shell, above the navigation graph, but the recitation is owned by the
 * reader's ViewModel. Rather than let the dock reach into a screen's state — which would mean the
 * shell creating the reader, or the reader owning the chrome — the owner **publishes** here and the
 * shell observes. Same shape as the reference app's player store.
 *
 * Both scripts are carried rather than one resolved name: the app's language is a setting, not the
 * process locale, so the *view* decides which to show.
 */
data class NowPlaying(
    val surahNumber: Int,
    val surahNameFa: String,
    val surahNameEn: String,
    val ayah: Int,
    val reciterNameFa: String,
    val reciterNameEn: String,
    val isPlaying: Boolean,
    val busy: Boolean,
    /**
     * Set while a preset is running. The dock shows it instead of the reciter name, because
     * "which recipe is playing" is the more useful fact at that moment — the reciter is
     * implied by the preset.
     */
    val presetNameFa: String? = null,
    /** What the preset is doing right now (`ayah`, `translation`, `tafsir`, `silence`). */
    val stepKind: String? = null,
    /**
     * Set when the sounding file is a whole juz rather than one verse.
     *
     * The dock draws "juz N" instead of a verse number, because [ayah] would otherwise be the
     * juz's first ayah — a number the reader is not hearing the start of.
     */
    val juzNumber: Int? = null,
)

@Singleton
class NowPlayingStore @Inject constructor() {

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    /**
     * Whether the full-screen player is open. The shell hides the dock while it is true, so the
     * player covers the whole screen instead of sitting under the chrome it belongs to.
     *
     * A single boolean, not the player's content: the reader draws the player itself (it owns the
     * engine and the verses), and the shell only needs to know to get out of the way.
     */
    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    fun setExpanded(expanded: Boolean) {
        _expanded.value = expanded
    }

    /**
     * The transport commands the dock offers, bound by whichever screen owns playback.
     *
     * The dock draws play/pause and next, but the engine belongs to the reader — so the reader
     * hands these two callbacks over while it is on screen and withdraws them when it goes. Two
     * commands, not a general command bus: anything else the dock needs would be a sign the dock
     * had grown a job it should not have.
     */
    private var onToggle: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onOpenControls: (() -> Unit)? = null

    /** Null clears the player half — nothing is queued, or the reader has gone away. */
    fun publish(nowPlaying: NowPlaying?) {
        _state.value = nowPlaying
    }

    fun bindTransport(
        onToggle: () -> Unit,
        onNext: () -> Unit,
        onOpenControls: () -> Unit,
    ) {
        this.onToggle = onToggle
        this.onNext = onNext
        this.onOpenControls = onOpenControls
    }

    fun unbindTransport() {
        onToggle = null
        onNext = null
        onOpenControls = null
    }

    fun togglePlayPause() {
        onToggle?.invoke()
    }

    fun next() {
        onNext?.invoke()
    }

    /** Tapping the mini-player asks the owner to show its full controls. */
    fun openControls() {
        onOpenControls?.invoke()
    }
}
