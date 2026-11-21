package ir.hawraa.quran.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.MotionSetting
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted user preferences. Single owner — screens and ViewModels read/write
 * through this, never DataStore directly.
 *
 * Reading settings are stored as plain primitives (int/bool/string preset keys),
 * so a future palette change never invalidates a user's saved choice.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontSizeKey = intPreferencesKey("reader_font_size_sp")
    private val showTranslationKey = booleanPreferencesKey("reader_show_translation")
    private val letterInkKey = stringPreferencesKey("reader_letter_ink")
    private val diacriticInkKey = stringPreferencesKey("reader_diacritic_ink")
    private val appLanguageKey = stringPreferencesKey("app_language")
    private val reciterKey = stringPreferencesKey("reciter_key")
    private val playbackSpeedKey = floatPreferencesKey("playback_speed")
    private val repeatAyahKey = booleanPreferencesKey("repeat_ayah")
    private val motionKey = stringPreferencesKey("motion_preference")
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeKey])
    }

    val appLanguage: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromKey(prefs[appLanguageKey])
    }

    val motionSetting: Flow<MotionSetting> = dataStore.data.map { prefs ->
        MotionSetting.fromKey(prefs[motionKey])
    }

    val hapticsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hapticsKey] ?: true
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[appLanguageKey] = language.key }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[themeKey] = mode.key }
    }

    suspend fun setMotionSetting(setting: MotionSetting) {
        dataStore.edit { prefs -> prefs[motionKey] = setting.key }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[hapticsKey] = enabled }
    }

    val readingSettings: Flow<ReadingSettings> = dataStore.data.map { prefs ->
        ReadingSettings(
            fontSizeSp = (prefs[fontSizeKey] ?: ReadingSettings.DEFAULT_FONT_SIZE_SP)
                .coerceIn(ReadingSettings.MIN_FONT_SIZE_SP, ReadingSettings.MAX_FONT_SIZE_SP),
            showTranslation = prefs[showTranslationKey] ?: true,
            letterInk = LetterInk.fromKey(prefs[letterInkKey]),
            diacriticInk = DiacriticInk.fromKey(prefs[diacriticInkKey]),
        )
    }

    suspend fun setFontSize(sp: Int) {
        dataStore.edit { prefs ->
            prefs[fontSizeKey] = sp.coerceIn(
                ReadingSettings.MIN_FONT_SIZE_SP,
                ReadingSettings.MAX_FONT_SIZE_SP,
            )
        }
    }

    suspend fun setShowTranslation(show: Boolean) {
        dataStore.edit { prefs -> prefs[showTranslationKey] = show }
    }

    suspend fun setLetterInk(ink: LetterInk) {
        dataStore.edit { prefs -> prefs[letterInkKey] = ink.key }
    }

    suspend fun setDiacriticInk(ink: DiacriticInk) {
        dataStore.edit { prefs -> prefs[diacriticInkKey] = ink.key }
    }

    val recitationSettings: Flow<RecitationSettings> = dataStore.data.map { prefs ->
        RecitationSettings(
            reciterKey = prefs[reciterKey],
            speed = PlaybackSpeed.fromValue(prefs[playbackSpeedKey] ?: PlaybackSpeed.NORMAL.value),
            repeatAyah = prefs[repeatAyahKey] ?: false,
        )
    }

    /** `null` clears the choice so the catalogue default applies again. */
    suspend fun setReciter(key: String?) {
        dataStore.edit { prefs ->
            if (key == null) prefs.remove(reciterKey) else prefs[reciterKey] = key
        }
    }

    suspend fun setPlaybackSpeed(speed: PlaybackSpeed) {
        dataStore.edit { prefs -> prefs[playbackSpeedKey] = speed.value }
    }

    suspend fun setRepeatAyah(repeat: Boolean) {
        dataStore.edit { prefs -> prefs[repeatAyahKey] = repeat }
    }
}
