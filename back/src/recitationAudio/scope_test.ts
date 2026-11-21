import { assertEquals, assertThrows } from "jsr:@std/assert@^1";
import { parseScope } from "./scope.ts";

Deno.test("scope: surah", () => {
  assertEquals(parseScope("surah:2"), { scope: "surah", surah: 2 });
});

Deno.test("scope: juz", () => {
  assertEquals(parseScope("juz:30"), { scope: "juz", juz: 30 });
});

Deno.test("scope: a single ayah", () => {
  assertEquals(parseScope("ayah:2:255"), {
    scope: "ayah",
    surah: 2,
    ayah: 255,
  });
});

Deno.test("scope: an inclusive range across surahs", () => {
  assertEquals(parseScope("range:2:1-2:20"), {
    scope: "range",
    fromSurah: 2,
    fromAyah: 1,
    toSurah: 2,
    toAyah: 20,
  });
  assertEquals(parseScope("range:1:7-2:5"), {
    scope: "range",
    fromSurah: 1,
    fromAyah: 7,
    toSurah: 2,
    toAyah: 5,
  });
});

Deno.test("scope: an unknown kind throws instead of defaulting to everything", () => {
  assertThrows(() => parseScope("sura:2"));
  assertThrows(() => parseScope("all"));
  assertThrows(() => parseScope(""));
});

Deno.test("scope: a missing or non-numeric coordinate throws", () => {
  assertThrows(() => parseScope("surah"));
  assertThrows(() => parseScope("surah:"));
  assertThrows(() => parseScope("surah:two"));
  assertThrows(() => parseScope("surah:0"));
  assertThrows(() => parseScope("ayah:2"));
  assertThrows(() => parseScope("range:2:1"));
});
