import { audioFile } from "../../mod.ts";
import type { ManifestFile } from "../recitationEditions/buildManifest.ts";
import { publicPathOf } from "./paths.ts";

/** A manifest file, plus what the server can say about the copy it holds. */
export type ManifestFileWithMirror =
  & ManifestFile
  & { bytes?: number; mirrorPath?: string };

/**
 * Enriches a file list with the server's own mirrored copies.
 *
 * This is the difference between "here are some URLs, good luck" and a download the device can
 * actually finish correctly. For each file the server holds (`audioFile.status = "stored"`), two
 * facts come back:
 *
 * - `bytes` — measured on the server's disk after a verified write. A chunked response carries no
 *   length, so without this the device cannot tell a complete file from a truncated one.
 * - `mirrorPath` — the same file reachable on *this* server (`/uploads/recitation/…`), which the
 *   device resolves against the base URL it already knows. So nothing here is absolute and the
 *   manifest is correct from a phone on the LAN or an emulator on loopback.
 *
 * Files the server does not hold keep only `url` (upstream) — the normal production case and the
 * fallback everywhere.
 *
 * Shared by `recitationEditions/getDownloadManifest` and `mediaEditions/getDownloadManifest` so
 * the two catalogues cannot drift on what "we have this file" means. Keyed by `editionKey`, which
 * is a string for both models — that is what lets one helper serve both.
 */
export const filesWithMirrorInfo = async (
  editionKey: string,
  files: ManifestFile[],
): Promise<ManifestFileWithMirror[]> => {
  if (files.length === 0) return [];

  const mirrored = await audioFile.find({
    filters: {
      editionKey,
      status: "stored",
      fileKey: { $in: files.map((file) => file.key) },
    },
    projection: { fileKey: 1, bytes: 1 },
  }).toArray();

  const bytesByFile = new Map<string, number>(
    mirrored.map((row) => [row.fileKey as string, row.bytes as number]),
  );

  return files.map((file) => {
    const bytes = bytesByFile.get(file.key);
    return bytes === undefined
      ? file
      : { ...file, bytes, mirrorPath: publicPathOf(file.relativePath) };
  });
};
