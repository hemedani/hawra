import { looksLikeMp3 } from "./mp3.ts";
import { dirname, resolveUnderRoot } from "./paths.ts";

/**
 * The mirror engine: turns "this edition over this scope" into files on the server's disk.
 *
 * The shape of this module is deliberate. Everything that touches the filesystem or the
 * network is here; everything that touches the database is behind [MirrorStore]. That split
 * is what lets `mirror_test.ts` run the real resume/verify/rename logic against a local HTTP
 * server and a temp directory, with no Mongo anywhere — the parts most likely to be wrong are
 * exactly the parts that are tested.
 *
 * ## What "correct" means for a mirrored file
 *
 * 1. **It is complete.** Written to `name.mp3.part`, renamed to `name.mp3` only after the
 *    whole body has landed. A file that exists is a file that finished; an interrupted run
 *    leaves a `.part` behind and nothing else.
 * 2. **It is audio.** The first bytes are checked for an ID3 tag or an MPEG frame sync. A CDN
 *    error page returns `200`, so status alone proves nothing.
 * 3. **It resumes.** A `.part` from an earlier run is continued with a `Range` request. A
 *    server that answers `200` to a ranged request (ignoring the range) truncates and starts
 *    over instead of appending — appending a whole file onto half a file produces a file that
 *    is exactly the wrong length and plays as garbage.
 * 4. **It is measured.** `bytes` comes from `stat` after the write, never from a header, so
 *    the manifest can hand the device a number that means something.
 */

export type AudioKind = "ayah" | "surah" | "juz";

/** One file to fetch. Structurally a subset of the manifest's `ManifestFile`. */
export interface MirrorFileInput {
  fileKey: string;
  kind: AudioKind;
  surah?: number;
  ayah?: number;
  juz?: number;
  globalAyah?: number;
  relativePath: string;
  url: string;
}

export interface StoredAudio {
  bytes: number;
  sha256?: string;
  contentType?: string;
}

/**
 * The database questions the engine needs. Injected so the engine has no `mod.ts` import and
 * the test needs no database.
 */
export interface MirrorStore {
  /** The row for this file if it is already stored, else `null`. */
  existing(editionKey: string, fileKey: string): Promise<StoredAudio | null>;
  recordStored(
    editionKey: string,
    file: MirrorFileInput,
    result: StoredAudio,
  ): Promise<void>;
  recordFailure(
    editionKey: string,
    file: MirrorFileInput,
    error: string,
  ): Promise<void>;
}

export interface MirrorOptions {
  editionKey: string;
  /** Absolute directory the relative paths are resolved under. */
  root: string;
  concurrency?: number;
  /** Re-fetch even when a stored row and an intact file already exist. */
  force?: boolean;
  /** Stop after this many files. For smoke tests. */
  limit?: number;
  /** Report what would happen without touching the network or the disk. */
  dryRun?: boolean;
  timeoutMs?: number;
  log?: (line: string) => void;
  /** Called after each file finishes, whatever the outcome. For a progress bar. */
  onProgress?: (done: number, total: number) => void;
}

export interface MirrorFailure {
  fileKey: string;
  url: string;
  error: string;
}

export interface MirrorSummary {
  total: number;
  stored: number;
  skipped: number;
  failed: number;
  bytes: number;
  failures: MirrorFailure[];
}

const DEFAULTS = {
  concurrency: 4,
  timeoutMs: 180_000,
};

const statSize = async (path: string): Promise<number | null> => {
  try {
    const stat = await Deno.stat(path);
    return stat.isFile ? stat.size : null;
  } catch {
    return null;
  }
};

const hex = (buffer: ArrayBuffer): string =>
  [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, "0")).join(
    "",
  );

const sha256Of = async (path: string): Promise<string> =>
  hex(await crypto.subtle.digest("SHA-256", await Deno.readFile(path)));

/**
 * Downloads [url] to [target], resuming from `target.part` when it exists.
 *
 * Returns the measured size. Throws on any condition that would leave a bad file behind —
 * the caller deletes the `.part` and records the failure.
 */
const fetchToFile = async (
  url: string,
  target: string,
  timeoutMs: number,
): Promise<number> => {
  const part = `${target}.part`;
  const already = (await statSize(part)) ?? 0;

  const headers = new Headers();
  if (already > 0) headers.set("Range", `bytes=${already}-`);

  const response = await fetch(url, {
    headers,
    redirect: "follow",
    signal: AbortSignal.timeout(timeoutMs),
  });

  let append: boolean;
  if (response.status === 206) {
    // A 206 must start where we asked. If the server sends a different start offset (or
    // ignores the range and answers 206 with the whole body — it happens), appending would
    // splice two unrelated runs together into a file of the wrong length that plays as
    // garbage. Trust the header over the status code when it is there.
    const range = response.headers.get("content-range");
    const start = range?.match(/^bytes\s+(\d+)-/i)?.[1];
    if (start !== undefined && Number(start) !== already) {
      await response.body?.cancel();
      throw new Error(
        `server resumed at byte ${start} but ${already} were requested (${url})`,
      );
    }
    append = true;
  } else if (response.status === 200) {
    // The server ignored the range and is sending the whole file.
    append = false;
  } else {
    await response.body?.cancel();
    throw new Error(`HTTP ${response.status} from ${url}`);
  }

  if (!response.body) throw new Error(`empty response body from ${url}`);

  const file = await Deno.open(
    part,
    append
      ? { write: true, create: true, append: true }
      : { write: true, create: true, truncate: true },
  );

  try {
    await response.body.pipeTo(file.writable);
  } catch (error) {
    // The socket died mid-body: keep the `.part` so the next run resumes from here.
    await file.writable.abort().catch(() => {});
    throw error;
  }

  const size = (await statSize(part)) ?? 0;
  if (size === 0) throw new Error(`zero bytes stored from ${url}`);

  const head = new Uint8Array(4096);
  const handle = await Deno.open(part, { read: true });
  let headLength = 0;
  try {
    headLength = await handle.read(head) ?? 0;
  } finally {
    handle.close();
  }
  if (!looksLikeMp3(head.subarray(0, headLength))) {
    throw new Error(
      `not audio: ${url} answered with ${size} bytes that are not MP3`,
    );
  }

  // Rename only now. `Deno.rename` replaces an existing target atomically on POSIX.
  await Deno.rename(part, target);
  return size;
};

/**
 * Fetches every file, [concurrency] at a time, and reports what happened.
 *
 * Never throws for a per-file failure: one dead URL must not abort a 286-file surah. The
 * summary is the report, and `failures` carries the reasons.
 */
export const mirrorFiles = async (
  files: MirrorFileInput[],
  store: MirrorStore,
  options: MirrorOptions,
): Promise<MirrorSummary> => {
  const concurrency = Math.max(1, options.concurrency ?? DEFAULTS.concurrency);
  const timeoutMs = options.timeoutMs ?? DEFAULTS.timeoutMs;
  const log = options.log ?? (() => {});
  const wanted = options.limit ? files.slice(0, options.limit) : files;

  const summary: MirrorSummary = {
    total: wanted.length,
    stored: 0,
    skipped: 0,
    failed: 0,
    bytes: 0,
    failures: [],
  };

  let done = 0;
  let cursor = 0;

  const handleOne = async (file: MirrorFileInput): Promise<void> => {
    const target = resolveUnderRoot(options.root, file.relativePath);

    if (!options.force) {
      const row = await store.existing(options.editionKey, file.fileKey);
      const size = row ? await statSize(target) : null;
      if (row && size !== null && size === row.bytes) {
        summary.skipped += 1;
        return;
      }
    }

    if (options.dryRun) {
      log(`would fetch ${file.fileKey} → ${file.relativePath}`);
      return;
    }

    try {
      await Deno.mkdir(dirname(target), { recursive: true });
      const bytes = await fetchToFile(file.url, target, timeoutMs);
      const sha256 = await sha256Of(target);
      await store.recordStored(options.editionKey, file, {
        bytes,
        sha256,
        contentType: "audio/mpeg",
      });
      summary.stored += 1;
      summary.bytes += bytes;
      log(`stored ${file.fileKey} (${(bytes / 1024).toFixed(1)} KB)`);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      // Drop the partial: a corrupt `.part` that fails verification would otherwise be
      // resumed forever, turning one bad response into a permanently stuck file.
      await Deno.remove(`${target}.part`).catch(() => {});
      await store.recordFailure(options.editionKey, file, message);
      summary.failed += 1;
      summary.failures.push({
        fileKey: file.fileKey,
        url: file.url,
        error: message,
      });
      log(`FAILED ${file.fileKey}: ${message}`);
    }
  };

  const worker = async (): Promise<void> => {
    while (true) {
      const index = cursor++;
      if (index >= wanted.length) return;
      await handleOne(wanted[index]);
      done += 1;
      options.onProgress?.(done, wanted.length);
    }
  };

  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return summary;
};
