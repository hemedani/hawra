package ir.hawraa.quran.ui.quran

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.hawraa.quran.R

/**
 * The mushaf typeface. **Scheherazade New** (SIL, OFL) — chosen in the Phase-1
 * typography spike because it is a clean Naskh that matches the owner's
 * readability reference (Ketab Mobin) and covers the full Uthmani repertoire
 * (dagger alef, waqf signs, small high letters, shadda ligatures).
 *
 * The face is bundled in **two derived ink layers** instead of one font, which is
 * what makes letters (حروف) and diacritics (اعراب) independently colorable:
 *
 *  * [QuranLettersFontFamily] — the same face with every mark's outline emptied;
 *  * [QuranMarksFontFamily]   — the same face with every letter's outline emptied.
 *
 * Both keep identical GSUB/GPOS/cmap/hmtx tables, so they shape the same string to
 * the same glyph run at the same positions; stacking them registers exactly. The
 * derivation is reproducible: `tools/build-quran-fonts.py` (the unmodified source
 * face lives in `tools/fonts/`, the license in `assets/licenses/`).
 */
val QuranLettersFontFamily = FontFamily(
    Font(resId = R.font.quran_letters, weight = FontWeight.Normal),
)

val QuranMarksFontFamily = FontFamily(
    Font(resId = R.font.quran_marks, weight = FontWeight.Normal),
)
