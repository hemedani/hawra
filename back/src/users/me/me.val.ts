import { object, optional } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * No `set` fields at all: the account is whatever the `token` header says it is.
 *
 * A `get.user` block is still accepted, because the app asks for exactly the fields it renders and
 * a fixed projection would either leak fields it does not use or omit ones it does.
 */
export const meValidator = () =>
  object({
    set: optional(object({})),
    get: optional(
      object({
        user: optional(selectStruct("user", 1)),
      }),
    ),
  });
