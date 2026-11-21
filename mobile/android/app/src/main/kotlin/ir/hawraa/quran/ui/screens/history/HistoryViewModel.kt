package ir.hawraa.quran.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.local.ReadingSessionEntity
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.session.SessionStats
import ir.hawraa.quran.domain.session.SessionSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One row of the history list, already resolved for display. */
data class HistoryRow(
    val clientId: String,
    val surahName: String,
    val ayahSpan: String,
    val startedAt: Long,
    val durationReadSec: Int,
    val completed: Boolean,
)

data class HistoryDay(
    val day: LocalDate,
    val durationReadSec: Int,
    val rows: List<HistoryRow>,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val days: List<HistoryDay> = emptyList(),
    val totalSeconds: Int = 0,
    val pagesTouched: Int = 0,
    val streakDays: Int = 0,
    val totalSessions: Int = 0,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    readingSessionDao: ReadingSessionDao,
    contentRepository: QuranContentRepository,
) : ViewModel() {

    // The content repository caches its parse, so re-emitting on each collection is cheap.
    private val content = flow { emit(contentRepository.content()) }

    val state: StateFlow<HistoryUiState> = combine(
        readingSessionDao.observeHistory(),
        content,
    ) { entities, quran ->
        val summaries = entities.map { it.toSummary() }
        val byId = entities.associateBy { it.clientId }

        HistoryUiState(
            isLoading = false,
            days = SessionStats.byDay(summaries).map { bucket ->
                HistoryDay(
                    day = bucket.day,
                    durationReadSec = bucket.durationReadSec,
                    rows = bucket.sessions.map { summary ->
                        HistoryRow(
                            clientId = summary.clientId,
                            surahName = quran.surahs[summary.surah]?.nameFa
                                ?: summary.surah.toString(),
                            ayahSpan = summary.ayahSpan,
                            startedAt = summary.startedAt,
                            durationReadSec = summary.durationReadSec,
                            completed = byId[summary.clientId]?.completed ?: summary.completed,
                        )
                    },
                )
            },
            totalSeconds = SessionStats.totalSeconds(summaries),
            pagesTouched = SessionStats.pagesTouched(summaries),
            streakDays = SessionStats.streakDays(summaries, LocalDate.now()),
            totalSessions = summaries.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )
}

/** Storage row → the pure summary the statistics understand. */
internal fun ReadingSessionEntity.toSummary(): SessionSummary = SessionSummary(
    clientId = clientId,
    startedAt = startedAt,
    endedAt = endedAt ?: updatedAt,
    durationReadSec = durationReadSec,
    surah = surah,
    fromAyah = fromAyah,
    toAyah = toAyah,
    page = fromPage ?: toPage,
    completed = completed,
    interrupted = interrupted,
)
