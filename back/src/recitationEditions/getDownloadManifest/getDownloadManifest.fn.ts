import { type ActFn } from "lesan";
import { recitationEdition } from "../../../mod.ts";
import { throwError } from "@lib";
import { buildManifest, resolveScope } from "../buildManifest.ts";
import { filesWithMirrorInfo } from "../../recitationAudio/manifest.ts";

/**
 * The download manifest: the server's answer to "what exactly do I fetch?".
 *
 * Why this is an act and not a static JSON file: the catalogue changes (a source dies, a
 * bitrate is added, a reciter is verified) without the app being reinstalled. The app keeps
 * no URL logic of its own — it asks, then downloads what it is told.
 *
 * Refuses to build a manifest for an edition that is not `verified`: a manifest is a
 * promise that every URL works, and shipping an unverified one would turn a catalogue
 * mistake into thousands of failed downloads on the device.
 */
export const getDownloadManifestFn: ActFn = async (body) => {
  const { set } = body.details;

  const edition = await recitationEdition.findOne({
    filters: { key: set.editionKey },
    projection: {
      key: 1,
      labelFa: 1,
      labelEn: 1,
      style: 1,
      granularity: 1,
      urlTemplate: 1,
      fileExtension: 1,
      bitrateKbps: 1,
      totalFiles: 1,
      availability: 1,
      sourceName: 1,
      licenseNote: 1,
      verifiedAt: 1,
      reciter: 1,
    },
  });

  if (!edition) throwError(`unknown recitation edition: ${set.editionKey}`);
  if (edition.availability !== "verified") {
    throwError(
      `edition ${set.editionKey} is "${edition.availability}" — no manifest is served for it`,
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
    throwError(
      `scope "${set.scope}" is missing its coordinates (surah/ayah, juz, or from/to)`,
    );
  }
  if (ayahs.length === 0) {
    throwError("the requested scope contains no ayahs");
  }

  const files = buildManifest(
    {
      key: edition.key,
      granularity: edition.granularity,
      urlTemplate: edition.urlTemplate,
      fileExtension: edition.fileExtension,
    },
    ayahs,
  );

  const enriched = await filesWithMirrorInfo(edition.key, files);

  return {
    edition: {
      key: edition.key,
      labelFa: edition.labelFa,
      labelEn: edition.labelEn,
      style: edition.style,
      granularity: edition.granularity,
      bitrateKbps: edition.bitrateKbps,
      totalFiles: edition.totalFiles,
      sourceName: edition.sourceName,
      licenseNote: edition.licenseNote,
      verifiedAt: edition.verifiedAt,
      reciter: edition.reciter,
    },
    scope: set.scope,
    /** How many ayahs the scope covers — the honest coverage count for the download. */
    ayahCount: ayahs.length,
    /** How many files the device must fetch (fewer than ayahs for surah/juz editions). */
    fileCount: files.length,
    /** How many of those this server can serve itself. `fileCount` when fully mirrored. */
    mirroredFileCount: enriched.filter((file) => "mirrorPath" in file).length,
    from: ayahs[0],
    to: ayahs[ayahs.length - 1],
    files: enriched,
  };
};
