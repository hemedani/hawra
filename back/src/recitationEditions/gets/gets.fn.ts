import { type ActFn, type Document } from "lesan";
import { recitationEdition } from "../../../mod.ts";
import { projectionOrPure } from "@lib";

export const getsFn: ActFn = async (body) => {
  const { set: { reciterKey, granularity, availability }, get } = body.details;

  const match: Document = {};
  if (reciterKey) match["reciter.key"] = reciterKey;
  if (granularity) match.granularity = granularity;
  if (availability) match.availability = availability;

  return await recitationEdition
    .aggregation({
      pipeline: [
        { $match: match },
        // Verified first, then by quality: the picker must never offer a dead source above
        // a working one.
        { $sort: { availability: 1, bitrateKbps: -1, key: 1 } },
      ],
      projection: projectionOrPure(get, "recitationEdition"),
    })
    .toArray();
};
