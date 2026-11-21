import { coreApp } from "../mod.ts";
import { boolean, defaulted, number, optional, string } from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";

/**
 * A person who recites the Quran. The reciter is *identity*; the audio lives on
 * `recitationEdition`, because one reciter can publish several editions (murattal,
 * mujawwad, teaching speed, different bitrates) and the app must be able to pick one
 * without knowing anything about the others.
 *
 * `key` is the stable, human-readable handle the client uses everywhere (`shakernejad`,
 * `parhizgar`, `minshawi`). It is never derived from the name, so renaming a person in
 * either script cannot break a stored preset or a downloaded file path.
 */
export const reciter_pure = {
  /** Stable slug. Unique. e.g. "shakernejad". */
  key: string(),
  nameFa: string(),
  nameEn: string(),
  nameAr: optional(string()),
  /** ISO 3166-1 alpha-2, for grouping in the picker ("IR", "EG"). */
  countryCode: optional(string()),
  bioFa: optional(string()),
  bioEn: optional(string()),
  /** Ascending display order in the picker. Lower first. */
  sortOrder: defaulted(number(), 100),
  isActive: defaulted(boolean(), true),
  ...createUpdateAt,
};

/**
 * No relations here: the child (`recitationEdition`) owns `reciter` and Lesan embeds the
 * reverse (`reciter.editions`) automatically. Declaring it twice would duplicate it.
 */
export const reciter_relations = {};

export const reciters = () =>
  coreApp.odm.newModel("reciter", reciter_pure, reciter_relations, {
    createIndex: {
      indexSpec: { key: 1 },
      options: { unique: true },
    },
  });
