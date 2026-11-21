import { coreApp } from "../../../mod.ts";
import { removeFn } from "./remove.fn.ts";
import { removeValidator } from "./remove.val.ts";
import { setTokens, setUser } from "@lib";

export const removeSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "remove",
    validationRunType: "create",
    validator: removeValidator(),
    preAct: [setTokens, setUser],
    fn: removeFn,
  });
