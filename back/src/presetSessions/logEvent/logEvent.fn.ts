import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, presetSession } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";

const COUNTER_FOR: Record<string, string | undefined> = {
  play: "playCount",
  pause: "pauseCount",
  seek: "seekCount",
  step: undefined,
  ayah: undefined,
};

/**
 * Records one play/pause/seek/step **inside the existing sitting**.
 *
 * Everything that can be retried is expressed as a merge rather than an increment:
 * `activeSec`, `ayahsCompleted` and `stepsCompleted` arrive as absolute values and are
 * merged with `$max`, so a resent batch cannot inflate them. The press counters are the one
 * place `$inc` is right — they count *events*, and the event log is the evidence.
 *
 * The event list is capped at 200: the oldest detail is dropped, the counters stay exact.
 */
export const logEventFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set } = body.details;
  const userId = new ObjectId(user._id);

  const session = await presetSession.findOne({
    filters: { "user._id": userId, clientId: set.clientId },
    projection: { _id: 1, status: 1 },
  });
  if (!session) {
    // The device always persists the row locally before the first event, so a missing row
    // is a client bug, not something to paper over with an invented sitting.
    throwError(`preset session ${set.clientId} was not started`);
  }

  const update: Document = {
    $set: { updatedAt: new Date() },
    $max: {
      lastEventAt: set.at,
      ...(set.activeSec !== undefined ? { activeSec: set.activeSec } : {}),
      ...(set.ayahsCompleted !== undefined
        ? { ayahsCompleted: set.ayahsCompleted }
        : {}),
      ...(set.stepsCompleted !== undefined
        ? { stepsCompleted: set.stepsCompleted }
        : {}),
    },
    $push: {
      events: {
        $each: [{
          kind: set.kind,
          at: set.at,
          surah: set.surah,
          ayah: set.ayah,
          stepIndex: set.stepIndex,
        }],
        $slice: -200,
      },
    },
  };

  const counter = COUNTER_FOR[set.kind];
  if (counter) update.$inc = { [counter]: 1 };

  // The cursor fields are *current position*, not maxima: a seek backwards is legitimate.
  for (const key of ["toSurah", "toAyah", "fromPage", "toPage"] as const) {
    if (set[key] !== undefined) update.$set[key] = set[key];
  }

  return await presetSession.findOneAndUpdate({
    filter: { _id: session._id, "user._id": userId },
    update,
    projection: {
      _id: 1,
      clientId: 1,
      status: 1,
      playCount: 1,
      pauseCount: 1,
      seekCount: 1,
      activeSec: 1,
      ayahsCompleted: 1,
      stepsCompleted: 1,
      lastEventAt: 1,
    },
  });
};
