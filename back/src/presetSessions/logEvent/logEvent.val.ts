import { enums, number, object, optional, string } from "lesan";

/**
 * A transport event **inside** an open sitting.
 *
 * This act never creates a row — that is the owner's requirement ("not a new history entry
 * every time the user presses play or pause"). It updates the one row for the sitting:
 * counters, range, last activity, and a capped event log.
 */
export const logEventValidator = () =>
  object({
    set: object({
      /** The session this event belongs to (the row `start` created). */
      clientId: string(),
      kind: enums(["play", "pause", "seek", "step", "ayah"]),
      at: number(),
      /** Where the event happened, for the detail log. */
      surah: optional(number()),
      ayah: optional(number()),
      stepIndex: optional(number()),
      /** Absolute (not delta) accumulated seconds — merged with `$max`, so retries are safe. */
      activeSec: optional(number()),
      ayahsCompleted: optional(number()),
      stepsCompleted: optional(number()),
      /** Current cursor; the range's end follows it. */
      toSurah: optional(number()),
      toAyah: optional(number()),
      fromPage: optional(number()),
      toPage: optional(number()),
    }),
  });
