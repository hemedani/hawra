package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock in every state it can be seen in (plan 02 Task 4): nav-only, playing, a
 * juz-granularity title, and buffering — in both themes and both locales, so the RTL
 * order, the glyph flip and the pill-to-panel radius change are all on the record.
 *
 * The dock is rendered over the real ambient canvas rather than a blank window, because
 * the frost pane is translucent: a capture on white is a surface the app never shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class DockScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun playing(persian: Boolean) = NowPlayingUi(
        title = if (persian) "فاتحه" else "Al-Fatiha",
        subtitle = if (persian) "آیۀ ۳ · شهریار پرهیزگار" else "Ayah 3 · Shahriar Parhizgar",
        number = if (persian) "۱" else "1",
        isPlaying = true,
    )

    private fun juz(persian: Boolean) = NowPlayingUi(
        title = if (persian) "جزء ۵" else "Juz 5",
        subtitle = if (persian) "جزء‌به‌جزء · شهریار پرهیزگار" else "Juz by juz · Shahriar Parhizgar",
        number = if (persian) "۵" else "5",
        isPlaying = true,
    )

    private fun busy(persian: Boolean) = playing(persian).copy(waiting = true)

    private val progress: StateFlow<Float> = MutableStateFlow(0.42f)

    @Composable
    private fun Stage(
        language: AppLanguage,
        theme: ThemeMode,
        nowPlaying: NowPlayingUi?,
        miniProgress: StateFlow<Float>?,
    ) {
        LocalizedApp(language) {
            HawraaTheme(themeMode = theme) {
                Box(Modifier.fillMaxSize()) {
                    AmbientCanvas()
                    AppDock(
                        selectedRoute = "reader",
                        onSelect = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        nowPlaying = nowPlaying,
                        miniProgress = miniProgress,
                    )
                }
            }
        }
    }

    private fun capture(
        name: String,
        language: AppLanguage,
        theme: ThemeMode,
        nowPlaying: NowPlayingUi?,
        miniProgress: StateFlow<Float>? = null,
    ) {
        composeRule.setContent { Stage(language, theme, nowPlaying, miniProgress) }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun navOnly(language: AppLanguage, theme: ThemeMode, tag: String) =
        capture("dock-nav-only-$tag.png", language, theme, null)

    private fun playing(language: AppLanguage, theme: ThemeMode, tag: String) =
        capture("dock-playing-$tag.png", language, theme, playing(language == AppLanguage.PERSIAN), progress)

    private fun juz(language: AppLanguage, theme: ThemeMode, tag: String) =
        capture("dock-juz-$tag.png", language, theme, juz(language == AppLanguage.PERSIAN), progress)

    private fun busy(language: AppLanguage, theme: ThemeMode, tag: String) =
        capture("dock-busy-$tag.png", language, theme, busy(language == AppLanguage.PERSIAN), progress)

    @Test
    fun nav_only_fa_light() = navOnly(AppLanguage.PERSIAN, ThemeMode.LIGHT, "light-fa")

    @Test
    fun nav_only_fa_dark() = navOnly(AppLanguage.PERSIAN, ThemeMode.DARK, "dark-fa")

    @Test
    fun nav_only_en_light() = navOnly(AppLanguage.ENGLISH, ThemeMode.LIGHT, "light-en")

    @Test
    fun nav_only_en_dark() = navOnly(AppLanguage.ENGLISH, ThemeMode.DARK, "dark-en")

    @Test
    fun playing_fa_light() = playing(AppLanguage.PERSIAN, ThemeMode.LIGHT, "light-fa")

    @Test
    fun playing_fa_dark() = playing(AppLanguage.PERSIAN, ThemeMode.DARK, "dark-fa")

    @Test
    fun playing_en_light() = playing(AppLanguage.ENGLISH, ThemeMode.LIGHT, "light-en")

    @Test
    fun playing_en_dark() = playing(AppLanguage.ENGLISH, ThemeMode.DARK, "dark-en")

    @Test
    fun juz_fa_light() = juz(AppLanguage.PERSIAN, ThemeMode.LIGHT, "light-fa")

    @Test
    fun juz_fa_dark() = juz(AppLanguage.PERSIAN, ThemeMode.DARK, "dark-fa")

    @Test
    fun juz_en_light() = juz(AppLanguage.ENGLISH, ThemeMode.LIGHT, "light-en")

    @Test
    fun juz_en_dark() = juz(AppLanguage.ENGLISH, ThemeMode.DARK, "dark-en")

    @Test
    fun busy_fa_light() = busy(AppLanguage.PERSIAN, ThemeMode.LIGHT, "light-fa")

    @Test
    fun busy_fa_dark() = busy(AppLanguage.PERSIAN, ThemeMode.DARK, "dark-fa")

    @Test
    fun busy_en_light() = busy(AppLanguage.ENGLISH, ThemeMode.LIGHT, "light-en")

    @Test
    fun busy_en_dark() = busy(AppLanguage.ENGLISH, ThemeMode.DARK, "dark-en")
}
