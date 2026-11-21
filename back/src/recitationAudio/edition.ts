import { mediaEdition, recitationEdition } from "../../mod.ts";

/**
 * The shape the manifest builder and the mirror engine need from an edition — nothing else.
 *
 * Both `recitationEdition` and `mediaEdition` carry these five fields, and both feed the same
 * pure `buildManifest`. Resolving to this one descriptor is the single place that knows an
 * edition might live in either catalogue, so the mirror tool and the manifest acts never branch
 * on "is this a reciter edition or a guya edition" themselves.
 */
export interface EditionDescriptor {
  key: string;
  granularity: "ayah" | "surah" | "juz";
  urlTemplate: string;
  fileExtension: string;
  availability: "verified" | "unverified" | "blocked";
  /** Which catalogue the row came from — for logging and attribution only. */
  source: "recitation" | "media";
  bitrateKbps?: number;
}

/**
 * Resolves an edition by key, recitation first then media.
 *
 * Recitation is tried first because its keys are already unique and well-known; a media key
 * that happens to collide would be a seed bug caught by the two `key` unique indexes, not a
 * runtime concern. Returns `null` when neither catalogue has the key, so callers produce one
 * clear "unknown edition" error rather than two.
 */
export const resolveEditionDescriptor = async (
  key: string,
): Promise<EditionDescriptor | null> => {
  const recitation = await recitationEdition.findOne({
    filters: { key },
    projection: {
      key: 1,
      granularity: 1,
      urlTemplate: 1,
      fileExtension: 1,
      availability: 1,
      bitrateKbps: 1,
    },
  });
  if (recitation) {
    return {
      key: recitation.key as string,
      granularity: recitation.granularity as EditionDescriptor["granularity"],
      urlTemplate: recitation.urlTemplate as string,
      fileExtension: recitation.fileExtension as string,
      availability: recitation
        .availability as EditionDescriptor["availability"],
      source: "recitation",
      bitrateKbps: recitation.bitrateKbps as number | undefined,
    };
  }

  const media = await mediaEdition.findOne({
    filters: { key },
    projection: {
      key: 1,
      granularity: 1,
      urlTemplate: 1,
      fileExtension: 1,
      availability: 1,
      bitrateKbps: 1,
    },
  });
  if (!media) return null;
  return {
    key: media.key as string,
    granularity: media.granularity as EditionDescriptor["granularity"],
    urlTemplate: media.urlTemplate as string,
    fileExtension: media.fileExtension as string,
    availability: media.availability as EditionDescriptor["availability"],
    source: "media",
    bitrateKbps: media.bitrateKbps as number | undefined,
  };
};
