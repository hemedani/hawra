package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.FrostedSurface
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.HairlineWave
import ir.hawraa.quran.ui.components.LocalBackdrop
import ir.hawraa.quran.ui.components.backdropSource
import ir.hawraa.quran.ui.components.rememberBackdrop
import ir.hawraa.quran.ui.theme.HawraaTheme
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the ported pane material over the ambient canvas: the frosted shell pane,
 * the glass card recipe and the hairline wave, in both themes. This is the visual gate
 * for Task 2's substrate before any screen consumes it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class GlassPrimitivesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Primitives() {
        val tokens = LocalHawraaTokens.current
        val backdrop = rememberBackdrop()
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize()) {
                AmbientCanvas(Modifier.backdropSource(backdrop, zIndex = 0f))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    FrostedSurface(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        shape = RoundedCornerShape(32.dp),
                        softElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "FrostedSurface",
                                style = MaterialTheme.typography.headlineSmall,
                                color = tokens.text,
                            )
                            Text(
                                text = "live backdrop · frost tint · grain · rim",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                            )
                            HairlineWave()
                        }
                    }
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        shape = RoundedCornerShape(28.dp),
                        strong = true,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "GlassSurface",
                                style = MaterialTheme.typography.headlineSmall,
                                color = tokens.text,
                            )
                            Text(
                                text = "fill · contour border · 3 dp rim light",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                            )
                            HairlineWave()
                        }
                    }
                }
            }
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

    @Test
    fun glass_primitives_light() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.LIGHT) { Primitives() }
        }
        capture("glass-primitives-light.png")
    }

    @Test
    fun glass_primitives_dark() {
        composeRule.setContent {
            HawraaTheme(themeMode = ThemeMode.DARK) { Primitives() }
        }
        capture("glass-primitives-dark.png")
    }
}
