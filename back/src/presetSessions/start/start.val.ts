import { number, object, optional, string } from "lesan";
import { selectStruct } from "../../../mod.ts";

/**
 * Opens a sitting. `clientId` is minted by the device, so a retried start after a lost
 * response returns the same row instead of opening a second sitting.
 */
export const startValidator = () =>
  object({
    set: object({
      clientId: string(),
      /** The preset being played, by the client id the device knows. */
      presetClientId: optional(string()),
      /** Snapshot of its name, so history survives the preset being archived. */
      presetName: string(),
      startedAt: number(),
      surah: number(),
      ayah: number(),
      page: optional(number()),
      reciterKey: optional(string()),
      speed: optional(number()),
    }),
    get: optional(selectStruct("presetSession", 2)),
  });
