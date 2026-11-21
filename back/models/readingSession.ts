import { coreApp } from "../mod.ts";
import {
  array,
  boolean,
  defaulted,
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
 * The reading settings in effect for this session, captured at open time so a
 * later settings change never rewrites history. Flat and optional-friendly:
 * the client sends whatever it had, the server stores it verbatim.
 */
export const readingSetting_pure = object({
  languageCode: optional(string()),
  theme: optional(string()),
  fontSize: optional(number()),
  /** Which translation/commentary panels were visible, by client-side key. */
  translationKeys: defaulted(array(string()), []),
  reciterKey: optional(string()),
  playbackSpeed: optional(number()),
});

export const readingSession_pure = {
  /** Client-generated UUID; the idempotency key for sync. Unique + sparse. */
  clientId: optional(string()),
  /** Epoch milliseconds. */
  startedAt: number(),
  endedAt: optional(number()),
  /**
   * The exact ayah range read. Surah is 1-based; from/to ayah are 1-based and
   * inclusive. A session may span a page rather than a whole surah, so the
   * mushaf page range is stored alongside for page-first history views.
   */
  surah: number(),
  fromAyah: number(),
  toAyah: number(),
  fromPage: optional(number()),
  toPage: optional(number()),
  /** Wall-clock seconds actually spent reading (integer). */
  durationReadSec: defaulted(number(), 0),
  /** Whether the reader reached the end of the intended range. */
  completed: defaulted(boolean(), false),
  /** Whether the session ended by switching away / app kill rather than finish. */
  interrupted: defaulted(boolean(), false),
  /** "read" (silent) or "listen" (recitation follow-along). */
  mode: defaulted(string(), "read"),
  deviceInfo: optional(string()),
  setting: optional(readingSetting_pure),
  ...createUpdateAt,
};

export const readingSession_relations = {
  user: {
    schemaName: "user",
    type: "single" as RelationDataType,
    optional: false,
    excludes: user_excludes,
    relatedRelations: {
      readingSessions: {
        type: "multiple" as RelationDataType,
        limit: 50,
        sort: {
          field: "_id",
          order: "desc" as RelationSortOrderType,
        },
      },
    },
  },
};

export const readingSessions = () =>
  coreApp.odm.newModel(
    "readingSession",
    readingSession_pure,
    readingSession_relations,
    {
      createIndex: {
        indexSpec: { clientId: 1 },
        options: { unique: true, sparse: true },
      },
    },
  );
