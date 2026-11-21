import { boolean, object, optional } from "lesan";

export const countValidator = () =>
  object({
    set: object({
      includeArchived: optional(boolean()),
    }),
  });
