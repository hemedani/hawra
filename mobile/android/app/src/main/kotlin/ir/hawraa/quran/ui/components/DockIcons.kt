package ir.hawraa.quran.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Glyphs the dock needs that `material-icons-core` does not ship.
 *
 * The same reason `TransportIcons` exists: core carries about forty icons and `material-icons-
 * extended` is a 10 MB artifact for the handful we are missing. A clock is two arcs and two
 * strokes, so it is cheaper to draw than to depend on.
 *
 * Stroked paths are tinted at draw time by `Icon`'s `ColorFilter`, so the placeholder colour here
 * never reaches the screen.
 */
object DockIcons {

    /** A clock: the "History" destination. */
    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // The dial, as two half-arcs so the path closes cleanly.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
            ) {
                moveTo(12f, 3.4f)
                arcToRelative(8.6f, 8.6f, 0f, true, true, 0f, 17.2f)
                arcToRelative(8.6f, 8.6f, 0f, true, true, 0f, -17.2f)
            }
            // Hands at roughly ten past ten, so the glyph reads as a clock at a glance.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 7.4f)
                lineTo(12f, 12.2f)
                lineTo(15.6f, 14.3f)
            }
        }.build()
    }
}
