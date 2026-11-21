import { number, object, objectIdValidation, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";
import { presetPatchFields } from "../presetInput.ts";

/** Patch one preset. Every editable field is optional; the id selects the row. */
export const updateValidator = () =>
  object({
    set: object({
      ...presetPatchFields,
      _id: optional(objectIdValidation),
      clientId: optional(string()),
      /** Device clock of this edit — the LWW token. Falls back to server time. */
      updatedAtMs: optional(number()),
    }),
    get: selectStruct("preset", 2),
  });
