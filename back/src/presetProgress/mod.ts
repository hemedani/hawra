import { getPresetPartsSetup } from "./getPresetParts/mod.ts";
import { syncProgressSetup } from "./syncProgress/mod.ts";

export const presetProgressSetup = () => {
  syncProgressSetup();
  getPresetPartsSetup();
};
