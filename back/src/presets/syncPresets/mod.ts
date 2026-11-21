import { coreApp } from "../../../mod.ts";
import { syncPresetsFn } from "./syncPresets.fn.ts";
import { syncPresetsValidator } from "./syncPresets.val.ts";
import { setTokens, setUser } from "@lib";

export const syncPresetsSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "syncPresets",
    validationRunType: "create",
    validator: syncPresetsValidator(),
    preAct: [setTokens, setUser],
    fn: syncPresetsFn,
  });
