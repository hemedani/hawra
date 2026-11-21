import { coreApp } from "../mod.ts";
import { date, defaulted, enums, number, optional, string } from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";

/**
 * One **mirrored audio file** — the server's own copy of a recitation file.
 *
 * The catalogue (`recitationEdition`) says where audio *lives upstream*; this collection
 * records what the server has actually fetched, measured and can hand out itself.
 *
 * Why keep this at all, when the CDN already serves the audio:
 *
 * - **Exact size.** A manifest built from a URL template cannot say how many bytes a file
 *   is. `bytes` here is measured on disk, so the device can verify that a download finished
 *   instead of trusting a truncated stream that plays until it stops.
 * - **A second origin.** If upstream is unreachable (blocked, rate-limited, gone), the
 *   server is still an origin the device can reach — over the LAN in development.
 * - **A verification record.** `sha256` and `fetchedAt` make "we have this file" a claim
 *   with evidence behind it rather than an assumption.
 *
 * `(editionKey, fileKey)` is unique: re-running a mirror updates the row, never duplicates
 * it. That is what makes the mirror idempotent and safe to resume.
 */
export const audioFile_pure = {
  /** `recitationEdition.key` — the edition this file belongs to. */
  editionKey: string(),
  /** Same identity the manifest uses: `ayah:2:3`, `surah:2`, `juz:30`. */
  fileKey: string(),
  kind: enums(["ayah", "surah", "juz"]),
  surah: optional(number()),
  ayah: optional(number()),
  juz: optional(number()),
  globalAyah: optional(number()),
  /** Where the device stores it, relative to its recitation root. Identical to the manifest. */
  relativePath: string(),
  /** The upstream URL this copy came from, kept so a row can be re-fetched without a manifest. */
  sourceUrl: string(),
  /** Measured on disk after the write completed — never taken from a header. */
  bytes: number(),
  /** Hex SHA-256 of the stored file. Optional: old rows may predate it. */
  sha256: optional(string()),
  contentType: optional(string()),
  /**
   * `stored` means the file is on disk and measured. `failed` keeps the attempt visible so a
   * broken upstream URL is a queryable fact instead of a silent gap.
   */
  status: defaulted(enums(["stored", "failed"]), "stored"),
  attempts: defaulted(number(), 1),
  error: optional(string()),
  fetchedAt: optional(date()),
  ...createUpdateAt,
};

/** No relations: a file belongs to an edition by key, not by a foreign document. */
export const audioFile_relations = {};

export const audioFiles = () =>
  coreApp.odm.newModel("audioFile", audioFile_pure, audioFile_relations, {
    createIndex: {
      indexSpec: { editionKey: 1 },
    },
  });
