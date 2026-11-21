import { coreApp } from "../../../mod.ts";
import { getAudioMirrorStatusFn } from "./getAudioMirrorStatus.fn.ts";
import { getAudioMirrorStatusValidator } from "./getAudioMirrorStatus.val.ts";

/**
 * No `preAct`: this act is deliberately public. It reports on the server's own mirror, which
 * is catalogue data, and it is the one call worth being able to make with `curl` while a
 * phone on the LAN is misbehaving.
 */
export const getAudioMirrorStatusSetup = () =>
  coreApp.acts.setAct({
    schema: "audioFile",
    actName: "getAudioMirrorStatus",
    validationRunType: "create",
    validator: getAudioMirrorStatusValidator(),
    fn: getAudioMirrorStatusFn,
  });
