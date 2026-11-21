import { type ActFn, type Document } from "lesan";
import { coreApp, reciter } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { projectionOrPure } from "@lib";

/**
 * The reciter catalogue, with each reciter's editions embedded.
 *
 * Read-only reference data: it is seeded from the deploy, not created by users, so there is
 * no `add` act. A new reciter is a data change (see `docs/plans`), not a release.
 */
export const getsFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { key, includeInactive }, get } = body.details;

  // Authenticated like every other act: the catalogue is not secret, but the device always
  // has a token by the time it can play anything, and one auth path is one thing to test.
  if (!user?._id) throw new Error("unauthorized");

  const match: Document = {};
  if (key) match.key = key;
  if (!includeInactive) match.isActive = true;

  return await reciter
    .aggregation({
      pipeline: [
        { $match: match },
        { $sort: { sortOrder: 1, "nameEn": 1 } },
      ],
      projection: projectionOrPure(get, "reciter"),
    })
    .toArray();
};
