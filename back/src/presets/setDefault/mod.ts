import { coreApp } from "../../../mod.ts";
import { setDefaultFn } from "./setDefault.fn.ts";
import { setDefaultValidator } from "./setDefault.val.ts";
import { setTokens, setUser } from "@lib";

export const setDefaultSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "setDefault",
    validationRunType: "create",
    validator: setDefaultValidator(),
    preAct: [setTokens, setUser],
    fn: setDefaultFn,
  });
