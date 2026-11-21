import { addSetup } from "./add/mod.ts";
import { countSetup } from "./count/mod.ts";
import { getSetup } from "./get/mod.ts";
import { getMyCoverageSetup } from "./getMyCoverage/mod.ts";
import { getsSetup } from "./gets/mod.ts";
import { removeSetup } from "./remove/mod.ts";
import { setDefaultSetup } from "./setDefault/mod.ts";
import { syncPresetsSetup } from "./syncPresets/mod.ts";
import { updateSetup } from "./update/mod.ts";

export const presetsSetup = () => {
  addSetup();
  getsSetup();
  getSetup();
  updateSetup();
  removeSetup();
  countSetup();
  setDefaultSetup();
  syncPresetsSetup();
  getMyCoverageSetup();
};
