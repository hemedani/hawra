import type { ActFn } from "lesan";
import { coreApp, user } from "../../../mod.ts";
import { createToken, throwError } from "@lib";

/**
 * Anonymous device registration, idempotent on `deviceId`.
 *
 * Hawraa has no login in Phase 0: the client persists a stable deviceId and
 * calls this once. Calling it again (reinstall-with-restore, lost token) returns
 * the same user row and a fresh token rather than creating a duplicate — the
 * unique index on deviceId is the backstop, this lookup is the fast path.
 */
export const registerDeviceFn: ActFn = async (body) => {
  const { set: { deviceId, displayName }, get } = body.details;

  const existing = await user.findOne({
    filters: { deviceId },
    projection: { _id: 1, deviceId: 1 },
  });

  const userId = existing ? existing._id : (await user.insertOne({
    doc: {
      deviceId,
      ...(displayName !== undefined ? { displayName } : {}),
      role: "user",
      status: "active",
      createdAt: new Date(),
      updatedAt: new Date(),
    },
    projection: { _id: 1 },
  }))?._id;

  if (!userId) {
    throwError("User was not created");
  }

  const token = await createToken({ _id: userId, deviceId });

  const requestedUser = get?.user ?? {};
  const userProjection = Object.keys(requestedUser).length > 0
    ? requestedUser
    : coreApp.schemas.createProjection("user", "Pure");

  const registeredUser = await user.findOne({
    filters: { _id: userId },
    projection: userProjection,
  });

  return { token, user: registeredUser };
};
