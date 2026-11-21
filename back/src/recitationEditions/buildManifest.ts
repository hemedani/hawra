import {
  type AyahRef,
  ayahsInRange,
  ayahsOfJuz,
  ayahsOfSurah,
  juzCoveringRange,
} from "../../content/quranIndex.ts";

/**
 * Turns "this edition + this scope" into the exact list of files a device must fetch.
 *
 * Pure on purpose: no DB, no `mod.ts` import, so `buildManifest_test.ts` can pin the URL
 * arithmetic without a Mongo instance. A wrong file name here is the most expensive bug in
 * the whole feature — it produces a download that succeeds and an ayah that is wrong.
 */

export type Granularity = "ayah" | "surah" | "juz";

export interface ManifestEdition {
  key: string;
  granularity: Granularity;
  urlTemplate: string;
  fileExtension: string;
}

export interface ManifestFile {
  /** Stable identity of this file within the edition, e.g. `ayah:2:3`, `juz:30`. */
  key: string;
  kind: Granularity;
  surah?: number;
  ayah?: number;
  juz?: number;
  globalAyah?: number;
  url: string;
  /** Where the device should store it, relative to its recitation root. */
  relativePath: string;
}

export type ManifestScope = "ayah" | "surah" | "juz" | "range";

export interface ManifestScopeInput {
  scope: ManifestScope;
  surah?: number;
  ayah?: number;
  juz?: number;
  fromSurah?: number;
  fromAyah?: number;
  toSurah?: number;
  toAyah?: number;
}

/** Placeholders an edition's template may use, and what they mean. */
const PLACEHOLDER = /\{(ayah|ayah3|surah|surah3|juz|juz2)\}/g;

const pad = (value: number, width: number): string =>
  String(value).padStart(width, "0");

/**
 * Resolves the ayah set a scope asks for. Returns `null` when the scope is malformed, so
 * the act can answer with a clear error instead of an empty manifest that looks like
 * "you already have everything".
 */
export const resolveScope = (input: ManifestScopeInput): AyahRef[] | null => {
  switch (input.scope) {
    case "ayah": {
      if (input.surah === undefined || input.ayah === undefined) return null;
      return ayahsInRange(input.surah, input.ayah, input.surah, input.ayah);
    }
    case "surah":
      return input.surah === undefined ? null : ayahsOfSurah(input.surah);
    case "juz":
      return input.juz === undefined ? null : ayahsOfJuz(input.juz);
    case "range": {
      const { fromSurah, fromAyah, toSurah, toAyah } = input;
      if (
        fromSurah === undefined || fromAyah === undefined ||
        toSurah === undefined || toAyah === undefined
      ) {
        return null;
      }
      return ayahsInRange(fromSurah, fromAyah, toSurah, toAyah);
    }
  }
};

const REQUIRED_PLACEHOLDER: Record<Granularity, string[]> = {
  ayah: ["{ayah}", "{ayah3}"],
  surah: ["{surah}", "{surah3}"],
  juz: ["{juz}", "{juz2}"],
};

const fill = (
  template: string,
  vars: { ayah?: number; ayahNumber?: number; surah?: number; juz?: number },
): string =>
  template.replace(PLACEHOLDER, (_match, name: string) => {
    switch (name) {
      case "ayah":
        return vars.ayah === undefined ? "" : String(vars.ayah);
      // `{ayah3}` is the in-surah ayah, zero-padded — the scheme Inoor (and many per-ayah
      // archives) name files by, e.g. Baqarah verse 5 = `002005`. `{ayah}` stays the global
      // number (1…6236) for the AlQuran Cloud CDN; the two coexist because different sources
      // address the same verse differently.
      case "ayah3":
        return vars.ayahNumber === undefined ? "" : pad(vars.ayahNumber, 3);
      case "surah":
        return vars.surah === undefined ? "" : String(vars.surah);
      // Zero-padded forms exist because the upstream sources pad: mp3quran wants
      // `001.mp3` and the Shakernejad archive wants `Juz-01.mp3`.
      case "surah3":
        return vars.surah === undefined ? "" : pad(vars.surah, 3);
      case "juz":
        return vars.juz === undefined ? "" : String(vars.juz);
      case "juz2":
        return vars.juz === undefined ? "" : pad(vars.juz, 2);
      default:
        return "";
    }
  });

/**
 * Builds the file list for [edition] over [ayahs].
 *
 * The granularity is honoured literally: a surah-granularity edition returns **whole surah
 * files** even when the range covers half a surah, and a juz-granularity edition returns
 * whole juz files even when the range is one page. Silently substituting per-ayah files
 * would be a different recitation than the one the user chose.
 */
export const buildManifest = (
  edition: ManifestEdition,
  ayahs: AyahRef[],
): ManifestFile[] => {
  if (ayahs.length === 0) return [];

  const template = edition.urlTemplate;
  if (
    !REQUIRED_PLACEHOLDER[edition.granularity].some((p) => template.includes(p))
  ) {
    throw new Error(
      `recitationEdition "${edition.key}" is ${edition.granularity}-granular but its urlTemplate has no ${
        REQUIRED_PLACEHOLDER[edition.granularity].join(" or ")
      } placeholder`,
    );
  }

  const ext = edition.fileExtension || "mp3";

  if (edition.granularity === "ayah") {
    return ayahs.map((ref) => ({
      key: `ayah:${ref.surah}:${ref.ayah}`,
      kind: "ayah" as const,
      surah: ref.surah,
      ayah: ref.ayah,
      globalAyah: ref.globalAyah,
      url: fill(template, {
        ayah: ref.globalAyah,
        surah: ref.surah,
        ayahNumber: ref.ayah,
      }),
      relativePath: `${edition.key}/ayah/${pad(ref.surah, 3)}/${
        pad(ref.ayah, 3)
      }.${ext}`,
    }));
  }

  if (edition.granularity === "surah") {
    const surahs = [...new Set(ayahs.map((ref) => ref.surah))].sort((a, b) =>
      a - b
    );
    return surahs.map((surahNumber) => ({
      key: `surah:${surahNumber}`,
      kind: "surah" as const,
      surah: surahNumber,
      url: fill(template, { surah: surahNumber }),
      relativePath: `${edition.key}/surah/${pad(surahNumber, 3)}.${ext}`,
    }));
  }

  const first = ayahs[0];
  const last = ayahs[ayahs.length - 1];
  const juzNumbers = juzCoveringRange(
    first.surah,
    first.ayah,
    last.surah,
    last.ayah,
  );
  return juzNumbers.map((juzNumber) => ({
    key: `juz:${juzNumber}`,
    kind: "juz" as const,
    juz: juzNumber,
    url: fill(template, { juz: juzNumber }),
    relativePath: `${edition.key}/juz/${pad(juzNumber, 2)}.${ext}`,
  }));
};
