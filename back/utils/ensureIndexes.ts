/**
 * Compound / extra indexes that Lesan's `newModel({ createIndex })` cannot express.
 *
 * `newModel` accepts exactly one index spec, and the interesting queries here are compound:
 * "my coverage for this preset", "my sessions newest first", "the ayahs I have not covered
 * yet". Rather than hide those in an option the framework does not have, they are declared
 * once, explicitly, at boot — one greppable list instead of implicit behaviour.
 *
 * Collection names are the **model names verbatim** (`presetAyahProgress`), which is how
 * Lesan creates them.
 *
 * `createIndex` is idempotent, so this runs on every boot.
 */

/** The slice of the Mongo `Db` handle this needs, without importing the driver's types. */
export interface IndexableCollection {
  createIndex: (
    spec: Record<string, 1 | -1>,
    options?: Record<string, unknown>,
  ) => Promise<string>;
  /** Optional so a minimal stub in a test does not have to implement them. */
  indexes?: () => Promise<
    { name?: string; key?: Record<string, unknown>; sparse?: boolean }[]
  >;
  dropIndex?: (name: string) => Promise<unknown>;
}

export interface IndexableDb {
  collection: (name: string) => IndexableCollection;
}

/**
 * Makes `{ field: 1 }` a **sparse** unique index, replacing a non-sparse one if it is there.
 *
 * This is the whole reason the helper exists. `deviceId` used to be required and unique; when it
 * became optional, the old index would have started treating every row *without* a device as the
 * same `null` and refused the second such row — a unique index on a missing value is a rule about
 * absent data that almost nobody intends. Mongo will not change an existing index's options in
 * place, so the old one has to go first.
 */
const ensureUniqueSparse = async (
  db: IndexableDb,
  collection: string,
  field: string,
): Promise<void> => {
  const coll = db.collection(collection);
  const name = `${field}_1`;

  const existing = (await coll.indexes?.())?.find((index) =>
    index.name === name
  );
  if (existing && existing.sparse !== true && coll.dropIndex) {
    await coll.dropIndex(name);
  }
  await coll.createIndex({ [field]: 1 }, { unique: true, sparse: true });
};

export const ensureIndexes = async (db: IndexableDb): Promise<void> => {
  // ---- accounts --------------------------------------------------------------------
  // The login handle. Sparse because device-only rows have no email, and unique because two
  // accounts answering to `ali@x.com` is a support ticket that cannot be resolved.
  await ensureUniqueSparse(db, "user", "email");
  // Recreated sparse: see the helper. Runs every boot and is a no-op once it is already sparse.
  await ensureUniqueSparse(db, "user", "deviceId");

  // ---- reading history -------------------------------------------------------------
  // History is "my rows, newest first", always scoped by user.
  await db.collection("readingSession").createIndex({
    "user._id": 1,
    startedAt: -1,
  });

  // ---- presets ---------------------------------------------------------------------
  // The list screen reads "my presets that are not archived".
  await db.collection("preset").createIndex({ "user._id": 1, isArchived: 1 });

  // ---- coverage --------------------------------------------------------------------
  // The ledger's natural key. Unique: a second sync for the same ayah must merge into the
  // existing row, never create a twin. Also serves the "coverage for this preset" prefix.
  await db.collection("presetAyahProgress").createIndex(
    { "user._id": 1, "preset._id": 1, globalAyah: 1 },
    { unique: true },
  );
  // "What did I cover most recently", across presets — the History summary card.
  await db.collection("presetAyahProgress").createIndex({
    "user._id": 1,
    lastAt: -1,
  });

  // ---- preset sessions -------------------------------------------------------------
  await db.collection("presetSession").createIndex({
    "user._id": 1,
    startedAt: -1,
  });
  await db.collection("presetSession").createIndex({
    "user._id": 1,
    "preset._id": 1,
    startedAt: -1,
  });
  // "the open row for this device" — the hot path of `logEvent`.
  await db.collection("presetSession").createIndex({
    "user._id": 1,
    status: 1,
  });

  // ---- catalogue -------------------------------------------------------------------
  // The picker asks for "verified editions of this granularity".
  await db.collection("recitationEdition").createIndex({
    granularity: 1,
    availability: 1,
  });

  // ---- mirrored audio --------------------------------------------------------------
  // The mirror's natural key. Unique: re-running a mirror for a file it already has must
  // update that row, never create a twin — otherwise "how many files are stored" grows
  // while the disk does not, and `existing()` starts answering from whichever row came back
  // first.
  await db.collection("audioFile").createIndex(
    { editionKey: 1, fileKey: 1 },
    { unique: true },
  );
  // "what does this edition have?" — the status act and the manifest lookup.
  await db.collection("audioFile").createIndex({
    editionKey: 1,
    status: 1,
  });
};
