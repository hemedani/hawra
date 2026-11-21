import { enums, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * `email` is the login handle; `displayName` is what the app greets the reader with.
 *
 * No password confirmation field: the client owns that check, because sending the password twice
 * over the wire doubles the chance of it appearing in a log without adding a server-side rule.
 */
export const registerValidator = () =>
  object({
    set: object({
      email: string(),
      password: string(),
      displayName: optional(string()),
    }),
    get: optional(
      object({
        token: optional(enums([0, 1])),
        user: optional(selectStruct("user", 1)),
      }),
    ),
  });
