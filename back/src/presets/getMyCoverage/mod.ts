import { coreApp } from "../../../mod.ts";
import { getMyCoverageFn } from "./getMyCoverage.fn.ts";
import { getMyCoverageValidator } from "./getMyCoverage.val.ts";
import { setTokens, setUser } from "@lib";

export const getMyCoverageSetup = () =>
  coreApp.acts.setAct({
    schema: "preset",
    actName: "getMyCoverage",
    validationRunType: "create",
    validator: getMyCoverageValidator(),
    preAct: [setTokens, setUser],
    fn: getMyCoverageFn,
  });
