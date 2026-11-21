import { assertEquals } from "jsr:@std/assert@^1";
import {
  ayahAt,
  ayahsInRange,
  ayahsOfJuz,
  ayahsOfSurah,
  globalAyahOf,
  JUZ,
  juzCoveringRange,
  juzOf,
  SURAHS,
  TOTAL_AYAHS,
} from "./quranIndex.ts";

/**
 * These tests exist because every URL in a download manifest is derived from this table.
 * A single mistyped ayah count would silently shift thousands of file names, and the app
 * would download the wrong recitation for the wrong verse — the kind of bug that is
 * invisible until someone listens carefully.
 */

Deno.test("114 surahs, 6236 ayahs, 30 juz", () => {
  assertEquals(SURAHS.length, 114);
  assertEquals(SURAHS.reduce((sum, s) => sum + s.ayahCount, 0), TOTAL_AYAHS);
  assertEquals(JUZ.length, 30);
  assertEquals(JUZ.reduce((sum, j) => sum + j.ayahCount, 0), TOTAL_AYAHS);
});

Deno.test("known boundaries", () => {
  assertEquals(SURAHS[0].ayahCount, 7); // al-Fatiha
  assertEquals(SURAHS[1].ayahCount, 286); // al-Baqarah
  assertEquals(SURAHS[113].ayahCount, 6); // an-Nas
  assertEquals(SURAHS[113].startGlobalAyah, 6231);
  assertEquals(globalAyahOf(1, 1), 1);
  assertEquals(globalAyahOf(2, 1), 8);
  assertEquals(globalAyahOf(114, 6), 6236);
  assertEquals(globalAyahOf(2, 287), undefined);
  assertEquals(globalAyahOf(115, 1), undefined);
});

Deno.test("global ayah round-trips", () => {
  for (const g of [1, 8, 100, 3000, 6236]) {
    const ref = ayahAt(g);
    if (!ref) throw new Error(`ayahAt(${g}) returned nothing`);
    assertEquals(ref.globalAyah, g);
    assertEquals(globalAyahOf(ref.surah, ref.ayah), g);
  }
  assertEquals(ayahAt(0), undefined);
  assertEquals(ayahAt(6237), undefined);
});

Deno.test("juz table is contiguous and starts where it says", () => {
  let expected = 1;
  for (const j of JUZ) {
    assertEquals(j.startGlobalAyah, expected);
    expected += j.ayahCount;
  }
  assertEquals(juzOf(1, 1), 1);
  assertEquals(juzOf(2, 142), 2);
  assertEquals(juzOf(2, 141), 1);
  assertEquals(juzOf(114, 6), 30);
});

Deno.test("ayahsOfSurah and ayahsOfJuz agree with the coordinates", () => {
  const baqarah = ayahsOfSurah(2);
  assertEquals(baqarah.length, 286);
  assertEquals(baqarah[0].globalAyah, 8);
  assertEquals(baqarah[285].globalAyah, 293);

  const juzOne = ayahsOfJuz(1);
  assertEquals(juzOne.length, 148); // 7 + 141
  assertEquals(juzOne[0].surah, 1);
  assertEquals(juzOne.at(-1)?.surah, 2);
  assertEquals(juzOne.at(-1)?.ayah, 141);

  assertEquals(ayahsOfSurah(0).length, 0);
  assertEquals(ayahsOfJuz(31).length, 0);
});

Deno.test("a range that straddles a surah boundary is continuous", () => {
  const range = ayahsInRange(1, 6, 2, 3);
  assertEquals(range.length, 5); // 1:6, 1:7, 2:1, 2:2, 2:3
  assertEquals(
    range.map((r) => `${r.surah}:${r.ayah}`).join(","),
    "1:6,1:7,2:1,2:2,2:3",
  );

  // Reversed input is normalised rather than returning nothing.
  assertEquals(ayahsInRange(2, 3, 1, 6).length, 5);
  assertEquals(ayahsInRange(1, 1, 1, 7).length, 7);
  assertEquals(ayahsInRange(0, 1, 1, 1).length, 0);
});

Deno.test("a juz-granularity request for al-Baqarah resolves to juz 1–3", () => {
  assertEquals(juzCoveringRange(2, 1, 2, 286), [1, 2, 3]);
  assertEquals(juzCoveringRange(1, 1, 1, 7), [1]);
  assertEquals(juzCoveringRange(114, 1, 114, 6), [30]);
});
