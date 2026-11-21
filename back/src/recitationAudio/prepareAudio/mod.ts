import { coreApp } from "../../../mod.ts";
import { prepareAudioFn } from "./prepareAudio.fn.ts";
import { prepareAudioValidator } from "./prepareAudio.val.ts";

/**
 * No `setTokens`/`setUser`: the guard is `x-admin-token`, checked in the function body
 * against `ADMIN_TOKEN`. A user JWT would be the wrong credential here — this act manages the
 * *server's* disk, which no end user has a claim on.
 */
export const prepareAudioSetup = () =>
  coreApp.acts.setAct({
    schema: "audioFile",
    actName: "prepareAudio",
    validationRunType: "create",
    validator: prepareAudioValidator(),
    fn: prepareAudioFn,
  });
