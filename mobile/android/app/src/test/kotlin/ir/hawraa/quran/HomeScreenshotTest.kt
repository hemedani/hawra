package ir.hawraa.quran

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.screens.home.HomeContent
import ir.hawraa.quran.ui.screens.home.HomeUiState
import ir.hawraa.quran.ui.screens.home.ResumePoint
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home in both themes and both locales, with and without something to resume. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    /** What the real ViewModel would produce for page 2. */
    private fun resumeOnPageTwo(): ResumePoint {
        val page = content.pages[1]
        return ResumePoint(
            pageIndex = 1,
            surahName = "بقره",
            page = page.page,
            preview = page.verses.take(2).joinToString(" ") { it.textUthmani },
            juz = page.juz,
        )
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun stage(
        language: AppLanguage,
        theme: ThemeMode,
        state: HomeUiState,
        name: String,
    ) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    TestShell(selected = "home") {
                        HomeContent(
                            state = state,
                            onStartReading = {},
                            onResume = {},
                            onHistory = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun home_light_with_resume_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.LIGHT,
        state = HomeUiState(
            todaySeconds = 24 * 60,
            streakDays = 4,
            resume = resumeOnPageTwo(),
        ),
        name = "home-light-fa.png",
    )

    @Test
    fun home_light_with_resume_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.LIGHT,
        state = HomeUiState(
            todaySeconds = 24 * 60,
            streakDays = 4,
            resume = resumeOnPageTwo(),
        ),
        name = "home-light-en.png",
    )

    /**
     * No history yet, and a part-finished goal — this is the state that also shows which
     * side the progress bar grows from.
     */
    @Test
    fun home_dark_first_run_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.DARK,
        state = HomeUiState(
            todaySeconds = 4 * 60,
            streakDays = 2,
            basmala = content.basmalaUthmani,
        ),
        name = "home-dark-fa.png",
    )

    @Test
    fun home_dark_first_run_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.DARK,
        state = HomeUiState(
            todaySeconds = 4 * 60,
            streakDays = 2,
            basmala = content.basmalaUthmani,
        ),
        name = "home-dark-en.png",
    )
}
