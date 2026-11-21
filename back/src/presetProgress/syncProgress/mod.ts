import { coreApp } from "../../../mod.ts";
import { syncProgressFn } from "./syncProgress.fn.ts";
import { syncProgressValidator } from "./syncProgress.val.ts";
import { setTokens, setUser } from "@lib";

export const syncProgressSetup = () =>
  coreApp.acts.setAct({
    schema: "presetAyahProgress",
    actName: "syncProgress",
    validationRunType: "create",
    validator: syncProgressValidator(),
    preAct: [setTokens, setUser],
    fn: syncProgressFn,
  });
