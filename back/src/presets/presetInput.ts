import {
  array,
  boolean,
  defaulted,
  enums,
  number,
  object,
  optional,
  string,
} from "lesan";

/**
 * What a client is allowed to write on a preset. One set of field definitions reused by
 * `add`, `update` and `syncPresets`, so the three can never drift apart — a field added here
 * is instantly writable through every path, and a field deliberately absent (server ids,
 * `revision`) can never be set by a payload.
 */
export const presetStepFields = {
  kind: enums(["ayah", "translation", "tafsir", "silence"]),
  textKey: optional(string()),
  repeat: defaulted(number(), 1),
  speed: optional(number()),
  gapMs: defaulted(number(), 0),
  languageCode: optional(string()),
};

export const presetStepInput = object(presetStepFields);

export const presetFields = {
  name: string(),
  description: optional(string()),
  steps: defaulted(array(presetStepInput), []),
  reciterKey: optional(string()),
  autoAdvance: defaulted(boolean(), true),
  ayahGapMs: defaulted(number(), 0),
  isDefault: optional(boolean()),
};

export const presetInput = object(presetFields);

/** Every field optional: a patch, not a replacement. */
export const presetPatchFields = {
  name: optional(string()),
  description: optional(string()),
  steps: optional(array(presetStepInput)),
  reciterKey: optional(string()),
  autoAdvance: optional(boolean()),
  ayahGapMs: optional(number()),
  isDefault: optional(boolean()),
  isArchived: optional(boolean()),
};

export const presetPatch = object(presetPatchFields);

/**
 * One row of a batched preset sync.
 *
 * `clientId` is required here (the device minted it) and `updatedAtMs` is the last-write-wins
 * token: the server keeps whichever edit is newer and reports back the row it kept, so the
 * client can reconcile instead of guessing.
 */
export const presetSyncFields = {
  ...presetFields,
  clientId: string(),
  isDefault: defaulted(boolean(), false),
  isArchived: defaulted(boolean(), false),
  updatedAtMs: number(),
};

export const presetSyncInput = object(presetSyncFields);
