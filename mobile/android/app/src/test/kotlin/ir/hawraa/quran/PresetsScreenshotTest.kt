package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import ir.hawraa.quran.data.repository.PresetCoverageSummary
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.preset.CoverageRange
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.quran.TranslationKeys
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.screens.presets.PresetEditorContent
import ir.hawraa.quran.ui.screens.presets.PresetEditorUiState
import ir.hawraa.quran.ui.screens.presets.PresetRow
import ir.hawraa.quran.ui.screens.presets.PresetsContent
import ir.hawraa.quran.ui.screens.presets.PresetsUiState
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the preset list and the preset editor on the JVM into `app/build/screenshots/`.
 *
 * The list is the screen that has to survive a real recipe: two of the three presets here are the
 * ones from the brief (`verse → Ansarian → al-Mizan`, and `verse twice → translation → verse`), so
 * the capture doubles as a check that the recipe summary is readable rather than a pile of words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class PresetsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The brief's first recipe. */
    private val alMizan = Preset(
        clientId = "preset-almizan",
        name = "آیه، ترجمه، المیزان",
        steps = listOf(
            PresetStep.ayah(),
            PresetStep.translation(TranslationKeys.ANSARIAN),
            PresetStep.tafsir("almizan"),
        ),
        isDefault = true,
    )

    /** The brief's second recipe: the verse twice, then the translation, then the verse again. */
    private val twice = Preset(
        clientId = "preset-twice",
        name = "دو بار آیه، ترجمه، آیه",
        steps = listOf(
            PresetStep.ayah(repeat = 2),
            PresetStep.translation(TranslationKeys.ANSARIAN),
            PresetStep.ayah(),
        ),
    )

    private val memorising = Preset(
        clientId = "preset-memorising",
        name = "حفظ",
        steps = listOf(
            PresetStep.ayah(),
            PresetStep.silence(3_000),
            PresetStep.ayah(),
        ),
    )

    private fun coverage(id: String, ayahs: Int, ranges: List<CoverageRange>) =
        PresetCoverageSummary(
            presetClientId = id,
            coveredAyahs = ayahs,
            percent = ayahs * 100.0 / 6236,
            ranges = ranges,
            bySurah = ranges.associate { it.fromSurah to it.ayahs },
        )

    /** Preset names are user data, so they are not translated — the en capture shows English ones. */
    private fun listState(english: Boolean = false) = PresetsUiState(
        loading = false,
        rows = listOf(
            PresetRow(
                preset = if (english) alMizan.copy(name = "Verse, translation, al-Mizan") else alMizan,
                coverage = coverage(
                    alMizan.clientId,
                    ayahs = 149,
                    ranges = listOf(
                        CoverageRange(1, 1, 1, 7, 7),
                        CoverageRange(2, 1, 2, 141, 141),
                        CoverageRange(18, 1, 18, 1, 1),
                    ),
                ),
            ),
            PresetRow(
                preset = if (english) twice.copy(name = "Verse twice, translation, verse") else twice,
                coverage = coverage(twice.clientId, ayahs = 7, ranges = listOf(CoverageRange(1, 1, 1, 7, 7))),
            ),
            PresetRow(
                preset = if (english) memorising.copy(name = "Memorising") else memorising,
                coverage = null,
            ),
        ),
    )

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
        name: String,
    ) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    TestShell(selected = "settings") {
                        PresetsContent(
                            state = listState(english = language == AppLanguage.ENGLISH),
                            onBack = {},
                        )
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun presets_light_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.LIGHT,
        name = "presets-light-fa.png",
    )

    @Test
    fun presets_dark_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.DARK,
        name = "presets-dark-fa.png",
    )

    @Test
    fun presets_light_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.LIGHT,
        name = "presets-light-en.png",
    )

    @Test
    fun presets_dark_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.DARK,
        name = "presets-dark-en.png",
    )

    @Test
    fun editor_light_persian() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.PERSIAN) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    TestShell(selected = "settings") {
                        PresetEditorContent(
                            state = PresetEditorUiState(
                                name = twice.name,
                                steps = twice.steps,
                                isNew = false,
                                loading = false,
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        capture("preset-editor-light-fa.png")
    }
}
