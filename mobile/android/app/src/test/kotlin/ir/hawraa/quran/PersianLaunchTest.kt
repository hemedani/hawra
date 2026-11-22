package ir.hawraa.quran

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.screens.history.HistoryContent
import ir.hawraa.quran.ui.screens.history.HistoryUiState
import ir.hawraa.quran.ui.screens.home.HomeContent
import ir.hawraa.quran.ui.screens.home.HomeUiState
import ir.hawraa.quran.ui.screens.reader.ReaderContent
import ir.hawraa.quran.ui.screens.reader.ReaderUiState
import ir.hawraa.quran.ui.screens.settings.SettingsContent
import ir.hawraa.quran.ui.theme.HawraaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The device is English but the app is set to Persian — the exact situation that made
 * the app fail to open. Every screen is composed under [LocalizedApp] so a failure here
 * names the screen responsible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
class PersianLaunchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    private fun render(block: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            LocalizedApp(language = AppLanguage.PERSIAN) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) { block() }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun home_opens_in_persian() {
        render {
            HomeContent(
                state = HomeUiState(todaySeconds = 125, streakDays = 3),
                onStartReading = {},
                onResume = {},
                onHistory = {},
                onSettings = {},
            )
        }
        val tree = composeRule.onRoot().printToString()
        assertTrue(tree.contains("ادامه خواندن") || tree.contains("شروع خواندن"))
    }

    @Test
    fun reader_opens_in_persian() {
        render {
            ReaderContent(
                state = ReaderUiState(
                    isLoading = false,
                    pages = content.pages,
                    surahs = content.surahs,
                    basmalaUthmani = content.basmalaUthmani,
                    settings = ReadingSettings(),
                ),
                onSettings = {},
            )
        }
        val tree = composeRule.onRoot().printToString()
        // The reader shows no page number any more, so the Persian chrome it does keep is
        // the juz marker beside the surah name in the top bar.
        assertTrue(tree.contains("جزء"))
    }

    @Test
    fun history_opens_in_persian() {
        render {
            HistoryContent(state = HistoryUiState(isLoading = false), onBack = {})
        }
        val tree = composeRule.onRoot().printToString()
        assertTrue(tree.contains("تاریخ"))
    }

    @Test
    fun settings_opens_in_persian() {
        render {
            SettingsContent(
                themeMode = ThemeMode.LIGHT,
                language = AppLanguage.PERSIAN,
                reading = ReadingSettings(),
                previewText = content.basmalaUthmani,
                onBack = {},
                onLanguage = {},
                onThemeMode = {},
                onFontSize = {},
                onShowTranslation = {},
                onLetterInk = {},
                onDiacriticInk = {},
            )
        }
        val tree = composeRule.onRoot().printToString()
        assertTrue(tree.contains("تنظیمات"))
    }
}
