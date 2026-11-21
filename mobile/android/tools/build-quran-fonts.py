#!/usr/bin/env python3
"""Derive the two "ink layer" fonts the reader needs for separately colorable
letters (حروف) and diacritics (اعراب).

Why this exists
---------------
The obvious implementation — draw the full verse in the diacritic color, then draw
the same verse with the marks stripped in the letter color — does **not** work.
Under HarfBuzz (which Android uses) removing the marks changes glyph selection and
advances: e.g. "لِلَّهِ" is ligated to a single glyph *with* the shadda when the
mark is present, and to a different "llah" glyph when it is not. The two layers
then drift apart and the page shows coloured ghosts. (Verified for Scheherazade
New, Amiri, Amiri Quran and KFGQPC Hafs — no font survives it.)

So instead we keep the *text* untouched and split the *font*:

  * `quran_letters.ttf` — same tables, same glyph ids, same metrics; every mark
    glyph's outline is emptied.
  * `quran_marks.ttf`  — same tables, same glyph ids, same metrics; every base
    glyph's outline is emptied.

Because GSUB/GPOS/cmap/hmtx are untouched, both fonts shape the identical string
to the identical glyph run at the identical positions. Drawing them stacked gives
exact registration and lets each layer carry its own colour.

Run it whenever the source face changes:

    python3 tools/build-quran-fonts.py
"""

from __future__ import annotations

import array
import pathlib
import sys

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import GlyphCoordinates
from fontTools.ttLib.tables.ttProgram import Program

FONT_DIR = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/res/font"
SOURCE = pathlib.Path(__file__).resolve().parent / "fonts" / "scheherazade-new-regular.ttf"
LETTERS_OUT = FONT_DIR / "quran_letters.ttf"
MARKS_OUT = FONT_DIR / "quran_marks.ttf"

# Quranic combining marks — the same set the app treats as "diacritics".
MARK_RANGES = [
    (0x0610, 0x061A),
    (0x064B, 0x065F),
    (0x0670, 0x0670),
    (0x06D6, 0x06ED),
    (0x08D3, 0x08FF),
]

# Glyphs that GDEF does not flag as marks but that are marks for our purposes.
FORCE_MARK_GLYPHS = {
    "uni06E5",  # small waw  (Quranic annotation mark, GDEF class 1 in this face)
    "uni06E6",  # small yeh
}

# Mark contours baked into an otherwise-letters glyph: glyph name -> contour indices.
# (The shadda of the "Allah" ligature.) Extend this list if a future face/ligature
# needs it — verify visually with `tools/render-font-layers.swift`.
BAKED_MARK_CONTOURS: dict[str, list[int]] = {
    "uni0647.fina.allah": [0],
}


def is_mark_codepoint(cp: int) -> bool:
    return any(lo <= cp <= hi for lo, hi in MARK_RANGES)


def blank(glyph) -> None:
    glyph.numberOfContours = 0
    glyph.coordinates = GlyphCoordinates()
    glyph.endPtsOfContours = []
    glyph.flags = array.array("B")
    glyph.program = Program()


def split_contours(glyph) -> list[list[tuple[int, int, int]]]:
    """Return the glyph's contours as lists of (x, y, flag) points."""
    if glyph.numberOfContours <= 0:
        return []
    coords = list(glyph.coordinates)
    flags = list(glyph.flags)
    out = []
    start = 0
    for end in glyph.endPtsOfContours:
        out.append([(coords[i][0], coords[i][1], flags[i] & 1) for i in range(start, end + 1)])
        start = end + 1
    return out


def write_contours(glyph, contours: list[list[tuple[int, int, int]]]) -> None:
    if not contours:
        blank(glyph)
        return
    coords = []
    ends = []
    flags = []
    for contour in contours:
        for x, y, flag in contour:
            coords.append((x, y))
            flags.append(flag)  # preserve on-curve/off-curve so curves stay curves
        ends.append(len(coords) - 1)
    glyph.numberOfContours = len(contours)
    glyph.coordinates = GlyphCoordinates(coords)
    glyph.endPtsOfContours = ends
    glyph.flags = array.array("B", flags)
    glyph.program = Program()


def main() -> None:
    if not SOURCE.exists():
        sys.exit(f"missing source font {SOURCE}")
    font = TTFont(SOURCE)
    order = font.getGlyphOrder()
    glyf = font["glyf"]
    cmap = font.getBestCmap()

    gdef = font["GDEF"].table
    class_defs = getattr(getattr(gdef, "GlyphClassDef", None), "classDefs", {}) or {}

    mark_glyphs: set[str] = {name for name, cls in class_defs.items() if cls == 3}
    # Also treat any glyph reachable from a combining-mark codepoint as a mark.
    for cp, name in cmap.items():
        if is_mark_codepoint(cp):
            mark_glyphs.add(name)
    mark_glyphs |= FORCE_MARK_GLYPHS

    letters = TTFont(SOURCE)
    marks = TTFont(SOURCE)

    for name in order:
        l_glyph = letters["glyf"][name]
        m_glyph = marks["glyf"][name]
        if l_glyph.numberOfContours == -1:  # composite glyph: leave both intact
            continue

        if name in mark_glyphs:
            blank(l_glyph)
            continue

        baked = BAKED_MARK_CONTOURS.get(name)
        if baked:
            contours = split_contours(l_glyph)
            mark_contours = [c for i, c in enumerate(contours) if i in baked]
            letter_contours = [c for i, c in enumerate(contours) if i not in baked]
            write_contours(l_glyph, letter_contours)
            write_contours(m_glyph, mark_contours)
            continue

        blank(m_glyph)

    letters.save(LETTERS_OUT)
    marks.save(MARKS_OUT)

    for path in (LETTERS_OUT, MARKS_OUT):
        print(f"wrote {path.relative_to(FONT_DIR.parents[3])} — {path.stat().st_size} bytes")
    print(f"mark glyphs: {len(mark_glyphs)}  baked-mark glyphs: {len(BAKED_MARK_CONTOURS)}")


if __name__ == "__main__":
    main()
