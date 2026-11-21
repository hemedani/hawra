import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset, presetSession } from "../../../mod.ts";
import type { MyContext } from "@lib";

export const countFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { presetClientId, from, to } } = body.details;
  const userId = new ObjectId(user._id);

  const filter: Document = { "user._id": userId };

  if (presetClientId) {
    const target = await preset.findOne({
      filters: { "user._id": userId, clientId: presetClientId },
      projection: { _id: 1 },
    });
    if (!target) return { qty: 0 };
    filter["preset._id"] = target._id;
  }

  if (from !== undefined || to !== undefined) {
    const range: Record<string, number> = {};
    if (from !== undefined) range.$gte = from;
    if (to !== undefined) range.$lte = to;
    filter.startedAt = range;
  }

  return { qty: await presetSession.countDocument({ filter }) };
};
