import { type ActFn } from "lesan";
import { coreApp, recitationEdition } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { throwError } from "@lib";
import {
  buildManifest,
  resolveScope,
} from "../../recitationEditions/buildManifest.ts";
import { type MirrorFileInput, mirrorFiles } from "../mirror.ts";
import { audioRoot } from "../paths.ts";
import { lesanMirrorStore } from "../store.ts";

/**
 * Asks the server to fetch a scope's audio onto its own disk, now.
 *
 * This exists for the small case — one surah on a surah edition, one juz, a single verse —
 * where waiting for a shell command is worse than waiting for the request. It is **not** the
 * bulk path: `deno task audio-mirror` is, and the cap below says so out loud.
 *
 * Three guards, because this act writes to the server's disk on someone else's request:
 *
 * 1. **Disabled unless `ADMIN_TOKEN` is set.** No default token, no "true" — an unset
 *    variable means the act refuses to run at all. A feature that turns itself on is a
 *    feature that is on in production.
 * 2. **Constant-time-ish comparison** against the `x-admin-token` header. Not a real auth
 *    system; a speed bump in front of a development convenience, and labelled as such.
 * 3. **A file cap.** 286 files is minutes of blocking work; the act says no and names the
 *    command that can do it.
 */
const MAX_FILES = 60;

export const prepareAudioFn: ActFn = async (body) => {
  const configured = Deno.env.get("ADMIN_TOKEN");
  if (!configured) {
    throwError(
      "prepareAudio is disabled: start the server with ADMIN_TOKEN=<secret> to enable it (or use `deno task audio-mirror`)",
    );
  }

  const context = coreApp.contextFns.getContextModel() as MyContext;
  const supplied = context.Headers?.get("x-admin-token") ?? "";
  if (supplied.length !== configured.length || supplied !== configured) {
    throwError("prepareAudio: bad or missing x-admin-token");
  }

  const { set } = body.details;

  const edition = await recitationEdition.findOne({
    filters: { key: set.editionKey },
    projection: {
      key: 1,
      granularity: 1,
      urlTemplate: 1,
      fileExtension: 1,
      availability: 1,
      bitrateKbps: 1,
    },
  });
  if (!edition) throwError(`unknown recitation edition: ${set.editionKey}`);
  if (edition.availability !== "verified") {
    throwError(
      `edition ${set.editionKey} is "${edition.availability}" — refusing to mirror an unverified source`,
    );
  }

  const ayahs = resolveScope({
    scope: set.scope,
    surah: set.surah,
    ayah: set.ayah,
    juz: set.juz,
    fromSurah: set.fromSurah,
    fromAyah: set.fromAyah,
    toSurah: set.toSurah,
    toAyah: set.toAyah,
  });
  if (ayahs === null) {
    throwError(`scope "${set.scope}" is missing its coordinates`);
  }
  if (ayahs.length === 0) throwError("the requested scope contains no ayahs");

  const manifest = buildManifest(
    {
      key: edition.key,
      granularity: edition.granularity,
      urlTemplate: edition.urlTemplate,
      fileExtension: edition.fileExtension,
    },
    ayahs,
  );

  if (manifest.length > MAX_FILES) {
    throwError(
      `scope needs ${manifest.length} files, above the ${MAX_FILES}-file cap for this act — run \`deno task audio-mirror --edition=${edition.key} --scope=…\` instead`,
    );
  }

  const files: MirrorFileInput[] = manifest.map((file) => ({
    fileKey: file.key,
    kind: file.kind,
    surah: file.surah,
    ayah: file.ayah,
    juz: file.juz,
    globalAyah: file.globalAyah,
    relativePath: file.relativePath,
    url: file.url,
  }));

  const summary = await mirrorFiles(files, lesanMirrorStore, {
    editionKey: edition.key,
    root: audioRoot(),
    concurrency: 4,
    force: Boolean(set.force),
  });

  return {
    editionKey: edition.key,
    scope: set.scope,
    total: summary.total,
    stored: summary.stored,
    skipped: summary.skipped,
    failed: summary.failed,
    bytes: summary.bytes,
    failures: summary.failures.slice(0, 10),
  };
};
