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
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens

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
 * The base glass pane (DESIGN.md §6): translucent fill + bright hairline border +
 * a top rim light. Depth comes from this trio over the ambient canvas, not from drop
 * shadows (only the dock/player opt into [elevation]).
 *
 * Real backdrop blur (RenderEffect, API 31+) lives in [FrostedSurface]; this recipe is
 * the designed fallback and is legible on its own — the mid-tone canvas guarantees
 * contrast. [strong] uses the higher-opacity fill for panes that must read over busy
 * content, and [filled] = false leaves the fill off for purely outlined surfaces.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalHawraaGeometry.current.radius.card),
    strong: Boolean = false,
    filled: Boolean = true,
    rimLight: Boolean = true,
    elevation: GlassElevation = GlassElevation.None,
    borderWidth: Dp = LocalHawraaGeometry.current.glass.borderWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    val shadowColor = LocalGlassColors.current.shadow
    val fill = if (strong) tokens.glassFillStrong else tokens.glassFill
    val shadowModifier = if (elevation == GlassElevation.None) {
        Modifier
    } else {
        Modifier.shadow(
            elevation = elevation.dp,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
    }
    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .then(if (filled) Modifier.background(fill) else Modifier)
            .then(
                if (rimLight) Modifier.drawWithContent {
                    drawContent()
                    // Top-edge rim light only — never a half-pane highlight.
                    val rim = geo.glass.rimHeight.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(tokens.glassHighlight, Color.Transparent),
                            startY = 0f,
                            endY = rim,
                        ),
                        topLeft = Offset.Zero,
                        size = Size(size.width, rim),
                    )
                } else {
                    Modifier
                },
            )
            .border(borderWidth, tokens.glassBorder, shape),
        content = content,
    )
}
