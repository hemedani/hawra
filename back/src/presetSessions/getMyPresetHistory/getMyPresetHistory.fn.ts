import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset, presetSession } from "../../../mod.ts";
import { type MyContext, projectionOrPure } from "@lib";

export const getMyPresetHistoryFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { page, limit, skip, presetClientId, from, to }, get } =
    body.details;
  const userId = new ObjectId(user._id);

  const match: Document = { "user._id": userId };

  if (presetClientId) {
    const target = await preset.findOne({
      filters: { "user._id": userId, clientId: presetClientId },
      projection: { _id: 1 },
    });
    // An unknown preset means "nothing", not "everything" — returning the whole history
    // for a typo would be a silent, confusing answer.
    if (!target) return [];
    match["preset._id"] = target._id;
  }

  if (from !== undefined || to !== undefined) {
    const range: Record<string, number> = {};
    if (from !== undefined) range.$gte = from;
    if (to !== undefined) range.$lte = to;
    match.startedAt = range;
  }

  const calculatedSkip = skip ?? (limit || 50) * ((page || 1) - 1);

  return await presetSession
    .aggregation({
      pipeline: [
        { $match: match },
        { $sort: { startedAt: -1, _id: -1 } },
        { $skip: calculatedSkip },
        { $limit: limit || 50 },
      ],
      projection: projectionOrPure(get, "presetSession"),
    })
    .toArray();
};
