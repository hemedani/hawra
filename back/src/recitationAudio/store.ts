import { audioFile } from "../../mod.ts";
import type { MirrorFileInput, MirrorStore } from "./mirror.ts";

/**
 * The [MirrorStore] the backend actually uses.
 *
 * Two writes and one read, all keyed on `(editionKey, fileKey)`. The upsert is written out
 * by hand rather than as `upsert: true` because the intent is worth being explicit about:
 * a re-run **updates** the row for a file it already has. Duplicating rows would make
 * "how many files do I have?" answer with a number that keeps growing while the disk does not.
 */
export const lesanMirrorStore: MirrorStore = {
  existing: async (editionKey, fileKey) => {
    const row = await audioFile.findOne({
      filters: { editionKey, fileKey },
      projection: { bytes: 1, sha256: 1, contentType: 1, status: 1 },
    });
    if (!row || row.status !== "stored" || typeof row.bytes !== "number") {
      return null;
    }
    return {
      bytes: row.bytes,
      sha256: row.sha256,
      contentType: row.contentType,
    };
  },

  recordStored: async (editionKey, file, result) => {
    await upsert(editionKey, file, {
      status: "stored",
      bytes: result.bytes,
      sha256: result.sha256,
      contentType: result.contentType,
      error: undefined,
      fetchedAt: new Date(),
    });
  },

  recordFailure: async (editionKey, file, error) => {
    await upsert(editionKey, file, {
      status: "failed",
      bytes: 0,
      error,
    });
  },
};

const upsert = async (
  editionKey: string,
  file: MirrorFileInput,
  fields: {
    status: "stored" | "failed";
    bytes: number;
    sha256?: string;
    contentType?: string;
    error?: string;
    fetchedAt?: Date;
  },
): Promise<void> => {
  const existing = await audioFile.findOne({
    filters: { editionKey, fileKey: file.fileKey },
    projection: { _id: 1, attempts: 1 },
  });

  const shared = {
    kind: file.kind,
    surah: file.surah,
    ayah: file.ayah,
    juz: file.juz,
    globalAyah: file.globalAyah,
    relativePath: file.relativePath,
    sourceUrl: file.url,
    ...fields,
    updatedAt: new Date(),
  };

  if (existing) {
    await audioFile.findOneAndUpdate({
      filter: { _id: existing._id },
      update: {
        $set: shared,
        $inc: { attempts: 1 },
      },
      projection: { _id: 1 },
    });
    return;
  }

  await audioFile.insertOne({
    doc: {
      editionKey,
      fileKey: file.fileKey,
      ...shared,
      attempts: 1,
      createdAt: new Date(),
    },
    projection: { _id: 1 },
  });
};
