import { coreApp } from "../../../mod.ts";
import { registerDeviceFn } from "./registerDevice.fn.ts";
import { registerDeviceValidator } from "./registerDevice.val.ts";

export const registerDeviceSetup = () =>
  coreApp.acts.setAct({
    schema: "user",
    actName: "registerDevice",
    validationRunType: "create",
    validator: registerDeviceValidator(),
    fn: registerDeviceFn,
  });
