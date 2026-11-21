import { assertEquals, assertNotEquals } from "jsr:@std/assert@^1";
import {
  hashPassword,
  looksLikeEmail,
  MIN_PASSWORD_LENGTH,
  normalizeEmail,
  verifyPassword,
} from "./password.ts";

/**
 * Password hashing is the one piece of this backend whose failure is silent and permanent: a hash
 * that does not verify locks every account out, and a hash that verifies the *wrong* password is
 * not a bug anyone reports as a bug. So the round trip is pinned here rather than discovered on a
 * phone.
 */

Deno.test("password: the right password verifies", async () => {
  const record = await hashPassword("hawraa-test-1");
  assertEquals(await verifyPassword("hawraa-test-1", record), true);
});

Deno.test("password: the wrong password does not", async () => {
  const record = await hashPassword("hawraa-test-1");
  assertEquals(await verifyPassword("hawraa-test-2", record), false);
  assertEquals(await verifyPassword("", record), false);
  assertEquals(await verifyPassword("HAWRAA-TEST-1", record), false);
});

Deno.test("password: the same password hashes differently every time", async () => {
  // Distinct salts. Without this, two users with the same password are visibly the same row in a
  // dump, and one rainbow table cracks both.
  const first = await hashPassword("same-password");
  const second = await hashPassword("same-password");
  assertNotEquals(first.hash, second.hash);
  assertNotEquals(first.salt, second.salt);
  assertEquals(await verifyPassword("same-password", first), true);
  assertEquals(await verifyPassword("same-password", second), true);
});

Deno.test("password: the iteration count is stored with the hash", async () => {
  // Raising the cost later must not invalidate existing rows, which is only true if each row
  // carries the parameters it was hashed with.
  const record = await hashPassword("hawraa-test-1");
  assertEquals(record.iterations >= 100_000, true);
  const weakened = { ...record, iterations: 1 };
  assertEquals(await verifyPassword("hawraa-test-1", weakened), false);
});

Deno.test("password: a tampered hash does not verify", async () => {
  const record = await hashPassword("hawraa-test-1");
  // Same length, same padding, one different byte — the case a length check alone would miss.
  const flipped = (record.hash[0] === "A" ? "B" : "A") + record.hash.slice(1);
  assertEquals(
    await verifyPassword("hawraa-test-1", { ...record, hash: flipped }),
    false,
  );
});

Deno.test("email: normalisation folds case and whitespace", () => {
  assertEquals(normalizeEmail("  Syd@Example.COM "), "syd@example.com");
});

Deno.test("email: the shape check accepts real addresses and rejects the rest", () => {
  for (
    const good of [
      "ali@example.com",
      "ali.reza@gmail.co.uk",
      "a_b+tag@sub.domain.ir",
    ]
  ) {
    assertEquals(looksLikeEmail(good), true);
  }
  for (const bad of ["", "nope", "a@b", "@example.com", "a b@c.com", "a@b.c"]) {
    assertEquals(looksLikeEmail(bad), false);
  }
});

Deno.test("password: the minimum length is a rule, not a suggestion", () => {
  assertEquals(MIN_PASSWORD_LENGTH >= 8, true);
});
