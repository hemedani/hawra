import { object, string } from "lesan";
import { pagination } from "@lib";

/**
 * "Which parts of the Quran have I covered with this preset?"
 *
 * Returns the individual ayahs (paged, for a detail list) **and** the contiguous ranges
 * (computed, for a human summary like «بقره ۱–۲۰»). Ranges are computed on the server
 * because they are the same arithmetic on every device, and because a phone should not have
 * to download 6236 rows to draw four lines.
 */
export const getPresetPartsValidator = () =>
  object({
    set: object({
      presetClientId: string(),
      ...pagination,
    }),
  });
