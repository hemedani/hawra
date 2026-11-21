package ir.hawraa.quran.ui.screens.presets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.repository.PresetRepository
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.TranslationKeys
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetEditorUiState(
    val name: String = "",
    val steps: List<PresetStep> = emptyList(),
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    /** A preset with no name or no playable step cannot recite anything, so saving is blocked. */
    val canSave: Boolean
        get() = !loading && !saving && name.isNotBlank() && steps.any { it.isPlayable }

    val playable: List<PresetStep> get() = steps.filter { it.isPlayable }
}

/**
 * The steps the editor can add.
 *
 * `TAFSIR` is absent on purpose: no tafsir text ships in the bundle yet, so a tafsir step added
 * here would be a step that cannot speak. The model and the server still carry it, and a preset
 * that already has one keeps it — the editor just does not invent one.
 */
enum class EditorStep(val kind: PresetStepKind) {
    AYAH(PresetStepKind.AYAH),
    TRANSLATION(PresetStepKind.TRANSLATION),
    SILENCE(PresetStepKind.SILENCE),
}

/**
 * Creates or edits one preset.
 *
 * The editor is deliberately *not* a form that can express everything the model can hold:
 * there is no speed override, no per-step language and no pinned reciter. Those exist on the
 * wire because a preset must survive arriving from a server that supports them, but a phone
 * editor that offered every field would make "verse twice, then the translation" — the thing
 * people actually want — look complicated. Editing a preset created elsewhere preserves the
 * fields the editor does not show, because the whole step list is carried through.
 */
@HiltViewModel
class PresetEditorViewModel @Inject constructor(
    private val repository: PresetRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val clientId: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(PresetEditorUiState(isNew = clientId == null))
    val state: StateFlow<PresetEditorUiState> = _state.asStateFlow()

    init {
        if (clientId == null) {
            _state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                val preset = repository.preset(clientId)
                _state.update { current ->
                    if (preset == null) {
                        current.copy(loading = false, isNew = true)
                    } else {
                        current.copy(
                            name = preset.name,
                            steps = preset.steps,
                            isNew = false,
                            loading = false,
                        )
                    }
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value) }

    /**
     * Appends a step. A translation step is created with the only bundled translation key, so
     * the new step is playable immediately — a step that needs a key nobody has is a step that
     * silently does nothing.
     */
    fun addStep(option: EditorStep) {
        val step = when (option) {
            EditorStep.AYAH -> PresetStep.ayah()
            EditorStep.TRANSLATION -> PresetStep.translation(TranslationKeys.ANSARIAN)
            EditorStep.SILENCE -> PresetStep.silence(DEFAULT_SILENCE_MS)
        }
        _state.update { it.copy(steps = it.steps + step) }
    }

    fun removeStep(index: Int) = _state.update { current ->
        if (index !in current.steps.indices) current
        else current.copy(steps = current.steps.filterIndexed { i, _ -> i != index })
    }

    /** One more or one fewer repetition, clamped to what the player can honour. */
    fun changeRepeat(index: Int, delta: Int) = _state.update { current ->
        if (index !in current.steps.indices) {
            current
        } else {
            current.copy(
                steps = current.steps.mapIndexed { i, step ->
                    if (i != index) step
                    else step.copy(repeat = (step.repeat + delta).coerceIn(1, PresetStep.MAX_REPEAT))
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true) }

        viewModelScope.launch {
            if (clientId == null) {
                repository.create(name = current.name.trim(), steps = current.steps)
            } else {
                // Read back and edit through the repository, which owns the revision bump and the
                // last-write-wins stamp. Building the row here would risk a stale stamp — the
                // one mistake that looks successful locally and then loses to the server.
                val existing: Preset? = repository.preset(clientId)
                if (existing != null) {
                    repository.update(
                        existing.copy(
                            name = current.name.trim().ifBlank { existing.name },
                            steps = current.steps,
                        ),
                    )
                }
            }
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    private companion object {
        /** Long enough to hear as a pause, short enough not to feel like a hang. */
        const val DEFAULT_SILENCE_MS = 1_000
    }
}
