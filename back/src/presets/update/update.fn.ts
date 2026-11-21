import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";
import { clearDefaultPresets } from "../shared.ts";

/**
 * Patches a preset and bumps `revision`.
 *
 * `updatedAtMs` is written from the payload when present (the device's clock) because it is
 * the value the *other* device will compare against when it syncs. Using server time here
 * would make a preset edited on an offline device look older than it is and silently lose
 * the merge.
 */
export const updateFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set, get } = body.details;
  const userId = new ObjectId(user._id);
  const now = Date.now();

  const filter: Document = { "user._id": userId };
  if (set._id) filter._id = new ObjectId(set._id as string);
  else if (set.clientId) filter.clientId = set.clientId;
  else throwError("either _id or clientId is required");

  const existing = await preset.findOne({
    filters: filter,
    projection: { _id: 1, clientId: 1, isDefault: 1 },
  });
  if (!existing) throwError("preset not found");

  const update: Document = {
    $set: { updatedAtMs: set.updatedAtMs ?? now, updatedAt: new Date() },
    $inc: { revision: 1 },
  };

  for (
    const key of [
      "name",
      "description",
      "steps",
      "reciterKey",
      "autoAdvance",
      "ayahGapMs",
      "isArchived",
    ] as const
  ) {
    if (set[key] !== undefined) update.$set[key] = set[key];
  }

  if (set.isDefault !== undefined) {
    update.$set.isDefault = set.isDefault;
    // Promote: clear the others *after* the flag is set, so a crash mid-way can only leave
    // two defaults (fixable) and never zero (a reader with no preset).
    if (set.isDefault) {
      await preset.findOneAndUpdate({
        filter,
        update: { $set: { isDefault: true } },
        projection: { _id: 1 },
      });
      await clearDefaultPresets(userId, existing.clientId);
    }
  }

  return await preset.findOneAndUpdate({ filter, update, projection: get });
};
