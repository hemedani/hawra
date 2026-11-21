import { coreApp } from "../../../mod.ts";
import { loginFn } from "./login.fn.ts";
import { loginValidator } from "./login.val.ts";

/** No `preAct`: the whole purpose is to obtain a token. */
export const loginSetup = () =>
  coreApp.acts.setAct({
    schema: "user",
    actName: "login",
    validationRunType: "create",
    validator: loginValidator(),
    fn: loginFn,
  });
