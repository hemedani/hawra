import { object, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

export const getValidator = () =>
  object({
    set: object({
      key: string(),
    }),
    get: selectStruct("recitationEdition", 2),
  });
