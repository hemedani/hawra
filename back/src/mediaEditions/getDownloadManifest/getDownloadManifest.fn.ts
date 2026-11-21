import { type ActFn } from "lesan";
import { mediaEdition } from "../../../mod.ts";
import { throwError } from "@lib";
import {
  buildManifest,
  resolveScope,
} from "../../recitationEditions/buildManifest.ts";
import { filesWithMirrorInfo } from "../../recitationAudio/manifest.ts";

/**
 * The media download manifest — the guya-audio sibling of `recitationEditions/getDownloadManifest`.
 *
 * Same machinery, different catalogue: it resolves the edition from `mediaEdition`, then reuses
 * the pure `buildManifest` (the URL/file arithmetic is identical — verse-tied media is addressed
 * exactly like recitation) and the shared `filesWithAudioMirror` (the mirror records files in
 * `audioFile` keyed by `editionKey`, which is just a string, so a media edition's files merge in
 * the same way). One contract, two catalogues — and neither can drift, because neither re-implements
 * the other.
 *
 * Refuses to build a manifest for an edition that is not `verified`. Guya editions are seeded
 * `unverified` placeholders: the owner drops the per-ayah files and verifies them before the app is
 * ever allowed to offer the download, so a half-populated source cannot turn into thousands of
 * failed downloads on the device.
 */
export const getDownloadManifestFn: ActFn = async (body) => {
  const { set } = body.details;

  const edition = await mediaEdition.findOne({
    filters: { key: set.editionKey },
    projection: {
      key: 1,
      contentType: 1,
      labelFa: 1,
      labelEn: 1,
      granularity: 1,
      urlTemplate: 1,
      fileExtension: 1,
      language: 1,
      textKey: 1,
      narrator: 1,
      bitrateKbps: 1,
      totalFiles: 1,
      availability: 1,
      sourceName: 1,
      licenseNote: 1,
      verifiedAt: 1,
    },
  });

  if (!edition) throwError(`unknown media edition: ${set.editionKey}`);
  if (edition.availability !== "verified") {
    throwError(
      `media edition ${set.editionKey} is "${edition.availability}" — no manifest is served for it`,
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
      contentType: edition.contentType,
      labelFa: edition.labelFa,
      labelEn: edition.labelEn,
      granularity: edition.granularity,
      language: edition.language,
      textKey: edition.textKey,
      narrator: edition.narrator,
      bitrateKbps: edition.bitrateKbps,
      totalFiles: edition.totalFiles,
      sourceName: edition.sourceName,
      licenseNote: edition.licenseNote,
      verifiedAt: edition.verifiedAt,
    },
    scope: set.scope,
    ayahCount: ayahs.length,
    fileCount: files.length,
    mirroredFileCount: enriched.filter((file) => "mirrorPath" in file).length,
    from: ayahs[0],
    to: ayahs[ayahs.length - 1],
    files: enriched,
  };
};
