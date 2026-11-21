import {
  array,
  boolean,
  defaulted,
  number,
  object,
  optional,
  string,
} from "lesan";

/** One finalized reading session as the client sends it. Mirrors the model. */
export const readingSessionInput = object({
  clientId: string(),
  startedAt: number(),
  endedAt: optional(number()),
  surah: number(),
  fromAyah: number(),
  toAyah: number(),
  fromPage: optional(number()),
  toPage: optional(number()),
  durationReadSec: defaulted(number(), 0),
  completed: defaulted(boolean(), false),
  interrupted: defaulted(boolean(), false),
  mode: defaulted(string(), "read"),
  deviceInfo: optional(string()),
  setting: optional(
    object({
      languageCode: optional(string()),
      theme: optional(string()),
      fontSize: optional(number()),
      translationKeys: defaulted(array(string()), []),
      reciterKey: optional(string()),
      playbackSpeed: optional(number()),
    }),
  ),
});

export const syncReadingSessionsValidator = () =>
  object({
    set: object({
      sessions: defaulted(array(readingSessionInput), []),
    }),
  });
