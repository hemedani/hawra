import { ObjectId } from "lesan";
import { coreApp, user } from "../mod.ts";
import type { MyContext } from "./context.ts";
import { throwError } from "./throwError.ts";

export const setUser = async () => {
  const ctx = coreApp.contextFns.getContextModel() as MyContext;
  const tokenUser = ctx.user;

  if (!tokenUser || !tokenUser._id) {
    throwError("Invalid or missing token data");
  }

  const foundedUser = await user.findOne({
    filters: { _id: new ObjectId(tokenUser._id) },
    projection: {
      _id: 1,
      role: 1,
      status: 1,
      deviceId: 1,
      email: 1,
      displayName: 1,
    },
  });

  !foundedUser && throwError("user not exist");

  if (foundedUser!.status === "deleted") {
    throwError("This account has been deleted");
  }

  coreApp.contextFns.setContext({
    user: {
      ...foundedUser,
      role: foundedUser!.role ?? "user",
      status: foundedUser!.status ?? "active",
    },
  });
};
