import { type ActFn } from "lesan";
import { recitationEdition } from "../../../mod.ts";

export const getFn: ActFn = async (body) => {
  const { set: { key }, get } = body.details;
  return await recitationEdition.findOne({
    filters: { key },
    projection: get,
  });
};
