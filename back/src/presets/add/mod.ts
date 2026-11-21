import { coreApp } from "../../../mod.ts";
import { addFn } from "./add.fn.ts";
import { addValidator } from "./add.val.ts";
import { setTokens, setUser } from "@lib";

export const addSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "add",
    validationRunType: "create",
    validator: addValidator(),
    preAct: [setTokens, setUser],
    fn: addFn,
  });
