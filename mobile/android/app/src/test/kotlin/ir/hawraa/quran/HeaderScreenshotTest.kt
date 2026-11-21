package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.HeaderSearch
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalBackdrop
import ir.hawraa.quran.ui.components.backdropSource
import ir.hawraa.quran.ui.components.rememberBackdrop
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The header anatomy (plan 02 Task 3): title-only, overline + subtitle, a leading
 * back bubble, trailing action bubbles, and the morphing search capsule — in both
 * themes and both locales. This is the visual gate before any screen adopts
 * [GlassHeader] in Task 8.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class HeaderScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Backdropped(content: @Composable () -> Unit) {
        val backdrop = rememberBackdrop()
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize()) {
                AmbientCanvas(Modifier.backdropSource(backdrop, zIndex = 0f))
                content()
            }
        }
    }

    @Composable
    private fun Gallery() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
        ) {
            GlassHeader(
                title = stringResource(R.string.home_title),
                userTitle = true,
            )
            GlassHeader(
                title = stringResource(R.string.settings_title),
                overline = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.home_history_hint),
                userTitle = true,
            )
            GlassHeader(
                title = stringResource(R.string.reader_title),
                userTitle = true,
                leading = {
                    BubbleButton(
                        icon = ICON_GLYPHS.getValue("back"),
                        contentDescription = stringResource(R.string.back),
                        onClick = {},
                    )
                },
            )
            GlassHeader(
                title = stringResource(R.string.downloads_title),
                userTitle = true,
                actions = {
                    BubbleButton(
                        icon = ICON_GLYPHS.getValue("settings"),
                        contentDescription = stringResource(R.string.settings_title),
                        onClick = {},
                    )
                    BubbleButton(
                        icon = ICON_GLYPHS.getValue("download"),
                        contentDescription = stringResource(R.string.downloads_title),
                        onClick = {},
                    )
                },
            )
            GlassHeader(
                title = stringResource(R.string.presets_title),
                userTitle = true,
                search = HeaderSearch(
                    value = "",
                    onValueChange = {},
                    placeholder = stringResource(R.string.search_placeholder),
                ),
            )
        }
    }

    @Composable
    private fun SearchHeader() {
        var query by remember { mutableStateOf("") }
        GlassHeader(
            title = stringResource(R.string.presets_title),
            userTitle = true,
            search = HeaderSearch(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_placeholder),
            ),
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

    private fun gallery(language: AppLanguage, theme: ThemeMode, name: String) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    Backdropped { Gallery() }
                }
            }
        }
        capture(name)
    }

    private fun searchExpanded(language: AppLanguage, theme: ThemeMode, name: String) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    Backdropped { SearchHeader() }
                }
            }
        }
        composeRule.onNode(hasClickAction()).performClick()
        capture(name)
    }

    @Test
    fun header_gallery_light_fa() = gallery(AppLanguage.PERSIAN, ThemeMode.LIGHT, "header-gallery-light-fa.png")

    @Test
    fun header_gallery_dark_fa() = gallery(AppLanguage.PERSIAN, ThemeMode.DARK, "header-gallery-dark-fa.png")

    @Test
    fun header_gallery_light_en() = gallery(AppLanguage.ENGLISH, ThemeMode.LIGHT, "header-gallery-light-en.png")

    @Test
    fun header_gallery_dark_en() = gallery(AppLanguage.ENGLISH, ThemeMode.DARK, "header-gallery-dark-en.png")

    @Test
    fun header_search_light_fa() =
        searchExpanded(AppLanguage.PERSIAN, ThemeMode.LIGHT, "header-search-light-fa.png")

    @Test
    fun header_search_dark_fa() =
        searchExpanded(AppLanguage.PERSIAN, ThemeMode.DARK, "header-search-dark-fa.png")

    @Test
    fun header_search_light_en() =
        searchExpanded(AppLanguage.ENGLISH, ThemeMode.LIGHT, "header-search-light-en.png")

    @Test
    fun header_search_dark_en() =
        searchExpanded(AppLanguage.ENGLISH, ThemeMode.DARK, "header-search-dark-en.png")
}
