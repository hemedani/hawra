import { enums, number, object, optional, string } from "lesan";

/**
 * Closes a sitting. Idempotent: finalizing an already-finalized row returns it unchanged,
 * because the device may finalize both when the reader leaves and when the app is killed.
 */
export const finalizeValidator = () =>
  object({
    set: object({
      clientId: string(),
      endedAt: number(),
      activeSec: optional(number()),
      ayahsCompleted: optional(number()),
      stepsCompleted: optional(number()),
      /** `abandoned` when the app died rather than the reader stopping. */
      status: optional(enums(["finalized", "abandoned"])),
    }),
  });
