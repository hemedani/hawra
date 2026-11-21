import { boolean, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";
import { pagination } from "@lib";

/** The preset list: newest-edited first, default pinned on top, archived hidden by default. */
export const getsValidator = () =>
  object({
    set: object({
      ...pagination,
      search: optional(string()),
      includeArchived: optional(boolean()),
    }),
    get: optional(selectStruct("preset", 2)),
  });
