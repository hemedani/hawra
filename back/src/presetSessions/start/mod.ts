import { coreApp } from "../../../mod.ts";
import { startFn } from "./start.fn.ts";
import { startValidator } from "./start.val.ts";
import { setTokens, setUser } from "@lib";

export const startSetup = () =>
  coreApp.acts.setAct({
    schema: "presetSession",
    actName: "start",
    validationRunType: "create",
    validator: startValidator(),
    preAct: [setTokens, setUser],
    fn: startFn,
  });
