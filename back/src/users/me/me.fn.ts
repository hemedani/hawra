import type { ActFn } from "lesan";
import { coreApp, user } from "../../../mod.ts";
import type { MyContext } from "@lib";
import { throwError } from "@lib";

/**
 * "Who am I?" — the one call the app makes at launch to decide whether it is still signed in.
 *
 * It exists because a stored token is only a *claim*. Ninety days is a long time in a world where
 * an account can be deleted or suspended in between, and the difference between "the server is
 * unreachable" and "this token is no longer valid" decides whether the app opens its gate or
 * keeps working offline. Those two must not be confused: only a real answer from the server may
 * sign someone out.
 *
 * `setTokens` + `setUser` in the preAct already reject a bad or stale token, so reaching this
 * function means the credential is good and the account row exists and is not deleted.
 */
export const meFn: ActFn = async (body) => {
  const { user: account }: MyContext = coreApp.contextFns
    .getContextModel() as MyContext;
  const { get } = body.details;

  const projection = get?.user && Object.keys(get.user).length > 0
    ? get.user
    : coreApp.schemas.createProjection("user", "Pure");

  const found = await user.findOne({
    filters: { _id: account._id },
    projection,
  });

  if (!found) throwError("this account no longer exists");

  return { user: found };
};
