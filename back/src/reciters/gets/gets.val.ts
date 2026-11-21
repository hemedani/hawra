import { boolean, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * The catalogue list. `key` narrows to one reciter; `includeInactive` is for maintenance
 * (the app never sets it).
 */
export const getsValidator = () =>
  object({
    set: object({
      key: optional(string()),
      includeInactive: optional(boolean()),
    }),
    get: optional(selectStruct("reciter", 2)),
  });
