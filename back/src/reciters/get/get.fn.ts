import { type ActFn } from "lesan";
import { reciter } from "../../../mod.ts";

export const getFn: ActFn = async (body) => {
  const { set: { key }, get } = body.details;
  return await reciter.findOne({
    filters: { key },
    projection: get,
  });
};
