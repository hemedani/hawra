package ir.hawraa.quran.ui.screens.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.repository.PresetCoverageSummary
import ir.hawraa.quran.data.repository.PresetProgressRepository
import ir.hawraa.quran.data.repository.PresetRepository
import ir.hawraa.quran.domain.preset.Preset
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the preset list: the recipe, and what it has covered so far. */
data class PresetRow(
    val preset: Preset,
    val coverage: PresetCoverageSummary?,
)

data class PresetsUiState(
    val rows: List<PresetRow> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * The preset list.
 *
 * Coverage is observed **per preset and combined**, rather than as one big query: the ledger is
 * keyed by preset, each preset's percentage is independent of the others, and a single aggregate
 * would have to be recomputed whenever any preset changed. Combining N small flows also means a
 * newly created preset starts at 0 % without a special case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val repository: PresetRepository,
    private val progress: PresetProgressRepository,
) : ViewModel() {

    val state: StateFlow<PresetsUiState> = repository.observePresets()
        .flatMapLatest { presets ->
            if (presets.isEmpty()) {
                flowOf(PresetsUiState(rows = emptyList(), loading = false))
            } else {
                combine(presets.map { preset -> progress.observeCoverage(preset.clientId) }) { summaries ->
                    PresetsUiState(
                        rows = presets.map { preset ->
                            PresetRow(
                                preset = preset,
                                coverage = summaries.firstOrNull { it.presetClientId == preset.clientId },
                            )
                        },
                        loading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    fun setDefault(clientId: String) {
        viewModelScope.launch { repository.setDefault(clientId) }
    }

    fun remove(clientId: String) {
        viewModelScope.launch { repository.remove(clientId) }
    }
}
