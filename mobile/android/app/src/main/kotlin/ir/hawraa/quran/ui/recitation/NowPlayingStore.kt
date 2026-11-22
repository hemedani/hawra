package ir.hawraa.quran.ui.recitation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
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
@Immutable
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

/** A reciter the full-screen player can switch to: identity plus both scripts. */
@Immutable
data class PlayerReciter(
    val key: String,
    val nameFa: String,
    val nameEn: String,
    /** Whether this voice has per-ayah audio (a juz-only voice cannot drive verse-by-verse play). */
    val perAyah: Boolean,
)

/** A preset the full-screen player can start: identity plus a readable shape. */
@Immutable
data class PlayerPreset(
    val clientId: String,
    val nameFa: String,
    val isDefault: Boolean,
    /** Number of steps, shown as a badge so an empty preset is visible before it is played. */
    val stepCount: Int,
)

/**
 * The verse the player features on its reading surface, with the translation shown beneath it.
 *
 * Sacred text is copied verbatim from the bundled content; the player styles it and never edits it.
 */
@Immutable
data class PlayerVerse(
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val arabic: String,
    val translation: String?,
)

/**
 * The complete snapshot the full-screen player renders.
 *
 * This is a **superset** of [NowPlaying]: the dock needs a fraction of it, the sheet needs all of
 * it, and both must read one source. Raw data is carried (script pairs, numbers, verses) rather than
 * resolved display strings, so the view remains the only place that chooses a language.
 *
 * A null store state means nothing is queued; a non-null snapshot always names what is sounding.
 */
@Immutable
data class PlayerUi(
    /** The surah's number, or the juz's starting surah when [juzNumber] is set. */
    val surahNumber: Int,
    val surahNameFa: String,
    val surahNameEn: String,
    /** The verse sounding; for a juz file, the juz's first verse (the dock ignores it). */
    val ayah: Int,
    /** Non-null when a whole-juz file is sounding; the mini row and hero tile use it. */
    val juzNumber: Int? = null,
    val reciterNameFa: String,
    val reciterNameEn: String,
    /** The running preset's name, or null in plain recitation. */
    val presetNameFa: String? = null,
    val stepKind: PresetStepKind? = null,
    val isPlaying: Boolean,
    val busy: Boolean,
    val failed: Boolean,
    val speed: PlaybackSpeed,
    val repeatAyah: Boolean,
    val reciters: List<PlayerReciter> = emptyList(),
    val selectedReciterKey: String? = null,
    val presets: List<PlayerPreset> = emptyList(),
    /** The verse the reading surface features, or null for a juz track (which has no verse). */
    val featuredVerse: PlayerVerse? = null,
    /** The page an explicit "download this page" would fetch, when the reader is on one. */
    val downloadablePage: Int? = null,
    /** Size of the featured verse, so the player renders at the reader's chosen scale. */
    val fontSizeSp: Int = 0,
) {
    /** The numeral on the identity tile: the juz's for a juz file, else the surah's. */
    val identityNumber: Int get() = juzNumber ?: surahNumber

    /**
     * The title's script pair. For a juz file the view formats [juzNumber] instead, because
     * "جزء N" is a sentence, not a surah name.
     */
    val titleFa: String get() = surahNameFa
    val titleEn: String get() = surahNameEn

    /** The line under the title: the running preset when there is one, else the reciter. */
    val subtitleFa: String get() = presetNameFa ?: reciterNameFa
    val subtitleEn: String get() = reciterNameEn

    /** The dock's fraction of this snapshot. The shell localises the script pair. */
    fun mini(): NowPlaying = NowPlaying(
        surahNumber = surahNumber,
        surahNameFa = surahNameFa,
        surahNameEn = surahNameEn,
        ayah = ayah,
        reciterNameFa = reciterNameFa,
        reciterNameEn = reciterNameEn,
        isPlaying = isPlaying,
        busy = busy,
        presetNameFa = presetNameFa,
        stepKind = stepKind?.wire,
        juzNumber = juzNumber,
    )
}

/**
 * The app-wide player store: the single place the reader publishes what is sounding and the shell
 * reads it from.
 *
 * It owns no playback. The reader keeps the engine and the session; this only carries the snapshot,
 * the transport commands it has bound, and the measured dock rect the full-screen player morphs out
 * of. Every command is a null-safe no-op until the owner binds it, so the shell can be composed (and
 * previewed) with no reader alive.
 */
@Stable
@Singleton
class NowPlayingStore @Inject constructor() {

    private val _state = MutableStateFlow<PlayerUi?>(null)
    val state: StateFlow<PlayerUi?> = _state.asStateFlow()

    /**
     * Whether the full-screen player is open. The shell hides the dock while it is true, so the
     * player covers the whole screen instead of sitting under the chrome it belongs to.
     */
    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    /**
     * The mini-player half's measured rect in root coordinates, published by the dock.
     *
     * The full-screen player starts its morph here, so opening from the dock is continuous rather
     * than a second surface appearing over it. Null until the dock has been laid out.
     */
    private val _miniPlayerRect = MutableStateFlow<Rect?>(null)
    val miniPlayerRect: StateFlow<Rect?> = _miniPlayerRect.asStateFlow()

    /** Null clears the player — nothing is queued, or the reader has gone away. */
    fun publish(player: PlayerUi?) {
        _state.value = player
    }

    fun setExpanded(expanded: Boolean) {
        _expanded.value = expanded
    }

    fun setMiniPlayerRect(rect: Rect?) {
        _miniPlayerRect.value = rect
    }

    // ---- bound by the reader ------------------------------------------------------

    private var onToggle: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeek: ((Long) -> Unit)? = null
    private var onCycleSpeed: (() -> Unit)? = null
    private var onToggleRepeat: (() -> Unit)? = null
    private var onSelectReciter: ((String) -> Unit)? = null
    private var onDownload: (() -> Unit)? = null
    private var onPlayPreset: ((String) -> Unit)? = null
    private var onExitPreset: (() -> Unit)? = null
    private var onOpenControls: (() -> Unit)? = null
    private var onClose: (() -> Unit)? = null

    /**
     * Position/duration in milliseconds, pulled on demand.
     *
     * A pull, not part of the snapshot: the seeker is the one place a per-second position is the
     * fact, and it reads this from a leaf ticker so a tick never recomposes the sheet or the dock.
     * Returns `0 to 0` until the reader binds it.
     */
    private var positionSource: (() -> Pair<Int, Int>)? = null
    val positionProvider: () -> Pair<Int, Int> = { positionSource?.invoke() ?: (0 to 0) }

    /**
     * Binds the reader's transport, options and preset commands.
     *
     * One call, because the reader binds them together and withdraws them together. Every command
     * is optional at the call site only in the sense that the shell may call it before a reader
     * exists; the reader supplies all of them.
     */
    fun bindCommands(
        onToggle: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onSeek: (Long) -> Unit,
        onCycleSpeed: () -> Unit,
        onToggleRepeat: () -> Unit,
        onSelectReciter: (String) -> Unit,
        onDownload: () -> Unit,
        onPlayPreset: (String) -> Unit,
        onExitPreset: () -> Unit,
        onOpenControls: () -> Unit,
        onClose: () -> Unit,
    ) {
        this.onToggle = onToggle
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeek = onSeek
        this.onCycleSpeed = onCycleSpeed
        this.onToggleRepeat = onToggleRepeat
        this.onSelectReciter = onSelectReciter
        this.onDownload = onDownload
        this.onPlayPreset = onPlayPreset
        this.onExitPreset = onExitPreset
        this.onOpenControls = onOpenControls
        this.onClose = onClose
    }

    fun bindPositionProvider(provider: (() -> Pair<Int, Int>)?) {
        positionSource = provider
    }

    /** Withdraws every command and the position source when the owning screen goes away. */
    fun unbind() {
        onToggle = null
        onNext = null
        onPrevious = null
        onSeek = null
        onCycleSpeed = null
        onToggleRepeat = null
        onSelectReciter = null
        onDownload = null
        onPlayPreset = null
        onExitPreset = null
        onOpenControls = null
        onClose = null
        positionSource = null
    }

    // ---- transport ----------------------------------------------------------------

    fun toggle() {
        onToggle?.invoke()
    }

    fun next() {
        onNext?.invoke()
    }

    fun previous() {
        onPrevious?.invoke()
    }

    fun seek(positionMs: Long) {
        onSeek?.invoke(positionMs)
    }

    fun cycleSpeed() {
        onCycleSpeed?.invoke()
    }

    fun toggleRepeat() {
        onToggleRepeat?.invoke()
    }

    fun selectReciter(key: String) {
        onSelectReciter?.invoke(key)
    }

    fun download() {
        onDownload?.invoke()
    }

    fun playPreset(clientId: String) {
        onPlayPreset?.invoke(clientId)
    }

    fun exitPreset() {
        onExitPreset?.invoke()
    }

    /** Tapping the mini-player asks the owner to show its full controls. */
    fun openControls() {
        onOpenControls?.invoke()
    }

    fun close() {
        onClose?.invoke()
    }
}
