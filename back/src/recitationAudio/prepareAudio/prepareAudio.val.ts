import { boolean, enums, number, object, optional, string } from "lesan";

/**
 * Same coordinates as `getDownloadManifest`, because it mirrors exactly what that act would
 * have told a device to download. Two different scope vocabularies would be two chances to
 * disagree about what "surah 2" means.
 */
export const prepareAudioValidator = () =>
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
      /** Re-fetch files the server already holds. Off by default; mirroring is idempotent. */
      force: optional(boolean()),
    }),
  });
