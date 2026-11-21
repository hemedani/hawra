import { enums, number, object, optional, string } from "lesan";

/**
 * "Give me the exact files to fetch for this media edition over this scope."
 *
 * Identical input shape to `recitationEditions/getDownloadManifest`: the scope decides the ayah
 * set, the edition's granularity decides how it collapses into files. Guya audio is verse-tied
 * exactly like recitation, so the same coordinate arithmetic applies.
 */
export const getDownloadManifestValidator = () =>
  object({
    set: object({
      editionKey: string(),
      scope: enums(["ayah", "surah", "juz", "range"]),
      surah: optional(number()),
      ayah: optional(number()),
      juz: optional(number()),
      fromSurah: optional(number()),
      fromAyah: optional(number()),
      toSurah: optional(number()),
      toAyah: optional(number()),
    }),
  });
