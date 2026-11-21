import { coreApp } from "../../../mod.ts";
import { countFn } from "./count.fn.ts";
import { countValidator } from "./count.val.ts";
import { setTokens, setUser } from "@lib";

export const countSetup = () =>
  coreApp.acts.setAct({
    schema: "presetSession",
    actName: "count",
    validationRunType: "create",
    validator: countValidator(),
    preAct: [setTokens, setUser],
    fn: countFn,
  });
