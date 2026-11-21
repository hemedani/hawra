import { number, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";
import { pagination } from "@lib";

/** Preset history, newest sitting first, optionally narrowed to one preset or date range. */
export const getMyPresetHistoryValidator = () =>
  object({
    set: object({
      ...pagination,
      presetClientId: optional(string()),
      /** Epoch millis bounds on `startedAt`. */
      from: optional(number()),
      to: optional(number()),
    }),
    get: optional(selectStruct("presetSession", 2)),
  });
