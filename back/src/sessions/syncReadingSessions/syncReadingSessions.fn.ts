import { type ActFn, ObjectId } from "lesan";
import { coreApp, readingSession } from "../../../mod.ts";
import type { MyContext } from "@lib";

type SessionRow = {
  clientId: string;
  startedAt: number;
  endedAt?: number;
  surah: number;
  fromAyah: number;
  toAyah: number;
  fromPage?: number;
  toPage?: number;
  durationReadSec: number;
  completed: boolean;
  interrupted: boolean;
  mode: string;
  deviceInfo?: string;
  setting?: {
    languageCode?: string;
    theme?: string;
    fontSize?: number;
    translationKeys: string[];
    reciterKey?: string;
    playbackSpeed?: number;
  };
};

/**
 * Batched, idempotent ingest of finalized reading sessions.
 *
 * The client resends its queue whenever a response is lost, so duplicates are
 * the normal case. Rows the server already has (matched on clientId within this
 * user) are filtered out before insert, and the unique sparse index on clientId
 * is the backstop. Only finalized sessions (endedAt present) should be sent;
 * the client enforces that, and the server stores whatever arrives verbatim.
 */
export const syncReadingSessionsFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { sessions: rawSessions = [] } } = body.details;
  const sessions = rawSessions as SessionRow[];
  const userId = new ObjectId(user._id);

  const knownClientIds = new Set(
    (sessions.length > 0
      ? await readingSession
        .aggregation({
          pipeline: [
            {
              $match: {
                "user._id": userId,
                clientId: { $in: sessions.map((s) => s.clientId) },
              },
            },
            { $project: { clientId: 1 } },
          ],
        })
        .toArray()
      : []).map((row) => String(row.clientId)),
  );

  const mappings: { clientId: string; serverId?: string }[] = [];
  let synced = 0;

  for (const session of sessions) {
    if (knownClientIds.has(String(session.clientId))) {
      const already = await readingSession.findOne({
        filters: { clientId: session.clientId, "user._id": userId },
        projection: { _id: 1 },
      });
      mappings.push({
        clientId: session.clientId,
        serverId: already ? String(already._id) : undefined,
      });
      continue;
    }

    const inserted = await readingSession.insertOne({
      doc: {
        clientId: session.clientId,
        startedAt: session.startedAt,
        endedAt: session.endedAt,
        surah: session.surah,
        fromAyah: session.fromAyah,
        toAyah: session.toAyah,
        fromPage: session.fromPage,
        toPage: session.toPage,
        durationReadSec: session.durationReadSec,
        completed: session.completed,
        interrupted: session.interrupted,
        mode: session.mode,
        deviceInfo: session.deviceInfo,
        setting: session.setting,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      relations: {
        user: {
          _ids: userId,
          relatedRelations: { readingSessions: true },
        },
      } as never,
      projection: { _id: 1 },
    });

    if (inserted) {
      mappings.push({
        clientId: session.clientId,
        serverId: String(inserted._id),
      });
      synced += 1;
    }
  }

  return { synced, sessions: mappings };
};
