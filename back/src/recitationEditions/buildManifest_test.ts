import { assertEquals, assertThrows } from "jsr:@std/assert@^1";
import {
  buildManifest,
  type ManifestEdition,
  resolveScope,
} from "./buildManifest.ts";
import { ayahsOfJuz, ayahsOfSurah } from "../../content/quranIndex.ts";

const ayahEdition: ManifestEdition = {
  key: "parhizgar-ayah-48",
  granularity: "ayah",
  urlTemplate:
    "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/{ayah}.mp3",
  fileExtension: "mp3",
};

const surahEdition: ManifestEdition = {
  key: "minshawi-surah-128",
  granularity: "surah",
  urlTemplate: "https://server10.mp3quran.net/minsh/{surah3}.mp3",
  fileExtension: "mp3",
};

const juzEdition: ManifestEdition = {
  key: "shakernejad-juz-24",
  granularity: "juz",
  urlTemplate:
    "https://dl.meshkatsound.com/Tartil/Hamed-Shakernejad/Shakernejad-Quran-Juz-{juz2}.mp3",
  fileExtension: "mp3",
};

Deno.test("ayah edition: one file per ayah, addressed by the global ayah number", () => {
  const files = buildManifest(ayahEdition, ayahsOfSurah(1));
  assertEquals(files.length, 7);
  assertEquals(
    files[0].url,
    "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/1.mp3",
  );
  assertEquals(
    files[6].url,
    "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/7.mp3",
  );
  assertEquals(files[0].relativePath, "parhizgar-ayah-48/ayah/001/001.mp3");
  assertEquals(files[0].key, "ayah:1:1");
});

Deno.test("ayah edition: al-Baqarah 3 maps to global ayah 10, not to 2:3", () => {
  const files = buildManifest(ayahEdition, [
    { surah: 2, ayah: 3, globalAyah: 10 },
  ]);
  assertEquals(files[0].url.endsWith("/10.mp3"), true);
  assertEquals(files[0].relativePath, "parhizgar-ayah-48/ayah/002/003.mp3");
});

Deno.test("surah edition: whole surah files, zero-padded, deduplicated", () => {
  const files = buildManifest(surahEdition, ayahsOfSurah(2));
  assertEquals(files.length, 1);
  assertEquals(files[0].url, "https://server10.mp3quran.net/minsh/002.mp3");
  assertEquals(files[0].relativePath, "minshawi-surah-128/surah/002.mp3");

  // A range straddling two surahs yields both files, in order.
  const straddle = buildManifest(surahEdition, [
    { surah: 1, ayah: 7, globalAyah: 7 },
    { surah: 2, ayah: 1, globalAyah: 8 },
  ]);
  assertEquals(straddle.map((f) => f.surah), [1, 2]);
});

Deno.test("juz edition: a partial range returns the whole juz it falls in", () => {
  const files = buildManifest(juzEdition, [
    { surah: 1, ayah: 1, globalAyah: 1 },
  ]);
  assertEquals(files.length, 1);
  assertEquals(
    files[0].url,
    "https://dl.meshkatsound.com/Tartil/Hamed-Shakernejad/Shakernejad-Quran-Juz-01.mp3",
  );
  assertEquals(files[0].relativePath, "shakernejad-juz-24/juz/01.mp3");

  // al-Baqarah spans juz 1–3.
  const baqarah = buildManifest(juzEdition, ayahsOfSurah(2));
  assertEquals(baqarah.map((f) => f.juz), [1, 2, 3]);
  assertEquals(baqarah[2].url.endsWith("Juz-03.mp3"), true);
});

Deno.test("a juz that starts mid-surah resolves correctly", () => {
  const files = buildManifest(juzEdition, ayahsOfJuz(30));
  assertEquals(files.length, 1);
  assertEquals(files[0].juz, 30);
  assertEquals(files[0].url.endsWith("Juz-30.mp3"), true);
});

Deno.test("a template without its granularity placeholder is a catalogue bug, not a 404", () => {
  assertThrows(
    () =>
      buildManifest(
        { ...ayahEdition, urlTemplate: "https://example.com/{surah}.mp3" },
        ayahsOfSurah(1),
      ),
    Error,
    "no {ayah} placeholder",
  );
});

Deno.test("resolveScope rejects malformed scopes instead of returning an empty list", () => {
  assertEquals(resolveScope({ scope: "ayah", surah: 1 }), null);
  assertEquals(resolveScope({ scope: "surah" }), null);
  assertEquals(resolveScope({ scope: "juz" }), null);
  assertEquals(resolveScope({ scope: "range", fromSurah: 1 }), null);
  assertEquals(resolveScope({ scope: "juz", juz: 1 })?.length, 148);
  assertEquals(
    resolveScope({
      scope: "range",
      fromSurah: 1,
      fromAyah: 1,
      toSurah: 1,
      toAyah: 7,
    })?.length,
    7,
  );
});
