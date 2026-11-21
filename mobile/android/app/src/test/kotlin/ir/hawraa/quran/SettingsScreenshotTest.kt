package ir.hawraa.quran

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import ir.hawraa.quran.ui.screens.settings.SettingsContent
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
 * Renders the reading settings on the JVM and writes PNGs to
 * `app/build/screenshots/` — the only way to eyeball this screen without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val basmala by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content().basmalaUthmani }
    }

    private val reciters = listOf(
        Reciter("parhizgar", "شهریار پرهیزگار", "Shahriar Parhizgar", "murattal", 48, "u1"),
        Reciter("alafasy", "مشاری راشد العفاسی", "Mishary Rashid al-Afasy", "murattal", 128, "u2"),
        Reciter("husary", "محمود خلیل الحصری", "Mahmoud Khalil al-Husary", "murattal", 128, "u3"),
        Reciter("minshawi", "محمد صدیق منشاوی", "Mohamed Siddiq al-Minshawi", "murattal", 128, "u4"),
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun settings_light() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.LIGHT) {
                TestShell(selected = "settings") {
                    SettingsContent(
                        themeMode = ThemeMode.LIGHT,
                        language = AppLanguage.SYSTEM,
                        recitation = RecitationSettings(),
                        reciters = reciters,
                        reading = ReadingSettings(
                            fontSizeSp = 32,
                            letterInk = LetterInk.INK,
                            diacriticInk = DiacriticInk.GOLD,
                        ),
                        previewText = basmala,
                        onBack = {},
                        onLanguage = {},
                        onThemeMode = {},
                        onFontSize = {},
                        onShowTranslation = {},
                        onLetterInk = {},
                        onDiacriticInk = {},
                    )
                }
            }
        }
        capture("settings-light.png")
    }

    @Test
    fun settings_dark() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.DARK) {
                TestShell(selected = "settings") {
                    SettingsContent(
                        themeMode = ThemeMode.DARK,
                        language = AppLanguage.PERSIAN,
                        recitation = RecitationSettings(reciterKey = "alafasy"),
                        reciters = reciters,
                        reading = ReadingSettings(
                            fontSizeSp = 36,
                            letterInk = LetterInk.GREEN,
                            diacriticInk = DiacriticInk.BURGUNDY,
                        ),
                        previewText = basmala,
                        onBack = {},
                        onLanguage = {},
                        onThemeMode = {},
                        onFontSize = {},
                        onShowTranslation = {},
                        onLetterInk = {},
                        onDiacriticInk = {},
                    )
                }
            }
        }
        capture("settings-dark.png")
    }

    /**
     * The recitation section lives below the fold; scroll to it so the reciter chips and
     * both attribution lines can actually be looked at.
     */
    @Test
    fun settings_recitation_section() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.LIGHT) {
                TestShell(selected = "settings") {
                    SettingsContent(
                        themeMode = ThemeMode.LIGHT,
                        language = AppLanguage.PERSIAN,
                        recitation = RecitationSettings(reciterKey = "parhizgar"),
                        reciters = reciters,
                        reading = ReadingSettings(),
                        previewText = basmala,
                        onBack = {},
                        onLanguage = {},
                        onThemeMode = {},
                        onFontSize = {},
                        onShowTranslation = {},
                        onLetterInk = {},
                        onDiacriticInk = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("reciter-chips").performScrollTo()
        capture("settings-recitation.png")
    }

    /**
     * The CC BY attribution for the ayah marker and the audio CDN credit must be present
     * in the product, not only in `assets/licenses/`. This is the assertion that keeps
     * them from being deleted as "unused string".
     */
    @Test
    fun settings_carries_both_attributions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.LIGHT) {
                TestShell(selected = "settings") {
                    SettingsContent(
                        themeMode = ThemeMode.LIGHT,
                        language = AppLanguage.PERSIAN,
                        recitation = RecitationSettings(),
                        reciters = reciters,
                        reading = ReadingSettings(),
                        previewText = basmala,
                        onBack = {},
                        onLanguage = {},
                        onThemeMode = {},
                        onFontSize = {},
                        onShowTranslation = {},
                        onLetterInk = {},
                        onDiacriticInk = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("reciter-chips").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_credits)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.recitation_credit)).assertExists()
    }
}
