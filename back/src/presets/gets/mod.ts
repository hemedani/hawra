import { coreApp } from "../../../mod.ts";
import { getsFn } from "./gets.fn.ts";
import { getsValidator } from "./gets.val.ts";
import { setTokens, setUser } from "@lib";

export const getsSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "gets",
    validationRunType: "create",
    validator: getsValidator(),
    preAct: [setTokens, setUser],
    fn: getsFn,
  });
