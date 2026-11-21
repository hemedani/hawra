import { type ActFn, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";
import { clearDefaultPresets, newClientId } from "../shared.ts";

/**
 * Creates a preset.
 *
 * Two invariants are established here rather than left to the client:
 *  - the user's **first** preset is the default (otherwise "play with my preset" has no
 *    answer on a fresh install);
 *  - there is **exactly one** default, so the previous one is cleared in the same call.
 */
export const addFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set, get } = body.details;
  const userId = new ObjectId(user._id);
  const now = Date.now();

  const existing = await preset.countDocument({
    filter: { "user._id": userId, isArchived: false },
  });

  const isDefault = set.isDefault ?? existing === 0;
  if (isDefault) await clearDefaultPresets(userId);

  const clientId = set.clientId ?? newClientId(now);

  const inserted = await preset.insertOne({
    doc: {
      clientId,
      name: set.name,
      description: set.description,
      steps: set.steps,
      reciterKey: set.reciterKey,
      autoAdvance: set.autoAdvance,
      ayahGapMs: set.ayahGapMs,
      isDefault,
      isArchived: false,
      revision: 1,
      updatedAtMs: set.updatedAtMs ?? now,
      createdAt: new Date(),
      updatedAt: new Date(),
    },
    relations: {
      user: { _ids: userId, relatedRelations: { presets: true } },
    } as never,
    projection: get,
  });

  if (!inserted) throwError("preset was not created");
  return inserted;
};
