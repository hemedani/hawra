import { array, defaulted, object } from "lesan";
import { presetSyncInput } from "../presetInput.ts";

/**
 * Batched preset sync: the device pushes what it edited offline and receives the rows the
 * server kept. An empty batch is valid (the worker calls unconditionally).
 */
export const syncPresetsValidator = () =>
  object({
    set: object({
      presets: defaulted(array(presetSyncInput), []),
    }),
  });
