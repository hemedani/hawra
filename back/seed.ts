/**
 * Seeds the recitation catalogue — the only data in this backend that is not user activity.
 *
 * Run once after a deploy, and again whenever an edition is added or re-verified:
 *
 * ```sh
 * deno task seed
 * ```
 *
 * Idempotent: every row is keyed by its stable slug, so re-running updates in place and
 * never duplicates. Nothing here is sacred text — a name, a URL template and a licence note.
 *
 * ## How these were verified (2026-11-15)
 *
 * Every URL template below was probed with a ranged GET and checked for `audio/mpeg`:
 *
 * | edition | probe |
 * |---|---|
 * | parhizgar ayah 48 kbps | `cdn.islamic.network/quran/audio/48/ar.parhizgar/1.mp3` → 206 audio/mpeg |
 * | minshawi ayah 128 kbps | `cdn.islamic.network/quran/audio/128/ar.minshawi/1.mp3` → 206 audio/mpeg |
 * | minshawi surah 128 kbps | `server10.mp3quran.net/minsh/001.mp3` → 206 (redirects to cdn.mp3quran.net) |
 * | shakernejad juz 24 kbps | `dl.meshkatsound.com/Tartil/Hamed-Shakernejad/Shakernejad-Quran-Juz-01.mp3` → 206 audio/mpeg |
 *
 * **Shakernejad is juz-granularity on purpose.** There is no public per-ayah source for him:
 * cdn.islamic.network returns 403 for every spelling of his slug, Quran.com's v4 recitation
 * list has 12 reciters (none of them him), mp3quran's 244 do not include him, everyayah's 177
 * directories do not either, and the Iranian archives that do carry his tartil (ramezan,
 * p30download, iranseda, ahlolbait) publish either one 400 MB file or juz-by-juz MP3s behind
 * landing pages. meshkatsound's `dl.` host is the one that serves the files directly. So his
 * edition is honest about what it is: whole-juz audio, for offline continuous listening,
 * not for verse-level presets.
 */
import { mediaEdition, recitationEdition, reciter } from "./mod.ts";
import type { ObjectId } from "lesan";

interface ReciterSeed {
  key: string;
  nameFa: string;
  nameEn: string;
  nameAr: string;
  countryCode: string;
  sortOrder: number;
  bioFa?: string;
  bioEn?: string;
}

interface EditionSeed {
  key: string;
  reciterKey: string;
  labelFa: string;
  labelEn: string;
  style: "murattal" | "mujawwad" | "tartil" | "muallim";
  granularity: "ayah" | "surah" | "juz";
  urlTemplate: string;
  bitrateKbps: number;
  totalFiles: number;
  availability: "verified" | "unverified" | "blocked";
  sourceName: string;
  sourceUrl?: string;
  licenseNote?: string;
  verifiedAt?: string;
  notesFa?: string;
  notesEn?: string;
  isDefault?: boolean;
}

const RECITERS: ReciterSeed[] = [
  {
    key: "shakernejad",
    nameFa: "حامد شاکرنژاد",
    nameEn: "Hamed Shakernejad",
    nameAr: "حامد شاكرنژاد",
    countryCode: "IR",
    sortOrder: 10,
    bioFa:
      "قاری بین‌المللی ایرانی، متولد مشهد. ترتیل او جزء‌به‌جزء منتشر شده است.",
    bioEn:
      "Iranian international reciter, born in Mashhad. His tartil is published juz by juz.",
  },
  {
    key: "parhizgar",
    nameFa: "شهریار پرهیزگار",
    nameEn: "Shahriar Parhizgar",
    nameAr: "شهريار پرهيزگار",
    countryCode: "IR",
    sortOrder: 20,
    bioFa: "قاری و حافظ کل قرآن، ترتیل آیه‌به‌آیه.",
    bioEn: "Iranian reciter and hafiz; verse-by-verse murattal.",
  },
  {
    key: "minshawi",
    nameFa: "محمد صدیق منشاوی",
    nameEn: "Mohamed Siddiq al-Minshawi",
    nameAr: "محمد صديق المنشاوي",
    countryCode: "EG",
    sortOrder: 30,
    bioFa: "قاری نامدار مصری، ترتیل و مجود.",
    bioEn: "Celebrated Egyptian reciter; murattal and mujawwad.",
  },
  {
    key: "alafasy",
    nameFa: "مشاری راشد العفاسی",
    nameEn: "Mishary Rashid al-Afasy",
    nameAr: "مشاري راشد العفاسي",
    countryCode: "KW",
    sortOrder: 40,
  },
  {
    key: "husary",
    nameFa: "محمود خلیل الحصری",
    nameEn: "Mahmoud Khalil al-Husary",
    nameAr: "محمود خليل الحصري",
    countryCode: "EG",
    sortOrder: 50,
  },
];

const EDITIONS: EditionSeed[] = [
  {
    key: "shakernejad-tartil-juz-24",
    reciterKey: "shakernejad",
    labelFa: "ترتیل — جزء‌به‌جزء (۲۴ کیلوبیت)",
    labelEn: "Tartil — juz by juz (24 kbps)",
    style: "tartil",
    granularity: "juz",
    urlTemplate:
      "https://dl.meshkatsound.com/Tartil/Hamed-Shakernejad/Shakernejad-Quran-Juz-{juz2}.mp3",
    bitrateKbps: 24,
    totalFiles: 30,
    availability: "verified",
    sourceName: "Meshkat Sound archive (dl.meshkatsound.com)",
    sourceUrl:
      "https://meshkatsound.com/recitation-of-the-holy-quran/tertile-of-the-holy-quran/tartil-hamed-shakernejad/",
    licenseNote:
      "Public archive of the recitation; kept for listening and study. Attribution shown in Settings.",
    verifiedAt: "2026-11-15",
    notesFa: "آیه‌به‌آیه منتشر نشده است؛ برای پخش پیوستهٔ آفلاین جزء‌به‌جزء.",
    notesEn:
      "No per-ayah source exists; whole-juz audio for continuous offline listening.",
  },
  {
    key: "shakernejad-murattal-ayah-1014",
    reciterKey: "shakernejad",
    labelFa: "ترتیل — آیه‌به‌آیه (اینور)",
    labelEn: "Murattal — verse by verse (Inoor)",
    style: "murattal",
    granularity: "ayah",
    // Inoor (قرآن اینور) publishes Shakernejad verse-by-verse. The file path is keyed by the
    // in-surah ayah (`{surah3}{ayah3}`), not the global number — hence the {ayah3} placeholder.
    // The server mirrors this into uploads/recitation/<key>/ayah/<surah3>/<ayah3>.mp3 for offline.
    urlTemplate:
      "https://apiquran.inoor.ir/File/Sound/Recitation/1014/{surah3}{ayah3}.mp3",
    bitrateKbps: 64,
    totalFiles: 6236,
    availability: "verified",
    sourceName: "Inoor Quran (apiquran.inoor.ir), recitor 1014",
    sourceUrl: "https://quran.inoor.ir/fa/home",
    licenseNote:
      "Verse-by-verse tartil of Hamed Shakernejad, served by the Inoor Quran platform (CRCIS).",
    verifiedAt: "2026-11-16",
    isDefault: true,
  },
  {
    key: "parhizgar-murattal-ayah-48",
    reciterKey: "parhizgar",
    labelFa: "ترتیل — آیه‌به‌آیه (۴۸ کیلوبیت)",
    labelEn: "Murattal — verse by verse (48 kbps)",
    style: "murattal",
    granularity: "ayah",
    urlTemplate:
      "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/{ayah}.mp3",
    bitrateKbps: 48,
    totalFiles: 6236,
    availability: "verified",
    sourceName: "AlQuran Cloud verse-by-verse CDN",
    sourceUrl: "https://alquran.cloud/api",
    licenseNote: "Streamed from the AlQuran Cloud CDN (islamic.network).",
    verifiedAt: "2026-11-15",
  },
  {
    key: "minshawi-murattal-ayah-128",
    reciterKey: "minshawi",
    labelFa: "ترتیل — آیه‌به‌آیه (۱۲۸ کیلوبیت)",
    labelEn: "Murattal — verse by verse (128 kbps)",
    style: "murattal",
    granularity: "ayah",
    urlTemplate:
      "https://cdn.islamic.network/quran/audio/128/ar.minshawi/{ayah}.mp3",
    bitrateKbps: 128,
    totalFiles: 6236,
    availability: "verified",
    sourceName: "AlQuran Cloud verse-by-verse CDN",
    sourceUrl: "https://alquran.cloud/api",
    licenseNote: "Streamed from the AlQuran Cloud CDN (islamic.network).",
    verifiedAt: "2026-11-15",
  },
  {
    key: "minshawi-murattal-surah-128",
    reciterKey: "minshawi",
    labelFa: "ترتیل — سوره‌به‌سوره (۱۲۸ کیلوبیت)",
    labelEn: "Murattal — surah by surah (128 kbps)",
    style: "murattal",
    granularity: "surah",
    urlTemplate: "https://server10.mp3quran.net/minsh/{surah3}.mp3",
    bitrateKbps: 128,
    totalFiles: 114,
    availability: "verified",
    sourceName: "MP3Quran (server10 → cdn.mp3quran.net)",
    sourceUrl: "https://mp3quran.net",
    licenseNote: "Served by the MP3Quran CDN.",
    verifiedAt: "2026-11-15",
  },
  {
    key: "alafasy-murattal-ayah-128",
    reciterKey: "alafasy",
    labelFa: "ترتیل — آیه‌به‌آیه (۱۲۸ کیلوبیت)",
    labelEn: "Murattal — verse by verse (128 kbps)",
    style: "murattal",
    granularity: "ayah",
    urlTemplate:
      "https://cdn.islamic.network/quran/audio/128/ar.alafasy/{ayah}.mp3",
    bitrateKbps: 128,
    totalFiles: 6236,
    availability: "verified",
    sourceName: "AlQuran Cloud verse-by-verse CDN",
    sourceUrl: "https://alquran.cloud/api",
    verifiedAt: "2026-11-15",
  },
  {
    key: "husary-murattal-ayah-128",
    reciterKey: "husary",
    labelFa: "ترتیل — آیه‌به‌آیه (۱۲۸ کیلوبیت)",
    labelEn: "Murattal — verse by verse (128 kbps)",
    style: "murattal",
    granularity: "ayah",
    urlTemplate:
      "https://cdn.islamic.network/quran/audio/128/ar.husary/{ayah}.mp3",
    bitrateKbps: 128,
    totalFiles: 6236,
    availability: "verified",
    sourceName: "AlQuran Cloud verse-by-verse CDN",
    sourceUrl: "https://alquran.cloud/api",
    verifiedAt: "2026-11-15",
  },
];

/**
 * Verse-tied **media** that is not a reciter's tilāwa — guya audio now, video/images later. Seeded
 * as **`unverified` placeholders**: the app will not offer them until the owner drops the files and
 * flips each to `verified`.
 *
 * ## The drop-file contract (one time, then the app downloads from here)
 *
 * Guya audio is per-ayah, addressed by the **global** ayah number (1…6236), exactly like the
 * recitation CDNs. To populate one of these editions:
 *
 * 1. Place the mp3s at `uploads/recitation/<key>/ayah/<globalAyah>.mp3` (the directory is created
 *    by the owner; `uploads/` is served statically — see `mod.ts` `staticPath`).
 * 2. Set the edition's `availability` to `"verified"` (this file or a one-line update).
 *
 * The `urlTemplate` is **server-relative** (`/uploads/recitation/…`), so the manifest hands the
 * device a `/uploads/...` path which the app resolves against its own configured base URL — the
 * same manifest is correct from a phone on the LAN or an emulator on loopback. No mirror rows are
 * required for the URL to work; mirroring (audioFile rows) only adds `bytes` + `mirrorPath` as a
 * bonus. Nothing here is the written translation/tafsir text — only the audio that speaks it and
 * the `textKey` of the text it narrates (D2).
 */
interface MediaEditionSeed {
  key: string;
  contentType: "recitation" | "translation" | "tafsir" | "video" | "image";
  labelFa: string;
  labelEn: string;
  granularity: "ayah" | "surah" | "juz";
  urlTemplate: string;
  fileExtension: string;
  language?: string;
  textKey?: string;
  narrator?: string;
  bitrateKbps?: number;
  totalFiles: number;
  sourceName: string;
  licenseNote?: string;
  notesFa?: string;
  notesEn?: string;
  isDefault?: boolean;
}

const MEDIA_EDITIONS: MediaEditionSeed[] = [
  {
    key: "tarjomeh-guya-ansarian-ayah",
    contentType: "translation",
    labelFa: "ترجمه‌گویا — انصاریان (آیه‌به‌آیه)",
    labelEn: "Spoken translation — Ansarian (verse by verse)",
    granularity: "ayah",
    urlTemplate:
      "/uploads/recitation/tarjomeh-guya-ansarian-ayah/ayah/{ayah}.mp3",
    fileExtension: "mp3",
    language: "fa",
    textKey: "fa.ansarian",
    narrator: "بهروز رضوی / Behrouz Razavi",
    bitrateKbps: 64,
    totalFiles: 6236,
    sourceName: "Owner-supplied translation audio (drop-file contract)",
    licenseNote: "Recorded narration of the Ansarian Persian translation.",
    notesFa:
      "جای‌نگهدار؛ فایل‌ها را بگذارید و verified کنید تا در برنامه فعال شود.",
    notesEn:
      "Placeholder: drop the per-ayah mp3s and mark verified to enable it.",
    isDefault: true,
  },
  {
    key: "tafsir-guya-qaraati-ayah",
    contentType: "tafsir",
    labelFa: "تفسیرگویا — قرائتی (آیه‌به‌آیه)",
    labelEn: "Spoken tafsir — Qara'ati (verse by verse)",
    granularity: "ayah",
    urlTemplate: "/uploads/recitation/tafsir-guya-qaraati-ayah/ayah/{ayah}.mp3",
    fileExtension: "mp3",
    language: "fa",
    textKey: "tafsir-guya",
    narrator: "محمدرضا قرائتی / Mohammadreza Qaraati",
    bitrateKbps: 64,
    totalFiles: 6236,
    sourceName: "Owner-supplied tafsir audio (drop-file contract)",
    licenseNote: "Recorded verse-by-verse exegesis (Tafsir-e Guya).",
    notesFa:
      "جای‌نگهدار؛ فایل‌ها را بگذارید و verified کنید تا در برنامه فعال شود.",
    notesEn:
      "Placeholder: drop the per-ayah mp3s and mark verified to enable it.",
    isDefault: true,
  },
];

const reciterIdByKey = new Map<string, ObjectId>();

for (const seed of RECITERS) {
  const existing = await reciter.findOne({
    filters: { key: seed.key },
    projection: { _id: 1 },
  });

  const doc = {
    ...seed,
    isActive: true,
    updatedAt: new Date(),
  };

  if (existing) {
    await reciter.findOneAndUpdate({
      filter: { _id: existing._id },
      update: { $set: doc },
      projection: { _id: 1 },
    });
    reciterIdByKey.set(seed.key, existing._id as ObjectId);
    console.log(`reciter updated: ${seed.key}`);
  } else {
    const inserted = await reciter.insertOne({
      doc: { ...doc, createdAt: new Date() },
      projection: { _id: 1 },
    });
    if (!inserted) throw new Error(`reciter not inserted: ${seed.key}`);
    reciterIdByKey.set(seed.key, inserted._id as ObjectId);
    console.log(`reciter inserted: ${seed.key}`);
  }
}

for (const seed of EDITIONS) {
  const reciterId = reciterIdByKey.get(seed.reciterKey);
  if (!reciterId) throw new Error(`unknown reciterKey: ${seed.reciterKey}`);

  const { reciterKey: _ignored, isDefault, ...rest } = seed;
  const doc = {
    ...rest,
    isDefault: isDefault ?? false,
    fileExtension: "mp3",
    updatedAt: new Date(),
  };

  const existing = await recitationEdition.findOne({
    filters: { key: seed.key },
    projection: { _id: 1 },
  });

  if (existing) {
    await recitationEdition.findOneAndUpdate({
      filter: { _id: existing._id },
      update: { $set: doc },
      projection: { _id: 1 },
    });
    console.log(`edition updated: ${seed.key}`);
  } else {
    const inserted = await recitationEdition.insertOne({
      doc: { ...doc, createdAt: new Date() },
      relations: {
        reciter: { _ids: reciterId, relatedRelations: { editions: true } },
      } as never,
      projection: { _id: 1 },
    });
    if (!inserted) throw new Error(`edition not inserted: ${seed.key}`);
    console.log(`edition inserted: ${seed.key}`);
  }
}

for (const seed of MEDIA_EDITIONS) {
  const { isDefault, ...rest } = seed;
  const doc = {
    ...rest,
    isDefault: isDefault ?? false,
    updatedAt: new Date(),
  };

  // `availability` is never overwritten once set, so a re-seed cannot disable a source the owner
  // just turned on. But it MUST be written on first insert: a raw `insertOne` bypasses the model's
  // `defaulted`, so omitting it here would leave the field `undefined`, not `"unverified"`.
  const existing = await mediaEdition.findOne({
    filters: { key: seed.key },
    projection: { _id: 1, availability: 1 },
  });

  if (existing) {
    // Preserve the owner's availability flip by writing the stored value back unchanged;
    // only a row that never had it gets the "unverified" default. A single object shape (not a
    // union) keeps this assignable to the update filter.
    const set = { ...doc, availability: existing.availability ?? "unverified" };
    await mediaEdition.findOneAndUpdate({
      filter: { _id: existing._id },
      update: { $set: set },
      projection: { _id: 1 },
    });
    console.log(`media edition updated: ${seed.key}`);
  } else {
    const inserted = await mediaEdition.insertOne({
      doc: { ...doc, availability: "unverified", createdAt: new Date() },
      projection: { _id: 1 },
    });
    if (!inserted) throw new Error(`media edition not inserted: ${seed.key}`);
    console.log(`media edition inserted: ${seed.key}`);
  }
}

console.log(
  `catalogue seeded: ${RECITERS.length} reciters, ${EDITIONS.length} editions, ${MEDIA_EDITIONS.length} media editions`,
);
Deno.exit(0);
