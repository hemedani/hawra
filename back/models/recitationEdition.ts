import { coreApp } from "../mod.ts";
import {
  boolean,
  defaulted,
  enums,
  number,
  optional,
  type RelationDataType,
  type RelationSortOrderType,
  string,
} from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";

/**
 * One **audio edition** of one reciter — the unit a device actually downloads.
 *
 * The three things that make a download manifest possible live here:
 *
 * - `granularity` — whether the source publishes one file per ayah, per surah or per juz.
 *   This is a property of the *source*, not a preference: Shakernejad's tartil is published
 *   juz-by-juz, Minshawi's murattal is published ayah-by-ayah, and pretending otherwise
 *   would produce 404s.
 * - `urlTemplate` — a template with exactly one placeholder matching the granularity.
 *   `{ayah}` (1…6236), `{surah}`/`{surah3}` (1…114), `{juz}`/`{juz2}` (1…30).
 * - `availability` — `verified` means every file was probed (ranged GET + mp3 frame check)
 *   on `verifiedAt`. `unverified`/`blocked` editions are listed for transparency but the
 *   app must not offer them for playback.
 *
 * Nothing here is sacred text: a URL, a bitrate and a licence note.
 */
export const recitationEdition_pure = {
  /** Stable slug. Unique. e.g. "parhizgar-ayah-48". */
  key: string(),
  labelFa: string(),
  labelEn: string(),
  /** How the reciter performs on this edition. */
  style: defaulted(
    enums(["murattal", "mujawwad", "tartil", "muallim"]),
    "murattal",
  ),
  /** One file per ayah, per surah, or per juz. Decides which placeholder is legal. */
  granularity: enums(["ayah", "surah", "juz"]),
  /** e.g. `https://cdn.islamic.network/quran/audio/48/ar.parhizgar/{ayah}.mp3`. */
  urlTemplate: string(),
  bitrateKbps: defaulted(number(), 128),
  fileExtension: defaulted(string(), "mp3"),
  /** 6236 for ayah editions, 114 for surah, 30 for juz. Cross-checked at boot. */
  totalFiles: number(),
  availability: defaulted(
    enums(["verified", "unverified", "blocked"]),
    "verified",
  ),
  /** Where the audio comes from, in words — shown in Settings for attribution. */
  sourceName: string(),
  sourceUrl: optional(string()),
  licenseNote: optional(string()),
  /** ISO date (YYYY-MM-DD) of the last probe of every file. */
  verifiedAt: optional(string()),
  notesFa: optional(string()),
  notesEn: optional(string()),
  /** The edition the app should preselect when the user has never chosen one. */
  isDefault: defaulted(boolean(), false),
  ...createUpdateAt,
};

export const recitationEdition_relations = {
  reciter: {
    schemaName: "reciter",
    type: "single" as RelationDataType,
    optional: false,
    relatedRelations: {
      editions: {
        type: "multiple" as RelationDataType,
        limit: 20,
        sort: { field: "_id", order: "asc" as RelationSortOrderType },
      },
    },
  },
};

export const recitationEditions = () =>
  coreApp.odm.newModel(
    "recitationEdition",
    recitationEdition_pure,
    recitationEdition_relations,
    {
      createIndex: {
        indexSpec: { key: 1 },
        options: { unique: true },
      },
    },
  );
