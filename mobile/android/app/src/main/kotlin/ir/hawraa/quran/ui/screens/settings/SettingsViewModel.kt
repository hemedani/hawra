package ir.hawraa.quran.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.data.quran.RecitationRepository
import ir.hawraa.quran.data.repository.SettingsRepository
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    contentRepository: QuranContentRepository,
    recitationRepository: RecitationRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    val appLanguage: StateFlow<AppLanguage> = settingsRepository.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLanguage.DEFAULT,
    )

    val readingSettings: StateFlow<ReadingSettings> = settingsRepository.readingSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReadingSettings(),
    )

    /**
     * Sample line for the live preview. Taken from the bundled asset (never typed
     * into code) so the preview always shows the real Uthmani text.
     */
    private val _previewText = MutableStateFlow("")
    val previewText: StateFlow<String> = _previewText.asStateFlow()

    val recitation: StateFlow<RecitationSettings> = settingsRepository.recitationSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RecitationSettings(),
    )

    /** Empty until the bundled catalogue loads; the section hides itself meanwhile. */
    private val _reciters = MutableStateFlow<List<Reciter>>(emptyList())
    val reciters: StateFlow<List<Reciter>> = _reciters.asStateFlow()

    init {
        viewModelScope.launch {
            _previewText.value = contentRepository.content().basmalaUthmani
        }
        viewModelScope.launch {
            _reciters.value = recitationRepository.catalog().reciters
        }
    }

    fun setReciter(key: String?) {
        viewModelScope.launch { settingsRepository.setReciter(key) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setAppLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { settingsRepository.setFontSize(sp) }
    }

    fun setShowTranslation(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowTranslation(show) }
    }

    fun setLetterInk(ink: LetterInk) {
        viewModelScope.launch { settingsRepository.setLetterInk(ink) }
    }

    fun setDiacriticInk(ink: DiacriticInk) {
        viewModelScope.launch { settingsRepository.setDiacriticInk(ink) }
    }
}
