import { coreApp } from "../../../mod.ts";
import { getDownloadManifestFn } from "./getDownloadManifest.fn.ts";
import { getDownloadManifestValidator } from "./getDownloadManifest.val.ts";
import { setTokens, setUser } from "@lib";

export const getDownloadManifestSetup = () =>
  coreApp.acts.setAct({
    schema: "mediaEdition",
    actName: "getDownloadManifest",
    validationRunType: "create",
    validator: getDownloadManifestValidator(),
    preAct: [setTokens, setUser],
    fn: getDownloadManifestFn,
  });
