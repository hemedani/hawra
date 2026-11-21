/**
 * Static reference index of the Hafs / Madani mushaf: 114 surahs and 30 juz.
 *
 * **This file contains no Quranic text.** It holds ayah *counts*, names and boundary
 * coordinates only — the coordinates are what a download manifest needs, and they are the
 * same numbers every printed mushaf agrees on. The sacred text itself never leaves the
 * device (see root AGENTS.md, invariant 9, and back/AGENTS.md D2).
 *
 * Two invariants are asserted at module load:
 *  - the 114 `ayahCount`s sum to exactly 6236 (the Hafs total);
 *  - the juz table covers ayahs 1…6236 with no gap and no overlap.
 *
 * `content/quranIndex_test.ts` re-checks both plus the boundary arithmetic, so a typo in a
 * count cannot silently shift every URL in a download manifest.
 */

export interface SurahRef {
  /** 1-based surah number. */
  number: number;
  nameAr: string;
  nameFa: string;
  nameEn: string;
  /** Number of ayahs, Hafs count. */
  ayahCount: number;
  /** 1-based index of this surah's first ayah in the global 1…6236 sequence. */
  startGlobalAyah: number;
}

export interface JuzRef {
  /** 1-based juz number. */
  number: number;
  startSurah: number;
  startAyah: number;
  startGlobalAyah: number;
  /** Number of ayahs in this juz. */
  ayahCount: number;
}

/** A concrete verse coordinate. */
export interface AyahRef {
  surah: number;
  ayah: number;
  globalAyah: number;
}

export const TOTAL_AYAHS = 6236;
export const TOTAL_SURAHS = 114;
export const TOTAL_JUZ = 30;

/** `[nameAr, nameFa, nameEn, ayahCount]`, in mushaf order. */
const SURAH_TABLE: readonly (readonly [string, string, string, number])[] = [
  ["الفاتحة", "فاتحه", "Al-Fatihah", 7],
  ["البقرة", "بقره", "Al-Baqarah", 286],
  ["آل عمران", "آلعمران", "Ali 'Imran", 200],
  ["النساء", "نساء", "An-Nisa", 176],
  ["المائدة", "مائده", "Al-Ma'idah", 120],
  ["الأنعام", "انعام", "Al-An'am", 165],
  ["الأعراف", "اعراف", "Al-A'raf", 206],
  ["الأنفال", "انفال", "Al-Anfal", 75],
  ["التوبة", "توبه", "At-Tawbah", 129],
  ["يونس", "یونس", "Yunus", 109],
  ["هود", "هود", "Hud", 123],
  ["يوسف", "یوسف", "Yusuf", 111],
  ["الرعد", "رعد", "Ar-Ra'd", 43],
  ["إبراهيم", "ابراهیم", "Ibrahim", 52],
  ["الحجر", "حجر", "Al-Hijr", 99],
  ["النحل", "نحل", "An-Nahl", 128],
  ["الإسراء", "اسراء", "Al-Isra", 111],
  ["الكهف", "کهف", "Al-Kahf", 110],
  ["مريم", "مریم", "Maryam", 98],
  ["طه", "طه", "Taha", 135],
  ["الأنبياء", "انبیاء", "Al-Anbiya", 112],
  ["الحج", "حج", "Al-Hajj", 78],
  ["المؤمنون", "مؤمنون", "Al-Mu'minun", 118],
  ["النور", "نور", "An-Nur", 64],
  ["الفرقان", "فرقان", "Al-Furqan", 77],
  ["الشعراء", "شعراء", "Ash-Shu'ara", 227],
  ["النمل", "نمل", "An-Naml", 93],
  ["القصص", "قصص", "Al-Qasas", 88],
  ["العنكبوت", "عنکبوت", "Al-'Ankabut", 69],
  ["الروم", "روم", "Ar-Rum", 60],
  ["لقمان", "لقمان", "Luqman", 34],
  ["السجدة", "سجده", "As-Sajdah", 30],
  ["الأحزاب", "احزاب", "Al-Ahzab", 73],
  ["سبأ", "سبأ", "Saba", 54],
  ["فاطر", "فاطر", "Fatir", 45],
  ["يس", "یس", "Ya-Sin", 83],
  ["الصافات", "صافات", "As-Saffat", 182],
  ["ص", "ص", "Sad", 88],
  ["الزمر", "زمر", "Az-Zumar", 75],
  ["غافر", "غافر", "Ghafir", 85],
  ["فصلت", "فصلت", "Fussilat", 54],
  ["الشورى", "شوری", "Ash-Shuraa", 53],
  ["الزخرف", "زخرف", "Az-Zukhruf", 89],
  ["الدخان", "دخان", "Ad-Dukhan", 59],
  ["الجاثية", "جاثیه", "Al-Jathiyah", 37],
  ["الأحقاف", "احقاف", "Al-Ahqaf", 35],
  ["محمد", "محمد", "Muhammad", 38],
  ["الفتح", "فتح", "Al-Fath", 29],
  ["الحجرات", "حجرات", "Al-Hujurat", 18],
  ["ق", "ق", "Qaf", 45],
  ["الذاريات", "ذاریات", "Adh-Dhariyat", 60],
  ["الطور", "طور", "At-Tur", 49],
  ["النجم", "نجم", "An-Najm", 62],
  ["القمر", "قمر", "Al-Qamar", 55],
  ["الرحمن", "الرحمن", "Ar-Rahman", 78],
  ["الواقعة", "واقعه", "Al-Waqi'ah", 96],
  ["الحديد", "حدید", "Al-Hadid", 29],
  ["المجادلة", "مجادله", "Al-Mujadila", 22],
  ["الحشر", "حشر", "Al-Hashr", 24],
  ["الممتحنة", "ممتحنه", "Al-Mumtahanah", 13],
  ["الصف", "صف", "As-Saff", 14],
  ["الجمعة", "جمعه", "Al-Jumu'ah", 11],
  ["المنافقون", "منافقون", "Al-Munafiqun", 11],
  ["التغابن", "تغابن", "At-Taghabun", 18],
  ["الطلاق", "طلاق", "At-Talaq", 12],
  ["التحريم", "تحریم", "At-Tahrim", 12],
  ["الملك", "ملک", "Al-Mulk", 30],
  ["القلم", "قلم", "Al-Qalam", 52],
  ["الحاقة", "حاقه", "Al-Haqqah", 52],
  ["المعارج", "معارج", "Al-Ma'arij", 44],
  ["نوح", "نوح", "Nuh", 28],
  ["الجن", "جن", "Al-Jinn", 28],
  ["المزمل", "مزمل", "Al-Muzzammil", 20],
  ["المدثر", "مدثر", "Al-Muddaththir", 56],
  ["القيامة", "قیامت", "Al-Qiyamah", 40],
  ["الإنسان", "انسان", "Al-Insan", 31],
  ["المرسلات", "مرسلات", "Al-Mursalat", 50],
  ["النبأ", "نبأ", "An-Naba", 40],
  ["النازعات", "نازعات", "An-Nazi'at", 46],
  ["عبس", "عبس", "'Abasa", 42],
  ["التكوير", "تکویر", "At-Takwir", 29],
  ["الانفطار", "انفطار", "Al-Infitar", 19],
  ["المطففين", "مطففین", "Al-Mutaffifin", 36],
  ["الانشقاق", "انشقاق", "Al-Inshiqaq", 25],
  ["البروج", "بروج", "Al-Buruj", 22],
  ["الطارق", "طارق", "At-Tariq", 17],
  ["الأعلى", "اعلی", "Al-A'la", 19],
  ["الغاشية", "غاشیه", "Al-Ghashiyah", 26],
  ["الفجر", "فجر", "Al-Fajr", 30],
  ["البلد", "بلد", "Al-Balad", 20],
  ["الشمس", "شمس", "Ash-Shams", 15],
  ["الليل", "لیل", "Al-Layl", 21],
  ["الضحى", "ضحی", "Ad-Duhaa", 11],
  ["الشرح", "انشراح", "Ash-Sharh", 8],
  ["التين", "تین", "At-Tin", 8],
  ["العلق", "علق", "Al-'Alaq", 19],
  ["القدر", "قدر", "Al-Qadr", 5],
  ["البينة", "بینه", "Al-Bayyinah", 8],
  ["الزلزلة", "زلزله", "Az-Zalzalah", 8],
  ["العاديات", "عادیات", "Al-'Adiyat", 11],
  ["القارعة", "قارعه", "Al-Qari'ah", 11],
  ["التكاثر", "تکاثر", "At-Takathur", 8],
  ["العصر", "عصر", "Al-'Asr", 3],
  ["الهمزة", "همزه", "Al-Humazah", 9],
  ["الفيل", "فیل", "Al-Fil", 5],
  ["قريش", "قریش", "Quraysh", 4],
  ["الماعون", "ماعون", "Al-Ma'un", 7],
  ["الكوثر", "کوثر", "Al-Kawthar", 3],
  ["الكافرون", "کافرون", "Al-Kafirun", 6],
  ["النصر", "نصر", "An-Nasr", 3],
  ["المسد", "مسد", "Al-Masad", 5],
  ["الإخلاص", "اخلاص", "Al-Ikhlas", 4],
  ["الفلق", "فلق", "Al-Falaq", 5],
  ["الناس", "ناس", "An-Nas", 6],
];

export const SURAHS: readonly SurahRef[] = (() => {
  let global = 1;
  return SURAH_TABLE.map(([nameAr, nameFa, nameEn, ayahCount], index) => {
    const surah: SurahRef = {
      number: index + 1,
      nameAr,
      nameFa,
      nameEn,
      ayahCount,
      startGlobalAyah: global,
    };
    global += ayahCount;
    return surah;
  });
})();

/** `[surah, ayah]` of the first verse of each juz, in order (the standard Hafs table). */
const JUZ_STARTS: readonly (readonly [number, number])[] = [
  [1, 1],
  [2, 142],
  [2, 253],
  [3, 93],
  [4, 24],
  [4, 148],
  [5, 82],
  [6, 111],
  [7, 88],
  [8, 41],
  [9, 93],
  [11, 6],
  [12, 53],
  [15, 1],
  [17, 1],
  [18, 75],
  [21, 1],
  [23, 1],
  [25, 21],
  [27, 56],
  [29, 46],
  [33, 31],
  [36, 28],
  [39, 32],
  [41, 47],
  [46, 1],
  [51, 31],
  [58, 1],
  [67, 1],
  [78, 1],
];

const surahByNumber = new Map<number, SurahRef>(
  SURAHS.map((s) => [s.number, s]),
);

/** The surah row for [surah] (1…114), or `undefined` when out of range. */
export const surah = (surahNumber: number): SurahRef | undefined =>
  surahByNumber.get(surahNumber);

/** Global ayah number (1…6236) for a coordinate, or `undefined` when out of range. */
export const globalAyahOf = (
  surahNumber: number,
  ayah: number,
): number | undefined => {
  const row = surahByNumber.get(surahNumber);
  if (!row || ayah < 1 || ayah > row.ayahCount) return undefined;
  return row.startGlobalAyah + ayah - 1;
};

/** Coordinate for a global ayah number, or `undefined` when out of range. */
export const ayahAt = (globalAyah: number): AyahRef | undefined => {
  if (globalAyah < 1 || globalAyah > TOTAL_AYAHS) return undefined;
  let low = 0;
  let high = SURAHS.length - 1;
  while (low <= high) {
    const mid = (low + high) >> 1;
    const row = SURAHS[mid];
    if (globalAyah < row.startGlobalAyah) high = mid - 1;
    else if (globalAyah >= row.startGlobalAyah + row.ayahCount) low = mid + 1;
    else {
      return {
        surah: row.number,
        ayah: globalAyah - row.startGlobalAyah + 1,
        globalAyah,
      };
    }
  }
  return undefined;
};

export const JUZ: readonly JuzRef[] = JUZ_STARTS.map(([s, a], index) => {
  const start = globalAyahOf(s, a);
  if (start === undefined) {
    throw new Error(
      `quranIndex: juz ${index + 1} starts at an invalid ayah ${s}:${a}`,
    );
  }
  const next = JUZ_STARTS[index + 1];
  const end = next
    ? (globalAyahOf(next[0], next[1]) ?? TOTAL_AYAHS + 1)
    : TOTAL_AYAHS + 1;
  return {
    number: index + 1,
    startSurah: s,
    startAyah: a,
    startGlobalAyah: start,
    ayahCount: end - start,
  };
});

/** Juz (1…30) that contains a coordinate, or `undefined` when out of range. */
export const juzOf = (
  surahNumber: number,
  ayah: number,
): number | undefined => {
  const global = globalAyahOf(surahNumber, ayah);
  if (global === undefined) return undefined;
  return JUZ.find(
    (j) =>
      global >= j.startGlobalAyah && global < j.startGlobalAyah + j.ayahCount,
  )?.number;
};

/** Every ayah of [surahNumber], in order. */
export const ayahsOfSurah = (surahNumber: number): AyahRef[] => {
  const row = surahByNumber.get(surahNumber);
  if (!row) return [];
  return Array.from({ length: row.ayahCount }, (_, i) => ({
    surah: row.number,
    ayah: i + 1,
    globalAyah: row.startGlobalAyah + i,
  }));
};

/** Every ayah of [juzNumber] (1…30), in order. */
export const ayahsOfJuz = (juzNumber: number): AyahRef[] => {
  const row = JUZ.find((j) => j.number === juzNumber);
  if (!row) return [];
  const ayahs: AyahRef[] = [];
  for (
    let g = row.startGlobalAyah;
    g < row.startGlobalAyah + row.ayahCount;
    g++
  ) {
    const ref = ayahAt(g);
    if (!ref) break;
    ayahs.push(ref);
  }
  return ayahs;
};

/**
 * Inclusive coordinate range. Handles the two directions (forward and backward) and a
 * range that straddles surahs, which is the normal case for a juz boundary.
 */
export const ayahsInRange = (
  fromSurah: number,
  fromAyah: number,
  toSurah: number,
  toAyah: number,
): AyahRef[] => {
  const start = globalAyahOf(fromSurah, fromAyah);
  const end = globalAyahOf(toSurah, toAyah);
  if (start === undefined || end === undefined) return [];
  const [lo, hi] = start <= end ? [start, end] : [end, start];
  const ayahs: AyahRef[] = [];
  for (let g = lo; g <= hi; g++) {
    const ref = ayahAt(g);
    if (ref) ayahs.push(ref);
  }
  return ayahs;
};

/**
 * Juz numbers that a coordinate range touches, in order. A juz-granularity edition can only
 * be downloaded whole, so a request for "surah 2" honestly resolves to juz 1–3.
 */
export const juzCoveringRange = (
  fromSurah: number,
  fromAyah: number,
  toSurah: number,
  toAyah: number,
): number[] => {
  const ayahs = ayahsInRange(fromSurah, fromAyah, toSurah, toAyah);
  if (ayahs.length === 0) return [];
  const found = new Set<number>();
  for (const ref of ayahs) {
    const juzNumber = juzOf(ref.surah, ref.ayah);
    if (juzNumber !== undefined) found.add(juzNumber);
  }
  return [...found].sort((a, b) => a - b);
};

// ---- load-time integrity ------------------------------------------------------------
const total = SURAHS.reduce((sum, s) => sum + s.ayahCount, 0);
if (total !== TOTAL_AYAHS) {
  throw new Error(
    `quranIndex: surah ayah counts sum to ${total}, expected ${TOTAL_AYAHS}`,
  );
}
const juzTotal = JUZ.reduce((sum, j) => sum + j.ayahCount, 0);
if (juzTotal !== TOTAL_AYAHS || JUZ.length !== TOTAL_JUZ) {
  throw new Error(
    `quranIndex: juz table covers ${juzTotal} ayahs in ${JUZ.length} juz`,
  );
}
