import { object, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/** Promote one preset to be the user's default. */
export const setDefaultValidator = () =>
  object({
    set: object({
      clientId: string(),
    }),
    get: selectStruct("preset", 2),
  });
