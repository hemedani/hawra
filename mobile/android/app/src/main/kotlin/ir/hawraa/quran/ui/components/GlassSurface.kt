package ir.hawraa.quran.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.ui.theme.LocalGlassColors

/**
 * How far a pane is lifted (`docs/DESIGN.md` §7).
 *
 * The dock's step is deliberately shallower than [Float]: a floating *control* is lifted off the
 * page, but a dock is a *surface*. The deep drop reads as the dock hovering above the screen
 * rather than floating in it, and darkens the canvas along the bottom edge of every list.
 */
enum class GlassElevation(val dp: Dp) {
    None(0.dp),
    Dock(5.dp),
    Panel(6.dp),
    Float(12.dp),
}

/**
 * The frosted pane every surface is built from (`docs/DESIGN.md` §7, §10.1).
 *
 * Three things make it read as glass rather than as a grey box:
 *
 *  1. a translucent **fill** — which is what gives the pane its colour and keeps text legible;
 *  2. a **hairline border** — dark in the light theme, light in the dark one, because a white
 *     border on a pale canvas disappears;
 *  3. a 2 dp **specular top edge** — a highlight confined to the top edge, never spread over the
 *     upper half (that blows out and stops looking like a lit surface).
 *
 * There is deliberately **no real backdrop blur**. The ambient canvas behind every screen is
 * already a low-frequency gradient, so blurring it changes nothing visible, and a genuine
 * `RenderEffect` on this node would blur the pane's own text instead of what is behind it. The
 * recipe above is the material; legibility never depends on blur, which is also why the API 29–30
 * fallback ([glassFillFallback]) is a fill change and nothing more.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    /** Use the more opaque fill for anything that must read over busy content. */
    strong: Boolean = false,
    elevation: GlassElevation = GlassElevation.None,
    borderWidth: Dp = 1.dp,
    /** Drop the top sheen for panes repeated in a long list, where it adds noise. */
    rimLight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    val fill = if (strong) glass.glassFillStrong else glass.glassFill

    Box(
        modifier = modifier
            .then(
                if (elevation == GlassElevation.None) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = elevation.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = glass.shadow,
                        spotColor = glass.shadow,
                    )
                },
            )
            .clip(shape)
            .background(fill)
            .then(
                if (rimLight) {
                    Modifier.drawWithContent {
                        drawContent()
                        // A 2 dp gradient at the very top edge only — the pane catching light.
                        val rim = 2.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(glass.glassHighlight, Color.Transparent),
                                startY = 0f,
                                endY = rim,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, rim),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(borderWidth, glass.glassBorder, shape),
        content = content,
    )
}
