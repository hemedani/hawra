import { getDownloadManifestSetup } from "./getDownloadManifest/mod.ts";
import { getsSetup } from "./gets/mod.ts";

export const mediaEditionsSetup = () => {
  getsSetup();
  getDownloadManifestSetup();
};
