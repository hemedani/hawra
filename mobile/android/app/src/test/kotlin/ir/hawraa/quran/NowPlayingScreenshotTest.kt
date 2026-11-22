package ir.hawraa.quran

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.quran.QuranContentRepository
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.recitation.PlayerPreset
import ir.hawraa.quran.ui.recitation.PlayerReciter
import ir.hawraa.quran.ui.recitation.PlayerUi
import ir.hawraa.quran.ui.recitation.PlayerVerse
import ir.hawraa.quran.ui.screens.player.PlayerContent
import ir.hawraa.quran.ui.theme.HawraaTheme
import ir.hawraa.quran.ui.theme.LocalMotionPreference
import ir.hawraa.quran.ui.theme.MotionPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full-screen player content, rendered on the JVM (real Skia + HarfBuzz + the bundled mushaf
 * face) into `app/build/screenshots/`. This is the visual gate for the ported Pejvak layout: the
 * verse must register on the opaque reading surface in both themes, the chrome must survive both
 * locales, and a juz track must show «جزء N» with no verse card.
 *
 * Motion is pinned to reduced so the hero entrance and the playing pulse are static; the capture
 * is the resting layout, not a frame from an animation.
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

    private val store = NowPlayingStore().apply {
        bindPositionProvider { 7_400 to 21_000 }
    }

    private fun player(
        persian: Boolean,
        isPlaying: Boolean,
        juz: Int? = null,
        activePreset: Boolean = false,
    ): PlayerUi {
        val v = verse.second
        val presetName = if (persian) "آیه، ترجمه، آیه" else "Verse, translation, verse"
        return PlayerUi(
            surahNumber = 1,
            surahNameFa = "الفاتحة",
            surahNameEn = "Al-Fatiha",
            ayah = if (juz != null) 1 else v.ayah,
            juzNumber = juz,
            reciterNameFa = "شهریار پرهیزگار",
            reciterNameEn = "Shahriar Parhizgar",
            presetNameFa = if (activePreset) presetName else null,
            stepKind = if (activePreset) PresetStepKind.TRANSLATION else null,
            isPlaying = isPlaying,
            busy = false,
            failed = false,
            speed = PlaybackSpeed.ONE_AND_QUARTER,
            repeatAyah = true,
            reciters = listOf(
                PlayerReciter("shakernejad", "حامد شاکرنژاد", "Hamed Shakernejad", perAyah = false),
                PlayerReciter("parhizgar", "شهریار پرهیزگار", "Shahriar Parhizgar", perAyah = true),
                PlayerReciter("alafasy", "مشاری العفاسی", "al-Afasy", perAyah = true),
            ),
            selectedReciterKey = "parhizgar",
            presets = listOf(
                PlayerPreset("p1", presetName, true, 3),
                PlayerPreset("p2", if (persian) "حفظ" else "Memorising", false, 4),
            ),
            featuredVerse = if (juz != null) {
                null
            } else {
                PlayerVerse(
                    surah = v.surah,
                    ayah = v.ayah,
                    page = verse.first,
                    arabic = v.textUthmani,
                    translation = v.translations.firstOrNull()?.text
                        ?: if (persian) {
                            "ستایش خداوندی را که پروردگار جهانیان است."
                        } else {
                            "Praise belongs to the Lord of all the worlds."
                        },
                )
            },
            downloadablePage = verse.first,
            fontSizeSp = 30,
        )
    }

    private fun render(
        mode: ThemeMode,
        persian: Boolean,
        isPlaying: Boolean,
        juz: Int? = null,
        activePreset: Boolean = false,
    ) {
        composeRule.setContent {
            HawraaTheme(themeMode = mode) {
                Box(Modifier.fillMaxSize()) {
                    AmbientCanvas()
                    CompositionLocalProvider(
                        LocalMotionPreference provides MotionPreference.Reduced,
                    ) {
                        PlayerContent(
                            ui = player(persian, isPlaying, juz, activePreset),
                            store = store,
                            onClose = {},
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

    @Test
    fun player_light_persian_playing() {
        render(ThemeMode.LIGHT, persian = true, isPlaying = true, activePreset = true)
        capture("player-light-fa.png")
    }

    @Test
    fun player_dark_persian_paused() {
        render(ThemeMode.DARK, persian = true, isPlaying = false)
        capture("player-dark-fa.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
    fun player_light_english() {
        render(ThemeMode.LIGHT, persian = false, isPlaying = true)
        capture("player-light-en.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
    fun player_dark_english() {
        render(ThemeMode.DARK, persian = false, isPlaying = true)
        capture("player-dark-en.png")
    }

    @Test
    fun player_dark_persian_juz() {
        render(ThemeMode.DARK, persian = true, isPlaying = true, juz = 1)
        capture("player-dark-fa-juz.png")
    }
}
