import { object, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/** One reciter by its stable key, with editions embedded. */
export const getValidator = () =>
  object({
    set: object({
      key: string(),
    }),
    get: selectStruct("reciter", 2),
  });
