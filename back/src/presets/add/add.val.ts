import { number, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";
import { presetFields } from "../presetInput.ts";

/**
 * Creates a preset. `clientId` may be supplied by the device (so an offline-created preset
 * keeps its identity when it finally syncs) or minted here.
 */
export const addValidator = () =>
  object({
    set: object({
      ...presetFields,
      clientId: optional(string()),
      /** Device clock of the edit; falls back to server time. */
      updatedAtMs: optional(number()),
    }),
    get: selectStruct("preset", 2),
  });
