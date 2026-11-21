import { coreApp } from "../mod.ts";
import {
  array,
  boolean,
  defaulted,
  enums,
  number,
  object,
  optional,
  type RelationDataType,
  type RelationSortOrderType,
  string,
} from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";
import { user_excludes } from "./excludes.ts";

/**
 * One step of a recitation pattern.
 *
 * A step is a *verb*, not content: "play this ayah's audio", "speak this ayah's Ansarian
 * translation", "pause for a moment". The text itself is referenced by `textKey` and never
 * stored (back/AGENTS.md D2) — the device owns the translations and commentaries it bundles.
 *
 * `repeat` is what makes the owner's second example expressible: `[ayah ×2,
 * translation(fa.ansarian), ayah ×1]` is "play the verse twice, read the translation, play
 * the verse once more, then move on".
 */
export const presetStep_pure = object({
  kind: enums(["ayah", "translation", "tafsir", "silence"]),
  /**
   * Which translation/commentary to speak. `fa.ansarian`, `fa.maleki`, `al-mizan`…
   * Required for `translation`/`tafsir`, ignored otherwise. A key the device has not
   * bundled is skipped by the client, not rejected by the server.
   */
  textKey: optional(string()),
  /** How many times to run this step for one ayah. 1–10; clamped by the client. */
  repeat: defaulted(number(), 1),
  /** Playback speed for `ayah` steps. Absent = the user's current speed. */
  speed: optional(number()),
  /** Pause after this step finishes, milliseconds. */
  gapMs: defaulted(number(), 0),
  /** BCP-47 language of the spoken text, so the device can pick a TTS voice. */
  languageCode: optional(string()),
});

/**
 * A user's recitation **pattern** — the answer to "how do I want to listen?".
 *
 * A preset is range-independent: the same pattern applies to a page, a selection, a surah
 * or a juz. That is what lets coverage be tracked *per preset* over the whole Quran, and it
 * is why nothing here mentions a start or an end position.
 *
 * `clientId` is minted on the device so the same preset can be edited offline on two
 * devices and reconciled: the server keeps the row with the higher `updatedAtMs` (last
 * write wins) and never overwrites a newer edit with an older one.
 */
export const preset_pure = {
  /** Client-generated UUID; the idempotency key for sync. Unique + sparse. */
  clientId: optional(string()),
  name: string(),
  description: optional(string()),
  /** The pattern, applied to each ayah of whatever range is played. */
  steps: defaulted(array(presetStep_pure), []),
  /** Edition key for `ayah` steps (`parhizgar-ayah-48`); absent = the user's current one. */
  reciterKey: optional(string()),
  /** Move to the next ayah after the last step (true) or stop there (false). */
  autoAdvance: defaulted(boolean(), true),
  /** Extra pause between ayahs, milliseconds. */
  ayahGapMs: defaulted(number(), 0),
  /** Exactly one preset per user is the default; `preset/setDefault` maintains that. */
  isDefault: defaulted(boolean(), false),
  /** Archived presets keep their coverage but are not offered for playback. */
  isArchived: defaulted(boolean(), false),
  /** Incremented on every server-side edit. Informational; `updatedAtMs` decides conflicts. */
  revision: defaulted(number(), 1),
  /** Device clock of the last edit, epoch ms — the LWW comparison value. */
  updatedAtMs: defaulted(number(), 0),
  ...createUpdateAt,
};

export const preset_relations = {
  user: {
    schemaName: "user",
    type: "single" as RelationDataType,
    optional: false,
    excludes: user_excludes,
    relatedRelations: {
      // A user owns a handful of presets, so a reverse array is safe and useful.
      presets: {
        type: "multiple" as RelationDataType,
        limit: 50,
        sort: { field: "_id", order: "desc" as RelationSortOrderType },
      },
    },
  },
};

export const presets = () =>
  coreApp.odm.newModel("preset", preset_pure, preset_relations, {
    createIndex: {
      indexSpec: { clientId: 1 },
      options: { unique: true, sparse: true },
    },
  });
