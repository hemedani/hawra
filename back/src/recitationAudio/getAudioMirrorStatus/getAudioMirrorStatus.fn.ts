import { type ActFn, type Document } from "lesan";
import { audioFile, recitationEdition } from "../../../mod.ts";

/**
 * "What does this server actually hold?"
 *
 * Read-only and unauthenticated on purpose: it answers a question about *the server*, not
 * about a person, and it is the first thing you want to curl when a download on a phone
 * behaves oddly. Requiring a token would make the one diagnostic you need during a LAN test
 * the one call you cannot make.
 *
 * The numbers come from `audioFile` rows, not from `stat`-ing the tree: a row exists only
 * after a verified write, so `storedFiles` is a claim with evidence behind it. A directory
 * listing would also count `.part` files, which are exactly the files that must not be
 * counted.
 */
export const getAudioMirrorStatusFn: ActFn = async (body) => {
  const { set } = body.details;

  const editionFilter: Document = {};
  if (set.editionKey) editionFilter.key = set.editionKey;

  const editions = await recitationEdition
    .find({
      filters: editionFilter,
      projection: {
        key: 1,
        labelFa: 1,
        labelEn: 1,
        granularity: 1,
        bitrateKbps: 1,
        totalFiles: 1,
        availability: 1,
        isDefault: 1,
      },
    })
    .toArray();

  const rows = await audioFile
    .aggregation({
      pipeline: [
        { $match: set.editionKey ? { editionKey: set.editionKey } : {} },
        {
          $group: {
            _id: { editionKey: "$editionKey", status: "$status" },
            files: { $sum: 1 },
            bytes: { $sum: "$bytes" },
          },
        },
      ],
      projection: { _id: 1, files: 1, bytes: 1 },
    })
    .toArray();

  const byEdition = new Map<
    string,
    { stored: number; failed: number; bytes: number }
  >();
  for (const row of rows) {
    const key = (row._id as { editionKey: string }).editionKey;
    const bucket = byEdition.get(key) ?? { stored: 0, failed: 0, bytes: 0 };
    if ((row._id as { status: string }).status === "stored") {
      bucket.stored += row.files as number;
      bucket.bytes += row.bytes as number;
    } else {
      bucket.failed += row.files as number;
    }
    byEdition.set(key, bucket);
  }

  return editions.map((edition) => {
    const bucket = byEdition.get(edition.key) ??
      { stored: 0, failed: 0, bytes: 0 };
    const total = edition.totalFiles ?? 0;
    return {
      editionKey: edition.key,
      labelFa: edition.labelFa,
      labelEn: edition.labelEn,
      granularity: edition.granularity,
      bitrateKbps: edition.bitrateKbps,
      availability: edition.availability,
      isDefault: edition.isDefault ?? false,
      totalFiles: total,
      storedFiles: bucket.stored,
      failedFiles: bucket.failed,
      bytes: bucket.bytes,
      /** 0…100, rounded down: "99 %" must never mean "one file short of complete". */
      completePercent: total > 0
        ? Math.floor((bucket.stored / total) * 100)
        : 0,
    };
  });
};
