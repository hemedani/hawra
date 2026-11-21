package ir.hawraa.quran.ui.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.theme.LocalQuranColors

/** Share of the marker's width taken by the digit's em. */
private const val NUMBER_EM_RATIO = 0.42f

/**
 * The ayah marker: an eight-pointed Rub el Hizb with the verse number inside, in Persian
 * digits — the mushaf idiom, not a Material badge.
 *
 * The octagram is **not drawn here**. It is Delapouite's "Rub el Hizb" from
 * game-icons.net (CC BY 3.0), shipped as `res/drawable/ayah_marker.xml`; see
 * `assets/licenses/ayah-marker-game-icons.txt`. Using a real icon instead of generated
 * geometry is what makes it read as a printed marker: the corner ticks, the edge notches
 * and the double octagram rule are all part of the drawing, and they stay crisp at every
 * size because the source is a vector.
 *
 * The one change to the original is that its central ring is omitted (see the drawable),
 * because the number occupies that space.
 *
 * [highlight] carries the *meaning* of the emphasis rather than a boolean, because the two
 * states are deliberately different hues: the reader's own selection is the warm [mark], the
 * ayah being recited is the cool [accent]. The band is a ring with a hole, not a filled disc —
 * so the number is drawn in the same hue rather than knocked out, which would leave it
 * invisible against the page showing through.
 */
@Composable
fun AyahOrnament(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    highlight: Color? = null,
) {
    val colors = LocalQuranColors.current
    val painter: Painter = painterResource(R.drawable.ayah_marker)
    val measurer = rememberTextMeasurer()
    val label = remember(number) { AnnotatedString(number.toPersianDigits()) }
    val density = LocalDensity.current

    val ink = highlight ?: colors.ornamentRing
    val numberInk = highlight ?: colors.ornamentNumber

    val emPx = with(density) { size.toPx() } * NUMBER_EM_RATIO
    val numberStyle = remember(size, numberInk, density) {
        TextStyle(
            fontFamily = QuranLettersFontFamily,
            fontSize = with(density) { emPx.toSp() },
            color = numberInk,
            textAlign = TextAlign.Center,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    val numberLayout = remember(label, numberStyle) { measurer.measure(label, numberStyle) }

    Canvas(modifier = modifier.size(size)) {
        // `size` is the Dp parameter here, so name the canvas box explicitly.
        val box = this.size
        with(painter) {
            draw(size = box, colorFilter = ColorFilter.tint(ink))
        }
        // Centre the digit by its baseline. Persian digits hang from the baseline with a
        // cap height of roughly half the em, so their visual middle is a quarter em below
        // it; centring the layout box instead would sit them visibly high.
        val baseline = numberLayout.getLineBaseline(0)
        drawText(
            textLayoutResult = numberLayout,
            topLeft = Offset(
                x = (box.width - numberLayout.size.width) / 2f,
                y = box.height / 2f + emPx * 0.25f - baseline,
            ),
        )
    }
}
