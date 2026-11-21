import { type ActFn, type Document } from "lesan";
import { mediaEdition } from "../../../mod.ts";
import { projectionOrPure } from "@lib";

export const getsFn: ActFn = async (body) => {
  const { set: { contentType, granularity, availability, textKey }, get } =
    body.details;

  const match: Document = {};
  if (contentType) match.contentType = contentType;
  if (granularity) match.granularity = granularity;
  if (availability) match.availability = availability;
  if (textKey) match.textKey = textKey;

  return await mediaEdition
    .aggregation({
      pipeline: [
        { $match: match },
        // Verified first (a dead source must never sit above a working one), then by key for a
        // stable order. `isDefault` first within a content type so the preselect leads.
        { $sort: { availability: 1, isDefault: -1, key: 1 } },
      ],
      projection: projectionOrPure(get, "mediaEdition"),
    })
    .toArray();
};
