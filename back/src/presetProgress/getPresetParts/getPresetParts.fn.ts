import { type ActFn, ObjectId } from "lesan";
import { coreApp, presetAyahProgress } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { ayahAt } from "../../../content/quranIndex.ts";
import { requirePreset } from "../../presets/shared.ts";

interface Range {
  fromSurah: number;
  fromAyah: number;
  toSurah: number;
  toAyah: number;
  ayahs: number;
}

/** Collapses a sorted list of global ayah numbers into contiguous ranges. */
const toRanges = (globals: number[]): Range[] => {
  const ranges: Range[] = [];
  let start: number | undefined;
  let previous: number | undefined;

  const close = () => {
    if (start === undefined || previous === undefined) return;
    const from = ayahAt(start);
    const to = ayahAt(previous);
    if (from && to) {
      ranges.push({
        fromSurah: from.surah,
        fromAyah: from.ayah,
        toSurah: to.surah,
        toAyah: to.ayah,
        ayahs: previous - start + 1,
      });
    }
  };

  for (const value of globals) {
    if (start === undefined) {
      start = value;
    } else if (previous !== undefined && value !== previous + 1) {
      close();
      start = value;
    }
    previous = value;
  }
  close();
  return ranges;
};

export const getPresetPartsFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { presetClientId, page, limit, skip } } = body.details;
  const userId = new ObjectId(user._id);

  const target = await requirePreset(userId, presetClientId);
  const presetId = target._id as ObjectId;

  const calculatedSkip = skip ?? (limit || 100) * ((page || 1) - 1);

  const ayahs = await presetAyahProgress
    .aggregation({
      pipeline: [
        { $match: { "user._id": userId, "preset._id": presetId } },
        { $sort: { globalAyah: 1 } },
        { $skip: calculatedSkip },
        { $limit: limit || 100 },
        {
          $project: {
            _id: 0,
            surah: 1,
            ayah: 1,
            globalAyah: 1,
            page: 1,
            plays: 1,
            listenedSec: 1,
            firstAt: 1,
            lastAt: 1,
          },
        },
      ],
    })
    .toArray();

  // Ranges need every row, so they come from a projection-only pass rather than the page.
  const all = await presetAyahProgress
    .aggregation({
      pipeline: [
        { $match: { "user._id": userId, "preset._id": presetId } },
        { $sort: { globalAyah: 1 } },
        { $project: { _id: 0, globalAyah: 1 } },
      ],
    })
    .toArray();

  return {
    presetClientId,
    presetName: target.name,
    totalAyahs: all.length,
    ranges: toRanges(all.map((row) => Number(row.globalAyah))),
    ayahs,
  };
};
