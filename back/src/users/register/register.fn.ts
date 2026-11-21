import type { ActFn } from "lesan";
import { user } from "../../../mod.ts";
import {
  hashPassword,
  looksLikeEmail,
  MIN_PASSWORD_LENGTH,
  normalizeEmail,
  throwError,
} from "@lib";
import { sessionResponse } from "../shared.ts";

/**
 * Creates an account and signs it in.
 *
 * Three rules, each with a reason:
 *
 * - **The email is normalised before anything else.** `Ali@x.com` and `ali@x.com` are the same
 *   person, and the unique index can only enforce that if the stored value is already folded.
 * - **The password is hashed here and never stored, logged or echoed.** The act's own response
 *   carries a token, not a credential.
 * - **A duplicate is a normal outcome, not an exception.** The pre-check gives a clear message in
 *   the common case; the unique index is the backstop for two requests racing, which is why the
 *   insert is wrapped — the loser of the race must get the same sentence as the loser of the
 *   pre-check, not a raw `E11000`.
 */
export const registerFn: ActFn = async (body) => {
  const { set, get } = body.details;

  const email = normalizeEmail(set.email);
  if (!looksLikeEmail(email)) {
    throwError("that email address does not look valid");
  }
  if (set.password.length < MIN_PASSWORD_LENGTH) {
    throwError(
      `the password must be at least ${MIN_PASSWORD_LENGTH} characters`,
    );
  }

  const existing = await user.findOne({
    filters: { email },
    projection: { _id: 1 },
  });
  if (existing) throwError("an account with this email already exists");

  const { hash, salt, iterations } = await hashPassword(set.password);

  let insertedId;
  try {
    const inserted = await user.insertOne({
      doc: {
        email,
        ...(set.displayName ? { displayName: set.displayName.trim() } : {}),
        passwordHash: hash,
        passwordSalt: salt,
        passwordIterations: iterations,
        role: "user",
        status: "active",
        lastLoginAt: new Date(),
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      projection: { _id: 1 },
    });
    insertedId = inserted?._id;
  } catch (error) {
    // Almost certainly the unique index on `email`, i.e. two registrations racing.
    const message = error instanceof Error ? error.message : String(error);
    if (
      message.includes("E11000") || message.toLowerCase().includes("duplicate")
    ) {
      throwError("an account with this email already exists");
    }
    throw error;
  }

  if (!insertedId) throwError("the account was not created");

  return sessionResponse(insertedId, get?.user);
};
