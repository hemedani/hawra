import { coreApp } from "../../../mod.ts";
import { logEventFn } from "./logEvent.fn.ts";
import { logEventValidator } from "./logEvent.val.ts";
import { setTokens, setUser } from "@lib";

export const logEventSetup = () =>
  coreApp.acts.setAct({
    schema: "presetSession",
    actName: "logEvent",
    validationRunType: "create",
    validator: logEventValidator(),
    preAct: [setTokens, setUser],
    fn: logEventFn,
  });
