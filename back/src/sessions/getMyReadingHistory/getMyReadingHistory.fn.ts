import { type ActFn, type Document, ObjectId } from "lesan";
import { coreApp, readingSession } from "../../../mod.ts";
import { type MyContext, projectionOrPure } from "@lib";

export const getMyReadingHistoryFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { page, limit, skip, from, to }, get } = body.details;

  const pipeline: Document[] = [
    { $match: { "user._id": new ObjectId(user._id) } },
  ];

  if (from !== undefined || to !== undefined) {
    const range: Record<string, number> = {};
    if (from !== undefined) range.$gte = from;
    if (to !== undefined) range.$lte = to;
    pipeline.push({ $match: { startedAt: range } });
  }

  pipeline.push({ $sort: { startedAt: -1 } });

  const calculatedSkip = skip ?? (limit || 50) * ((page || 1) - 1);
  pipeline.push({ $skip: calculatedSkip });
  pipeline.push({ $limit: limit || 50 });

  return await readingSession
    .aggregation({
      pipeline,
      projection: projectionOrPure(get, "readingSession"),
    })
    .toArray();
};
