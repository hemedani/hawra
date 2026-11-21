import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset, presetAyahProgress } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { TOTAL_AYAHS } from "../../../content/quranIndex.ts";

interface GroupedCoverage {
  _id: ObjectId;
  ayahs: number;
  plays: number;
  listenedSec: number;
  lastAt: number;
  firstAt: number;
}

/**
 * Per-preset coverage.
 *
 * The percentage is `covered ayahs / 6236`, not `sessions × something`: coverage is a *set*
 * of ayahs, so replaying the same page a hundred times must not push the number past 100 %.
 * That is exactly why the ledger is keyed by ayah.
 *
 * `plays` and `listenedSec` are summed across the set, so "how much time" and "how much
 * breadth" stay separate questions.
 */
export const getMyCoverageFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { presetClientId, includeSurahs } } = body.details;
  const userId = new ObjectId(user._id);

  const presets = await preset.find({
    filters: { "user._id": userId },
    projection: { _id: 1, clientId: 1, name: 1, isArchived: 1, isDefault: 1 },
  }).toArray();

  let targetIds: ObjectId[] | undefined;
  if (presetClientId) {
    const target = presets.find((p) => p.clientId === presetClientId);
    if (!target) return { presets: [], totalAyahs: TOTAL_AYAHS };
    targetIds = [target._id as ObjectId];
  }

  const match: Document = { "user._id": userId };
  if (targetIds) match["preset._id"] = { $in: targetIds };

  const grouped = (await presetAyahProgress
    .aggregation({
      pipeline: [
        { $match: match },
        {
          $group: {
            _id: "$preset._id",
            ayahs: { $sum: 1 },
            plays: { $sum: "$plays" },
            listenedSec: { $sum: "$listenedSec" },
            lastAt: { $max: "$lastAt" },
            firstAt: { $min: "$firstAt" },
          },
        },
      ],
    })
    .toArray()) as GroupedCoverage[];

  const byPreset = new Map(grouped.map((row) => [String(row._id), row]));

  const surahBreakdown = new Map<string, { surah: number; ayahs: number }[]>();
  if (includeSurahs) {
    const perSurah = await presetAyahProgress
      .aggregation({
        pipeline: [
          { $match: match },
          {
            $group: {
              _id: { preset: "$preset._id", surah: "$surah" },
              ayahs: { $sum: 1 },
            },
          },
          { $sort: { "_id.surah": 1 } },
        ],
      })
      .toArray();

    for (
      const row of perSurah as {
        _id: { preset: ObjectId; surah: number };
        ayahs: number;
      }[]
    ) {
      const key = String(row._id.preset);
      const list = surahBreakdown.get(key) ?? [];
      list.push({ surah: row._id.surah, ayahs: row.ayahs });
      surahBreakdown.set(key, list);
    }
  }

  return {
    totalAyahs: TOTAL_AYAHS,
    presets: presets
      .filter((p) =>
        !targetIds || targetIds.some((id) => String(id) === String(p._id))
      )
      .map((p) => {
        const row = byPreset.get(String(p._id));
        const ayahs = row?.ayahs ?? 0;
        return {
          clientId: p.clientId,
          name: p.name,
          isDefault: p.isDefault ?? false,
          isArchived: p.isArchived ?? false,
          ayahsCovered: ayahs,
          // One decimal is enough to see progress; more looks like false precision.
          percent: Math.round((ayahs / TOTAL_AYAHS) * 1000) / 10,
          plays: row?.plays ?? 0,
          listenedSec: row?.listenedSec ?? 0,
          firstAt: row?.firstAt ?? null,
          lastAt: row?.lastAt ?? null,
          surahs: includeSurahs
            ? (surahBreakdown.get(String(p._id)) ?? [])
            : undefined,
        };
      }),
  };
};
