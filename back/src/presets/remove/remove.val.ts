import { object, objectIdValidation, optional, string } from "lesan";

/**
 * Removing a preset is **safe by construction**: a preset that has coverage or history is
 * archived instead of deleted, because deleting it would orphan the very data the owner
 * asked to keep ("how much have I covered with this preset"). A preset nothing refers to is
 * deleted outright.
 */
export const removeValidator = () =>
  object({
    set: object({
      _id: optional(objectIdValidation),
      clientId: optional(string()),
    }),
  });
