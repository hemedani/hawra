import { coreApp } from "../mod.ts";
import { boolean, defaulted, enums, number, optional, string } from "lesan";
import { createUpdateAt } from "../utils/createUpdateAt.ts";

/**
 * A **media edition** tied to the verses — the general sibling of `recitationEdition`.
 *
 * `recitationEdition` is reciter-centric: it always belongs to a `reciter`, and its "style"
 * is a recitation style. That fits *tilāwa* but not the other spoken/visual media the owner
 * wants in the same backend — **ترجمه‌گویا** (a narrator reading the translation verse by
 * verse), **تفسیرگویا** (a narrated exegesis), and later per-verse **video** and **image**
 * content. Those are not recitations by a قاری; they are media addressed to the same verse
 * coordinates. This model carries them.
 *
 * The download machinery does not care which model an edition came from: `buildManifest`
 * works off `{key, granularity, urlTemplate, fileKey}` and the mirror records files in
 * `audioFile` keyed by `editionKey` (a string). So a media edition flows through the *same*
 * manifest + mirror + storage path as a recitation edition — see
 * `src/recitationAudio/edition.ts` (the one place that resolves an edition by key from either
 * model). One contract, two catalogue shapes.
 *
 * Scope rule (D2, amended for the mirror): this stores **references and coordinates, never
 * the sacred text or a translation's/tafsir's written content.** Guya *audio* is a recording
 * that happens to speak a translation; the server holds the audio file and the key of the text
 * it narrates (`textKey`), not the text itself. Video/image are referenced the same way.
 */
export const mediaEdition_pure = {
  /** Stable slug. Unique. e.g. "tarjomeh-guya-ansarian-ayah". */
  key: string(),
  /**
   * What kind of media this is. `recitation` is accepted for future folding, but tilāwa lives
   * in `recitationEdition` today; this model's reason to exist is the rest.
   */
  contentType: enums(["recitation", "translation", "tafsir", "video", "image"]),
  labelFa: string(),
  labelEn: string(),
  /**
   * One file per ayah, per surah, or per juz — the same granularity contract as recitation.
   * Verse-tied video/image use `ayah` (one item per verse). Decides the legal urlTemplate
   * placeholder; honoured literally by `buildManifest`.
   */
  granularity: enums(["ayah", "surah", "juz"]),
  /**
   * A template with exactly one placeholder matching `granularity`: `{ayah}` (1…6236),
   * `{surah}`/`{surah3}`, `{juz}`/`{juz2}`. A template beginning with `/` is server-relative
   * and resolves against the app's base URL — which is how a pre-placed mirror is served.
   */
  urlTemplate: string(),
  /** mp3 for guya audio; mp4/webm for video; jpg/png for images. */
  fileExtension: defaulted(string(), "mp3"),
  /** BCP-47 of the *spoken/shown* content, e.g. "fa". Quran recitation is "ar". */
  language: optional(string()),
  /**
   * Which text this media narrates or illustrates, in the same vocabulary as the app's
   * content keys (`fa.ansarian`, `al-mizan`, `tafsir-guya`). A *key*, never content — this is
   * what ties a tafsir-guya track to the exegesis it reads without storing the exegesis here.
   */
  textKey: optional(string()),
  /** Who speaks a translation/tafsir track (e.g. the narrator's name). Free text, not a reciter. */
  narrator: optional(string()),
  /** Audio bitrate where meaningful; absent for image/video. */
  bitrateKbps: optional(number()),
  /** 6236 for ayah editions, 114 for surah, 30 for juz. */
  totalFiles: defaulted(number(), 6236),
  /**
   * `verified` means every file was probed and is offered for download. **Default is
   * `unverified`**: a guya edition is seeded as a placeholder the owner must drop files for and
   * verify before the app offers it — an unverified source is never handed out as a manifest.
   */
  availability: defaulted(
    enums(["verified", "unverified", "blocked"]),
    "unverified",
  ),
  sourceName: string(),
  sourceUrl: optional(string()),
  licenseNote: optional(string()),
  /** ISO date (YYYY-MM-DD) of the last probe of every file. */
  verifiedAt: optional(string()),
  notesFa: optional(string()),
  notesEn: optional(string()),
  /** The edition the app should preselect for this content type. */
  isDefault: defaulted(boolean(), false),
  ...createUpdateAt,
};

/** No relations: a media edition is addressed by its own key, never by a reciter document. */
export const mediaEdition_relations = {};

export const mediaEditions = () =>
  coreApp.odm.newModel(
    "mediaEdition",
    mediaEdition_pure,
    mediaEdition_relations,
    {
      createIndex: {
        indexSpec: { key: 1 },
        options: { unique: true },
      },
    },
  );
