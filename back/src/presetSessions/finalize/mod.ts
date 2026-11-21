import { coreApp } from "../../../mod.ts";
import { finalizeFn } from "./finalize.fn.ts";
import { finalizeValidator } from "./finalize.val.ts";
import { setTokens, setUser } from "@lib";

export const finalizeSetup = () =>
  coreApp.acts.setAct({
    schema: "presetSession",
    actName: "finalize",
    validationRunType: "create",
    validator: finalizeValidator(),
    preAct: [setTokens, setUser],
    fn: finalizeFn,
  });
