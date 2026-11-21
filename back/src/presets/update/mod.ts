import { coreApp } from "../../../mod.ts";
import { updateFn } from "./update.fn.ts";
import { updateValidator } from "./update.val.ts";
import { setTokens, setUser } from "@lib";

export const updateSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "update",
    validationRunType: "create",
    validator: updateValidator(),
    preAct: [setTokens, setUser],
    fn: updateFn,
  });
