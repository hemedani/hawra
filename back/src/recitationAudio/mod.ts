import { getAudioMirrorStatusSetup } from "./getAudioMirrorStatus/mod.ts";
import { prepareAudioSetup } from "./prepareAudio/mod.ts";

export const recitationAudioSetup = () => {
  getAudioMirrorStatusSetup();
  prepareAudioSetup();
};
