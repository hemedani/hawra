import { coreApp } from "../mod.ts";
import {
  defaulted,
  number,
  optional,
  type RelationDataType,
  string,
} from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";
import { user_excludes } from "./excludes.ts";

/**
 * Coverage ledger: **one row per (user, preset, ayah)**.
 *
 * Two questions this answers without scanning history:
 *  - "how much of the Quran have I covered with this preset?" → a count;
 *  - "which ayahs exactly?" → an indexed range read.
 *
 * ## Why the counters are merged, not incremented
 *
 * The client sends **absolute** values (`plays`, `listenedSec`) rather than deltas, and the
 * server merges with `$max` / `$min`. That makes a retried sync idempotent — the classic
 * bug with `$inc` is that a lost response doubles the count — and lets two devices playing
 * the same ayah converge instead of summing. Exact per-play detail belongs to
 * `presetSession.events`, which is append-only and capped.
 *
 * No `relatedRelations` on either relation: a preset can accumulate 6236 rows, and a
 * reverse array of that size on the parent would make every write rewrite a huge document.
 * Coverage is always read by filter, which the compound unique index covers.
 */
export const presetAyahProgress_pure = {
  surah: number(),
  ayah: number(),
  /** 1…6236, denormalised so a range query needs no join or arithmetic. */
  globalAyah: number(),
  page: optional(number()),
  /** Completed passes through this ayah under this preset. */
  plays: defaulted(number(), 0),
  /** Seconds spent on this ayah (integer). */
  listenedSec: defaulted(number(), 0),
  /** First and last time this ayah was reached, epoch ms. */
  firstAt: number(),
  lastAt: number(),
  /** The last step kind that completed here: "ayah" | "translation" | "tafsir". */
  lastKind: optional(string()),
  ...createUpdateAt,
};

/**
 * `relatedRelations: {}` is deliberate, not an omission: it is how Lesan is told "embed the
 * reference, but keep no reverse array". A preset can accumulate 6236 coverage rows, and a
 * reverse array of that size on the parent would turn every write into a rewrite of a huge
 * document. Coverage is always read by filter, which the compound unique index covers.
 */
export const presetAyahProgress_relations = {
  user: {
    schemaName: "user",
    type: "single" as RelationDataType,
    optional: false,
    excludes: user_excludes,
    relatedRelations: {},
  },
  preset: {
    schemaName: "preset",
    type: "single" as RelationDataType,
    optional: false,
    relatedRelations: {},
  },
};

export const presetAyahProgresses = () =>
  coreApp.odm.newModel(
    "presetAyahProgress",
    presetAyahProgress_pure,
    presetAyahProgress_relations,
  );
