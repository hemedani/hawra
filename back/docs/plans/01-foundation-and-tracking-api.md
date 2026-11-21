# Plan 01 — Backend Foundation & Tracking API

> **This file is both the roadmap and the record.** Work is done by taking the first unchecked box,
> doing it, ticking it, and appending one entry to the [Progress log](#progress-log).
>
> **⚠️ Update this plan after EVERY small task.** Not at the end of a phase, not at the end of a
> session — after each small task: tick the box, log what changed, note any decision. A future agent
> (or you, after a break) must be able to read the last log entry and know exactly where things stand.
>
> A phase is not finished until its **"Done when"** line is verified — not when its boxes are ticked.
> Where this plan and the code disagree, **the code is right and this file is a bug.**

**Status:** Phase 0 **and** Phase 1 complete and verified live (server booted on 1407 against local
MongoDB; every act exercised over HTTP; 14 unit tests green). Plan authored 2026-11-12, Phase 1
added 2026-11-15.
**Sector:** `back/` · **Companion docs:** [`../summary.md`](../summary.md), [`../../AGENTS.md`](../../AGENTS.md)

---

## 0. How to use this document

| I want to… | Read |
|---|---|
| Know what exists and what it's for | [`../summary.md`](../summary.md) |
| Start work | [§3 Phases](#3-phases) — first unchecked box |
| Know the API contract | [`../summary.md §6`](../summary.md#6-acts-the-api-surface) |
| Continue after a break | [Progress log](#progress-log) — last entry |
| Check a convention | [`../../AGENTS.md`](../../AGENTS.md) |

**Rules of engagement**
1. One box at a time. If a box needs more than a session, split it and note the split in the log.
2. Never tick a box whose "Done when" you have not verified — run the gate, don't assume.
3. **Update this file after every small task** (tick + log entry). This is non-negotiable.
4. A decision that changes the architecture goes in [§2 Decisions](#2-decisions) with an ID.
5. Backend work is not "done" until `deno check mod.ts`, `deno lint`, and `deno fmt --check` pass.

## 1. Goal & scope

Stand up the Lesan backend that durably records every reading session the Android app syncs, using
the owner's established Lesan conventions (reference: Pejvak). Phase 0 = the scaffold + the three
core acts. Phase 1 = the recitation domain: user presets, per-preset coverage, sitting-level history,
and a reciter/edition catalogue that serves download manifests. Later phases grow settings sync,
stats, and a content-version manifest — **never the sacred text itself** (that stays on-device).

**In scope now:** `user` (device identity), `readingSession`, `reciter`, `recitationEdition`,
`preset`, `presetAyahProgress`, `presetSession` + their acts, auth utils, idempotent ingest,
LWW preset sync, monotonic coverage merge, manifest expansion.
**Out of scope now:** login/accounts, billing, admin, audio hosting/proxying, the whole Quran text.

## 2. Decisions

| ID | Decision | Rationale | Status |
|---|---|---|---|
| D1 | Anonymous **device identity** (`deviceId`), no login in Phase 0 | Frictionless start; one user row per device; account layer can be added later without touching sessions | resolved 2026-11-12 |
| D2 | Server stores **activity + references, never the Quran text** | Sacred-text integrity; offline-first; text is bundled on-device | resolved 2026-11-12 |
| D3 | Idempotency on client-generated `clientId` (unique sparse index) | A lost sync response must never create duplicates | resolved 2026-11-12 |
| D4 | Port **1407**, db `hawraa` | Avoid the owner's other project ports (1370/1404/1405/1406/8080/8090) | resolved 2026-11-12 |
| D5 | Timestamps = epoch **millis**, durations = integer **seconds** | Matches Pejvak; unambiguous wire contract | resolved 2026-11-12 |
| D6 | A **preset is a range-independent step recipe** (`ayah`/`translation`/`tafsir`/`silence`), not a fixed passage | One preset must work over any surah, juz or bookmark — the player applies the recipe at each ayah stop | resolved 2026-11-15 |
| D7 | Coverage is a **separate monotonic ledger** (`presetAyahProgress`), one row per (preset, ayah) | Coverage must survive preset edits and converge across devices; absolute values + `$max`/`$min` make retries safe | resolved 2026-11-15 |
| D8 | **One `presetSession` row per sitting**, with counters + a 200-event cap — play/pause increments, never inserts | Directly answers the brief: "must not create a new history entry every time the user presses play or pause" | resolved 2026-11-15 |
| D9 | Preset sync is **last-write-wins on `updatedAtMs`**, server bumps `revision`; stale edits are acknowledged but not applied | Multi-device editing without a lock; the client adopts the returned row | resolved 2026-11-15 |
| D10 | Editions describe audio as a **URL template + granularity**, and manifests are expanded server-side | Verse/surah/juz download share one code path; the client never learns a provider's layout | resolved 2026-11-15 |
| D11 | Manifests **refuse non-`verified` editions**; `availability` is a first-class field | Never hand the client a URL that 403s or 404s — honesty over a bigger catalogue | resolved 2026-11-15 |
| D12 | Quran structure (surah names, ayah counts, juz bounds) ships as **`content/quranIndex.ts`**, not a table | Immutable reference data; type-checked and testable offline; still contains no sacred text | resolved 2026-11-15 |
| D13 | Deleting a preset that has coverage or history **archives** it instead of removing the row | History must not dangle; the user sees it gone, the record stays intact | resolved 2026-11-15 |
| D14 | Shakernejad is modelled as **juz-granularity tartil** because no public per-ayah source exists | Probing found no per-ayah file for him anywhere reputable; a juz edition is honest about the real source | resolved 2026-11-15 |

## 3. Phases

### Phase 0 — Scaffold & core tracking API  ✅ complete

**Done when:** the server boots against MongoDB, and all three acts are exercised successfully
through the playground (register → sync → read history), with `check`/`lint`/`fmt` green.

- [x] `deno.json` (tasks, aliases, fmt/lint) + `mod.ts` entry point + Mongo wiring
- [x] `user` model (deviceId unique index) + `readingSession` model (clientId unique sparse index, user relation)
- [x] `models/mod.ts` barrel + `excludes.ts`
- [x] Auth utils: `token`, `setTokens`, `setUser`, `context`, `throwError`, `createUpdateAt`, `pagination`, `projection`
- [x] `user/registerDevice` act (val + fn + setAct), idempotent on deviceId
- [x] `readingSession/syncReadingSessions` act (val + fn + setAct), batched + idempotent, preAct auth
- [x] `readingSession/getMyReadingHistory` act (val + fn + setAct), paginated, preAct auth
- [x] `src/mod.ts` + domain setups wired; `Dockerfile`, `.env.example`, `uploads/`
- [x] `deno check mod.ts` + `deno lint` + `deno fmt --check` all pass
- [x] **Boot against MongoDB** (`deno task dev`) — listens on 1407, playground loads
- [x] **Smoke test** register → sync → history over HTTP; idempotent re-sync creates no duplicates
- [ ] Add a committed HTTP/e2e suite (hurl or JSON) covering the three acts + the duplicate-sync case

### Phase 1 — Recitation domain  ✅ complete (2026-11-15)

**Goal:** presets, per-preset coverage, sitting-level history, and a reciter/edition catalogue that
serves download manifests.

**Done when:** a user can create several independent presets, play them, accumulate per-preset
coverage, see one history row per sitting (not per button press), and obtain a flat, verified file
list for verse/surah/juz download.

- [x] `content/quranIndex.ts` — 114 surahs, 30 juz, 6236 ayahs; `globalAyahOf`, `ayahAt`, `juzOf`,
      `ayahsOfSurah`, `ayahsOfJuz`, `ayahsInRange`, `juzCoveringRange`; load-time assertions
- [x] `content/quranIndex_test.ts` — 7 tests green
- [x] `reciter` model (unique `key`) + `recitationEdition` model (unique `key`, `urlTemplate`, `availability`)
- [x] `preset` model (`presetStep_pure` + `preset_pure`, unique sparse `clientId`, `revision`, `updatedAtMs`)
- [x] `presetAyahProgress` model (both relations `relatedRelations: {}`)
- [x] `presetSession` model (`presetEvent_pure`, counters, capped `events[]`, optional preset relation)
- [x] `utils/ensureIndexes.ts` — compound + the unique coverage index (Lesan allows one `createIndex` per model)
- [x] `utils/throwError.ts` converted to `export function` so TS narrows `never`
- [x] `src/recitationEditions/buildManifest.ts` (+ 7 tests) — placeholder fill, zero-padded `{surah3}`/`{juz2}`, scope resolution
- [x] `reciter/gets`, `reciter/get` — catalogue with editions embedded
- [x] `recitationEdition/gets`, `get`, `getDownloadManifest` — refuses non-`verified`
- [x] `preset/add|gets|get|update|remove|count|setDefault|syncPresets|getMyCoverage`
- [x] `presetSession/start|logEvent|finalize|getMyPresetHistory|count`
- [x] `presetAyahProgress/syncProgress|getPresetParts`
- [x] `seed.ts` — idempotent catalogue seed with a documented verification table; 5 reciters, 6 editions
- [x] `deno.json` tasks: `seed`, `test`; `content/` + `seed.ts` in fmt/lint includes
- [x] **Verified live:** LWW both directions, 6 events → 1 history row, `inserted: 7` then `merged: 7`,
      coverage 7/6236 with surah rollup, ranges coalesced to 1:1–1:7, Shakernejad juz manifest (juz 01–03, 286 ayahs)
- [ ] Commit the smoke script as a real e2e suite

### Phase 2 — Settings sync & content manifest  *(next)*

**Done when:** the client can push/pull shared settings (LWW) and read a content-version manifest;
acts type-check and are smoke-tested.

- [ ] `user/updateMySettings` + `user/getMySettings` (LWW on `setting.updatedAt`, merge not clobber)
- [ ] Content-version manifest act (returns which content bundle version is current — **references only**)
- [ ] Decide account/login direction (stay anonymous vs. optional login) — record as a Decision

### Phase 3 — History quality & stats

**Done when:** history reads support the mobile History screen's needs; basic aggregates available.

- [ ] `getMyReadingHistory` filters (by surah, by mode, date range) as the UI requires
- [ ] Stats act(s): total reading time, sessions count, per-surah coverage, streaks
- [ ] Cross-domain stat: reading time + preset listening time in one rollup
- [ ] Retention/quota policy decision (Pejvak skips overflow newest-first; adopt or adapt)

### Phase 4 — Hardening

- [ ] Rate limiting / abuse guards on ingest
- [ ] Index review for history query patterns
- [ ] Backup/restore + migration story for schema changes
- [ ] Re-probe catalogue URLs periodically and flip `availability` when a provider moves a file

## 4. Verification gates (run before ticking any box)

```sh
cd back
deno check mod.ts && deno lint && deno fmt --check && deno task test
# For runtime boxes: deno task dev, then exercise the act via playground/curl.
```

## 5. Conventions reminder

Validator before implementation; register every model/act in the barrels; one-directional relations;
`token` header (no Bearer); `clientId` idempotency; never commit `declarations/` or `.env`; never
`git reset`; commit only when asked. Full rules: [`../../AGENTS.md`](../../AGENTS.md).

---

## Progress log

> Append one entry per small task. Newest at the bottom. Format: date — what changed — verification.

- **2026-11-12** — Phase 0 scaffold authored: entry point, `user` + `readingSession` models, auth
  utils, and the three acts (`registerDevice`, `syncReadingSessions`, `getMyReadingHistory`), wired
  through `src/mod.ts`. Added `Dockerfile`, `.env.example`, `uploads/`.
  **Verified:** `deno check mod.ts`, `deno lint`, `deno fmt --check` all pass. **Not yet run** against
  MongoDB — boot + smoke test is the next unchecked box.

- **2026-11-15** — **`AGENTS.md` rewritten** with a full "The Lesan framework" reference section
  (boot sequence, model API surface, field-validator table, relation rules incl. the
  `relatedRelations: {}` requirement, act/validator rules, index limits, auth/context, error shape).
  Reason: new schemas were about to be added and the conventions had been re-derived painfully
  several times. This is now the authoritative reference; it also records that
  `validationRunType` accepts only `"create" | "assert"` and that `findOneAndUpdate` requires
  `projection`.

- **2026-11-15** — Phase 0 boot + smoke test **passed**. Server listens on 1407; Lesan answers at
  **`/lesan`** (not `/` — that cost an hour). Register → sync → history verified over HTTP, including
  an idempotent re-sync that created no duplicates.
  **Lesson recorded:** a 501 with an opaque body was an `E11000 duplicate key` from a reused
  `clientId`; smoke scripts must use per-run unique ids and must print the response body on error.

- **2026-11-15** — **Reciter research done by probing, not assumption.**
  *Parhizgar* per-ayah `cdn.islamic.network/quran/audio/48/ar.parhizgar/{ayah}.mp3` → 206 audio/mpeg.
  *Minshawi* per-ayah `…/128/ar.minshawi/{ayah}.mp3` → 206, per-surah
  `server10.mp3quran.net/minsh/{surah3}.mp3` → 206 (redirects to `cdn.mp3quran.net`).
  *Shakernejad* has **no public per-ayah source**: cdn.islamic.network 403 for every slug spelling,
  Quran.com v4 lists 12 reciters (not him), mp3quran's 244 omit him, everyayah's 177 dirs omit him,
  `everyayah/ShakerNejad_128kbps` 404. Working source found and verified:
  `dl.meshkatsound.com/Tartil/Hamed-Shakernejad/Shakernejad-Quran-Juz-{juz2}.mp3` → 206 for juz
  01/15/30. **Therefore his edition is juz-granularity tartil** (D14) — honest about the source
  instead of fabricating a per-ayah template.

- **2026-11-15** — Phase 1 models + content + acts authored (see the Phase 1 checklist). Static gates
  green: `deno check`, `deno lint`, `deno fmt --check`, and `deno test --allow-all content/ src/` →
  **14 passed, 0 failed**.
  **Errors fixed along the way:** `relatedRelations` missing on two high-cardinality relations;
  `throwError` as an arrow const blocked TS narrowing → converted to `export function … : never`;
  `validationRunType: "update"` rejected → all five sites use `"create"`; `findOneAndUpdate` missing
  `projection`; missing value imports for `string`/`number` in two validators; spreading
  `object(patch)` into a validator → split into `patchFields` + `object(patchFields)`.

- **2026-11-15** — **Phase 1 verified end-to-end against live MongoDB.** `deno task seed` →
  `5 reciters, 6 editions`. Full HTTP smoke run:
  preset add (first becomes default, second does not) → `syncPresets` stale stamp `synced: 0` /
  newer stamp `synced: 1` with `revision` bump (LWW proven both ways) → `presetSession/start` +
  **6 events** + `finalize` → **exactly 1 history row** with `playCount: 2`, `pauseCount: 2`,
  `events: 6` (D8 proven) → `syncProgress` twice → `inserted: 7` then `merged: 7` (idempotent, D7) →
  `getMyCoverage` → 7/6236, `percent: 0.1`, surah 1 rollup → `getPresetParts` → one coalesced range
  1:1–1:7 → Shakernejad juz manifest for al-Baqarah → juz 01/02/03, 286 ayahs.
  **Backend Phase 1 is done**; next work is the mobile app.

- **2026-11-15** — **Audio mirror (owner request: "a method on backend to download and setup Telavat
  audios").** New `audioFile` model + `src/recitationAudio/` engine + `deno task audio-mirror` +
  `getAudioMirrorStatus` / `prepareAudio` acts; `getDownloadManifest` now adds `bytes` and
  `mirrorPath` per stored file. Gates: `deno check` (124 files), `deno lint` clean, **37 tests**
  (23 new). Live: `--edition=parhizgar-murattal-ayah-48 --scope=surah:1` → 7 files, 320 384 bytes,
  verified `MPEG ADTS, layer III, 48 kbps`; a second run skipped all 7 in 0.8 s; the manifest then
  reported `mirroredFileCount: 7` with `bytes: 67712` matching the file on disk; a ranged GET of
  `bytes=60000-` returned exactly 7712 bytes.

  **Three findings worth keeping:**
  1. **`deno run --watch` watches the working directory, not just the module graph** — proven by the
     existing `--watch-exclude=declarations` (generated files are not in the graph). Mirroring into
     `uploads/` therefore restarted the server once per file. `--watch-exclude=uploads` is now
     load-bearing, not cosmetic.
  2. **Lesan's static handler drops every response header except `Content-Type`.** It builds the
     response with `{...fileResponse.headers}`, which is a no-op on a `Headers` instance, so
     `Content-Length`/`Content-Range`/`Accept-Ranges` are lost and audio is served `chunked`. Ranges
     still work; the *length* does not. Fix belongs upstream (`new Headers(fileResponse.headers)`);
     until then the manifest's measured `bytes` is what lets a client detect a truncated file.
  3. **A tool that imports `mod.ts` must call `Deno.exit`.** The Mongo pool keeps the event loop
     alive, so the command printed its summary and then hung until killed — a successful run that
     looks like a crash (exit 137, empty stdout once the pipe was gone).

  **Recorded as a deliberate amendment to D2**, which previously read "never … audio bytes": the
  mirror is a cache of files the catalogue already points at, written only by an explicit command or
  a token-guarded act, never committed, and never required — a manifest without it is still valid.
  Text rules are unchanged.
- **2026-11-15** — **Accounts (owner request: "we don't have login or register on the mobile app").**
  New `utils/password.ts` (PBKDF2-HMAC-SHA256, 210 000 iterations, per-row salt + iteration count,
  constant-time compare, `normalizeEmail`, loose email test, `MIN_PASSWORD_LENGTH = 8`), three public
  acts `user/register` / `user/login` / `user/me`, and `src/users/shared.ts` (`sessionResponse`) so
  register and login cannot drift in shape. `user` gained `email` (unique + sparse), the three
  password fields and `lastLoginAt`; `deviceId` became optional. Gates: `deno check` (136 files),
  `deno lint` clean, `deno fmt` applied, `deno test --allow-all content/ src/ utils/` → **45 passed**.
  Live: register (`Syd@Example.com` → `syd@example.com`), duplicate, short password, bad email,
  login with case/whitespace, wrong password and unknown address answering identically, `me` with a
  good and a garbage token, and an account token accepted by `preset/gets` and
  `audioFile/getAudioMirrorStatus`.

  **Two findings worth keeping:**
  1. **Lesan creates a model's `createIndex` during `newModel`, before `ensureIndexes` runs.** Changing
     an index in place therefore aborts boot with *"an existing index has the same name as the
     requested index"* — the model's own index had to move to the new `email` field, and
     `ensureUniqueSparse` drops-then-recreates `deviceId_1` as sparse. A *new* field is safe; a
     *changed* one is not.
  2. **`crypto.subtle.deriveBits` needs a non-shared buffer.** `Uint8Array<ArrayBufferLike>` is not
     assignable to `BufferSource`; `fromBase64` allocates on a fresh `ArrayBuffer` and returns
     `Uint8Array<ArrayBuffer>`.

  `registerDevice` stays as a legacy act so an old client does not 500, but nothing in the app calls
  it any more: the gate requires a real account.

- **2026-11-15** — The mirror got its first real payload: **the first five mushaf pages, for both
  Persian reciters.** Nothing in `src/` changed; this was a data operation whose value is that it
  turned a URL template into measured bytes.

  ```sh
  deno task audio-mirror --edition=parhizgar-murattal-ayah-48 --scope=range:1:1-2:29
  #   → 36 files, 29 stored + 7 already present, 3.4 MB, 0 failed
  deno task audio-mirror --edition=shakernejad-tartil-juz-24 --scope=juz:1
  #   → 1 file, 12.1 MB stored
  ```

  **Why `range:1:1-2:29` is the first five pages.** The wire has no page scope and should not grow
  one — a page is a property of the mushaf text the client ships. Resolved against the bundled
  pages, pages 1–5 are 1:1–1:7, 2:1–2:5, 2:6–2:16, 2:17–2:24, 2:25–2:29: **36 ayahs**, all inside
  **juz 1**. So the owner's two requirements ("first five pages" and "Shakernejad, who is juz-only")
  describe the same download, and a juz edition answers `juz:1` for that range rather than pretending
  to serve 36 separate files.

  **The failure that was not a mirror bug.** Shakernejad's host answered
  `invalid peer certificate: certificate expired: verification time 1794751676, but certificate is
  not valid after 1794579031`. Rather than assume a broken mirror, the payload was checked on its own
  (`HTTP/2 200`, `content-type: audio/mpeg`, `content-length: 12651123`) and the clock compared with
  the certificate's `notAfter` (2026-11-13 vs. today 2026-11-15): genuinely expired upstream. The
  run then used `--unsafely-ignore-certificate-errors=dl.meshkatsound.com` — **scoped to that host,
  never globally**, because a global bypass would silently accept a real MITM on every other fetch.
  The upstream certificate still needs renewing; recorded in `AGENTS.md` §5.4.

  **Verified live**, against a probe server on a spare port (`APP_PORT=1408 deno run --allow-all
  mod.ts`): `range 1:1-2:29` → `ayahCount 36, fileCount 36, mirroredFileCount 36`, e.g.
  `ayah:1:1` = 67,712 bytes at `/uploads/recitation/parhizgar-murattal-ayah-48/ayah/001/001.mp3`;
  `juz 1` → `ayahCount 148, fileCount 1`, `juz:1` = 12,651,123 bytes at
  `/uploads/recitation/shakernejad-tartil-juz-24/juz/01.mp3`. Static serving confirmed for both
  (`200`, exact sizes). The 12.65 MB / ~70 min measurement also calibrated the client's
   `NOMINAL_JUZ_SECONDS`, which had been 40 % low.

- **2026-11-16** — **D27: a general verse-tied media model, in Lesan.** The owner's direction: the
  app should "have anything we want" — tilāwa plus **ترجمه‌گویا** (spoken translation) and
  **تفسیرگویا** (spoken tafsir), and later per-verse **video/images** — and it should all be served
  from **Lesan** (not a separate media server), set up once, so "we get everything from the backend".
  This is the schema + plumbing slice; the mobile consumption is the next step.
  1. **New model `mediaEdition`** (`models/mediaEdition.ts`): the reciter-centric
     `recitationEdition` fits tilāwa but not media that merely *addresses* verses. So this carries
     `contentType` (recitation|translation|tafsir|video|image), `granularity` (ayah|surah|juz),
     `urlTemplate`, `fileExtension`, `language`, `textKey` (which translation/tafsir it narrates —
     a KEY, never the text), `narrator`, `availability` (default **unverified**), `isDefault`, etc.
     No relations. Registered in `models/mod.ts`, instantiated in `mod.ts`, unique index on `key`.
     D2 still holds: references + coordinates only, never the written translation/tafsir content —
     guya is *audio that speaks* a text, stored like the recitation mirror exception (§5.4).
  2. **The machinery was already general; only the lookup was recitation-specific.** `buildManifest`
     and `resolveScope` are pure over `{key, granularity, urlTemplate, fileExtension}`, and
     `audioFile` keys by `editionKey` (a string), so a media edition flows through the *same*
     manifest + mirror path. Two extractions made that explicit and DRY:
     - `src/recitationAudio/edition.ts` — `resolveEditionDescriptor(key)`: recitation first, then
       media; one place that knows an edition may live in either catalogue.
     - `src/recitationAudio/manifest.ts` — `filesWithMirrorInfo(editionKey, files)`: the
       `audioFile` bytes+mirrorPath merge, now the single source of truth. The existing
       `recitationEditions/getDownloadManifest` was refactored to call it (no behaviour change).
  3. **New `mediaEditions` domain** (`src/mediaEditions/`): `gets` (catalogue read, filters by
     contentType/granularity/availability/textKey) and `getDownloadManifest` (resolve mediaEdition,
     refuse non-verified, reuse `buildManifest` + `filesWithMirrorInfo`). Registered via
     `mediaEditionsSetup()` in `src/mod.ts`. Both authenticated (`setTokens, setUser`), like the
     recitation catalogue.
  4. **Mirror tool generalised** (`tools/mirrorAudio.ts`): resolves the edition through
     `resolveEditionDescriptor`, so `deno task audio-mirror --edition=<guya key> --scope=…` works for
     media editions too. Log line now prints `source` + optional bitrate.
  5. **Seed placeholders** (`seed.ts`): `tarjomeh-guya-ansarian-ayah` and
     `tafsir-guya-qaraati-ayah`, **server-relative** templates
     (`/uploads/recitation/<key>/ayah/{ayah}.mp3`) and `availability: "unverified"`. The **drop-file
     contract** is documented in the file: place per-global-ayah mp3s at
     `uploads/recitation/<key>/ayah/<global>.mp3`, then flip to `verified` — the static handler serves
     them and the manifest hands the device the URL (no mirror rows required; mirroring only adds
     bytes+mirrorPath). Re-seed preserves an owner's `verified`: `availability` is written on insert
     and on update only backfilled when a row never had it (a raw `insertOne` bypasses `defaulted`,
     so it must be set explicitly — caught live when the manifest first reported `availability:
     undefined`).
  **Verified:** `deno check mod.ts seed.ts tools/mirrorAudio.ts` clean; `deno lint` + `deno fmt
  --check` clean (146 files); `deno test` **45 passed / 0 failed**. **Exercised over HTTP** against a
  live server on a spare port (`APP_PORT=1499`) with a real account token: `mediaEdition/gets`
  returns both guya editions; `mediaEdition/getDownloadManifest` correctly refuses
  *"is unverified — no manifest is served"*; the **recitation** manifest (the refactor's control)
  still returns a full manifest; `deno task audio-mirror --edition=tarjomeh-guya-…` resolves the
  media key and refuses on unverified (a bogus key reports *"unknown edition"*), proving the
  descriptor resolution. `deno task seed` re-run is idempotent (updated, not duplicated).
  **Next:** D27b — the mobile consumes `mediaEditions` (catalogue read + guya audio through the
  preset provision/download path), so a preset's translation/tafsir steps play real guya audio
  downloaded verse-by-verse, not TTS.

- **2026-11-16** — **Shakernejad verse-by-verse is real: Inoor source located + mirrored.** The owner
  was right that a per-ayah Shakernejad source exists (my earlier "juz-only" note was wrong — it only
  covered the foreign CDNs). Found Inoor's API by reading the Nuxt bundle:
  - `GET https://apiquran.inoor.ir/api/quran/ListRecitor?language=fa&surahId=N` → reciter id↔name
    (**Shakernejad = 1014**, Qaraati/tafsir-guya = 1015, Parhizgar = 30, Minshawi = 2, Husary = 10).
  - `GET …/api/quran/RecitationList?recitorId=ID&surahId=N` → per-ayah `{FileUrl, Size, …}`.
  - audio = `https://apiquran.inoor.ir` + `FileUrl` = `/File/Sound/Recitation/<id4>/<surah3><ayah3>.mp3`
    (in-surah ayah, zero-padded — NOT the global number).
  1. **`buildManifest` gained `{ayah3}`** (in-surah padded ayah) alongside `{ayah}` (global), and the
     ayah-granularity gate now accepts either — so a template can address a verse the way Inoor names
     it. Pure change; `buildManifest_test` still green.
  2. **New edition** `shakernejad-murattal-ayah-1014` (granularity ayah, Inoor template,
     `availability: verified`, `isDefault: true`); the juz edition lost `isDefault`. Re-seeded
     (idempotent).
  3. **Mirrored the first five pages** from Inoor: `deno task audio-mirror
     --edition=shakernejad-murattal-ayah-1014 --scope=range:1:1-2:29` → **36/36 stored, 3.2 MB, 0
     failed**, into `uploads/recitation/<key>/ayah/<surah3>/<ayah3>.mp3` + `audioFile` rows.
  **Verified:** `deno check mod.ts seed.ts tools/mirrorAudio.ts` + `lint` + `fmt --check` clean;
  `deno test` 45/45; **live over HTTP** — the backend serves a mirrored file (`HTTP 200`,
  `audio/mpeg`, 31,575 bytes) and `getDownloadManifest` for ayah 1:1 returns the Inoor `url`
  (`…/1014/001001.mp3`), the backend `mirrorPath` (`/uploads/recitation/…/ayah/001/001.mp3`), and exact
  `bytes`. **Note for the owner:** the macOS application firewall was blocking the phone from reaching
  Deno on `:1407` (Deno not in the allow-list) — add it to restore Downloads/offline from the backend;
  verse-by-verse Shakernejad now also streams straight from Inoor, which needs no backend.
