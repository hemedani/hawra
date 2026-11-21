import { type ActFn, ObjectId } from "lesan";
import { coreApp, presetAyahProgress } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { requirePreset } from "../../presets/shared.ts";

type Entry = {
  surah: number;
  ayah: number;
  globalAyah: number;
  page?: number;
  plays: number;
  listenedSec: number;
  firstAt: number;
  lastAt: number;
  lastKind?: string;
};

/**
 * Merges a device's coverage for one preset.
 *
 * The merge is **monotonic**:
 *  - `plays`, `listenedSec`, `lastAt` take the maximum,
 *  - `firstAt` takes the minimum,
 *  - `lastKind`/`page` take the latest arrival (they describe the most recent pass).
 *
 * So sending the same batch twice is a no-op, and a phone that was offline for a week cannot
 * roll the ledger back. The client is therefore free to resend its whole local ledger on
 * every sync, which is far more robust than shipping deltas with sequence numbers.
 */
export const syncProgressFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { presetClientId, entries: raw } } = body.details;
  const userId = new ObjectId(user._id);

  const target = await requirePreset(userId, presetClientId);
  const presetId = target._id as ObjectId;

  const entries = raw as Entry[];
  let inserted = 0;
  let merged = 0;

  for (const entry of entries) {
    const existing = await presetAyahProgress.findOne({
      filters: {
        "user._id": userId,
        "preset._id": presetId,
        globalAyah: entry.globalAyah,
      },
      projection: { _id: 1 },
    });

    if (existing) {
      await presetAyahProgress.findOneAndUpdate({
        filter: { _id: existing._id, "user._id": userId },
        update: {
          $max: {
            plays: entry.plays,
            listenedSec: entry.listenedSec,
            lastAt: entry.lastAt,
          },
          $min: { firstAt: entry.firstAt },
          $set: {
            surah: entry.surah,
            ayah: entry.ayah,
            page: entry.page,
            lastKind: entry.lastKind,
            updatedAt: new Date(),
          },
        },
        projection: { _id: 1 },
      });
      merged += 1;
      continue;
    }

    await presetAyahProgress.insertOne({
      doc: {
        surah: entry.surah,
        ayah: entry.ayah,
        globalAyah: entry.globalAyah,
        page: entry.page,
        plays: entry.plays,
        listenedSec: entry.listenedSec,
        firstAt: entry.firstAt,
        lastAt: entry.lastAt,
        lastKind: entry.lastKind,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      relations: {
        user: { _ids: userId },
        preset: { _ids: presetId },
      } as never,
      projection: { _id: 1 },
    });
    inserted += 1;
  }

  return {
    presetClientId,
    received: entries.length,
    inserted,
    merged,
  };
};
