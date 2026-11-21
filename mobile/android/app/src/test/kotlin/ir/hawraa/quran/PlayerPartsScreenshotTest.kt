package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.PaletteTile
import ir.hawraa.quran.ui.components.player.HeroArtwork
import ir.hawraa.quran.ui.components.player.Scrubber
import ir.hawraa.quran.ui.components.player.ScrubberMarker
import ir.hawraa.quran.ui.components.player.TransportControls
import ir.hawraa.quran.ui.theme.HawraaTheme
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player parts on a real glass panel over the ambient canvas, rendered on the JVM
 * (Robolectric native graphics) into `app/build/screenshots/`. The scrubber is shown
 * playing and waiting; the transport playing, paused, waiting and with the optional ±30
 * bubbles; the identity tile at hero and dock size — each in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class PlayerPartsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Stage(content: @Composable ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            AmbientCanvas()
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 72.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(LocalHawraaGeometry.current.radius.card),
                    strong = true,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        content = content,
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalHawraaTokens.current.textSecondary,
        )
    }

    private fun render(theme: ThemeMode, content: @Composable ColumnScope.() -> Unit) {
        composeRule.setContent {
            HawraaTheme(themeMode = theme) { Stage(content) }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun scrubberPlaying(theme: ThemeMode) {
        render(theme) {
            SectionTitle("Scrubber · playing")
            Scrubber(
                positionSec = 74,
                durationSec = 180,
                isPlaying = true,
                onSeek = {},
                waveform = null,
                waveSeed = "2:255",
                markers = listOf(
                    ScrubberMarker("a", 40, null),
                    ScrubberMarker("b", 120, null),
                ),
            )
        }
    }

    private fun scrubberWaiting(theme: ThemeMode) {
        render(theme) {
            SectionTitle("Scrubber · waiting")
            Scrubber(
                positionSec = 0,
                durationSec = 180,
                isPlaying = false,
                waiting = true,
                onSeek = {},
                waveform = null,
                waveSeed = "2:255",
            )
        }
    }

    private fun transport(theme: ThemeMode, isPlaying: Boolean, waiting: Boolean, skip: Boolean = false) {
        val skipAction: (() -> Unit)? = if (skip) ({}) else null
        render(theme) {
            SectionTitle(
                when {
                    waiting -> "Transport · waiting"
                    isPlaying -> "Transport · playing"
                    else -> "Transport · paused"
                },
            )
            TransportControls(
                isPlaying = isPlaying,
                waiting = waiting,
                onToggle = {},
                onNext = {},
                onPrevious = {},
                onRewind = skipAction,
                onForward = skipAction,
            )
        }
    }

    private fun transportRtl(theme: ThemeMode) {
        render(theme) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SectionTitle("Transport · RTL (fa)")
                TransportControls(
                    isPlaying = true,
                    waiting = false,
                    onToggle = {},
                    onNext = {},
                    onPrevious = {},
                )
            }
        }
    }

    private fun identity(theme: ThemeMode) {
        render(theme) {
            SectionTitle("Identity tile")
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroArtwork(size = 112.dp, seedKey = "surah:2", number = "۲")
                PaletteTile(seedKey = "surah:2", number = "۲", size = 48.dp)
                PaletteTile(seedKey = "juz:30", number = "۳۰", size = 48.dp)
            }
        }
    }

    @Test
    fun scrubber_playing_light() {
        scrubberPlaying(ThemeMode.LIGHT)
        capture("player-parts-scrubber-playing-light.png")
    }

    @Test
    fun scrubber_playing_dark() {
        scrubberPlaying(ThemeMode.DARK)
        capture("player-parts-scrubber-playing-dark.png")
    }

    @Test
    fun scrubber_waiting_light() {
        scrubberWaiting(ThemeMode.LIGHT)
        capture("player-parts-scrubber-waiting-light.png")
    }

    @Test
    fun scrubber_waiting_dark() {
        scrubberWaiting(ThemeMode.DARK)
        capture("player-parts-scrubber-waiting-dark.png")
    }

    @Test
    fun transport_playing_light() {
        transport(ThemeMode.LIGHT, isPlaying = true, waiting = false)
        capture("player-parts-transport-playing-light.png")
    }

    @Test
    fun transport_playing_dark() {
        transport(ThemeMode.DARK, isPlaying = true, waiting = false)
        capture("player-parts-transport-playing-dark.png")
    }

    @Test
    fun transport_paused_light() {
        transport(ThemeMode.LIGHT, isPlaying = false, waiting = false)
        capture("player-parts-transport-paused-light.png")
    }

    @Test
    fun transport_paused_dark() {
        transport(ThemeMode.DARK, isPlaying = false, waiting = false)
        capture("player-parts-transport-paused-dark.png")
    }

    @Test
    fun transport_waiting_light() {
        transport(ThemeMode.LIGHT, isPlaying = false, waiting = true)
        capture("player-parts-transport-waiting-light.png")
    }

    @Test
    fun transport_waiting_dark() {
        transport(ThemeMode.DARK, isPlaying = false, waiting = true)
        capture("player-parts-transport-waiting-dark.png")
    }

    @Test
    fun transport_skip_light() {
        transport(ThemeMode.LIGHT, isPlaying = true, waiting = false, skip = true)
        capture("player-parts-transport-skip-light.png")
    }

    @Test
    fun transport_skip_dark() {
        transport(ThemeMode.DARK, isPlaying = true, waiting = false, skip = true)
        capture("player-parts-transport-skip-dark.png")
    }

    @Test
    fun transport_rtl_light() {
        transportRtl(ThemeMode.LIGHT)
        capture("player-parts-transport-rtl-light.png")
    }

    @Test
    fun transport_rtl_dark() {
        transportRtl(ThemeMode.DARK)
        capture("player-parts-transport-rtl-dark.png")
    }

    @Test
    fun identity_tile_light() {
        identity(ThemeMode.LIGHT)
        capture("player-parts-identity-light.png")
    }

    @Test
    fun identity_tile_dark() {
        identity(ThemeMode.DARK)
        capture("player-parts-identity-dark.png")
    }
}
