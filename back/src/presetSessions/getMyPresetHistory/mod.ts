import { coreApp } from "../../../mod.ts";
import { getMyPresetHistoryFn } from "./getMyPresetHistory.fn.ts";
import { getMyPresetHistoryValidator } from "./getMyPresetHistory.val.ts";
import { setTokens, setUser } from "@lib";

export const getMyPresetHistorySetup = () =>
  coreApp.acts.setAct({
    schema: "presetSession",
    actName: "getMyPresetHistory",
    validationRunType: "create",
    validator: getMyPresetHistoryValidator(),
    preAct: [setTokens, setUser],
    fn: getMyPresetHistoryFn,
  });
