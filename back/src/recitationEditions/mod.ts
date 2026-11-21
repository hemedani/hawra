import { getSetup } from "./get/mod.ts";
import { getDownloadManifestSetup } from "./getDownloadManifest/mod.ts";
import { getsSetup } from "./gets/mod.ts";

export const recitationEditionsSetup = () => {
  getsSetup();
  getSetup();
  getDownloadManifestSetup();
};
