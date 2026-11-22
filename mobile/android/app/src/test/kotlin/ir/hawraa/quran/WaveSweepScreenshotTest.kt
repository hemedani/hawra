package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.NavWaveKind
import ir.hawraa.quran.ui.components.WaveSweep
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The navigation current on the ambient field, pinned at the crest's full-strength
 * midpoint (`t = 0.5`). This is the visual gate for Task 7's [WaveSweep]: the aqua band
 * must sit inside the viewport, the dual crests and swimmer must be present, and a tab
 * drift must mirror for RTL while push/pop stay vertical.
 *
 * Captures are `wavesweep-{tab|push|pop}-{light|dark}-{fa|en}.png` in
 * `app/build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class WaveSweepScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(mode: ThemeMode, language: AppLanguage, kind: NavWaveKind) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = mode) {
                    Box(Modifier.fillMaxSize()) {
                        AmbientCanvas()
                        WaveSweep(
                            trigger = 1,
                            kind = kind,
                            modifier = Modifier.fillMaxSize(),
                            progressOverride = 0.5f,
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sweep(state: String, kind: NavWaveKind, mode: ThemeMode, language: AppLanguage) {
        render(mode, language, kind)
        val theme = if (mode == ThemeMode.LIGHT) "light" else "dark"
        val locale = if (language == AppLanguage.PERSIAN) "fa" else "en"
        capture("wavesweep-$state-$theme-$locale.png")
    }

    @Test
    fun tab_light_fa() = sweep("tab", NavWaveKind.TabDrift(1), ThemeMode.LIGHT, AppLanguage.PERSIAN)

    @Test
    fun tab_dark_fa() = sweep("tab", NavWaveKind.TabDrift(1), ThemeMode.DARK, AppLanguage.PERSIAN)

    @Test
    fun tab_light_en() = sweep("tab", NavWaveKind.TabDrift(1), ThemeMode.LIGHT, AppLanguage.ENGLISH)

    @Test
    fun tab_dark_en() = sweep("tab", NavWaveKind.TabDrift(1), ThemeMode.DARK, AppLanguage.ENGLISH)

    @Test
    fun push_light_fa() = sweep("push", NavWaveKind.Push, ThemeMode.LIGHT, AppLanguage.PERSIAN)

    @Test
    fun push_dark_fa() = sweep("push", NavWaveKind.Push, ThemeMode.DARK, AppLanguage.PERSIAN)

    @Test
    fun push_light_en() = sweep("push", NavWaveKind.Push, ThemeMode.LIGHT, AppLanguage.ENGLISH)

    @Test
    fun push_dark_en() = sweep("push", NavWaveKind.Push, ThemeMode.DARK, AppLanguage.ENGLISH)

    @Test
    fun pop_light_fa() = sweep("pop", NavWaveKind.Pop, ThemeMode.LIGHT, AppLanguage.PERSIAN)

    @Test
    fun pop_dark_fa() = sweep("pop", NavWaveKind.Pop, ThemeMode.DARK, AppLanguage.PERSIAN)

    @Test
    fun pop_light_en() = sweep("pop", NavWaveKind.Pop, ThemeMode.LIGHT, AppLanguage.ENGLISH)

    @Test
    fun pop_dark_en() = sweep("pop", NavWaveKind.Pop, ThemeMode.DARK, AppLanguage.ENGLISH)
}
