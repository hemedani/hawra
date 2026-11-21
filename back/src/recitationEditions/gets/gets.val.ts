import { enums, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * The edition catalogue. The app asks for "verified editions of reciter X" when it builds
 * the download screen, and for "all of them" when it renders Settings attributions.
 */
export const getsValidator = () =>
  object({
    set: object({
      /** Reciter slug, e.g. "parhizgar". */
      reciterKey: optional(string()),
      granularity: optional(enums(["ayah", "surah", "juz"])),
      availability: optional(enums(["verified", "unverified", "blocked"])),
    }),
    get: optional(selectStruct("recitationEdition", 2)),
  });
