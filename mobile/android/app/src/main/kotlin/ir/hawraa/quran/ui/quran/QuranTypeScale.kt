package ir.hawraa.quran.ui.quran

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Mushaf type scale. The line height is deliberately generous: Uthmani marks stack
 * above and below the letters (shadda + haraka, dagger alef, small high letters), so
 * a tight leading would collide the marks of consecutive lines.
 *
 * Kept in one place so the reader and the settings preview can never disagree.
 */
private const val LINE_HEIGHT_RATIO = 1.85f

fun quranLineHeight(fontSizeSp: Int): TextUnit = (fontSizeSp * LINE_HEIGHT_RATIO).sp
