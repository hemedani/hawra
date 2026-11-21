import { type ActFn, ObjectId } from "lesan";
import { coreApp, preset, presetSession } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";

/**
 * Opens one preset session row.
 *
 * Idempotent on `clientId`: the device persists the row locally first and pushes it, so a
 * lost response must not produce two sittings. Nothing else is closed here — a *different*
 * device may legitimately be listening at the same time, and its row is its own business.
 */
export const startFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set, get } = body.details;
  const userId = new ObjectId(user._id);

  const existing = await presetSession.findOne({
    filters: { "user._id": userId, clientId: set.clientId },
    projection: get,
  });
  if (existing) return existing;

  let presetId: ObjectId | undefined;
  if (set.presetClientId) {
    const found = await preset.findOne({
      filters: { "user._id": userId, clientId: set.presetClientId },
      projection: { _id: 1 },
    });
    if (found) presetId = found._id as ObjectId;
  }

  const inserted = await presetSession.insertOne({
    doc: {
      clientId: set.clientId,
      presetName: set.presetName,
      startedAt: set.startedAt,
      activeSec: 0,
      fromSurah: set.surah,
      fromAyah: set.ayah,
      toSurah: set.surah,
      toAyah: set.ayah,
      fromPage: set.page,
      toPage: set.page,
      ayahsCompleted: 0,
      stepsCompleted: 0,
      playCount: 0,
      pauseCount: 0,
      seekCount: 0,
      lastEventAt: set.startedAt,
      status: "open",
      reciterKey: set.reciterKey,
      speed: set.speed,
      events: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    },
    relations: {
      user: { _ids: userId, relatedRelations: { presetSessions: true } },
      ...(presetId ? { preset: { _ids: presetId } } : {}),
    } as never,
    projection: get,
  });

  if (!inserted) throwError("preset session was not created");
  return inserted;
};
