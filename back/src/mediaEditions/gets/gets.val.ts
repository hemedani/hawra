import { enums, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * The media catalogue. The app asks for "verified `translation`/`tafsir` editions" when it builds
 * the guya download picker, and for "all of them" when it renders attributions. Same shape as
 * `recitationEditions/gets`, but the filters are the media ones (contentType, textKey) — there is
 * no reciter here.
 */
export const getsValidator = () =>
  object({
    set: object({
      contentType: optional(
        enums(["recitation", "translation", "tafsir", "video", "image"]),
      ),
      granularity: optional(enums(["ayah", "surah", "juz"])),
      availability: optional(enums(["verified", "unverified", "blocked"])),
      /** Which text this media narrates/illustrates, e.g. "fa.ansarian". */
      textKey: optional(string()),
    }),
    get: optional(selectStruct("mediaEdition", 2)),
  });
