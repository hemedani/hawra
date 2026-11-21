import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, presetSession } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";

export const finalizeFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set } = body.details;
  const userId = new ObjectId(user._id);

  const existing = await presetSession.findOne({
    filters: { "user._id": userId, clientId: set.clientId },
    projection: { _id: 1, status: 1 },
  });
  if (!existing) throwError(`preset session ${set.clientId} was not started`);

  const update: Document = {
    $set: {
      endedAt: set.endedAt,
      status: set.status ?? "finalized",
      updatedAt: new Date(),
    },
    $max: {
      lastEventAt: set.endedAt,
      ...(set.activeSec !== undefined ? { activeSec: set.activeSec } : {}),
      ...(set.ayahsCompleted !== undefined
        ? { ayahsCompleted: set.ayahsCompleted }
        : {}),
      ...(set.stepsCompleted !== undefined
        ? { stepsCompleted: set.stepsCompleted }
        : {}),
    },
  };

  return await presetSession.findOneAndUpdate({
    filter: { _id: existing._id, "user._id": userId },
    update,
    projection: {
      _id: 1,
      clientId: 1,
      status: 1,
      startedAt: 1,
      endedAt: 1,
      activeSec: 1,
      ayahsCompleted: 1,
      playCount: 1,
      pauseCount: 1,
    },
  });
};
