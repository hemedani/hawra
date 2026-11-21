import { object, objectIdValidation, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/** One preset, by server id or by the client id the device minted. */
export const getValidator = () =>
  object({
    set: object({
      _id: optional(objectIdValidation),
      clientId: optional(string()),
    }),
    get: selectStruct("preset", 2),
  });
