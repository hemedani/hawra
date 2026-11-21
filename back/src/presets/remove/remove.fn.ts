import { type ActFn, type Document, ObjectId } from "lesan";
import {
  coreApp,
  preset,
  presetAyahProgress,
  presetSession,
} from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";

export const removeFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set } = body.details;
  const userId = new ObjectId(user._id);

  const filter: Document = { "user._id": userId };
  if (set._id) filter._id = new ObjectId(set._id as string);
  else if (set.clientId) filter.clientId = set.clientId;
  else throwError("either _id or clientId is required");

  const existing = await preset.findOne({
    filters: filter,
    projection: { _id: 1, clientId: 1, isDefault: 1 },
  });
  if (!existing) throwError("preset not found");

  const referenced = await presetAyahProgress.countDocument({
    filter: { "user._id": userId, "preset._id": existing._id },
  });
  const sessions = await presetSession.countDocument({
    filter: { "user._id": userId, "preset._id": existing._id },
  });

  if (referenced > 0 || sessions > 0) {
    await preset.findOneAndUpdate({
      filter,
      update: {
        $set: {
          isArchived: true,
          isDefault: false,
          updatedAtMs: Date.now(),
          updatedAt: new Date(),
        },
        $inc: { revision: 1 },
      },
      projection: { _id: 1, isArchived: 1 },
    });
    return {
      removed: false,
      archived: true,
      coverageRows: referenced,
      sessions,
    };
  }

  await preset.deleteOne({ filter, hardCascade: false });
  return { removed: true, archived: false, coverageRows: 0, sessions: 0 };
};
