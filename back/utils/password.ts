/**
 * Password hashing with PBKDF2-HMAC-SHA256 via WebCrypto.
 *
 * Why not bcrypt/argon2: this is Deno, and the platform ships PBKDF2 with no dependency to pin,
 * audit or build. PBKDF2 is the weakest of the three against GPU attack, which is exactly why the
 * iteration count is not a default — 210 000 is the OWASP figure for PBKDF2-SHA256 and it is
 * stored **per row**, so raising it later only affects new passwords and does not lock anyone out.
 *
 * The salt and the count live next to the hash because a hash without its parameters cannot be
 * verified. Nothing here ever returns the password, and nothing logs it.
 *
 * Constant-time comparison is not theatre: `===` on strings short-circuits at the first differing
 * character, which leaks how much of a guess was right. Over a network the signal is faint, but
 * it costs three lines to remove.
 */

const ITERATIONS = 210_000;
const KEY_BITS = 256;
const SALT_BYTES = 16;

export interface PasswordRecord {
  hash: string;
  salt: string;
  iterations: number;
}

const toBase64 = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes));

/**
 * Returns `Uint8Array<ArrayBuffer>` rather than the default `Uint8Array<ArrayBufferLike>`.
 *
 * Not pedantry: `crypto.subtle.deriveBits` takes a `BufferSource`, which since TypeScript 5.7 is
 * generic over the backing buffer, and a view that *might* be backed by a `SharedArrayBuffer` is
 * not assignable to it. Building on a fresh `ArrayBuffer` states what is true and keeps the cast
 * out of the call site.
 */
const fromBase64 = (value: string): Uint8Array<ArrayBuffer> => {
  const raw = atob(value);
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
};

const derive = async (
  password: string,
  salt: Uint8Array<ArrayBuffer>,
  iterations: number,
): Promise<Uint8Array> => {
  const material = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations, hash: "SHA-256" },
    material,
    KEY_BITS,
  );
  return new Uint8Array(bits);
};

const equalsConstantTime = (a: Uint8Array, b: Uint8Array): boolean => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
};

export const hashPassword = async (
  password: string,
): Promise<PasswordRecord> => {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  const hash = await derive(password, salt, ITERATIONS);
  return {
    hash: toBase64(hash),
    salt: toBase64(salt),
    iterations: ITERATIONS,
  };
};

export const verifyPassword = async (
  password: string,
  record: PasswordRecord,
): Promise<boolean> => {
  const expected = fromBase64(record.hash);
  const actual = await derive(
    password,
    fromBase64(record.salt),
    record.iterations,
  );
  return equalsConstantTime(expected, actual);
};

/**
 * Emails are compared lowercase and trimmed.
 *
 * Without this, `Ali@x.com` and `ali@x.com` are two accounts that look identical to the person who
 * owns them, and the unique index does not help because it sees two different strings.
 */
export const normalizeEmail = (email: string): string =>
  email.trim().toLowerCase();

/** Deliberately loose. The address is not verified by mail yet; a strict regex rejects valid addresses. */
export const looksLikeEmail = (email: string): boolean =>
  /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email);

export const MIN_PASSWORD_LENGTH = 8;
