import { ObjectId } from "lesan";
import { preset } from "../../mod.ts";
import { throwError } from "@lib";

/** A client-shaped id that sorts by time even before `createdAt` is read. */
export const newClientId = (now: number): string =>
  `${now}-${crypto.randomUUID()}`;

/**
 * Exactly one default preset per user.
 *
 * Lesan has no `updateMany`, and a user owns a handful of presets, so the honest
 * implementation is "find the ones that are default and unset them". `exceptClientId` lets
 * the caller set the new default first and then clear the rest, so there is never a window
 * with two defaults or none.
 */
export const clearDefaultPresets = async (
  userId: ObjectId,
  exceptClientId?: string,
): Promise<void> => {
  const current = await preset.find({
    filters: { "user._id": userId, isDefault: true },
    projection: { _id: 1, clientId: 1 },
  }).toArray();

  for (const row of current) {
    if (exceptClientId && row.clientId === exceptClientId) continue;
    await preset.findOneAndUpdate({
      filter: { _id: row._id, "user._id": userId },
      update: { $set: { isDefault: false, updatedAt: new Date() } },
      projection: { _id: 1 },
    });
  }
};

/**
 * Loads a preset that must exist, or throws. Returning non-null is the point: it lets the
 * callers use `_id` without a null-check dance at every use site.
 */
export const requirePreset = async (userId: ObjectId, clientId: string) => {
  const found = await preset.findOne({
    filters: { "user._id": userId, clientId },
    projection: { _id: 1, name: 1, clientId: 1 },
  });
  if (!found) throwError(`preset ${clientId} not found`);
  return found;
};
