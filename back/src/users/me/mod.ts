import { coreApp } from "../../../mod.ts";
import { meFn } from "./me.fn.ts";
import { meValidator } from "./me.val.ts";
import { setTokens, setUser } from "@lib";

/** Authenticated: the answer is about the token's owner, so the token is required. */
export const meSetup = () =>
  coreApp.acts.setAct({
    schema: "user",
    actName: "me",
    validationRunType: "create",
    validator: meValidator(),
    preAct: [setTokens, setUser],
    fn: meFn,
  });
