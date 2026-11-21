package ir.hawraa.quran

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.screens.reader.NowPlayingScreen
import ir.hawraa.quran.ui.screens.reader.PresetOption
import ir.hawraa.quran.ui.screens.reader.ReciterChoice
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
 * The full-screen player, rendered on the JVM (real Skia + HarfBuzz + the bundled mushaf face) into
 * `app/build/screenshots/`. This is the visual gate for D30: the verse must register on the opaque
 * reading surface in both themes, and the chrome must survive both locales.
 *
 * The player is drawn **without the dock** — the shell hides it while the player is open — so a bare
 * canvas behind the player is exactly what the app composes here, unlike the reader captures that
 * need [TestShell].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class NowPlayingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val content by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { QuranContentRepository(context).content() }
    }

    private val verse by lazy {
        content.pages.flatMap { p -> p.verses.map { p.page to it } }
            .first { it.second.surah == 1 && it.second.ayah == 3 }
    }

    @Composable
    private fun player(persian: Boolean, isPlaying: Boolean) = NowPlayingScreen(
        surahName = if (persian) "الفاتحة" else "Al-Fatiha",
        ayah = 3,
        juz = null,
        verseArabic = verse.second.textUthmani,
        translationText = verse.second.translations.firstOrNull()?.text
            ?: if (persian) "ستایش خداوندی را که پروردگار جهانیان است." else "Praise belongs to the Lord of all the worlds.",
        featuredStep = null,
        reciterName = if (persian) "شهریار پرهیزگار" else "Shahriar Parhizgar",
        presetName = null,
        isPlaying = isPlaying,
        buffering = false,
        failed = false,
        speed = PlaybackSpeed.ONE_AND_QUARTER,
        repeatAyah = true,
        reciters = listOf(
            ReciterChoice("shakernejad", "حامد شاکرنژاد", "Hamed Shakernejad", perAyah = false),
            ReciterChoice("parhizgar", "شهریار پرهیزگار", "Shahriar Parhizgar", perAyah = true),
            ReciterChoice("alafasy", "مشاری العفاسی", "al-Afasy", perAyah = true),
        ),
        selectedReciterKey = "parhizgar",
        fontSizeSp = 30,
        persian = persian,
        positionMs = 7_400,
        durationMs = 21_000,
        downloading = false,
        presets = listOf(
            PresetOption("p1", if (persian) "آیه، ترجمه، آیه" else "Verse, translation, verse", true, 3),
            PresetOption("p2", if (persian) "حفظ" else "Memorising", false, 4),
        ),
        activePresetName = null,
        onClose = {},
        onToggle = {},
        onPrevious = {},
        onNext = {},
        onCycleSpeed = {},
        onToggleRepeat = {},
        onSelectReciter = {},
        onDownload = {},
        onPlayPreset = {},
        onExitPreset = {},
    )

    private fun render(mode: ThemeMode, persian: Boolean, isPlaying: Boolean) {
        composeRule.setContent {
            HawraaTheme(themeMode = mode) {
                Box(Modifier.fillMaxSize()) {
                    AmbientCanvas()
                    player(persian, isPlaying)
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

    @Test
    fun player_light_persian_playing() {
        render(ThemeMode.LIGHT, persian = true, isPlaying = true)
        capture("player-light-fa.png")
    }

    @Test
    fun player_dark_persian_paused() {
        render(ThemeMode.DARK, persian = true, isPlaying = false)
        capture("player-dark-fa.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
    fun player_dark_english() {
        render(ThemeMode.DARK, persian = false, isPlaying = true)
        capture("player-dark-en.png")
    }
}
