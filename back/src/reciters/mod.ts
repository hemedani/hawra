import { getSetup } from "./get/mod.ts";
import { getsSetup } from "./gets/mod.ts";

export const recitersSetup = () => {
  getsSetup();
  getSetup();
};
