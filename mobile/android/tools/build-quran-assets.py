#!/usr/bin/env python3
"""Build the bundled Quran content asset for the Hawraa Android app.

Provenance (do not change without recording it in docs/plans/01-*.md):

  text        Tanzil.net — Uthmani (simple-enhanced, Uthmani orthography)
              https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt&agree=true
              Tanzil Quran Text (Uthmani) — Copyright (C) 2007-2025 Tanzil Project.
              Licensed under the Creative Commons Attribution 3.0 license.
              VERBATIM copy: the script never normalises, reorders or "fixes" the text.

  translation Tanzil.net — fa.ansarian (Hossein Ansarian, Persian)
              https://tanzil.net/trans/fa.ansarian

  layout      Quran.com API v4 (`/verses/by_page/{n}`) for page → ayah mapping,
              juz / hizb / rub-el-hizb / sajdah markers, and surah metadata.
              Madani 604-page mushaf.

The app must never fetch sacred text at runtime; this script is the only place
that talks to the network, and its output is committed under app/src/main/assets.

Usage:
    python3 tools/build-quran-assets.py            # uses ./ignore-scripts/cache
    python3 tools/build-quran-assets.py --offline  # fail instead of downloading
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
import urllib.request

REPO = pathlib.Path(__file__).resolve().parents[3]  # …/Hawraa
CACHE = REPO / "ignore-scripts" / "cache" / "quran"
OUT = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/quran/pages-v1.json"

PAGES = [1, 2, 3, 4, 5]
SCHEMA_VERSION = 1

SOURCES = {
    "text": {
        "name": "Tanzil Quran Text (Uthmani)",
        "url": "https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt&agree=true",
        "license": "CC BY 3.0 — Copyright (C) 2007-2025 Tanzil Project",
    },
    "translation": {
        "name": "Ansarian (Persian) — fa.ansarian",
        "url": "https://tanzil.net/trans/fa.ansarian",
        "license": "Tanzil translation download terms",
    },
    "layout": {
        "name": "Quran.com API v4 (Madani mushaf page metadata)",
        "url": "https://api.quran.com/api/v4/verses/by_page/{page}",
        "license": "Quran.com open API",
    },
}

# Persian surah names are not in the API; add them as the surahs are bundled.
SURAH_NAMES_FA = {1: "فاتحه", 2: "بقره"}


def fetch(url: str, filename: str, offline: bool) -> bytes:
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / filename
    if path.exists():
        return path.read_bytes()
    if offline:
        sys.exit(f"missing cached source {path} and --offline was given")
    print(f"  downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "hawraa-content-builder/1"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    path.write_bytes(data)
    return data


# Ayah counts per surah in the Hafs 'an 'Asim reading (Madani mushaf, 6236 verses).
AYAH_COUNTS = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98,
    135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11,
    11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25,
    22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6,
]


def parse_tanzil(data: bytes, columns: int) -> dict[tuple[int, int], str]:
    """Parse `sura|aya|text` (3 cols) or one verse per line (1 col, sequential)."""
    out: dict[tuple[int, int], str] = {}
    text = data.decode("utf-8")
    if columns == 3:
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            sura, aya, body = line.split("|", 2)
            out[(int(sura), int(aya))] = body
        return out
    # Tanzil "uthmani" txt: one verse per line, ordered 1:1 … 114:6, no markers,
    # with `#` comment lines (license footer) that must be skipped.
    sura, aya = 1, 1
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        out[(sura, aya)] = line
        aya += 1
        if sura <= 114 and aya > AYAH_COUNTS[sura - 1]:
            sura, aya = sura + 1, 1
    if len(out) != 6236:
        sys.exit(f"expected 6236 verses, parsed {len(out)} — source changed?")
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true")
    args = ap.parse_args()

    print("sources:")
    text_raw = fetch(SOURCES["text"]["url"], "tanzil-uthmani.txt", args.offline)
    trans_raw = fetch(SOURCES["translation"]["url"], "fa.ansarian.txt", args.offline)
    uthmani = parse_tanzil(text_raw, columns=1)
    ansarian = parse_tanzil(trans_raw, columns=3)
    print(f"  uthmani verses: {len(uthmani)}   ansarian verses: {len(ansarian)}")

    chapters = json.loads(fetch("https://api.quran.com/api/v4/chapters", "chapters.json", args.offline))
    by_number = {c["id"]: c for c in chapters["chapters"]}

    # Tanzil writes the basmala *inside* the first ayah of every surah that has one.
    # The reader draws the basmala as its own line, so drop that exact prefix from
    # ayah 1 — asserted byte-for-byte, never guessed. Al-Fatiha (basmala = ayah 1)
    # and At-Tawbah (no basmala) are untouched because bismillah_pre is false there.
    basmala = uthmani[(1, 1)]
    basmala_stripped: list[str] = []

    pages = []
    for page in PAGES:
        meta = json.loads(
            fetch(
                SOURCES["layout"]["url"].format(page=page),
                f"page-{page}.json",
                args.offline,
            )
        )["verses"]
        verses = []
        for v in meta:
            surah, ayah = (int(x) for x in v["verse_key"].split(":"))
            key = (surah, ayah)
            if key not in uthmani:
                sys.exit(f"no Uthmani text for {v['verse_key']}")
            text = uthmani[key]
            if ayah == 1 and by_number[surah]["bismillah_pre"]:
                if not text.startswith(basmala):
                    sys.exit(f"expected the basmala prefix on {v['verse_key']}, found {text[:40]!r}")
                text = text[len(basmala):].lstrip()
                basmala_stripped.append(v["verse_key"])
            verses.append(
                {
                    "key": v["verse_key"],
                    "surah": surah,
                    "ayah": ayah,
                    "textUthmani": text,
                    "translationFa": ansarian.get(key, ""),
                    "juz": v["juz_number"],
                    "hizb": v["hizb_number"],
                    "rubElHizb": v["rub_el_hizb_number"],
                    "sajdah": v["sajdah_number"],
                }
            )
        surahs = sorted({v["surah"] for v in verses})
        pages.append(
            {
                "page": page,
                "juz": verses[0]["juz"],
                "surahNumbers": surahs,
                "verses": verses,
            }
        )

    used_surahs = sorted({v["surah"] for p in pages for v in p["verses"]})
    surahs_out = [
        {
            "number": n,
            "nameArabic": by_number[n]["name_arabic"],
            "nameFa": SURAH_NAMES_FA.get(n, by_number[n]["name_simple"]),
            "nameEn": by_number[n]["name_simple"],
            "ayahCount": by_number[n]["verses_count"],
            "revelationPlace": by_number[n]["revelation_place"],
            "bismillahPre": by_number[n]["bismillah_pre"],
        }
        for n in used_surahs
    ]

    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "mushaf": "madani-604",
        "sources": SOURCES,
        "checksums": {
            "textSha256": hashlib.sha256(text_raw).hexdigest(),
            "translationSha256": hashlib.sha256(trans_raw).hexdigest(),
        },
        # Taken from the vetted source (1:1), never typed by hand.
        "basmalaUthmani": basmala,
        "transformations": {
            # Documented, asserted edits applied on top of the verbatim source.
            "basmalaStrippedFromSurahOpening": basmala_stripped,
        },
        "surahs": surahs_out,
        "pages": pages,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    total = sum(len(p["verses"]) for p in pages)
    print(f"wrote {OUT.relative_to(REPO)} — {len(pages)} pages, {total} verses, {OUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
