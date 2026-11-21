import { type ActFn, ObjectId } from "lesan";
import { coreApp, preset } from "../../../mod.ts";
import type { MyContext } from "@lib";

export const countFn: ActFn = async (body) => {
  const { user }: MyContext = coreApp.contextFns.getContextModel() as MyContext;
  const { set: { includeArchived } } = body.details;

  const qty = await preset.countDocument({
    filter: {
      "user._id": new ObjectId(user._id),
      ...(includeArchived ? {} : { isArchived: false }),
    },
  });

  return { qty };
};
