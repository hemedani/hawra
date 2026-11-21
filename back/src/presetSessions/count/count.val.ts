import { number, object, optional, string } from "lesan";

export const countValidator = () =>
  object({
    set: object({
      presetClientId: optional(string()),
      from: optional(number()),
      to: optional(number()),
    }),
  });
