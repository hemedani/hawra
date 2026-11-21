import { coreApp } from "../mod.ts";
import {
  array,
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
 * One transport event inside a preset session.
 *
 * The detail is kept *inside* the row on purpose: the owner's requirement is that pressing
 * play or pause must not create a new history entry, so the granularity has to live
 * somewhere that is not a row. `events[]` is that somewhere — capped, oldest-first dropped,
 * never the source of truth for the counters (those are exact fields).
 */
export const presetEvent_pure = object({
  kind: enums(["play", "pause", "seek", "step", "ayah"]),
  /** Epoch milliseconds. */
  at: number(),
  surah: optional(number()),
  ayah: optional(number()),
  /** Index into the preset's `steps`, for "which step was running". */
  stepIndex: optional(number()),
});

/**
 * **One sitting with one preset.** Not one play/pause press.
 *
 * The lifecycle is deliberately boring:
 *  - `presetSession/start` inserts the row (idempotent on `clientId`);
 *  - `presetSession/logEvent` **updates** it — counters, range, last activity, capped events;
 *  - `presetSession/finalize` closes it (`status: "finalized"`, `endedAt` set).
 *
 * A new row is only created when the device starts a genuinely new sitting (the pure
 * tracker decides that from an idle timeout). That is what makes History readable: one line
 * per listening stretch, with "played 12, paused 9" as detail rather than 21 rows.
 *
 * `presetName` is a snapshot so history survives the preset being archived or deleted;
 * the relation is optional for the same reason.
 */
export const presetSession_pure = {
  /** Client-generated UUID. Unique + sparse; the idempotency key for sync. */
  clientId: optional(string()),
  /** Snapshot of the preset's name at start. */
  presetName: string(),
  startedAt: number(),
  endedAt: optional(number()),
  /** Seconds the preset was actually running (integer, monotonic). */
  activeSec: defaulted(number(), 0),
  /** The traversed range, widened as playback advances. */
  fromSurah: number(),
  fromAyah: number(),
  toSurah: number(),
  toAyah: number(),
  fromPage: optional(number()),
  toPage: optional(number()),
  /** Ayahs completed end-to-end under the pattern. */
  ayahsCompleted: defaulted(number(), 0),
  stepsCompleted: defaulted(number(), 0),
  playCount: defaulted(number(), 0),
  pauseCount: defaulted(number(), 0),
  seekCount: defaulted(number(), 0),
  /** Epoch ms of the last event; the device uses it to decide "same sitting or new one". */
  lastEventAt: defaulted(number(), 0),
  status: defaulted(enums(["open", "finalized", "abandoned"]), "open"),
  /** Which edition was used, and at what speed — the "with which settings" record. */
  reciterKey: optional(string()),
  speed: optional(number()),
  /** Capped detail log (last 200 events). */
  events: defaulted(array(presetEvent_pure), []),
  ...createUpdateAt,
};

export const presetSession_relations = {
  user: {
    schemaName: "user",
    type: "single" as RelationDataType,
    optional: false,
    excludes: user_excludes,
    relatedRelations: {
      presetSessions: {
        type: "multiple" as RelationDataType,
        limit: 50,
        sort: { field: "startedAt", order: "desc" as RelationSortOrderType },
      },
    },
  },
  /** Optional: the row outlives the preset it was recorded with. */
  preset: {
    schemaName: "preset",
    type: "single" as RelationDataType,
    optional: true,
    // No reverse array: a preset's session count is unbounded, and the history read is
    // always "my rows, optionally filtered by preset".
    relatedRelations: {},
  },
};

export const presetSessions = () =>
  coreApp.odm.newModel(
    "presetSession",
    presetSession_pure,
    presetSession_relations,
    {
      createIndex: {
        indexSpec: { clientId: 1 },
        options: { unique: true, sparse: true },
      },
    },
  );
