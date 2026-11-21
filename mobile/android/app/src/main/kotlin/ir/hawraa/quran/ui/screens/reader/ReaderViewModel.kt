package ir.hawraa.quran.ui.screens.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.hawraa.quran.data.download.AudioAutoCache
import ir.hawraa.quran.data.preset.PresetPlaybackListener
import ir.hawraa.quran.data.preset.PresetPlaybackState
import ir.hawraa.quran.data.preset.PresetPlayer
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.data.quran.RecitationCatalog
import ir.hawraa.quran.data.quran.RecitationRepository
import ir.hawraa.quran.data.recitation.PlaybackState
import ir.hawraa.quran.data.recitation.RecitationEngine
import ir.hawraa.quran.data.repository.PresetProgressRepository
import ir.hawraa.quran.data.repository.PresetRepository
import ir.hawraa.quran.data.repository.PresetSittingRepository
import ir.hawraa.quran.data.repository.ReadingSessionRepository
import ir.hawraa.quran.data.repository.SettingsRepository
import ir.hawraa.quran.di.ApplicationScope
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetCue
import ir.hawraa.quran.domain.preset.PresetEventKind
import ir.hawraa.quran.domain.preset.PresetSequence
import ir.hawraa.quran.domain.preset.PresetSequenceBuilder
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.QuranPage
import ir.hawraa.quran.domain.quran.SelectionPlan
import ir.hawraa.quran.domain.quran.SurahInfo
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.quran.toRef
import ir.hawraa.quran.domain.recitation.AyahNumbering
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.recitation.RecitationQueue
import ir.hawraa.quran.domain.recitation.RecitationTrack
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.session.SessionMode
import ir.hawraa.quran.domain.session.VerseRef
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import ir.hawraa.quran.ui.recitation.NowPlaying
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the recitation bar and the follow-along highlight need. */
data class RecitationUiState(
    /** False until the bundled catalogue is loaded (or if it is empty). */
    val available: Boolean = false,
    /** Both scripts, because the name follows the app language, not the VM's locale. */
    val reciterNameFa: String = "",
    val reciterNameEn: String = "",
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val repeatAyah: Boolean = false,
    /** `surah to ayah` currently sounding, or null when nothing is queued. */
    val playingVerse: Pair<Int, Int>? = null,
    /**
     * The juz currently sounding, when the reciter publishes juz files.
     *
     * Separate from [playingVerse] rather than a second meaning for it: a whole-juz file has no
     * position, so there is no verse to highlight. Keeping them apart is what lets the page stop
     * claiming a verse is playing when the file it is hearing began at the top of a juz.
     */
    val playingJuz: Int? = null,
    val hasQueue: Boolean = false,
    /** Presets the reader can start from the sheet. Empty until the list loads. */
    val presets: List<PresetOption> = emptyList(),
    /** Set while a preset is running; null in plain recitation. */
    val activePresetName: String? = null,
    /** What the running preset is doing right now, for the mini-player subtitle. */
    val activeStep: PresetStepKind? = null,
    /**
     * Text keys the running preset asked for that the bundle does not carry. Surfaced rather
     * than swallowed: a preset that sounds shorter than it was built to must say why.
     */
    val missingTextKeys: Set<String> = emptySet(),
) {
    fun reciterName(persian: Boolean): String =
        if (persian) reciterNameFa.ifBlank { reciterNameEn } else reciterNameEn.ifBlank { reciterNameFa }

    val presetActive: Boolean get() = activePresetName != null
}

/** A preset as the reader's picker needs it: identity plus a readable shape. */
data class PresetOption(
    val clientId: String,
    val nameFa: String,
    val isDefault: Boolean,
    /** Number of steps, shown as a badge so an empty preset is visible before it is played. */
    val stepCount: Int,
)

/** A reciter the full-screen player can switch to: identity plus both scripts. */
data class ReciterChoice(
    val key: String,
    val nameFa: String,
    val nameEn: String,
    /** Whether this voice has per-ayah audio (a juz-only voice cannot drive verse-by-verse play). */
    val perAyah: Boolean,
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val pages: List<QuranPage> = emptyList(),
    val surahs: Map<Int, SurahInfo> = emptyMap(),
    val basmalaUthmani: String = "",
    val settings: ReadingSettings = ReadingSettings(),
    val recitation: RecitationUiState = RecitationUiState(),
    /** 0-based page index to open on (set by "continue reading"). */
    val startPageIndex: Int = 0,
    /** The reciters the player offers to switch between, catalogue order. Empty until loaded. */
    val reciters: List<ReciterChoice> = emptyList(),
    /** The reader's standing reciter key (null = the catalogue default). */
    val selectedReciterKey: String? = null,
) {
    fun surahOf(page: QuranPage?): SurahInfo? =
        page?.verses?.firstOrNull()?.let { surahs[it.surah] }

    /** The verse currently sounding, resolved from the bundled content, or null. */
    fun currentVerse(): VerseItem? {
        val playing = recitation.playingVerse ?: return null
        for (page in pages) {
            val verse = page.verses.firstOrNull { it.surah == playing.first && it.ayah == playing.second }
            if (verse != null) return VerseItem(page.page, verse)
        }
        return null
    }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val contentRepository: QuranContentRepository,
    private val settingsRepository: SettingsRepository,
    private val sessions: ReadingSessionRepository,
    private val recitationRepository: RecitationRepository,
    private val engine: RecitationEngine,
    private val nowPlaying: NowPlayingStore,
    private val presetPlayer: PresetPlayer,
    private val presetRepository: PresetRepository,
    private val presetSittings: PresetSittingRepository,
    private val presetProgress: PresetProgressRepository,
    private val audio: LocalAudioSource,
    private val autoCache: AudioAutoCache,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /**
     * Whether the full-screen player is open.
     *
     * State, not a one-shot event: the player is a surface the reader draws, and it must survive
     * recomposition while it is up. Opening it also tells the app-wide store to hide the dock, so
     * the player covers the whole screen.
     */
    private val _playerOpen = MutableStateFlow(false)
    val playerOpen: StateFlow<Boolean> = _playerOpen.asStateFlow()

    private var themeMode = ThemeMode.SYSTEM
    private var recitationSettings = RecitationSettings()

    private var catalog: RecitationCatalog? = null

    /** Resolved reciter; null until the catalogue has loaded. */
    private var reciter: Reciter? = null

    /**
     * The queue the engine is holding (D31/D32).
     *
     * [queueSurah] is the surah a plain queue runs to the end of, or null for a selection run —
     * which is also how the resume logic tells the two apart, so a paused selection is resumed as
     * a selection and not quietly widened into a surah.
     */
    private var queue: List<RecitationTrack> = emptyList()
    private var queueSurah: Int? = null
    private var queueIsSelection: Boolean = false

    /** True while the preset player runs an ad-hoc selection recipe (D32): no sittings, no coverage. */
    private var selectionRun = false

    /** Page whose audio was last handed to the auto-cache, so a transition caches each page once. */
    private var lastCachedPage: Int? = null

    /** The preset run currently loaded, if any. Plain recitation when null. */
    private var activePreset: Preset? = null
    private var activeSequence: PresetSequence? = null

    /**
     * The page at the top of the screen, as last reported by the scroll.
     *
     * The dock's play button has no verse context — it is chrome, not a tap on a pane — so it
     * needs to know which page the reader is looking at without asking the UI.
     */
    private var lastVisiblePage: Int = 0

    private val requestedStartPage: Int =
        savedStateHandle.get<Int>("page")?.coerceAtLeast(0) ?: 0

    init {
        viewModelScope.launch {
            try {
                val content = contentRepository.content()
                _state.update {
                    it.copy(
                        isLoading = false,
                        failed = content.pages.isEmpty(),
                        pages = content.pages,
                        surahs = content.surahs,
                        basmalaUthmani = content.basmalaUthmani,
                        // Clamped here: the requested index came from storage and may be
                        // stale if the bundled page set ever shrinks.
                        startPageIndex = requestedStartPage
                            .coerceIn(0, (content.pages.size - 1).coerceAtLeast(0)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
        viewModelScope.launch {
            settingsRepository.readingSettings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { themeMode = it }
        }
        viewModelScope.launch {
            val loaded = recitationRepository.catalog()
            catalog = loaded
            applyReciter(loaded.reciter(recitationSettings.reciterKey))
            _state.update {
                it.copy(
                    reciters = loaded.reciters.map { r ->
                        ReciterChoice(r.key, r.nameFa, r.nameEn, r.servesPerAyahPlayback)
                    },
                    selectedReciterKey = loaded.reciter(recitationSettings.reciterKey)?.key,
                )
            }
        }
        // The dock lives in the app shell, so the player half is fed through the store rather
        // than by the reader reaching up into the chrome. One collector keeps it honest: every
        // change the reader already computes is mirrored, with no second source of truth.
        viewModelScope.launch {
            _state.collect { publishNowPlaying(it) }
        }
        // The dock's play button is chrome: it has no verse context, so it resumes from the page
        // the reader is looking at. Bound here and withdrawn in `onCleared`, because this screen
        // owns the engine.
        nowPlaying.bindTransport(
            onToggle = { onPlayPause(focused = null, visiblePage = lastVisiblePage) },
            onNext = { onNextAyah() },
            onOpenControls = { openPlayer() },
        )
        viewModelScope.launch {
            settingsRepository.recitationSettings.collect { settings ->
                val previousKey = reciter?.key
                recitationSettings = settings
                // Restore the reader's last speed/repeat onto the engine, so a recreated
                // screen never silently drops back to 1×.
                engine.setSpeed(settings.speed)
                engine.setRepeatAyah(settings.repeatAyah)
                val next = catalog?.reciter(settings.reciterKey) ?: return@collect
                applyReciter(next)
                _state.update { it.copy(selectedReciterKey = next.key) }
                // A different voice: rebuild the queue for the same run in the new voice, so the
                // next ayah is already the new reciter's instead of finishing in the old one.
                // Speed/repeat are untouched — they are not the voice. A selection run keeps its
                // own verses; a plain run keeps its surah-to-end scope (D31).
                if (next.key == previousKey || queue.isEmpty()) return@collect
                val playback = engine.state.value
                val at = queue.getOrNull(playback.index) ?: queue.firstOrNull() ?: return@collect
                if (queueIsSelection) {
                    startSelectionQueue(queue.map { it.surah to it.ayah }, play = playback.isPlaying)
                } else {
                    startSurahQueue(at.surah, at.ayah, play = playback.isPlaying)
                }
            }
        }
        viewModelScope.launch {
            engine.state.collect(::onPlaybackChanged)
        }
        // The preset list feeds the sheet's picker. Kept as a flow rather than a one-shot read
        // so creating a preset on the presets screen shows up here without reopening the reader.
        viewModelScope.launch {
            presetRepository.observePresets().collect { presets ->
                _state.update {
                    it.copy(
                        recitation = it.recitation.copy(
                            presets = presets.map { preset ->
                                PresetOption(
                                    clientId = preset.clientId,
                                    nameFa = preset.name,
                                    isDefault = preset.isDefault,
                                    stepCount = preset.playableSteps.size,
                                )
                            },
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            presetPlayer.state.collect(::onPresetPlaybackChanged)
        }
    }

    // ---- reading ----------------------------------------------------------------

    /**
     * [page] is now the one at the top of the screen. Moving to a different page means the
     * previous one was read through, so its session closes as completed and a new one
     * opens.
     *
     * This is the only place sessions are opened: no timer ever starts one. With
     * continuous scrolling the caller is the lazy list reporting its first visible item,
     * so the trigger is a real scroll, exactly as the pager settle used to be.
     */
    fun onPageVisible(page: Int) {
        val quranPage = _state.value.pages.firstOrNull { it.page == page } ?: return
        val first = quranPage.verses.firstOrNull() ?: return
        val last = quranPage.verses.lastOrNull() ?: return
        lastVisiblePage = page

        viewModelScope.launch {
            val listening = engine.state.value.isPlaying
            val openPage = sessions.activePage
            if (openPage != null && openPage != quranPage.page) {
                sessions.finish(completed = true)
            }
            // D31: a scroll no longer re-queues the audio. The running queue is a *run* — to the
            // end of the surah, or the reader's selection — and following the page would cut it
            // short at every boundary. Follow-along scrolling keeps the reciting ayah in view.
            if (sessions.activePage == null) {
                sessions.open(
                    start = first.toRef(quranPage.page),
                    intendedEnd = last.toRef(quranPage.page),
                    settings = _state.value.settings,
                    themeMode = themeMode,
                    languageCode = languageCode(),
                    mode = if (listening) SessionMode.LISTEN else SessionMode.READ,
                )
                if (listening) markListening()
            }
            sessions.progress(first.toRef(quranPage.page), last.toRef(quranPage.page))
        }
    }

    /**
     * The reader is moving through the current page (scroll, page turn). A real UI event,
     * so it is what keeps the reading clock alive — there is no ticking timer anywhere.
     */
    fun onReadingActivity(page: Int) {
        val quranPage = _state.value.pages.firstOrNull { it.page == page } ?: return
        val first = quranPage.verses.firstOrNull() ?: return
        val last = quranPage.verses.lastOrNull() ?: return
        viewModelScope.launch {
            if (sessions.activePage != quranPage.page) return@launch
            sessions.progress(first.toRef(quranPage.page), last.toRef(quranPage.page))
        }
    }

    /** The app went to the background: stop billing time and stop the audio. */
    fun onReaderStopped() {
        engine.pause()
        viewModelScope.launch { sessions.finish(completed = false) }
    }

    /**
     * The reader left composition without the app stopping — navigating to Settings, say.
     * The session ends because the page is no longer on screen, but playback keeps
     * running: the settings screen exists to change the recitation, and silencing it the
     * moment you open it would make the speed and reciter controls useless.
     */
    fun onScreenLeft() {
        viewModelScope.launch { sessions.finish(completed = false) }
    }

    // ---- recitation -------------------------------------------------------------

    /**
     * Play/pause. With a verse focused (the reader tapped a pane) playback starts there;
     * otherwise from the top of the page at the top of the screen.
     *
     * While a preset is running this drives the preset instead of the plain queue, so the dock
     * button, the sheet and the action box all keep working without knowing which mode is on.
     */
    fun onPlayPause(focused: Pair<Int, Int>?, visiblePage: Int) {
        if (activePreset != null) {
            val wasPlaying = presetPlayer.state.value.isPlaying
            presetPlayer.toggle()
            recordPresetEvent(
                if (wasPlaying) PresetEventKind.PAUSE else PresetEventKind.PLAY,
                presetPlayer.state.value.cue,
            )
            return
        }
        val playback = engine.state.value
        if (playback.isPlaying) {
            engine.pause()
            return
        }
        // The surah the play belongs to: a focused verse names it; the dock starts from the page
        // on screen. A queue of the same surah is *resumed* rather than rebuilt, so a pause keeps
        // the reader's place to the verse (D31). A paused selection run is resumed as a selection
        // — only a fresh verse tap replaces it.
        val visibleFirst = _state.value.pages.firstOrNull { it.page == visiblePage }?.verses
            ?.firstOrNull()
            ?: _state.value.pages.firstOrNull()?.verses?.firstOrNull()
        val targetSurah = focused?.first ?: visibleFirst?.surah
        if (playback.hasQueue && (
                (!queueIsSelection && queueSurah == targetSurah) ||
                    (queueIsSelection && focused == null)
                )
        ) {
            engine.play()
            viewModelScope.launch { markListening() }
            return
        }
        val start = focused ?: visibleFirst?.let { it.surah to it.ayah } ?: return
        startSurahQueue(start.first, start.second, play = true)
        viewModelScope.launch { markListening() }
    }

    fun onNextAyah() {
        if (activePreset != null) {
            presetPlayer.next()
            recordPresetEvent(PresetEventKind.SEEK, presetPlayer.state.value.cue)
            return
        }
        val playback = engine.state.value
        RecitationQueue.next(queue, playback.index)?.let { engine.seekToIndex(it) }
    }

    fun onPreviousAyah() {
        if (activePreset != null) {
            presetPlayer.previous()
            recordPresetEvent(PresetEventKind.SEEK, presetPlayer.state.value.cue)
            return
        }
        val playback = engine.state.value
        RecitationQueue.previous(queue, playback.index)?.let { engine.seekToIndex(it) }
    }

    /** One tap cycles the presets; the choice is remembered for the next session. */
    fun onCycleSpeed() {
        val next = engine.state.value.speed.next()
        engine.setSpeed(next)
        viewModelScope.launch { settingsRepository.setPlaybackSpeed(next) }
    }

    fun onToggleRepeat() {
        val repeat = !engine.state.value.repeatAyah
        engine.setRepeatAyah(repeat)
        viewModelScope.launch { settingsRepository.setRepeatAyah(repeat) }
    }

    // ---- full-screen player ------------------------------------------------------

    /** Opens the player and tells the shell to hide the dock so it covers the screen. */
    fun openPlayer() {
        _playerOpen.value = true
        nowPlaying.setExpanded(true)
    }

    fun closePlayer() {
        _playerOpen.value = false
        nowPlaying.setExpanded(false)
    }

    /** Position/duration for the player's progress bar, read straight from the engine. */
    fun currentPositionMs(): Long = engine.currentPositionMs()

    fun currentDurationMs(): Long = engine.currentDurationMs()

    /** Switches the standing reciter; the settings collector rebuilds the queue on the new voice. */
    fun selectReciter(key: String) {
        viewModelScope.launch { settingsRepository.setReciter(key) }
    }

    /**
     * Downloads the page currently on screen for the current reciter — the explicit half of
     * "download easily from any page". It reuses the same scope logic the silent auto-cache fires
     * on, so the two can never disagree about what "this page" means for this voice.
     */
    fun downloadCurrentPage() {
        val page = _state.value.pages.firstOrNull { it.page == lastVisiblePage }
            ?: _state.value.pages.firstOrNull()
            ?: return
        lastCachedPage = null // an explicit request must not be swallowed by the once-per-page rule
        cachePage(page.page)
    }

    /**
     * Starts the plain run: [ayah] through the **end of its surah** (D31), across pages.
     *
     * The scope is the bundled text's end, which today is the end of the mushaf pages that ship;
     * when the full text lands the same code runs to the real surah end with no change.
     */
    private fun startSurahQueue(surah: Int, ayah: Int, play: Boolean) {
        val current = reciter ?: return
        val items = surahVerseItems(surah)
        val tracks = buildTracks(current, items)
        if (tracks.isEmpty()) return
        val start = RecitationQueue.startIndex(tracks, surah to ayah)
        queue = tracks
        queueSurah = surah
        queueIsSelection = false
        engine.setQueue(tracks, start, play)
        cachePage(tracks.getOrNull(start)?.page)
    }

    /**
     * Plays exactly the verses in [verses], in order (D32, recitation-only selection).
     *
     * A juz voice cannot honour a two-verse pick faithfully — its unit is the whole juz — so it
     * gets the juzes containing the selection, which is the most honest narrowing available.
     */
    private fun startSelectionQueue(verses: List<Pair<Int, Int>>, play: Boolean) {
        val current = reciter ?: return
        val items = verses.mapNotNull(::verseItemFor)
        val tracks = buildTracks(current, items)
        if (tracks.isEmpty()) return
        queue = tracks
        queueSurah = null
        queueIsSelection = true
        engine.setQueue(tracks, startIndex = 0, playWhenReady = play)
        cachePage(items.firstOrNull()?.page)
    }

    /**
     * The reader asked to hear **just what they selected** (D32).
     *
     * Two shapes. Recitation only → a plain queue of exactly those verses. Any spoken layer →
     * an ad-hoc recipe (verse, then each selected translation, then each selected tafsir) run by
     * the preset player, because that is the machinery that already interleaves recitation with
     * spoken text. The run is deliberately *not* a preset: nothing is persisted, no sitting is
     * opened and no coverage is billed — it is a one-shot listening, not a saved recipe.
     */
    fun onPlaySelection(plan: SelectionPlan) {
        if (plan.verses.isEmpty()) return
        val spoken = plan.translationKeys.isNotEmpty() || plan.commentaryKeys.isNotEmpty()
        if (!spoken) {
            startSelectionQueue(plan.verses, play = true)
            viewModelScope.launch { markListening() }
            return
        }
        viewModelScope.launch {
            val items = plan.verses.mapNotNull(::verseItemFor)
            if (items.isEmpty()) return@launch
            val steps = buildList {
                if (plan.reciteVerses) add(PresetStep.ayah())
                plan.translationKeys.forEach { add(PresetStep.translation(it)) }
                plan.commentaryKeys.forEach { add(PresetStep.tafsir(it)) }
            }
            if (steps.isEmpty()) return@launch
            val recipe = Preset(clientId = SELECTION_CLIENT_ID, name = "", steps = steps)
            val speed = engine.state.value.speed
            val sequence = PresetSequenceBuilder.build(recipe, items, speed.value)
            if (sequence.isEmpty) return@launch

            // The voice: the reader's current one if it can recite verse-by-verse, else the
            // catalogue's per-ayah default — the same rule `presetVoice` applies to saved
            // recipes, because a whole-juz file cannot be a per-verse step.
            val voice = reciter?.takeIf { it.servesPerAyahPlayback }
                ?: catalog?.reciters?.firstOrNull { it.servesPerAyahPlayback }
            engine.pause()
            activePreset = recipe
            activeSequence = sequence
            selectionRun = true
            presetPlayer.load(
                sequence = sequence,
                presetName = recipe.name,
                reciter = voice,
                speed = speed,
                startIndex = 0,
                listener = presetListener,
            )
            presetPlayer.play()
            markListening()
        }
    }

    /** Tracks for [items] in the current voice, resolving downloaded files first. */
    private fun buildTracks(current: Reciter, items: List<VerseItem>): List<RecitationTrack> {
        val index = audio.current()
        return if (current.servesPerAyahPlayback) {
            RecitationQueue.build(items, current) { surah, ayah, remote ->
                // Prefer a downloaded file: the reader chose to keep this reciter offline, and
                // streaming the same verse again would spend data they already paid for.
                index.urlFor(current.key, current.style, surah, ayah, remote)
            }
        } else {
            RecitationQueue.buildJuz(items, current) { juz, remote ->
                index.urlForJuz(current.key, current.style, juz, remote)
            }
        }
    }

    /** Every bundled verse of [surah], in mushaf order, with the page each one sits on. */
    private fun surahVerseItems(surah: Int): List<VerseItem> =
        _state.value.pages.sortedBy { it.page }.flatMap { page ->
            page.verses.filter { it.surah == surah }.map { VerseItem(page.page, it) }
        }

    private fun verseItemFor(surah: Int, ayah: Int): VerseItem? {
        for (page in _state.value.pages) {
            val verse = page.verses.firstOrNull { it.surah == surah && it.ayah == ayah }
            if (verse != null) return VerseItem(page.page, verse)
        }
        return null
    }

    private fun verseItemFor(surahAyah: Pair<Int, Int>): VerseItem? =
        verseItemFor(surahAyah.first, surahAyah.second)

    /**
     * Asks the background cache for the audio of one page (D31: a run caches each page as it
     * reaches it, not the whole surah up front — the queue streams regardless).
     *
     * Deliberately fire-and-forget: the outcome never changes what the reader hears, because the
     * queue already streams. A juz reciter's scope is the whole juz — 12 MB for Shakernejad — and
     * that is the honest unit, since it is the only file the source publishes.
     */
    private fun cachePage(page: Int?) {
        val current = reciter ?: return
        if (page == null || page == lastCachedPage) return
        lastCachedPage = page
        val quranPage = _state.value.pages.firstOrNull { it.page == page } ?: return
        val first = quranPage.verses.firstOrNull() ?: return
        val last = quranPage.verses.lastOrNull() ?: return
        val scopes = if (current.servesPerAyahPlayback) {
            listOf(DownloadScope.Range(first.surah, first.ayah, last.surah, last.ayah))
        } else {
            quranPage.verses
                .mapNotNull { AyahNumbering.juzOf(it.surah, it.ayah) }
                .distinct()
                .map { DownloadScope.Juz(it) }
        }
        if (scopes.isEmpty()) return
        viewModelScope.launch {
            scopes.forEach { scope -> autoCache.ensure(current.key, current.style, scope) }
        }
    }

    // ---- presets -----------------------------------------------------------------

    /**
     * Starts a preset run from [from] (a `surah to ayah` pair), or from the page on screen.
     *
     * The whole *visible range* is compiled into one cue list up front, not just the page:
     * a preset's recipe spans several cues per ayah, and compiling lazily would mean
     * re-deriving gaps and repeats at every boundary. The range is what the reader can
     * scroll through, which is what "play the mushaf with this preset" means.
     */
    fun onPlayPreset(clientId: String, from: Pair<Int, Int>? = null) {
        val preset = _state.value.recitation.presets.firstOrNull { it.clientId == clientId } ?: return
        viewModelScope.launch {
            val full = presetRepository.preset(clientId) ?: return@launch
            val pages = _state.value.pages
            if (pages.isEmpty()) return@launch

            val verses = pages.flatMap { page -> page.verses.map { VerseItem(page.page, it) } }
            val start = from ?: verses.firstOrNull()?.let { it.verse.surah to it.verse.ayah } ?: return@launch
            val speed = engine.state.value.speed

            val sequence = PresetSequenceBuilder.build(full, verses, speed.value)
            if (sequence.isEmpty) return@launch

            // The voice is the preset's, not the reader's current selection: "which reciter's
            // version is to be played" must be explicit, and a verse-by-verse preset can only be
            // driven by a per-ayah edition.
            val voice = presetVoice(full.reciterKey, catalog, reciter)

            // Plain recitation and a preset run must not both hold the engine.
            engine.pause()
            activePreset = full
            activeSequence = sequence
            selectionRun = false

            val startIndex = sequence.indexOfAyah(start.first, start.second) ?: 0
            val page = verses.firstOrNull { it.verse.surah == start.first && it.verse.ayah == start.second }?.page

            presetSittings.start(
                preset = full,
                surah = start.first,
                ayah = start.second,
                page = page,
                reciterKey = voice?.key,
                speed = speed.value,
            )
            presetPlayer.load(
                sequence = sequence,
                presetName = full.name,
                reciter = voice,
                speed = speed,
                startIndex = startIndex,
                listener = presetListener,
            )
            presetPlayer.play()
            recordPresetEvent(PresetEventKind.PLAY, sequence.cueAt(startIndex))
            markListening()
            _state.update {
                it.copy(
                    recitation = it.recitation.copy(
                        missingTextKeys = sequence.missingTextKeys,
                        activePresetName = full.name,
                        // The dock and sheet name the voice that is actually sounding, which is
                        // the preset's, not necessarily the reader's standing selection.
                        reciterNameFa = voice?.nameFa.orEmpty(),
                        reciterNameEn = voice?.nameEn.orEmpty(),
                    ),
                )
            }
        }
    }

    /** Leaves preset mode and closes the sitting. Plain recitation resumes on the next play. */
    fun onExitPreset() {
        if (activePreset == null) return
        presetPlayer.stop()
        activePreset = null
        activeSequence = null
        val wasSelectionRun = selectionRun
        selectionRun = false
        _state.update {
            it.copy(
                recitation = it.recitation.copy(
                    activePresetName = null,
                    activeStep = null,
                    missingTextKeys = emptySet(),
                    // Back to the reader's standing voice now that the preset's is no longer sounding.
                    reciterNameFa = reciter?.nameFa.orEmpty(),
                    reciterNameEn = reciter?.nameEn.orEmpty(),
                ),
            )
        }
        // A selection run never opened a sitting (D32), so there is nothing to close.
        if (!wasSelectionRun) viewModelScope.launch { presetSittings.finish(completed = false) }
    }

    /**
     * Bills each finished cue: a counter event for the sitting, and coverage for a completed
     * recitation. Coverage is only recorded for the ayah itself, never for a translation —
     * "I have heard this ayah with this preset" is the question the ledger answers.
     */
    private val presetListener = object : PresetPlaybackListener {
        override fun onCueFinished(cue: PresetCue, elapsedMs: Long) {
            // A selection run is a one-shot listening (D32): no sitting events, no coverage —
            // the ledger answers "how much of this *preset* have I covered", and an ad-hoc
            // pick of three verses is not a preset.
            if (selectionRun) return
            val sequence = activeSequence ?: return
            val index = sequence.cues.indexOf(cue)
            val next = sequence.cues.getOrNull(index + 1)
            // "Last cue of this ayah" is the only honest definition of a completed ayah: the
            // recipe may end on a translation, not on a recitation.
            val isLastOfAyah = next == null || next.surah != cue.surah || next.ayah != cue.ayah
            val kind = if (isLastOfAyah) PresetEventKind.AYAH else PresetEventKind.STEP
            recordPresetEvent(kind, cue)

            if (cue is PresetCue.Recite) {
                val preset = activePreset ?: return
                viewModelScope.launch {
                    presetProgress.recordPlaythrough(
                        presetClientId = preset.clientId,
                        surah = cue.surah,
                        ayah = cue.ayah,
                        globalAyah = cue.globalAyah,
                        page = cue.page,
                        listenedSec = (elapsedMs / 1000L).toInt(),
                    )
                }
            }
        }

        override fun onFinished() {
            if (!selectionRun) viewModelScope.launch { presetSittings.finish(completed = true) }
            _state.update {
                it.copy(
                    recitation = it.recitation.copy(
                        activePresetName = null,
                        activeStep = null,
                        reciterNameFa = reciter?.nameFa.orEmpty(),
                        reciterNameEn = reciter?.nameEn.orEmpty(),
                    ),
                )
            }
            activePreset = null
            activeSequence = null
            selectionRun = false
        }
    }

    /** Records one event on the open sitting. Never opens a row — that is `start`'s job. */
    private fun recordPresetEvent(kind: PresetEventKind, cue: PresetCue?) {
        if (activePreset == null) return
        val surah = cue?.surah ?: return
        val ayah = cue.ayah
        val stepIndex = cue.stepIndex
        val page = cue.page
        viewModelScope.launch {
            presetSittings.record(kind, surah, ayah, stepIndex, page)
        }
    }

    /** Mirrors the preset player into the UI, which is what the highlight and dock read. */
    private fun onPresetPlaybackChanged(playback: PresetPlaybackState) {
        if (activePreset == null) return
        _state.update {
            it.copy(
                recitation = it.recitation.copy(
                    isPlaying = playback.isPlaying,
                    failed = playback.failed,
                    buffering = playback.waiting,
                    playingVerse = playback.currentAyah,
                    hasQueue = playback.size > 0,
                    activeStep = playback.cue?.kind,
                ),
            )
        }
        // Listening is activity: each ayah the preset reaches feeds the open reading session
        // exactly as a plain recitation does, so both modes bill time the same way.
        val verse = playback.currentAyah ?: return
        val page = playback.cue?.page ?: return
        if (!playback.isPlaying) return
        viewModelScope.launch {
            if (sessions.activePage != page) return@launch
            val ref = VerseRef(verse.first, verse.second, page)
            sessions.progress(ref, ref)
        }
    }

    /** Mirrors engine state into the UI and bills listening time to the open session. */
    private fun onPlaybackChanged(playback: PlaybackState) {
        // While a preset is running it drives this same engine, and its own collector
        // (`onPresetPlaybackChanged`) owns the UI. Mirroring the plain queue here would read a
        // stale `queue` and blank the highlight/dock the preset just published — so the preset
        // wins while it is active.
        if (activePreset != null) return
        val verse = queue.getOrNull(playback.index)
        // D31: a surah-wide run crosses pages; each one is handed to the auto-cache the first
        // time the audio reaches it, so listening ahead also downloads ahead.
        verse?.let { cachePage(it.page) }
        _state.update {
            it.copy(
                recitation = it.recitation.copy(
                    isPlaying = playback.isPlaying,
                    buffering = playback.buffering,
                    failed = playback.failed,
                    speed = playback.speed,
                    repeatAyah = playback.repeatAyah,
                    hasQueue = playback.hasQueue,
                    // A juz track has no verse position, so it publishes the juz and leaves the
                    // verse empty rather than pointing the highlight at the juz's first ayah.
                    playingVerse = verse?.takeIf { track -> track.servesOneVerse }
                        ?.let { track -> track.surah to track.ayah },
                    playingJuz = verse?.juz,
                ),
            )
        }
        // Listening is activity: each ayah the audio moves to is a real event, so the
        // session's clock advances without any polling (same rule as page scrolls).
        // Skipped for a juz track: recording "progress at 1:1" while a whole juz plays would
        // rewind the session to a verse the reader is not on.
        if (verse != null && verse.servesOneVerse && playback.isPlaying) {
            viewModelScope.launch {
                if (sessions.activePage != verse.page) return@launch
                val ref = VerseRef(verse.surah, verse.ayah, verse.page)
                sessions.progress(ref, ref)
            }
        }
    }

    private suspend fun markListening() {
        sessions.markListening(
            speed = engine.state.value.speed.value,
            reciterKey = reciter?.key,
        )
    }

    private fun applyReciter(next: Reciter?) {
        reciter = next
        _state.update {
            it.copy(
                recitation = it.recitation.copy(
                    available = next != null,
                    reciterNameFa = next?.nameFa.orEmpty(),
                    reciterNameEn = next?.nameEn.orEmpty(),
                ),
            )
        }
    }

    /**
     * Mirrors the recitation half of the state into the app-wide store the dock reads.
     *
     * Only the fields the dock draws are copied, so a scroll does not push a new dock model and
     * the dock does not recompose while the reader scrolls.
     */
    private fun publishNowPlaying(state: ReaderUiState) {
        val recitation = state.recitation

        // A juz-granularity reciter has no verse to show, but it does have something playing:
        // the dock shows the juz instead of hiding the player entirely.
        val juz = recitation.playingJuz
        if (juz != null) {
            val start = AyahNumbering.juzStart(juz)
            val surah = start?.let { state.surahs[it.first] }
            nowPlaying.publish(
                NowPlaying(
                    surahNumber = surah?.number ?: 0,
                    surahNameFa = surah?.nameFa.orEmpty(),
                    surahNameEn = surah?.nameEn.orEmpty(),
                    ayah = start?.second ?: 0,
                    reciterNameFa = recitation.reciterNameFa,
                    reciterNameEn = recitation.reciterNameEn,
                    isPlaying = recitation.isPlaying,
                    busy = recitation.buffering,
                    juzNumber = juz,
                ),
            )
            return
        }

        val verse = recitation.playingVerse
        val surah = verse?.let { state.surahs[it.first] }
        if (verse == null || surah == null) {
            nowPlaying.publish(null)
            return
        }
        nowPlaying.publish(
            NowPlaying(
                surahNumber = surah.number,
                surahNameFa = surah.nameFa,
                surahNameEn = surah.nameEn,
                ayah = verse.second,
                reciterNameFa = recitation.reciterNameFa,
                reciterNameEn = recitation.reciterNameEn,
                isPlaying = recitation.isPlaying,
                busy = recitation.buffering,
                presetNameFa = recitation.activePresetName,
                stepKind = recitation.activeStep?.wire,
            ),
        )
    }

    /**
     * The reader screen is being popped. The ViewModel scope is already cancelled here, so
     * finalization runs on the application scope — otherwise the session would stay open
     * and be attributed to the next reading burst.
     */
    override fun onCleared() {
        super.onCleared()
        // The player half of the dock belongs to this screen's playback; leaving must clear it.
        nowPlaying.unbindTransport()
        nowPlaying.publish(null)
        // The player is this screen's overlay; if it was open, the shell must get its dock back.
        nowPlaying.setExpanded(false)
        // A preset run outlives the ViewModel scope, so it is stopped here explicitly and its
        // sitting closed on the application scope — the same treatment a reading session gets,
        // for the same reason: a popped screen must not leave an open row behind.
        presetPlayer.stop()
        engine.pause()
        engine.release()
        appScope.launch {
            sessions.finish(completed = false)
            presetSittings.finish(completed = false)
        }
    }

    /** The language the UI is actually showing, for the session's settings snapshot. */
    private fun languageCode(): String {
        val tag = context.resources.configuration.locales[0]?.language
        return tag?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language
    }

    private companion object {
        /** Identity for the ad-hoc selection recipe (D32). It is never persisted. */
        const val SELECTION_CLIENT_ID = "selection"
    }
}

/**
 * The voice a preset run should recite with.
 *
 * A preset is a **verse-by-verse** recipe, so it can only be driven by a per-ayah edition. The
 * resolution order encodes the owner's rule that "which reciter's version is to be played" is
 * explicit:
 *
 *  1. the reciter the preset **pins** (`reciterKey`), when it has per-ayah audio;
 *  2. else the reader's **current selection**, when that is per-ayah;
 *  3. else the catalogue's **per-ayah default**, else any per-ayah reciter.
 *
 * A pinned **juz-only** voice (Shakernejad) is deliberately *not* used: a whole-juz MP3 cannot
 * start at verse 17, so handing it to a per-ayah preset would play juz 1 from the top and make the
 * follow-along lie. Degrading to a per-ayah voice is the honest choice; surfacing "this reciter has
 * no verse-by-verse audio" is a later UI nicety, not a reason to play the wrong thing.
 */
internal fun presetVoice(
    pinnedKey: String?,
    catalog: RecitationCatalog?,
    selected: Reciter?,
): Reciter? {
    pinnedKey?.let { key ->
        catalog?.reciter(key)?.takeIf { it.servesPerAyahPlayback }?.let { return it }
    }
    selected?.takeIf { it.servesPerAyahPlayback }?.let { return it }
    val perAyah = catalog?.reciters?.filter { it.servesPerAyahPlayback }.orEmpty()
    return perAyah.firstOrNull { it.key == catalog?.defaultReciterKey }
        ?: perAyah.firstOrNull()
        ?: selected
}
