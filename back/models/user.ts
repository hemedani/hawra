import { coreApp } from "../mod.ts";
import {
  date,
  defaulted,
  enums,
  nullable,
  number,
  object,
  optional,
  string,
} from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";

/**
 * Shared reader preferences. Optional so registration stays valid; defaults
 * are applied on read, not written at registration. `updatedAt` is epoch
 * milliseconds — the last-write-wins token clients send and compare.
 */
export const setting_pure = object({
  languageCode: defaulted(nullable(string()), null),
  theme: defaulted(enums(["light", "dark", "system"]), "system"),
  /** Base mushaf font size in sp. */
  fontSize: defaulted(number(), 28),
  updatedAt: defaulted(number(), 0),
});

export const user_pure = {
  /**
   * The device that most recently used this account.
   *
   * Optional, and no longer an identity. Hawraa used to be device-only: a device registered
   * once and its `deviceId` *was* the account. Accounts arrived later, so the field survives as
   * diagnostic context (which phone was this?) and as the key `registerDevice` still uses — but
   * a person may sign in from several devices, and a device may host several accounts, so it
   * cannot be the natural key any more. The unique index on it is sparse for that reason.
   */
  deviceId: optional(string()),
  /** Normalised (trimmed, lowercased). Unique when present — the login handle. */
  email: optional(string()),
  displayName: optional(string()),
  /**
   * PBKDF2 output plus its parameters. Absent for device-only rows that never set a password.
   * The salt and iteration count are per row so raising the cost later does not invalidate
   * existing passwords — see `utils/password.ts`.
   */
  passwordHash: optional(string()),
  passwordSalt: optional(string()),
  passwordIterations: optional(number()),
  lastLoginAt: optional(date()),
  setting: optional(setting_pure),
  role: defaulted(enums(["user", "admin"]), "user"),
  status: defaulted(enums(["active", "suspended", "deleted"]), "active"),
  ...createUpdateAt,
};

export const user_relations = {};

export const users = () =>
  coreApp.odm.newModel("user", user_pure, user_relations, {
    // `email` rather than `deviceId`, and that choice is load-bearing.
    //
    // Lesan creates this index while the model is instantiated, which is *before* `ensureIndexes`
    // runs. `deviceId` used to be unique and non-sparse; declaring it here again with `sparse`
    // makes Mongo reject the boot with "an existing index has the same name as the requested
    // index". The migration therefore has to happen in `ensureIndexes` (drop, then recreate), and
    // the model may only declare an index that cannot already exist in an older shape.
    //
    // `email` is new, so there is nothing to conflict with, and a unique sparse index on it is
    // exactly what the login handle needs.
    createIndex: {
      indexSpec: { email: 1 },
      options: { unique: true, sparse: true },
    },
  });
