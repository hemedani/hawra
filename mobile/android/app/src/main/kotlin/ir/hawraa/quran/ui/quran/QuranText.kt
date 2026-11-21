package ir.hawraa.quran.ui.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.domain.quran.SilentLetters
import ir.hawraa.quran.ui.theme.MUTED_LETTER_ALPHA
import kotlin.math.max

/**
 * Renders Uthmani Quran text with **independently colorable letters and diacritics**
 * (product invariant #7) with no custom shaping engine and no clipping tricks.
 *
 * The same string is laid out twice — once with [QuranMarksFontFamily] (only mark
 * outlines) and once with [QuranLettersFontFamily] (only letter outlines) — and the
 * two layers are drawn stacked. Because the two faces share every shaping table and
 * metric, the glyph runs are identical and registration is exact; the mark layer
 * carries [diacriticColor], the letter layer [letterColor].
 *
 * [silentRanges] are character offsets (see `SilentLetters`) of letters that are written
 * but not recited. They are drawn in [silentColor] — the same glyphs, at lower weight —
 * so the orthography stays complete while the pronunciation is not misled. Colouring
 * happens through a `SpanStyle` on a single shaped layout, never by splitting the string:
 * splitting would break joining and change the glyph selection, which is exactly the
 * trap that rules out per-character rendering here.
 *
 * See `QuranFont.kt` for why the face is split, and `tools/build-quran-fonts.py`
 * for how the split is produced.
 */
@Composable
fun QuranText(
    text: String,
    letterColor: Color,
    diacriticColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = Int.MAX_VALUE,
    /** Silent letters are found from the text; this only switches the muting off. */
    muteSilentLetters: Boolean = true,
) {
    val measurer = rememberTextMeasurer(cacheSize = 96)
    // Computed here rather than passed in, so no call site can forget it.
    val silentRanges = remember(text, muteSilentLetters) {
        if (muteSilentLetters) SilentLetters.ranges(text) else emptyList()
    }
    val silentColor = letterColor.copy(alpha = MUTED_LETTER_ALPHA)
    val density = LocalDensity.current
    // Quranic marks sit outside a normal ascent/descent box; give the canvas room
    // so the top of a fatha or a small high letter is never clipped.
    val verticalBleed = with(density) { 6.dp.roundToPx() }

    val baseStyle = remember(fontSize, lineHeight, textAlign) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            textAlign = textAlign,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }
    val letterStyle = remember(baseStyle, letterColor) {
        baseStyle.copy(fontFamily = QuranLettersFontFamily, color = letterColor)
    }
    val markStyle = remember(baseStyle, diacriticColor) {
        baseStyle.copy(fontFamily = QuranMarksFontFamily, color = diacriticColor)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        // Full-width paragraph so the text alignment is meaningful and both layers
        // share the same box. Guard against an unbounded parent (e.g. a Row without
        // weight), where a min-width constraint would be invalid.
        val paragraph = if (constraints.hasBoundedWidth) {
            Constraints(minWidth = widthPx, maxWidth = widthPx)
        } else {
            Constraints()
        }

        val letters = remember(text, letterStyle, widthPx, maxLines, silentRanges, silentColor) {
            val annotated = buildAnnotatedString {
                append(text)
                for (range in silentRanges) {
                    // Clamp: a range computed for a different string must never throw
                    // inside a text pass.
                    val start = range.first.coerceIn(0, text.length)
                    val end = (range.last + 1).coerceIn(start, text.length)
                    if (end > start) addStyle(SpanStyle(color = silentColor), start, end)
                }
            }
            measurer.measure(
                text = annotated,
                style = letterStyle,
                maxLines = maxLines,
                constraints = paragraph,
                layoutDirection = LayoutDirection.Rtl,
            )
        }
        val marks = remember(text, markStyle, widthPx, maxLines) {
            measurer.measure(
                text = AnnotatedString(text),
                style = markStyle,
                maxLines = maxLines,
                constraints = paragraph,
                layoutDirection = LayoutDirection.Rtl,
            )
        }

        val contentHeight = max(letters.size.height, marks.size.height)
        val heightDp = with(density) { (contentHeight + verticalBleed * 2).toDp() }

        Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
            val origin = Offset(0f, verticalBleed.toFloat())
            // Colours live in the styles, so nothing here overrides the muted spans.
            drawText(textLayoutResult = marks, topLeft = origin)
            drawText(textLayoutResult = letters, topLeft = origin)
        }
    }
}
