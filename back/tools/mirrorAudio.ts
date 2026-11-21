/**
 * Mirrors recitation audio from its upstream source into the server's own tree.
 *
 * ```sh
 * deno task audio-mirror --edition=parhizgar-murattal-ayah-48 --scope=surah:1
 * deno task audio-mirror --edition=shakernejad-tartil-juz-24   --scope=juz:1
 * deno task audio-mirror --edition=parhizgar-murattal-ayah-48 --scope=surah:2 --limit=10 --dry-run
 * ```
 *
 * Run from `back/` — the audio root is resolved against the working directory, and Lesan's
 * static handler serves from the same place. `deno task` guarantees that.
 *
 * This is the *bulk* path and it is intentionally a command, not an HTTP act: mirroring a
 * surah on an ayah edition is 286 requests, which is minutes of work and should not be
 * something a request can start on a server. (There is a capped, token-guarded act for the
 * small case — `prepareAudio` — but it refuses to run without `ADMIN_TOKEN` set.)
 *
 * Safe to re-run: files already stored with the right size are skipped, interrupted files
 * resume from their `.part`, and rows are keyed so nothing duplicates.
 */
import { parseArgs } from "jsr:@std/cli@^1/parse-args";
import {
  buildManifest,
  resolveScope,
} from "../src/recitationEditions/buildManifest.ts";
import {
  type MirrorFileInput,
  mirrorFiles,
} from "../src/recitationAudio/mirror.ts";
import { resolveEditionDescriptor } from "../src/recitationAudio/edition.ts";
import { audioRoot } from "../src/recitationAudio/paths.ts";
import { parseScope } from "../src/recitationAudio/scope.ts";
import { lesanMirrorStore } from "../src/recitationAudio/store.ts";

const USAGE = `
Mirror recitation audio onto this server.

  deno task audio-mirror --edition=<key> --scope=<scope> [options]

Scopes:
  surah:2               one surah
  juz:1                 one juz
  ayah:2:255            one verse
  range:2:1-2:20        inclusive, may cross a surah boundary

Options:
  --limit=<n>           stop after n files (smoke tests)
  --concurrency=<n>     parallel downloads, default 4
  --force               re-fetch files that are already stored
  --dry-run             list what would be fetched, touch nothing
`;

const args = parseArgs(Deno.args, {
  string: ["edition", "scope", "limit", "concurrency"],
  boolean: ["force", "dry-run", "help"],
  alias: { h: "help", f: "force" },
});

/** `parseArgs` has no numeric type: every value arrives as a string. */
const positiveInt = (
  value: string | undefined,
  name: string,
): number | undefined => {
  if (value === undefined) return undefined;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    console.error(`✗ --${name}=${value} must be a positive integer`);
    Deno.exit(1);
  }
  return parsed;
};

if (args.help || !args.edition || !args.scope) {
  console.log(USAGE.trim());
  Deno.exit(args.help ? 0 : 1);
}

const editionKey = args.edition as string;

const edition = await resolveEditionDescriptor(editionKey);

if (!edition) {
  console.error(
    `✗ unknown edition "${editionKey}" — run \`deno task seed\` first.`,
  );
  Deno.exit(1);
}
if (edition.availability !== "verified") {
  console.error(
    `✗ edition "${editionKey}" is "${edition.availability}"; refusing to mirror a source that was never verified.`,
  );
  Deno.exit(1);
}

const scopeInput = parseScope(args.scope as string);
const ayahs = resolveScope(scopeInput);

if (!ayahs || ayahs.length === 0) {
  console.error(`✗ scope "${args.scope}" covers no ayahs.`);
  Deno.exit(1);
}

const manifest = buildManifest(
  {
    key: edition.key,
    granularity: edition.granularity,
    urlTemplate: edition.urlTemplate,
    fileExtension: edition.fileExtension,
  },
  ayahs,
);

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

const root = audioRoot();
const bitrate = edition.bitrateKbps === undefined
  ? ""
  : ` · ${edition.bitrateKbps} kbps`;
console.log(
  `\n${edition.key} · ${edition.source} · ${edition.granularity}${bitrate}`,
);
console.log(
  `scope      ${args.scope} → ${ayahs.length} ayahs → ${files.length} files`,
);
console.log(`root       ${root}`);
if (args["dry-run"]) console.log("dry run    nothing will be written\n");
else console.log("");

const summary = await mirrorFiles(files, lesanMirrorStore, {
  editionKey: edition.key,
  root,
  concurrency: positiveInt(args.concurrency, "concurrency"),
  limit: positiveInt(args.limit, "limit"),
  force: Boolean(args.force),
  dryRun: Boolean(args["dry-run"]),
  log: (line) => console.log(`  ${line}`),
});

const mb = (bytes: number) => (bytes / (1024 * 1024)).toFixed(1);

console.log("");
console.log(`  total     ${summary.total}`);
console.log(`  stored    ${summary.stored}  (${mb(summary.bytes)} MB)`);
console.log(`  skipped   ${summary.skipped}  already on disk`);
console.log(`  failed    ${summary.failed}`);

if (summary.failures.length > 0) {
  console.log("\nFailures:");
  for (const failure of summary.failures.slice(0, 20)) {
    console.log(`  ${failure.fileKey}  ${failure.error}`);
  }
  if (summary.failures.length > 20) {
    console.log(`  … and ${summary.failures.length - 20} more`);
  }
  Deno.exit(1);
}

// Exit explicitly. Importing `mod.ts` opens a Mongo connection pool, which keeps the event
// loop alive — without this the tool does all its work, prints the summary, and then sits
// there until something kills it, which makes a successful run look like a hang.
Deno.exit(0);
