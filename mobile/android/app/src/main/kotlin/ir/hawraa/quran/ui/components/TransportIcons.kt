package ir.hawraa.quran.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Transport glyphs (play / pause / skip) as [ImageVector]s.
 *
 * `material-icons-core` ships only `PlayArrow`; the rest live in the 10 MB
 * `material-icons-extended` artifact, which is not worth its weight in a reader that
 * needs four glyphs. These are built with Material's own 24 dp geometry (the paths are
 * the canonical `play_arrow`, `pause`, `skip_next` and `skip_previous` outlines), so they
 * look native next to the icons that *are* in core. `Icon` tints them, so the fill colour
 * here is irrelevant.
 */
object TransportIcons {

    /** Filled triangle, `play_arrow`. */
    val Play: ImageVector by lazy {
        builder("Play").apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
        }.build()
    }

    /** Two bars, `pause`. */
    val Pause: ImageVector by lazy {
        builder("Pause").apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f)
                horizontalLineTo(10f)
                verticalLineTo(19f)
                horizontalLineTo(6f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 5f)
                horizontalLineTo(18f)
                verticalLineTo(19f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    /** Triangle plus trailing bar, `skip_next`. */
    val Next: ImageVector by lazy {
        builder("Next").apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 18f)
                lineTo(14.5f, 12f)
                lineTo(6f, 6f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(16f)
                close()
            }
        }.build()
    }

    /** Leading bar plus triangle, `skip_previous`. */
    val Previous: ImageVector by lazy {
        builder("Previous").apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineTo(8f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.5f, 12f)
                lineTo(18f, 18f)
                lineTo(18f, 6f)
                close()
            }
        }.build()
    }

    /** A single down chevron, `expand_more` — collapse the full-screen player. */
    val ChevronDown: ImageVector by lazy {
        builder("ChevronDown").apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 16f)
                lineTo(6f, 10f)
                lineTo(7.41f, 8.59f)
                lineTo(12f, 13.17f)
                lineTo(16.59f, 8.59f)
                lineTo(18f, 10f)
                close()
            }
        }.build()
    }

    /** A down arrow into a base line, `file_download` — "keep this offline". */
    val Download: ImageVector by lazy {
        builder("Download").apply {
            // The arrow: a stem that widens into a downward point.
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 3f)
                lineTo(13f, 3f)
                lineTo(13f, 10f)
                lineTo(16f, 10f)
                lineTo(12f, 15f)
                lineTo(8f, 10f)
                lineTo(11f, 10f)
                close()
            }
            // The tray: a base line the arrow lands on.
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 17f)
                lineTo(19f, 17f)
                lineTo(19f, 19f)
                lineTo(5f, 19f)
                close()
            }
        }.build()
    }

    private fun builder(name: String): ImageVector.Builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
}
