import { array, defaulted, number, object, optional, string } from "lesan";

/**
 * One coverage entry. Values are **absolute**, not deltas — the server merges them with
 * `$max`/`$min`, which is what makes a retried batch idempotent and lets two devices
 * converge instead of double-counting.
 */
export const progressEntry = object({
  surah: number(),
  ayah: number(),
  globalAyah: number(),
  page: optional(number()),
  /** Completed passes through this ayah under the preset. */
  plays: number(),
  listenedSec: defaulted(number(), 0),
  firstAt: number(),
  lastAt: number(),
  lastKind: optional(string()),
});

export const syncProgressValidator = () =>
  object({
    set: object({
      /** Which preset this coverage belongs to (the client id the device knows). */
      presetClientId: string(),
      entries: defaulted(array(progressEntry), []),
    }),
  });
