/**
 * Cheap sanity check that a byte buffer starts like an MP3.
 *
 * This exists because HTTP status codes are not evidence. A captive portal, a CDN error page
 * or a truncated HTML redirect all answer `200 OK` with a body, and a downloader that only
 * checks the status code will happily save 4 KB of HTML as `002.mp3`. The device then plays
 * silence and nobody knows why. Checking the first frame costs nothing and turns that into a
 * named failure at fetch time.
 *
 * Two accepted shapes:
 * - an `ID3` tag (`49 44 33`), which is what almost every MP3 in the wild starts with;
 * - a raw MPEG frame sync: eleven set bits (`FF Ex`/`FF Fx`), with the version and layer
 *   fields not set to their reserved values.
 *
 * Deliberately permissive. Its job is to reject *not-audio*, not to validate encoding — a
 * stricter check would start rejecting perfectly good files whose first frame sits behind a
 * large ID3 tag or a few bytes of padding.
 */

const ID3 = [0x49, 0x44, 0x33];

export const looksLikeMp3 = (head: Uint8Array): boolean => {
  if (
    head.length >= 3 && head[0] === ID3[0] && head[1] === ID3[1] &&
    head[2] === ID3[2]
  ) {
    return true;
  }

  const limit = Math.min(head.length - 1, 4096);
  for (let i = 0; i < limit; i++) {
    if (head[i] !== 0xff) continue;
    const second = head[i + 1];
    if ((second & 0xe0) !== 0xe0) continue;
    const version = (second >> 3) & 0x03;
    const layer = (second >> 1) & 0x03;
    // version 0b01 and layer 0b00 are reserved: not a real frame header.
    if (version !== 0b01 && layer !== 0b00) return true;
  }
  return false;
};
