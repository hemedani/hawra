import type { Document, ObjectId } from "lesan";
import { coreApp, user } from "../../mod.ts";
import { createToken } from "@lib";

/**
 * Builds the answer both `register` and `login` return: a token and the account it belongs to.
 *
 * One function because the two acts must not drift. A client that stores `user._id` from
 * registration and a different shape from login would be a class of bug that only appears on the
 * second launch, and it would be found by whoever happened to log out first.
 *
 * The projection honours whatever the client asked for in `get`, falling back to the whole pure
 * user — never to a fixed list, so a client can ask for `{ email: 1 }` and get exactly that.
 */
export const sessionResponse = async (
  userId: ObjectId,
  requestedUser: Document | undefined,
): Promise<{ token: string; user: Document | null }> => {
  const token = await createToken({ _id: userId });

  const projection = requestedUser && Object.keys(requestedUser).length > 0
    ? requestedUser
    : coreApp.schemas.createProjection("user", "Pure");

  const account = await user.findOne({
    filters: { _id: userId },
    projection,
  });

  return { token, user: account ?? null };
};
