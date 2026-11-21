package ir.hawraa.quran.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.session.SessionStats
import ir.hawraa.quran.ui.screens.history.toSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Where to resume: the page index the reader should open on, plus everything the card
 * needs to look like a page of the mushaf rather than a generic "continue" row.
 */
data class ResumePoint(
    val pageIndex: Int,
    val surahName: String,
    val page: Int,
    /** The opening verses of that page, Uthmani, for the card's preview. */
    val preview: String,
    val juz: Int,
)

data class HomeUiState(
    val todaySeconds: Int = 0,
    val streakDays: Int = 0,
    val resume: ResumePoint? = null,
    /** Shown in place of the preview before the reader has read anything. */
    val basmala: String = "",
    /** How much reading the home screen counts as "a good day". */
    val dailyGoalSeconds: Int = DEFAULT_GOAL_SECONDS,
) {
    val hasReadToday: Boolean get() = todaySeconds > 0

    /** 0f..1f, clamped: a long session must not overflow the progress bar. */
    val goalFraction: Float
        get() = if (dailyGoalSeconds <= 0) {
            0f
        } else {
            (todaySeconds.toFloat() / dailyGoalSeconds).coerceIn(0f, 1f)
        }

    val todayMinutes: Int get() = todaySeconds / 60

    companion object {
        /** Ten minutes a day: enough to be real, small enough to reach. */
        const val DEFAULT_GOAL_SECONDS = 10 * 60
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    readingSessionDao: ReadingSessionDao,
    contentRepository: QuranContentRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        readingSessionDao.observeHistory(),
        flow { emit(contentRepository.content()) },
    ) { entities, quran ->
        val summaries = entities.map { it.toSummary() }
        val today = LocalDate.now()
        val todaySeconds = summaries
            .filter { SessionStats.dayOf(it.startedAt) == today }
            .sumOf { it.durationReadSec }

        // The most recent session decides where "continue" goes: its last page, and the
        // surah that page opens with.
        val last = summaries.maxByOrNull { it.startedAt }
        val resumePage = last?.page
        val resume = if (resumePage == null) {
            null
        } else {
            val index = quran.pages.indexOfFirst { it.page == resumePage }
            if (index < 0) {
                null
            } else {
                val page = quran.pages[index]
                ResumePoint(
                    pageIndex = index,
                    surahName = page.verses.firstOrNull()
                        ?.let { verse -> quran.surahs[verse.surah]?.nameFa }
                        .orEmpty(),
                    page = resumePage,
                    // Two verses is what fits the card; a real page opens with real text,
                    // which is a far better invitation than a label.
                    preview = page.verses.take(2).joinToString(" ") { it.textUthmani },
                    juz = page.juz,
                )
            }
        }

        HomeUiState(
            todaySeconds = todaySeconds,
            streakDays = SessionStats.streakDays(summaries, today),
            resume = resume,
            basmala = quran.basmalaUthmani,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
