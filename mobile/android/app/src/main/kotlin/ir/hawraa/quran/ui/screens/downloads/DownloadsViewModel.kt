package ir.hawraa.quran.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.download.DownloadManager
import ir.hawraa.quran.data.download.RecitationCatalogueRepository
import ir.hawraa.quran.data.local.DownloadBatch
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.DownloadSizing
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.ReciterInfo
import ir.hawraa.quran.domain.quran.QuranPage
import ir.hawraa.quran.domain.recitation.AyahNumbering
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which unit the reader is picking to download.
 *
 * Four, because four are useful and they are not interchangeable: a **page** is what the mushaf
 * shows and what the reader thinks in, a **surah** is what people memorise by, a **juz** is the
 * only unit a juz-granularity edition publishes, and a single **ayah** is for a verse someone
 * wants to keep. The *file* granularity is still the edition's business — picking "surah 2" on a
 * per-ayah edition is 286 files, and the screen says so before anything is fetched.
 */
enum class ScopeMode { PAGE, SURAH, JUZ, AYAH }

/**
 * One-shot feedback, as data rather than a formatted string: the ViewModel has no business
 * choosing Persian or English, and a snackbar message must not be re-shown on recomposition.
 */
sealed interface DownloadNotice {
    data class Queued(val files: Int, val alreadyHave: Int) : DownloadNotice
    data object CatalogueUnavailable : DownloadNotice
    data class Failed(val reason: String?) : DownloadNotice
}

/**
 * Everything on disk for one edition, rolled up from its batches.
 *
 * Batches are the storage unit (one per scope the reader ever asked for), but a list of thirty
 * "page 1 … page 30" rows is not a *picture* of what is downloaded. The screen groups them, so
 * "Parhizgar, 36 of 36 files, 3.4 MB" is one line and the individual pages are the detail.
 */
data class EditionGroup(
    val editionKey: String,
    val reciterKey: String,
    val batches: List<DownloadBatch>,
) {
    val total: Int get() = batches.sumOf { it.total }
    val done: Int get() = batches.sumOf { it.done }
    val failed: Int get() = batches.sumOf { it.failed }
    val bytes: Long get() = batches.sumOf { it.bytes }
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val isComplete: Boolean get() = total > 0 && done == total
}

data class DownloadsUiState(
    val loading: Boolean = true,
    val catalogueUnavailable: Boolean = false,
    val reciters: List<ReciterInfo> = emptyList(),
    val selectedReciterKey: String? = null,
    val selectedEditionKey: String? = null,
    val mode: ScopeMode = ScopeMode.PAGE,
    /** Surah picker, and the ayah picker inside it. */
    val surah: Int = 1,
    val ayah: Int = 1,
    /** Juz picker. */
    val juz: Int = 1,
    /** Page picker. 1-based and inclusive at both ends. */
    val fromPage: Int = 1,
    val toPage: Int = 5,
    /** Total pages in the bundled mushaf; 0 until the content loads. */
    val pageCount: Int = 0,
    /**
     * The scope the current pickers describe, resolved by the view model.
     *
     * Held as a value rather than derived from the pickers because a page range needs the verse
     * table, which lives in the content repository — the state must not carry 604 pages just so a
     * getter can walk them on every recomposition.
     */
    val scope: DownloadScope = DownloadScope.Surah(1),
    val scopeAyahs: Int = 0,
    /** Set only in [ScopeMode.PAGE], so the label can say "pages 1–5" instead of a verse range. */
    val scopePages: Pair<Int, Int>? = null,
    val batches: List<DownloadBatch> = emptyList(),
    val totalBytes: Long = 0,
    val pendingFiles: Int = 0,
    val busy: Boolean = false,
    val notice: DownloadNotice? = null,
) {
    val selectedReciter: ReciterInfo? get() = reciters.firstOrNull { it.key == selectedReciterKey }

    val editions: List<EditionInfo> get() = selectedReciter?.downloadable.orEmpty()

    val selectedEdition: EditionInfo?
        get() = editions.firstOrNull { it.key == selectedEditionKey } ?: editions.firstOrNull()

    /** The selected edition's granularity, which is what the pickers have to respect. */
    val granularity: EditionGranularity
        get() = selectedEdition?.granularity ?: EditionGranularity.AYAH

    /** A juz-granularity edition can only be fetched whole, so only the juz picker applies. */
    val scopeModes: List<ScopeMode>
        get() = if (granularity == EditionGranularity.JUZ) {
            listOf(ScopeMode.JUZ)
        } else {
            ScopeMode.entries
        }

    /** Ayahs of the chosen surah — the upper bound of the ayah picker. */
    val ayahCountOfSurah: Int get() = AyahNumbering.ayahCount(surah)

    /**
     * Rough size of the download, shown before anything is fetched.
     *
     * Two formulas because there are two units: a juz edition's file is a whole juz of audio
     * (about 45 minutes), not `148 × 7 s`, so the ayah-based estimate would be four times low.
     */
    val estimatedBytes: Long
        get() {
            val edition = selectedEdition ?: return 0L
            return if (edition.granularity == EditionGranularity.JUZ) {
                DownloadSizing.estimateJuzBytes(edition.bitrateKbps, juzCountInScope)
            } else {
                DownloadSizing.estimateBytes(edition.bitrateKbps, scopeAyahs)
            }
        }

    /** How many juz files the scope would fetch. One for a juz scope; one per juz touched otherwise. */
    val juzCountInScope: Int
        get() = when (val current = scope) {
            is DownloadScope.Juz -> 1
            else -> ((scopeAyahs + DownloadSizing.NOMINAL_AYAHS_PER_JUZ - 1) /
                DownloadSizing.NOMINAL_AYAHS_PER_JUZ).coerceAtLeast(1)
        }

    /** What is on disk, grouped by edition. Ordered so the most complete sits first. */
    val groups: List<EditionGroup>
        get() = batches
            .groupBy { it.editionKey to it.reciterKey }
            .map { (key, list) -> EditionGroup(key.first, key.second, list) }
            .sortedWith(compareByDescending<EditionGroup> { it.bytes }.thenBy { it.editionKey })

    val canDownload: Boolean
        get() = selectedEdition != null && !busy && scopeAyahs > 0
}

/**
 * The downloads screen's state: the catalogue, what to fetch, and what is already on disk.
 *
 * Four decisions worth stating:
 *
 *  - **The catalogue is fetched on open and cached offline.** A downloads screen that needs the
 *    network to render is absurd, and the cached copy also carries each edition's style, which is
 *    what lets the reader tell a downloaded file apart from a different performance.
 *  - **The scope is chosen on the client, the file granularity by the server.** The reader picks
 *    "surah 2"; whether that becomes 286 files or one is the edition's business, and the app only
 *    reports what came back.
 *  - **Pages are resolved against the bundled mushaf.** The backend has no page scope, and it
 *    should not grow one: page boundaries are a property of the *mushaf text*, which the app
 *    already ships. A page range becomes a verse range here, and the wire stays four scopes wide.
 *  - **A juz edition's pickers collapse to juz.** Offering "surah" for a reciter who publishes
 *    only whole juzes would promise a precision that does not exist.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manager: DownloadManager,
    private val catalogue: RecitationCatalogueRepository,
    private val content: QuranContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    /** The bundled mushaf, used to turn a page range into a verse range. Not part of the UI state. */
    private var pages: List<QuranPage> = emptyList()

    init {
        viewModelScope.launch {
            manager.observeBatches().collect { list -> _state.update { it.copy(batches = list) } }
        }
        viewModelScope.launch {
            manager.observeTotalBytes().collect { bytes -> _state.update { it.copy(totalBytes = bytes) } }
        }
        viewModelScope.launch {
            manager.observePending().collect { pending -> _state.update { it.copy(pendingFiles = pending) } }
        }
        loadPages()
        loadCatalogue()
    }

    /**
     * Loads the mushaf so the page picker has something to point at.
     *
     * Failure is not surfaced: without pages the page mode simply reports no scope and cannot be
     * downloaded, while surah/juz/ayah keep working. A downloads screen that refuses to open
     * because the *reader's* content failed would be a worse trade.
     */
    private fun loadPages() {
        viewModelScope.launch {
            runCatching { content.content().pages }.onSuccess { loaded ->
                pages = loaded
                _state.update { it.copy(pageCount = loaded.size) }
                recomputeScope()
            }
        }
    }

    private fun loadCatalogue() {
        viewModelScope.launch {
            // Show the cached copy first so the screen is usable offline…
            val cached = catalogue.cached()
            if (cached.isNotEmpty()) {
                _state.update { it.withCatalogue(cached, loading = false) }
                recomputeScope()
            }
            // …then refresh, which also repairs a stale cache.
            val refreshed = catalogue.refresh()
            val list = refreshed.getOrNull()
            if (list.isNullOrEmpty()) {
                _state.update {
                    it.copy(loading = false, catalogueUnavailable = it.reciters.isEmpty())
                }
            } else {
                _state.update { it.withCatalogue(list, loading = false) }
                recomputeScope()
            }
        }
    }

    /** Chooses the reciter and preselects its best edition, so one tap is enough to download. */
    fun onReciter(key: String) {
        _state.update { current ->
            val reciter = current.reciters.firstOrNull { it.key == key }
            val edition = reciter?.preferredEdition()
            current.copy(
                selectedReciterKey = key,
                selectedEditionKey = edition?.key,
                // A juz-only edition has exactly one mode; snapping here means the segmented
                // control never shows a choice the edition cannot honour.
                mode = if (edition?.granularity == EditionGranularity.JUZ) ScopeMode.JUZ else current.mode,
            )
        }
        recomputeScope()
    }

    fun onEdition(key: String) {
        _state.update { current ->
            val edition = current.editions.firstOrNull { it.key == key }
            current.copy(
                selectedEditionKey = key,
                mode = if (edition?.granularity == EditionGranularity.JUZ) ScopeMode.JUZ else current.mode,
            )
        }
        recomputeScope()
    }

    fun onMode(mode: ScopeMode) {
        _state.update { it.copy(mode = mode) }
        recomputeScope()
    }

    fun onSurah(number: Int) {
        val surah = number.coerceIn(1, AyahNumbering.SURAH_COUNT)
        _state.update {
            // The ayah picker is bounded by the surah, so a smaller surah must pull it back.
            it.copy(surah = surah, ayah = it.ayah.coerceIn(1, AyahNumbering.ayahCount(surah)))
        }
        recomputeScope()
    }

    fun onAyah(number: Int) {
        _state.update { it.copy(ayah = number.coerceIn(1, AyahNumbering.ayahCount(it.surah))) }
        recomputeScope()
    }

    fun onJuz(number: Int) {
        _state.update { it.copy(juz = number.coerceIn(1, AyahNumbering.JUZ_COUNT)) }
        recomputeScope()
    }

    fun onFromPage(page: Int) {
        _state.update {
            val max = it.pageCount.coerceAtLeast(1)
            val from = page.coerceIn(1, max)
            // Dragging the start past the end carries the end along instead of refusing the drag.
            it.copy(fromPage = from, toPage = it.toPage.coerceAtLeast(from))
        }
        recomputeScope()
    }

    fun onToPage(page: Int) {
        _state.update {
            val max = it.pageCount.coerceAtLeast(1)
            val to = page.coerceIn(it.fromPage, max)
            it.copy(toPage = to)
        }
        recomputeScope()
    }

    /** The owner's shorthand: the first five pages of the mushaf, in one tap. */
    fun onFirstFivePages() {
        _state.update {
            it.copy(mode = ScopeMode.PAGE, fromPage = 1, toPage = minOf(5, it.pageCount.coerceAtLeast(5)))
        }
        recomputeScope()
    }

    fun download() {
        val current = _state.value
        val edition = current.selectedEdition ?: return
        if (current.busy || current.scopeAyahs == 0) return
        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            val result = manager.request(edition, current.scope)
            _state.update { it.copy(busy = false) }
            result.onSuccess { request ->
                _state.update {
                    it.copy(
                        notice = DownloadNotice.Queued(
                            files = request.queued,
                            alreadyHave = request.alreadyDone,
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(notice = DownloadNotice.Failed(error.message)) }
            }
        }
    }

    fun retry(batch: DownloadBatch) {
        viewModelScope.launch { manager.retry(batch) }
    }

    fun delete(batch: DownloadBatch) {
        viewModelScope.launch { manager.delete(batch) }
    }

    /** Deletes every batch of one edition — the "free this space" gesture on a group card. */
    fun deleteGroup(group: EditionGroup) {
        viewModelScope.launch { group.batches.forEach { manager.delete(it) } }
    }

    /**
     * Re-resolves the pickers into a [DownloadScope].
     *
     * The single place that knows how a mode maps to a wire scope. Called after every picker
     * change rather than computed in a getter because a page range needs the verse table, and
     * because `scopeAyahs` must be exact — it is what the size estimate and the "36 ayahs" line
     * are built from.
     */
    private fun recomputeScope() {
        _state.update { current ->
            val (scope, ayahs, scopePages) = resolveScope(current)
            current.copy(scope = scope, scopeAyahs = ayahs, scopePages = scopePages)
        }
    }

    private fun resolveScope(state: DownloadsUiState): Triple<DownloadScope, Int, Pair<Int, Int>?> =
        when (state.mode) {
            ScopeMode.SURAH -> {
                val scope = DownloadScope.Surah(state.surah)
                Triple(scope, AyahNumbering.ayahCount(state.surah), null)
            }

            ScopeMode.AYAH -> {
                val scope = DownloadScope.Ayah(state.surah, state.ayah)
                Triple(scope, 1, null)
            }

            ScopeMode.JUZ -> {
                val scope = DownloadScope.Juz(state.juz)
                // Exact, not nominal: the mushaf table knows how many ayahs juz 17 has.
                Triple(scope, ayahsInJuz(state.juz), null)
            }

            ScopeMode.PAGE -> {
                val from = state.fromPage.coerceAtLeast(1)
                val to = state.toPage.coerceAtLeast(from)
                val first = pages.firstOrNull { it.page == from }?.verses?.firstOrNull()
                val last = pages.firstOrNull { it.page == to }?.verses?.lastOrNull()
                if (first == null || last == null) {
                    // No content yet: keep a legal scope so nothing crashes, and report 0 ayahs
                    // so the action stays disabled until the pages arrive.
                    Triple(DownloadScope.Range(from, 1, from, 1), 0, from to to)
                } else {
                    val scope = DownloadScope.Range(first.surah, first.ayah, last.surah, last.ayah)
                    Triple(scope, ayahsBetween(first.surah, first.ayah, last.surah, last.ayah), from to to)
                }
            }
        }

    /** Ayahs in a juz, derived from the same table the juz queue uses. */
    private fun ayahsInJuz(juz: Int): Int {
        val start = AyahNumbering.juzStart(juz) ?: return 0
        val next = AyahNumbering.juzStart(juz + 1)
        val from = AyahNumbering.globalAyah(start.first, start.second) ?: return 0
        val to = if (next == null) {
            AyahNumbering.TOTAL_AYAHS + 1
        } else {
            AyahNumbering.globalAyah(next.first, next.second) ?: (AyahNumbering.TOTAL_AYAHS + 1)
        }
        return (to - from).coerceAtLeast(0)
    }

    /** Inclusive count between two coordinates, in either direction. */
    private fun ayahsBetween(fromSurah: Int, fromAyah: Int, toSurah: Int, toAyah: Int): Int {
        val start = AyahNumbering.globalAyah(fromSurah, fromAyah) ?: return 0
        val end = AyahNumbering.globalAyah(toSurah, toAyah) ?: return 0
        return (kotlin.math.abs(end - start) + 1)
    }

    private fun DownloadsUiState.withCatalogue(list: List<ReciterInfo>, loading: Boolean): DownloadsUiState {
        // Keep the reader's choice across a refresh; fall back to the first reciter otherwise.
        val reciter = list.firstOrNull { it.key == selectedReciterKey } ?: list.firstOrNull()
        val edition = reciter?.let { chosen ->
            chosen.downloadable.firstOrNull { it.key == selectedEditionKey } ?: chosen.preferredEdition()
        }
        return copy(
            loading = loading,
            catalogueUnavailable = false,
            reciters = list,
            selectedReciterKey = reciter?.key,
            selectedEditionKey = edition?.key,
            mode = if (edition?.granularity == EditionGranularity.JUZ) ScopeMode.JUZ else mode,
        )
    }
}
