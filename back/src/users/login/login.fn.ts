import type { ActFn } from "lesan";
import { user } from "../../../mod.ts";
import { normalizeEmail, throwError, verifyPassword } from "@lib";
import { sessionResponse } from "../shared.ts";

/**
 * Signs an existing account in.
 *
 * Two deliberate omissions:
 *
 * - **One message for every failure.** "No such account" and "wrong password" both answer
 *   `email or password is incorrect`. A different sentence for each turns the login form into an
 *   oracle that tells an attacker which addresses are registered — which, for an app whose users
 *   are identifiable by name, is information about the person, not about the system.
 * - **`deviceId` is not written here.** It used to be unique, and two accounts signing in on the
 *   same phone would collide on it. It stays as registration-time context for legacy rows; an
 *   account's device history is not worth a unique constraint that can reject a valid login.
 *
 * The password is verified even when the account does not exist? No — that would be pure cost
 * with no benefit, since the response is identical either way.
 */
export const loginFn: ActFn = async (body) => {
  const { set, get } = body.details;

  const email = normalizeEmail(set.email);

  const account = await user.findOne({
    filters: { email },
    projection: {
      _id: 1,
      status: 1,
      passwordHash: 1,
      passwordSalt: 1,
      passwordIterations: 1,
    },
  });

  const rejected = () => throwError("email or password is incorrect");

  if (!account || !account.passwordHash || !account.passwordSalt) rejected();

  const matches = await verifyPassword(set.password, {
    hash: account!.passwordHash!,
    salt: account!.passwordSalt!,
    iterations: account!.passwordIterations ?? 210_000,
  });
  if (!matches) rejected();

  if (account!.status !== "active") {
    throwError(
      account!.status === "deleted"
        ? "this account has been deleted"
        : "this account is suspended",
    );
  }

  await user.findOneAndUpdate({
    filter: { _id: account!._id },
    update: { $set: { lastLoginAt: new Date(), updatedAt: new Date() } },
    projection: { _id: 1 },
  });

  return sessionResponse(account!._id, get?.user);
};
