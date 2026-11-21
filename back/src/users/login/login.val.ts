import { enums, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * Same `set` shape as `register`, minus `displayName`: logging in is not a place to rename
 * someone. The `get` block matches register's too, so a client can decode one DTO for both acts.
 */
export const loginValidator = () =>
  object({
    set: object({
      email: string(),
      password: string(),
    }),
    get: optional(
      object({
        token: optional(enums([0, 1])),
        user: optional(selectStruct("user", 1)),
      }),
    ),
  });
