import { boolean, object, optional, string } from "lesan";

/**
 * Coverage per preset — "how much of the Quran have I covered with this one, and which
 * parts". `presetClientId` narrows to one; omitting it returns every preset, including ones
 * with zero coverage (so the UI can show 0 % instead of hiding them).
 */
export const getMyCoverageValidator = () =>
  object({
    set: object({
      presetClientId: optional(string()),
      /** Include the per-surah breakdown. Off by default: it is the bigger payload. */
      includeSurahs: optional(boolean()),
    }),
  });
