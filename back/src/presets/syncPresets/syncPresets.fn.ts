import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { clearDefaultPresets } from "../shared.ts";

/** One step as it arrives over the wire; mirrors `presetStepInput`. */
interface SyncStep {
  kind: "ayah" | "translation" | "tafsir" | "silence";
  textKey?: string;
  repeat: number;
  speed?: number;
  gapMs: number;
  languageCode?: string;
}

type SyncRow = {
  clientId: string;
  name: string;
  description?: string;
  steps: SyncStep[];
  reciterKey?: string;
  autoAdvance: boolean;
  ayahGapMs: number;
  isDefault: boolean;
  isArchived: boolean;
  updatedAtMs: number;
};

/**
 * Last-write-wins preset sync.
 *
 * The device is the editor and the server is the meeting point, so the merge is decided by
 * `updatedAtMs` — the device clock at the moment of the edit — and **not** by arrival order.
 * An older edit arriving late (a phone that was offline for a week) must not overwrite a
 * newer one; it is simply acknowledged with the row the server already has, which is what
 * lets the client reconcile without a conflict UI.
 *
 * The response carries the *server* row for every client id in the batch, so one round trip
 * is enough to make the device consistent.
 */
export const syncPresetsFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { presets: rows = [] } } = body.details;
  const userId = new ObjectId(user._id);
  const now = Date.now();

  const incoming = rows as SyncRow[];
  const pureProjection = coreApp.schemas.createProjection("preset", "Pure");

  let synced = 0;
  let defaultClientId: string | undefined;

  for (const row of incoming) {
    const existing = await preset.findOne({
      filters: { "user._id": userId, clientId: row.clientId },
      projection: { _id: 1, updatedAtMs: 1, isDefault: 1 },
    });

    if (existing) {
      const serverStamp = Number(existing.updatedAtMs ?? 0);
      if (row.updatedAtMs > serverStamp) {
        const update: Document = {
          $set: {
            name: row.name,
            description: row.description,
            steps: row.steps,
            reciterKey: row.reciterKey,
            autoAdvance: row.autoAdvance,
            ayahGapMs: row.ayahGapMs,
            isDefault: row.isDefault,
            isArchived: row.isArchived,
            updatedAtMs: row.updatedAtMs,
            updatedAt: new Date(),
          },
          $inc: { revision: 1 },
        };
        await preset.findOneAndUpdate({
          filter: { _id: existing._id, "user._id": userId },
          update,
          projection: { _id: 1 },
        });
        synced += 1;
      }
    } else {
      await preset.insertOne({
        doc: {
          clientId: row.clientId,
          name: row.name,
          description: row.description,
          steps: row.steps,
          reciterKey: row.reciterKey,
          autoAdvance: row.autoAdvance,
          ayahGapMs: row.ayahGapMs,
          isDefault: row.isDefault,
          isArchived: row.isArchived,
          revision: 1,
          updatedAtMs: row.updatedAtMs,
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        relations: {
          user: { _ids: userId, relatedRelations: { presets: true } },
        } as never,
        projection: { _id: 1 },
      });
      synced += 1;
    }

    if (row.isDefault && !row.isArchived) defaultClientId = row.clientId;
  }

  // Normalise the "exactly one default" invariant once, after the batch, rather than inside
  // the loop — otherwise a batch with two defaults would clear whichever came first.
  if (defaultClientId) await clearDefaultPresets(userId, defaultClientId);

  const serverRows: Document[] = [];
  for (const row of incoming) {
    const stored = await preset.findOne({
      filters: { "user._id": userId, clientId: row.clientId },
      projection: pureProjection,
    });
    if (stored) serverRows.push({ ...stored, serverId: String(stored._id) });
  }

  return { synced, received: incoming.length, presets: serverRows, at: now };
};
