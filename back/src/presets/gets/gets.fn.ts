import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import { type MyContext, projectionOrPure } from "@lib";

export const getsFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { page, limit, skip, search, includeArchived }, get } =
    body.details;

  const match: Document = { "user._id": new ObjectId(user._id) };
  if (!includeArchived) match.isArchived = false;
  if (search) match.name = { $regex: search, $options: "i" };

  const calculatedSkip = skip ?? (limit || 50) * ((page || 1) - 1);

  return await preset
    .aggregation({
      pipeline: [
        { $match: match },
        // The default preset is what the reader will play, so it comes first regardless of
        // when it was last edited; everything else is most-recently-edited first.
        { $sort: { isDefault: -1, updatedAtMs: -1, _id: -1 } },
        { $skip: calculatedSkip },
        { $limit: limit || 50 },
      ],
      projection: projectionOrPure(get, "preset"),
    })
    .toArray();
};
