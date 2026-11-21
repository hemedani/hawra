import { object, optional, string } from "lesan";

/**
 * No coordinates: this act reports on every edition, optionally narrowed to one.
 * The useful question during a LAN test is "what do I have at all?", and a required
 * `editionKey` would turn one call into six.
 */
export const getAudioMirrorStatusValidator = () =>
  object({
    set: object({
      editionKey: optional(string()),
    }),
  });
