import { enums, number, object, optional, string } from "lesan";

/**
 * "Give me the exact files to fetch for this edition over this scope."
 *
 * `scope` decides the ayah set; the edition's `granularity` decides how that set collapses
 * into files. Both are needed: asking for *surah 2* on an ayah edition returns 286 files,
 * on a juz edition returns 3 — and the client must be told which, not left to guess.
 */
export const getDownloadManifestValidator = () =>
  object({
    set: object({
      editionKey: string(),
      scope: enums(["ayah", "surah", "juz", "range"]),
      /** For `scope: "surah"` (and `"ayah"`, with `ayah`). */
      surah: optional(number()),
      ayah: optional(number()),
      /** For `scope: "juz"`. */
      juz: optional(number()),
      /** For `scope: "range"` — inclusive, and may straddle a surah boundary. */
      fromSurah: optional(number()),
      fromAyah: optional(number()),
      toSurah: optional(number()),
      toAyah: optional(number()),
    }),
  });
