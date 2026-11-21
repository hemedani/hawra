import { enums, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

export const registerDeviceValidator = () =>
  object({
    set: object({
      deviceId: string(),
      displayName: optional(string()),
    }),
    get: optional(
      object({
        token: optional(enums([0, 1])),
        user: optional(selectStruct("user", 1)),
      }),
    ),
  });
