import { loginSetup } from "./login/mod.ts";
import { meSetup } from "./me/mod.ts";
import { registerDeviceSetup } from "./registerDevice/mod.ts";
import { registerSetup } from "./register/mod.ts";

export const usersSetup = () => {
  // Accounts: the way in.
  registerSetup();
  loginSetup();
  meSetup();
  // Legacy: device-only registration. Kept because rows created before accounts exist still
  // resolve through it, and because a client that has not shipped the login screen yet must not
  // break. Nothing new should call it.
  registerDeviceSetup();
};
