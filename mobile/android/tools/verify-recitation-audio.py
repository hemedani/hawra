#!/usr/bin/env python3
"""Verify that every recitation URL in the bundled catalog actually resolves.

The catalog (`app/src/main/assets/quran/recitations-v1.json`) holds URL *templates*
with a `{ayah}` placeholder. A template is only useful if the host serves real audio
for the ayahs we will ask for, so this script probes a spread of them — first, a
couple of mid-Quran ayahs, the last ayah of the mushaf, and one long ayah — with a
ranged GET (1000 bytes is enough: a 206 with `audio/mpeg` proves the file exists and
supports the seeking Media3 will do).

Nothing in the app calls this. It is the gate you run when adding a reciter, and it
fails loudly rather than letting a dead URL ship.

Usage:
    python3 tools/verify-recitation-audio.py
    python3 tools/verify-recitation-audio.py --reciter alafasy --verbose
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

CATALOG = (
    Path(__file__).resolve().parent.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "quran"
    / "recitations-v1.json"
)

# 1 = al-Fatiha 1:1, 293 = al-Baqarah 2:286 (long), 262 = 2:255 ayat al-kursi (long),
# 6236 = an-Nas 114:6 (last ayah of the mushaf).
PROBE_AYAHS = (1, 262, 293, 6236)

TIMEOUT_SEC = 25


def probe(url: str) -> tuple[bool, str]:
    request = urllib.request.Request(
        url,
        headers={"Range": "bytes=0-999", "User-Agent": "hawraa-asset-verify/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SEC) as response:
            status = response.status
            kind = response.headers.get("Content-Type", "?")
            body = response.read(64)
    except urllib.error.HTTPError as error:
        return False, f"HTTP {error.code}"
    except Exception as error:  # noqa: BLE001 - any transport failure is a failure
        return False, type(error).__name__

    if status not in (200, 206):
        return False, f"HTTP {status}"
    if "audio" not in kind:
        return False, f"content-type {kind}"
    # An ID3 tag or an MPEG frame sync (11 set bits) — not every encoder writes ID3,
    # and the frame header's layer/bitrate bits vary per file.
    if body[:3] == b"ID3" or (len(body) >= 2 and body[0] == 0xFF and body[1] & 0xE0 == 0xE0):
        return True, f"HTTP {status} {kind}"
    return False, f"HTTP {status} {kind} (not mp3: {body[:8]!r})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reciter", help="only check this catalog key")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    reciters = catalog["reciters"]
    if args.reciter:
        reciters = [r for r in reciters if r["key"] == args.reciter]
        if not reciters:
            print(f"no reciter named {args.reciter!r} in {CATALOG.name}", file=sys.stderr)
            return 2

    failures = 0
    for reciter in reciters:
        template = reciter["urlTemplate"]
        if "{ayah}" not in template:
            print(f"{reciter['key']}: template has no {{ayah}} placeholder")
            failures += 1
            continue
        reciter_failures = 0
        for ayah in PROBE_AYAHS:
            url = template.replace("{ayah}", str(ayah))
            ok, detail = probe(url)
            if not ok:
                reciter_failures += 1
                print(f"  FAIL {reciter['key']:<10} ayah {ayah:<5} {detail}  {url}")
            elif args.verbose:
                print(f"  ok   {reciter['key']:<10} ayah {ayah:<5} {detail}")
        failures += reciter_failures
        state = "ok  " if reciter_failures == 0 else "FAIL"
        print(f"{state} {reciter['key']}: {len(PROBE_AYAHS) - reciter_failures}/{len(PROBE_AYAHS)} probes")

    if failures:
        print(f"\n{failures} probe(s) failed", file=sys.stderr)
        return 1
    print(f"\nall {len(reciters)} reciter(s) resolve per-ayah audio")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
