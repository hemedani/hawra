package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's player row for a **juz-granularity** reciter.
 *
 * A juz track has no verse position, so the row cannot say "ayah 1" — that would be the juz's first
 * verse, which is not where playback started. It says the juz instead, and the identity tile has to
 * agree with the title: `جزء ۵` beside a tile reading `۴` (the juz's starting surah) puts two
 * different numbers in one 58 dp row with nothing to say which is which. That mismatch is invisible
 * in a unit test and obvious in a screenshot, which is why this exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class DockNowPlayingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun juzNowPlaying() = NowPlayingUi(
        title = "جزء ۵",
        subtitle = "جزء‌به‌جزء · شهریار پرهیزگار",
        // Juz 5 starts at 4:24, so the *surah* number here would be 4 — deliberately not what the
        // tile should show.
        number = "۵",
        isPlaying = true,
        waiting = false,
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
    fun the_tile_and_the_title_name_the_same_juz() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.DARK) {
                TestShell(selected = "reader", nowPlaying = juzNowPlaying()) {
                    // The dock is the subject; the screen behind it is empty on purpose.
                }
            }
        }

        composeRule.onNodeWithText("جزء ۵").assertIsDisplayed()
        // Exact match, so this is the tile and not a substring of the title.
        composeRule.onNodeWithText("۵").assertIsDisplayed()
        capture("dock-juz-fa.png")
    }

    @Test
    fun the_dock_publishes_the_measured_player_half_rect() {
        var rect: Rect? = null
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.fillMaxSize()) {
                    AppDock(
                        selectedRoute = "reader",
                        onSelect = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        nowPlaying = juzNowPlaying(),
                        onMiniPlayerRect = { rect = it },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val measured = rect
        assertNotNull("the dock must publish its mini half for the Task 6 morph", measured)
        val expectedHeight = with(composeRule.density) { 58.dp.toPx() }
        assertEquals(expectedHeight, measured!!.height, 1f)
        assertEquals(true, measured.width > 0f)
    }
}
