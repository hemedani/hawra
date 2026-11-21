import { coreApp } from "../../../mod.ts";
import { getFn } from "./get.fn.ts";
import { getValidator } from "./get.val.ts";
import { setTokens, setUser } from "@lib";

export const getSetup = () =>
  coreApp.acts.setAct({
    schema: "reciter",
    actName: "get",
    validationRunType: "create",
    validator: getValidator(),
    preAct: [setTokens, setUser],
    fn: getFn,
  });
