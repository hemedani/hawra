import { countSetup } from "./count/mod.ts";
import { finalizeSetup } from "./finalize/mod.ts";
import { getMyPresetHistorySetup } from "./getMyPresetHistory/mod.ts";
import { logEventSetup } from "./logEvent/mod.ts";
import { startSetup } from "./start/mod.ts";

export const presetSessionsSetup = () => {
  startSetup();
  logEventSetup();
  finalizeSetup();
  getMyPresetHistorySetup();
  countSetup();
};
