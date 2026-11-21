import { type ActFn, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";
import { clearDefaultPresets } from "../shared.ts";

export const setDefaultFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { clientId }, get } = body.details;
  const userId = new ObjectId(user._id);

  const target = await preset.findOne({
    filters: { "user._id": userId, clientId },
    projection: { _id: 1, isArchived: 1 },
  });
  if (!target) throwError("preset not found");
  if (target.isArchived) throwError("an archived preset cannot be the default");

  const promoted = await preset.findOneAndUpdate({
    filter: { _id: target._id, "user._id": userId },
    update: { $set: { isDefault: true, updatedAt: new Date() } },
    projection: get,
  });

  await clearDefaultPresets(userId, clientId);
  return promoted;
};
