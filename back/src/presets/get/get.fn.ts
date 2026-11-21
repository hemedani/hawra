import { type ActFn, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import { type MyContext, throwError } from "@lib";

export const getFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { _id, clientId }, get } = body.details;

  const filter = _id
    ? { _id: new ObjectId(_id as string), "user._id": new ObjectId(user._id) }
    : { clientId, "user._id": new ObjectId(user._id) };

  if (!_id && !clientId) throwError("either _id or clientId is required");

  return await preset.findOne({ filters: filter, projection: get });
};
