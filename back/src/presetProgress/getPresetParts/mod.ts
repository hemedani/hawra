import { coreApp } from "../../../mod.ts";
import { getPresetPartsFn } from "./getPresetParts.fn.ts";
import { getPresetPartsValidator } from "./getPresetParts.val.ts";
import { setTokens, setUser } from "@lib";

export const getPresetPartsSetup = () =>
  coreApp.acts.setAct({
    schema: "presetAyahProgress",
    actName: "getPresetParts",
    validationRunType: "create",
    validator: getPresetPartsValidator(),
    preAct: [setTokens, setUser],
    fn: getPresetPartsFn,
  });
