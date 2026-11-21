package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.screens.history.HistoryContent
import ir.hawraa.quran.ui.screens.history.HistoryDay
import ir.hawraa.quran.ui.screens.history.HistoryRow
import ir.hawraa.quran.ui.screens.history.HistoryUiState
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the History screen with seeded sessions, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class HistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun at(day: LocalDate, hour: Int): Long =
        day.atTime(hour, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val today = LocalDate.now()

    private fun state() = HistoryUiState(
        isLoading = false,
        totalSeconds = 3 * 3600 + 24 * 60,
        pagesTouched = 5,
        streakDays = 4,
        totalSessions = 6,
        days = listOf(
            HistoryDay(
                day = today,
                durationReadSec = 1_920,
                rows = listOf(
                    HistoryRow("a", "بقره", "2:6-16", at(today, 21), 1_020, true),
                    HistoryRow("b", "بقره", "2:1-5", at(today, 9), 900, true),
                ),
            ),
            HistoryDay(
                day = today.minusDays(1),
                durationReadSec = 640,
                rows = listOf(
                    HistoryRow("c", "فاتحه", "1:1-7", at(today.minusDays(1), 20), 640, true),
                ),
            ),
            HistoryDay(
                day = today.minusDays(3),
                durationReadSec = 210,
                rows = listOf(
                    HistoryRow("d", "بقره", "2:17-24", at(today.minusDays(3), 18), 210, false),
                ),
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

    @Test
    fun history_light() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.LIGHT) {
                TestShell(selected = "history") {
                    HistoryContent(state = state(), onBack = {})
                }
            }
        }
        capture("history-light.png")
    }

    @Test
    fun history_dark() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.DARK) {
                TestShell(selected = "history") {
                    HistoryContent(state = state(), onBack = {})
                }
            }
        }
        capture("history-dark.png")
    }
}
