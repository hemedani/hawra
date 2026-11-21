import type { ManifestScopeInput } from "../recitationEditions/buildManifest.ts";

/**
 * Parses the compact scope syntax used on the command line.
 *
 * ```sh
 * deno task audio:mirror --edition=… --scope=surah:2
 * deno task audio:mirror --edition=… --scope=juz:1
 * deno task audio:mirror --edition=… --scope=ayah:2:255
 * deno task audio:mirror --edition=… --scope=range:2:1-2:20
 * ```
 *
 * A separate module with its own tests because this is where a typo turns into work on the
 * wrong data: `--scope=surah:2` that silently parsed as "everything" would download 6236
 * files and look like it was doing what you asked. So it is strict — every form either
 * parses completely or throws with the form that was expected.
 */
export const parseScope = (spec: string): ManifestScopeInput => {
  const [kind, ...rest] = spec.split(":");

  const int = (value: string | undefined, name: string): number => {
    const parsed = Number(value);
    if (!value || !Number.isInteger(parsed) || parsed <= 0) {
      throw new Error(`--scope=${spec}: "${name}" must be a positive integer`);
    }
    return parsed;
  };

  switch (kind) {
    case "surah":
      if (rest.length !== 1) {
        throw new Error(`--scope=${spec}: expected surah:<1-114>`);
      }
      return { scope: "surah", surah: int(rest[0], "surah") };

    case "juz":
      if (rest.length !== 1) {
        throw new Error(`--scope=${spec}: expected juz:<1-30>`);
      }
      return { scope: "juz", juz: int(rest[0], "juz") };

    case "ayah":
      if (rest.length !== 2) {
        throw new Error(`--scope=${spec}: expected ayah:<surah>:<ayah>`);
      }
      return {
        scope: "ayah",
        surah: int(rest[0], "surah"),
        ayah: int(rest[1], "ayah"),
      };

    case "range": {
      // range:<fromSurah>:<fromAyah>-<toSurah>:<toAyah>
      const [from, to] = rest.join(":").split("-");
      const fromParts = from?.split(":") ?? [];
      const toParts = to?.split(":") ?? [];
      if (fromParts.length !== 2 || toParts.length !== 2) {
        throw new Error(
          `--scope=${spec}: expected range:<fromSurah>:<fromAyah>-<toSurah>:<toAyah>`,
        );
      }
      return {
        scope: "range",
        fromSurah: int(fromParts[0], "fromSurah"),
        fromAyah: int(fromParts[1], "fromAyah"),
        toSurah: int(toParts[0], "toSurah"),
        toAyah: int(toParts[1], "toAyah"),
      };
    }

    default:
      throw new Error(
        `--scope=${spec}: expected surah:N, juz:N, ayah:S:A or range:S:A-S:A`,
      );
  }
};
