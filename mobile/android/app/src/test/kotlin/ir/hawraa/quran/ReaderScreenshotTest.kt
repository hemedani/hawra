package ir.hawraa.quran

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.screens.reader.RecitationUiState
import ir.hawraa.quran.ui.screens.reader.ReaderContent
import ir.hawraa.quran.ui.screens.reader.ReaderUiState
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the reader on the JVM with Robolectric's native graphics (real Skia +
 * HarfBuzz, real bundled fonts) and writes PNGs to `app/build/screenshots/`.
 *
 * This is the gate for the mushaf typography: it proves on Android's own text
 * stack that the two derived ink layers register exactly, in both themes.
 * Run `./gradlew testDebugUnitTest` then open the PNGs.
 *
 * Every capture goes through [TestShell] — the ambient canvas, the real screen frame and the dock —
 * because the reader is deliberately transparent and the dock is part of every destination. A bare
 * `ReaderContent` photographs a screen the app never shows and cannot catch chrome, dock or inset
 * regressions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class ReaderScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    /**
     * The catalogue always loads in the real app, so every reader screenshot carries the play
     * affordance — the screenshots are the visual gate, and a gate that omits a permanent part of
     * the layout is a gate that misses regressions in it.
     */
    private fun state(settings: ReadingSettings) = ReaderUiState(
        isLoading = false,
        pages = content.pages,
        surahs = content.surahs,
        basmalaUthmani = content.basmalaUthmani,
        settings = settings,
        recitation = RecitationUiState(
            available = true,
            reciterNameFa = "شهریار پرهیزگار",
            reciterNameEn = "Shahriar Parhizgar",
        ),
    )

    private fun nowPlaying() = NowPlayingUi(
        title = "فاتحه",
        subtitle = "آیۀ ۳ · شهریار پرهیزگار",
        number = "۱",
        isPlaying = true,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * The reader as the app actually composes it: the ambient canvas, the reading pane, the
     * floating action box and the dock. Locale is explicit so both `fa` and `en` chrome are gated.
     */
    private fun stage(
        language: AppLanguage = AppLanguage.PERSIAN,
        theme: ThemeMode,
        nowPlaying: NowPlayingUi? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    TestShell(selected = "reader", nowPlaying = nowPlaying) {
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun reader_shell_with_dock_and_selection() {
        stage(theme = ThemeMode.LIGHT, nowPlaying = nowPlaying()) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        composeRule.onNodeWithTag("verse-1:3").performClick()
        capture("reader-shell.png")
    }

    @Test
    fun reader_shell_with_dock_and_selection_en() {
        stage(language = AppLanguage.ENGLISH, theme = ThemeMode.LIGHT, nowPlaying = nowPlaying()) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        composeRule.onNodeWithTag("verse-1:3").performClick()
        capture("reader-shell-en.png")
    }

    /**
     * The dock with nothing playing. This is not a redundant capture: the player half is an
     * [androidx.compose.animation.AnimatedVisibility], so the idle state is a *different height*
     * and a *different seam* — and it is the state the app opens in.
     */
    @Test
    fun reader_shell_dock_idle() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-shell-idle.png")
    }

    @Test
    fun reader_shell_dock_idle_en() {
        stage(language = AppLanguage.ENGLISH, theme = ThemeMode.LIGHT) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-shell-idle-en.png")
    }

    /** The dock in the dark theme: the fill, border and rim light all invert. */
    @Test
    fun reader_shell_dark() {
        stage(theme = ThemeMode.DARK, nowPlaying = nowPlaying()) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-shell-dark.png")
    }

    @Test
    fun reader_shell_dark_en() {
        stage(language = AppLanguage.ENGLISH, theme = ThemeMode.DARK, nowPlaying = nowPlaying()) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-shell-dark-en.png")
    }

    @Test
    fun reader_light_default_ink() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-light.png")
    }

    @Test
    fun reader_light_default_ink_en() {
        stage(language = AppLanguage.ENGLISH, theme = ThemeMode.LIGHT) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-light-en.png")
    }

    @Test
    fun reader_dark_gold_marks() {
        stage(theme = ThemeMode.DARK) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-dark.png")
    }

    @Test
    fun reader_dark_gold_marks_en() {
        stage(language = AppLanguage.ENGLISH, theme = ThemeMode.DARK) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        capture("reader-dark-en.png")
    }

    @Test
    fun reader_light_green_letters_burgundy_marks() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(
                state = state(
                    ReadingSettings(
                        fontSizeSp = 34,
                        letterInk = LetterInk.GREEN,
                        diacriticInk = DiacriticInk.BURGUNDY,
                    ),
                ),
                onSettings = {},
            )
        }
        capture("reader-ink-variant.png")
    }

    /**
     * Page 2 opens Al-Baqarah: it is the first page that needs the standalone
     * basmala line and it carries the longest verses, so it exercises the wrapping
     * and the multi-line two-layer registration.
     */
    @Test
    fun reader_light_page_two_with_basmala() {
        val pageTwo = state(ReadingSettings()).copy(pages = content.pages.drop(1))
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(state = pageTwo, onSettings = {})
        }
        capture("reader-page-two.png")
    }

    /**
     * Tapping the Arabic highlights the Arabic **only**: gold wash, leading-edge bar,
     * filled rosette — and its translation appears even though translations are switched
     * off in this state (the tap is a peek, not a settings change).
     */
    @Test
    fun reader_light_selected_verse() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(
                state = state(ReadingSettings(showTranslation = false)),
                onSettings = {},
            )
        }
        composeRule.onNodeWithTag("verse-1:3").performClick()
        capture("reader-selected-verse.png")
    }

    /**
     * Tapping a translation highlights that translation **only** — the Arabic above it
     * stays unselected. This is the state the reader could not express before: verse and
     * translation are separate panes with separate state, so a later layout can show
     * three translations and a commentary and let the reader point at any one of them.
     */
    @Test
    fun reader_light_selected_translation_only() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(
                state = state(ReadingSettings(showTranslation = true)),
                onSettings = {},
            )
        }
        composeRule.onNodeWithTag("translation-1:3-ansarian").performClick()
        capture("reader-selected-translation.png")
    }

    /**
     * The end of al-Fatiha running straight into the al-Baqarah plate: page 1 ends and
     * page 2 begins with no break, no rule and no page number between them. This is the
     * continuous scroll, captured.
     */
    @Test
    fun reader_continuous_across_a_page_boundary() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(state = state(ReadingSettings()), onSettings = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mushaf-scroll").performScrollToIndex(6)
        capture("reader-page-boundary.png")
    }

    /**
     * Recitation state: the verse being recited tinted in the accent (not the gold of a tapped
     * verse), rosette filled, translation shown. Playback controls now live in the dock, which the
     * shell draws — see `reader_shell_with_dock_and_selection`.
     */
    @Test
    fun reader_light_recitation_playing() {
        stage(theme = ThemeMode.LIGHT) {
            ReaderContent(
                state = state(ReadingSettings()).copy(
                    recitation = RecitationUiState(
                        available = true,
                        reciterNameFa = "شهریار پرهیزگار",
                        reciterNameEn = "Shahriar Parhizgar",
                        isPlaying = true,
                        speed = PlaybackSpeed.ONE_AND_QUARTER,
                        repeatAyah = true,
                        playingVerse = 1 to 3,
                        hasQueue = true,
                    ),
                ),
                onSettings = {},
            )
        }
        capture("reader-recitation.png")
    }

    /** The same state in the dark theme, paused mid-page. */
    @Test
    fun reader_dark_recitation_paused() {
        stage(theme = ThemeMode.DARK) {
            ReaderContent(
                state = state(ReadingSettings()).copy(
                    recitation = RecitationUiState(
                        available = true,
                        reciterNameFa = "شهریار پرهیزگار",
                        reciterNameEn = "Shahriar Parhizgar",
                        isPlaying = false,
                        playingVerse = 1 to 5,
                        hasQueue = true,
                    ),
                ),
                onSettings = {},
            )
        }
        capture("reader-recitation-dark.png")
    }
}
