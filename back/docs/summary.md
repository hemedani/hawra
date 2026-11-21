# Hawraa Backend — Summary

> **Sector:** `back/` · **Framework:** Lesan (the owner's own) on Deno 2 + MongoDB
> **Read this first** when picking up backend work with any agent. Then read
> [`../AGENTS.md`](../AGENTS.md) (rules), [`plans/01-foundation-and-tracking-api.md`](plans/01-foundation-and-tracking-api.md)
> (what to do next), and [`prompts/01-initial-scaffold.md`](prompts/01-initial-scaffold.md) (why).
>
> **Plan discipline:** the plan file is a living record. **Update it after every small task** —
> tick the box, append a progress-log entry. See the plan's rules of engagement.

---

## 1. What this backend is for

Hawraa (حوراء) is an Android-only Quran app with two pillars: exceptional readability and
**complete per-user activity tracking**. The backend's job is the second pillar: durably store
every reading session (when, which ayah range, which settings) that the mobile app records
offline-first and syncs up. It is **not** a content server — the sacred Quran text, translation
and audio live on the device; the server stores references and activity, never the text itself.

The backend is built on **Lesan**, the owner's own framework. Conventions are inherited from his
existing Lesan projects (Pejvak, lesanSatek, ziwound, nejayat) — **match those repos, do not invent
new patterns.** Pejvak is the closest reference because it solves the same problem (offline-first
client + append-heavy session ingest + idempotent batched sync).

## 2. Current status (Phase 1 — recitation domain)

**Phase 0 and Phase 1 both complete and verified live.** `deno check mod.ts`, `deno lint`,
`deno fmt --check` all pass; `deno test --allow-all content/ src/` → **37 passed, 0 failed**.
The server has been booted on port 1407 against local MongoDB and every act has been exercised
end-to-end over HTTP.

| Area | State |
|---|---|
| Entry point, Mongo wiring, model instantiation | ✅ done |
| `user` + `readingSession` models | ✅ done |
| Auth utils (JWT, setTokens, setUser, context) | ✅ done |
| `reciter` + `recitationEdition` catalogue (+ `seed.ts`) | ✅ done, seeded |
| `preset` + `presetAyahProgress` + `presetSession` models | ✅ done |
| `content/quranIndex.ts` (114 surahs / 6236 ayahs / 30 juz) | ✅ done, 7 tests |
| Accounts (`register`, `login`, `me`; PBKDF2; 8 tests) | ✅ done, smoke-tested live |
| Acts: reading tracking (`registerDevice`, sync, history) | ✅ done, smoke-tested |
| Acts: presets (add/gets/get/update/remove/count/setDefault/sync/coverage) | ✅ done, smoke-tested |
| Acts: preset sessions (start/logEvent/finalize/history/count) | ✅ done, smoke-tested |
| Acts: progress (syncProgress/getPresetParts) | ✅ done, smoke-tested |
| Acts: reciters + editions + download manifest | ✅ done, smoke-tested |
| Audio mirror (`audioFile`, engine, `audio-mirror`, 2 acts) | ✅ done, 23 tests, **37 files mirrored live** |
| HTTP/e2e test suite (committed, not ad-hoc) | ⬜ pending |
| Settings LWW sync, aggregate stats | ⬜ later phases |

### The one behaviour that matters most

> "Activity associated with these presets should be logged as history; however, this should not
> create a new history entry every time the user presses play or pause."

Verified: a session that received **6 events** (`play, step, ayah, pause, play, pause`) produced
**exactly one** `presetSession` row with `playCount: 2`, `pauseCount: 2`, `events: 6`. The
granularity lives inside the row, never as new rows.

## 3. Tech stack (pinned)

| Piece | Choice | Notes |
|---|---|---|
| Runtime | Deno 2.8.x (on PATH) | strict TypeScript, no `any` |
| Framework | `jsr:@hemedani/lesan@^0.2.3` | imported as `lesan` |
| Database | MongoDB | default `mongodb://127.0.0.1:27017/`, db `hawraa` |
| JWT | `https://deno.land/x/djwt@v2.8/mod.ts` | HS512, 90-day expiry |
| Aliases | `@lib` → `utils/mod.ts`, `@model` → `models/mod.ts` | |

## 4. File map

```
back/
├── AGENTS.md                     # sector rules (authoritative for conventions)
├── mod.ts                        # ENTRY POINT: coreApp=lesan(); Mongo connect; models; functionsSetup(); runServer
├── deno.json                     # tasks (dev/start/check/lint/fmt), fmt/lint config, import aliases
├── deno.lock                     # generated lockfile (commit it)
├── Dockerfile                    # denoland/deno:2.1.4, dev + production stages, EXPOSE 1407
├── .env.example                  # MONGO_URI, DB_NAME, APP_PORT, ENV, TOKEN_KEY
├── docs/                         # this summary + plans/ + prompts/
├── seed.ts                       # idempotent catalogue seed (reciters + editions) — `deno task seed`
├── content/                      # STATIC reference data (never fetched at runtime)
│   ├── quranIndex.ts             # 114 surahs, 30 juz, 6236 ayahs; globalAyahOf/ayahAt/juzOf/…
│   └── quranIndex_test.ts        # load-time + unit assertions
├── models/
│   ├── mod.ts                    # barrel: user, readingSession, reciter, recitationEdition, preset, presetAyahProgress, presetSession
│   ├── excludes.ts               # user/session/preset/edition excludes (relation field exclusions)
│   ├── user.ts                   # user_pure + setting_pure + users() factory (unique deviceId index)
│   ├── readingSession.ts         # readingSession_pure + readingSetting_pure + relations + factory
│   ├── reciter.ts                # reciter_pure (unique `key`) + relations
│   ├── recitationEdition.ts      # recitationEdition_pure (unique `key`, urlTemplate) + reciter relation
│   ├── preset.ts                 # presetStep_pure + preset_pure (unique sparse clientId) + user relation
│   ├── presetAyahProgress.ts     # coverage ledger row per (preset, ayah) — both relations `relatedRelations: {}`
│   └── presetSession.ts          # presetEvent_pure + presetSession_pure (counters + capped events[])
├── src/
│   ├── mod.ts                    # functionsSetup() → users, sessions, reciters, recitationEditions, presets, presetSessions, presetProgress
│   ├── users/                    # register, login, me, registerDevice (legacy) + shared.ts (sessionResponse)
│   ├── sessions/                 # syncReadingSessions, getMyReadingHistory
│   ├── reciters/                 # gets, get
│   ├── recitationEditions/       # gets, get, getDownloadManifest, buildManifest.ts (+ _test.ts)
│   ├── presets/                  # add, gets, get, update, remove, count, setDefault, syncPresets, getMyCoverage
│   │                             # + presetInput.ts (shared field sets), shared.ts (newClientId/clearDefaultPresets/requirePreset)
│   ├── presetSessions/           # start, logEvent, finalize, getMyPresetHistory, count
│   └── presetProgress/           # syncProgress, getPresetParts
├── utils/
│   ├── mod.ts                    # @lib barrel
│   ├── context.ts                # MyContext (LesanContenxt + user)
│   ├── createUpdateAt.ts         # createdAt/updatedAt mixin (import directly in models, not via @lib)
│   ├── token.ts                  # jwtTokenKey (HS512) + createToken (90d)
│   ├── setTokens.ts              # preAct: verify `token` header → context.user
│   ├── setUser.ts                # preAct: load user by _id, reject deleted, default role/status
│   ├── ensureIndexes.ts          # compound + unique indexes created at boot (Lesan allows only one createIndex per model)
│   ├── throwError.ts             # `export function throwError(msg: string): never` — must be a function, not an arrow const, or TS won't narrow
│   ├── pagination.ts             # page/limit/skip validators
│   └── projection.ts             # projectionOrPure(get, schema)
├── declarations/                 # GENERATED by typeGeneration — never edit, never commit
└── uploads/                      # static files (staticPath: ["/uploads"])
```

**`content/` is code, not a database table.** The Quran index is immutable reference data; shipping
it as a TS module makes it type-checked, testable offline, and impossible to drift at runtime. It
contains *structure only* — surah names, ayah counts, juz boundaries. **Never the sacred text.**

## 5. Data model

### `user`

A **reader account**: email + password, and the owner of every row in the other collections. The
mobile app gates on it — nothing is written until someone is signed in, because history recorded
before sign-in belongs to nobody.

| Field | Type | Notes |
|---|---|---|
| `email` | string | **unique sparse index**; normalised (trim + lowercase) before it is stored |
| `passwordHash` / `passwordSalt` / `passwordIterations` | string/string/number | PBKDF2-HMAC-SHA256, 210 000 iterations, 16-byte salt per row; the count is stored so the cost can be raised later |
| `deviceId` | string? | legacy: the anonymous device row this account was created from. Was unique, is not any more — two accounts may sign in on one phone |
| `displayName` | string? | optional; the UI falls back to the local part of the email |
| `setting` | object? | `languageCode`, `theme` (light/dark/system), `fontSize`, `updatedAt` (LWW millis) |
| `role` | enum | user/admin, default user |
| `status` | enum | active/suspended/deleted, default active |
| `lastLoginAt` | date? | written by `login`, not by `me` |
| `createdAt`/`updatedAt` | date | server bookkeeping |

### `readingSession`
One reading session. **Append-only after finalize.** Timestamps are epoch **milliseconds**;
durations are integer **seconds**.

| Field | Type | Notes |
|---|---|---|
| `clientId` | string? | client UUID, **idempotency key**, unique sparse index |
| `startedAt` / `endedAt` | number / number? | epoch millis; `endedAt` set = finalized |
| `surah` / `fromAyah` / `toAyah` | number | 1-based, inclusive range |
| `fromPage` / `toPage` | number? | mushaf page range (Madani 604-page) |
| `durationReadSec` | number | integer seconds, default 0 |
| `completed` / `interrupted` | boolean | default false |
| `mode` | string | "read" (silent) or "listen" (recitation), default "read" |
| `deviceInfo` | string? | |
| `setting` | object? | snapshot at open time: languageCode, theme, fontSize, translationKeys[], reciterKey, playbackSpeed |

**Relation:** `user` (single, embedded, `relatedRelations.readingSessions` limit 50 desc). Filter on
`user._id`; never denormalize `userId` into pure fields. One-directional relation — Lesan creates the reverse.

### `reciter` — catalogue, global (not per-user)

| Field | Type | Notes |
|---|---|---|
| `key` | string | natural key, **unique index** (e.g. `parhizgar`) |
| `nameFa` / `nameEn` / `nameAr` | string | display names |
| `countryCode` | string? | ISO-2 |
| `bioFa` / `bioEn` | string? | short prose |
| `sortOrder` | number | catalogue ordering, default 100 |
| `isActive` | boolean | hide without deleting |

### `recitationEdition` — one downloadable rendition of one reciter

An edition is **a URL template plus a granularity**. That single idea is what lets verse-, surah- and
juz-level download share one code path: the manifest builder fills `{ayah}`/`{surah}`/`{surah3}`/`{juz}`/`{juz2}`
and expands the scope to a flat file list. The client never needs to know the provider's layout.

| Field | Type | Notes |
|---|---|---|
| `key` | string | **unique** (e.g. `parhizgar-murattal-ayah-48`) |
| `style` | string | `murattal` / `tartil` / `mujawwad` |
| `granularity` | enum | `ayah` \| `surah` \| `juz` — decides which placeholder the template must contain |
| `urlTemplate` | string | absolute or relative; must include the granularity placeholder |
| `bitrateKbps` | number? | informational |
| `fileExtension` | string | default `mp3` |
| `totalFiles` | number? | expected count for the whole Quran at this granularity |
| `availability` | enum | `verified` \| `unverified` \| `blocked` — **manifest refuses non-verified** |
| `sourceName` / `sourceUrl` | string? | attribution |
| `licenseNote` | string? | |
| `verifiedAt` | date? | last successful probe |
| `isDefault` | boolean | default edition for its reciter |

**Relation:** `reciter` (single, reverse `editions`).

### `preset` — a reusable recitation *recipe*, per user

A preset is **range-independent**: it describes *how to recite one ayah*, not *which ayahs*. The
player walks the user's chosen range and applies the recipe at each stop. This is why presets
compose with any surah, any juz, any bookmark.

| Field | Type | Notes |
|---|---|---|
| `clientId` | string? | client UUID, **idempotency key**, unique sparse |
| `name` | string | user-facing label |
| `steps` | `presetStep[]` | the recipe, in order |
| `reciterKey` | string? | optional pin; falls back to the user's current reciter |
| `autoAdvance` | boolean | default true — move to next ayah by itself |
| `ayahGapMs` | number | silence between ayahs, default 0 |
| `isDefault` | boolean | one per user (enforced by `clearDefaultPresets`) |
| `isArchived` | boolean | soft delete when history/coverage exists |
| `revision` | number | bumped on every applied sync |
| `updatedAtMs` | number | **LWW stamp** — client clock, monotonic |

`presetStep`:

| Field | Type | Notes |
|---|---|---|
| `kind` | enum | `ayah` \| `translation` \| `tafsir` \| `silence` |
| `textKey` | string? | required for `translation`/`tafsir` (e.g. `fa.ansarian`) |
| `repeat` | number | how many times, default 1 |
| `speed` | number? | per-step playback rate override |
| `gapMs` | number | pause after this step, default 0 |
| `languageCode` | string? | for TTS voice selection |

**Relation:** `user` (single, reverse `presets`).

Worked examples from the brief:

| Intent | `steps` |
|---|---|
| verse → Ansarian → Maleki → Al-Mizan | `[{ayah}, {translation, textKey:"fa.ansarian"}, {translation, textKey:"fa.maleki"}, {tafsir, textKey:"fa.almizan"}]` |
| first verse twice → Ansarian → verse again → next | `[{ayah, repeat:2}, {translation, textKey:"fa.ansarian"}, {ayah}]` |

### `presetAyahProgress` — the coverage ledger, per (preset, ayah)

One row per ayah touched by a preset. **Monotonic**: clients send *absolute* values and the server
merges with `$max`/`$min`, so a retried or out-of-order batch can never walk coverage backwards and
two devices converge without coordination.

| Field | Type | Notes |
|---|---|---|
| `surah` / `ayah` | number | 1-based |
| `globalAyah` | number | 1…6236 — the dedupe/sort key |
| `page` | number? | mushaf page |
| `plays` | number | merged with `$max` |
| `listenedSec` | number | merged with `$max` |
| `firstAt` | number | merged with `$min` |
| `lastAt` | number | merged with `$max` |
| `lastKind` | string? | last step kind heard |

**Relations:** `user` and `preset`, **both with `relatedRelations: {}`** — this table can reach tens of
thousands of rows per user and must never be embedded into the parent documents.
Unique compound index `{user._id, preset._id, globalAyah}`.

### `presetSession` — one row per *sitting*, not per button press

| Field | Type | Notes |
|---|---|---|
| `clientId` | string? | idempotency key |
| `presetName` | string | snapshot (survives rename/delete) |
| `startedAt` / `endedAt` | number / number? | epoch millis |
| `activeSec` | number | listening time, not wall clock |
| `fromSurah`/`fromAyah`/`toSurah`/`toAyah` | number | the range traversed |
| `ayahsCompleted` / `stepsCompleted` | number | counters |
| `playCount` / `pauseCount` / `seekCount` | number | **counters, incremented in place** |
| `lastEventAt` | number | heartbeat |
| `status` | enum | `active` \| `finalized` \| `abandoned` |
| `reciterKey` / `speed` | string? / number? | playback context |
| `events` | `presetEvent[]` | capped at **200** (`$push` + `$slice: -200`) |

`presetEvent`: `{ kind: play|pause|seek|step|ayah, at, surah, ayah, stepIndex }`.

**Why both counters and events:** counters stay exact even after the event cap truncates the tail;
events give the fine-grained timeline for the history detail screen. Play/pause increments a counter
on the existing row — it never inserts.

**Relation:** `preset` (optional, `relatedRelations: {}`) — optional because a preset can be deleted
while its history must survive.

## 6. Acts (the API surface)

Wire format: `POST /lesan` with body `{ model, act, details: { set, get } }`. `get` must be **omitted**
when empty — sending `get: null` fails validation. JWT in the **`token` header (no Bearer prefix)**.
Response envelope `{ success, body }`.

> **Note:** the base path is `/lesan`, **not** `/`. A POST to `/` returns nothing useful and cost an
> hour of debugging once.

### Accounts

| model / act | Auth | Purpose | Returns |
|---|---|---|---|
| `user/register` | none | Create an account **and sign it in**: normalise → validate shape → reject a duplicate → hash → insert | `{ token, user }` |
| `user/login` | none | Verify the password, update `lastLoginAt`. One message for every credential failure | `{ token, user }` |
| `user/me` | token | "is this token still good, and whose is it?" — the app's launch check | `{ user }` |
| `user/registerDevice` | none | **Legacy** anonymous device row, idempotent on `deviceId`; kept so old clients do not 500 | `{ token, user }` |

`register` and `login` answer the same shape so the client decodes one DTO for both. Their
`body.message` strings are the only ones the UI reads: the form matches a few fragments and
localises them (`auth_error_credentials`, `auth_error_duplicate`, …), falling back to a generic
sentence. Rewording one of those messages without updating `AuthViewModel.errorOf` degrades the form
silently — noted in `AGENTS.md` §4.9 and §5.5.

### Reading tracking

| model / act | Auth | Purpose | Returns |
|---|---|---|---|
| `user/registerDevice` | none | Idempotent on `deviceId`; create-or-return user + fresh token | `{ token, user }` |
| `readingSession/syncReadingSessions` | token | Batched ingest of finalized sessions; filters known `clientId`s before insert | `{ synced, sessions:[{clientId, serverId}] }` |
| `readingSession/getMyReadingHistory` | token | Paginated history, newest first, optional `from`/`to` millis filter | array of sessions |

### Catalogue

| model / act | Auth | Purpose | Returns |
|---|---|---|---|
| `reciter/gets` | none | All active reciters with `editions[]` embedded, sorted by `sortOrder` | array |
| `reciter/get` | none | One reciter by `_id` or `key` | reciter + editions |
| `recitationEdition/gets` | none | Editions, filterable by `reciterKey` / `granularity` / `availability` | array |
| `recitationEdition/get` | none | One edition by `_id` or `key` | edition |
| `recitationEdition/getDownloadManifest` | none | Expand an edition over a scope into a flat file list | `{ edition, scope, ayahCount, fileCount, from, to, files[] }` |

`getDownloadManifest` scope: `{ scope: "ayah", surah, ayah }` (one file), `{ scope: "surah", surah }`,
`{ scope: "juz", juz }`, `{ scope: "range", fromSurah, fromAyah, toSurah, toAyah }`.
It **throws on non-`verified` editions** rather than handing the client a broken URL.

Each file in the response carries `url` (upstream) plus, **when this server holds a copy**,
`bytes` (measured on disk) and `mirrorPath` (server-relative, `/uploads/recitation/…`). The client
resolves `mirrorPath` against the origin it already knows, so nothing in the payload is absolute and
the same manifest is correct over the LAN or loopback. `mirroredFileCount` at the top says how many
of the files came from the mirror.

### Audio mirror

| model / act | Auth | Purpose | Returns |
|---|---|---|---|
| `audioFile/getAudioMirrorStatus` | none | Per-edition mirror state: stored/failed counts, bytes, completion % | array |
| `audioFile/prepareAudio` | `x-admin-token` | Mirror one scope now (≤ 60 files); disabled unless `ADMIN_TOKEN` is set | `{ total, stored, skipped, failed, bytes, failures[] }` |

Bulk mirroring is a command, not an endpoint: `deno task audio-mirror --edition=<key> --scope=surah:1`.
Design, invariants and the two upstream caveats are in `../AGENTS.md` §5.4.

### Presets

| model / act | Auth | Purpose |
|---|---|---|
| `preset/add` | token | Create; first preset becomes default |
| `preset/gets` | token | List (archived excluded unless asked) |
| `preset/get` | token | One preset by `clientId` or `_id` |
| `preset/update` | token | Patch name/steps/flags; bumps `revision` |
| `preset/remove` | token | Delete, **or archive** if coverage/history exists |
| `preset/count` | token | Cheap badge count |
| `preset/setDefault` | token | Atomically demote others |
| `preset/syncPresets` | token | Batched upsert with **last-write-wins** on `updatedAtMs` |
| `preset/getMyCoverage` | token | Per-preset coverage rollup (+ optional `surahs[]` breakdown) |

### Preset sessions (history)

| model / act | Auth | Purpose |
|---|---|---|
| `presetSession/start` | token | Open a sitting; idempotent on `clientId` |
| `presetSession/logEvent` | token | Append event + increment counters **on the same row** |
| `presetSession/finalize` | token | Close the row, stamp `endedAt`/`status` |
| `presetSession/getMyPresetHistory` | token | Paginated sittings, newest first |
| `presetSession/count` | token | Total sittings |

### Progress

| model / act | Auth | Purpose |
|---|---|---|
| `presetAyahProgress/syncProgress` | token | Batched monotonic merge; returns `{ received, inserted, merged }` |
| `presetAyahProgress/getPresetParts` | token | Coalesced ayah **ranges** actually covered + sample ayahs |

**Idempotency contract:** a device resends its queue whenever a response is lost, so duplicates are
the normal case. Every sync act matches existing `clientId`s within the user and skips/merges them;
unique sparse indexes are the backstop. A retry must never create a duplicate.

**LWW contract (presets):** the client stamps `updatedAtMs` from its own clock. The server applies an
incoming preset only when its stamp is newer than the stored one, then bumps `revision`. An older
edit arriving late is **acknowledged but not applied** (`synced: 0`) — the client adopts the returned
row. Verified both directions in the smoke test.

## 7. Run / verify

```sh
cd back
# MongoDB must be running locally (or set MONGO_URI). Then:
deno task dev          # watch mode; playground at http://localhost:1407/playground
deno task seed         # idempotent: reciters + recitation editions into the catalogue
deno task test         # deno test --allow-all content/ src/
deno check mod.ts      # type check  (MUST pass)
deno lint              # MUST pass
deno fmt --check       # MUST pass
```

Env vars (see `.env.example`): `MONGO_URI`, `DB_NAME` (hawraa), `APP_PORT` (1407), `ENV`
(development enables the playground), `TOKEN_KEY` (change in any non-local deployment).

**Port 1407** is deliberate — the owner's other projects use 1370, 1404, 1405, 1406, 8080, 8090.

**Indexes are created at boot** by `utils/ensureIndexes.ts`, because Lesan's `newModel` accepts only
one `createIndex`. The unique index on `presetAyahProgress {user._id, preset._id, globalAyah}` is what
makes the coverage merge safe under concurrent retries.

## 8. Conventions (condensed — full rules in `../AGENTS.md`)

- Entry point is always `mod.ts`; strict TS, no `any`; `camelCase.ts` file names.
- Every act: validator (`*.val.ts`) **before** implementation (`*.fn.ts`); register via `setAct` in the
  act's `mod.ts`; register each domain setup in `src/mod.ts`; register each model in `models/mod.ts`
  **and** instantiate it in `mod.ts`.
- Schema ≠ folder: acts register under the real model they operate on.
- In `models/`, import mixins from their own file (not `@lib`) to avoid an import cycle.
- Keep `typeGeneration: true`; the mobile client consumes only the generated `declarations/` types.
- Never commit `declarations/` or `.env`. Never use `git reset`. Commit only when asked.

## 9. Environment notes (this machine)

- Deno 2.8.x is on PATH; JSR (`jsr:@hemedani/lesan`) resolves fine here.
- MongoDB **is** running locally on `127.0.0.1:27017` (db `hawraa`) as of the Phase 1 smoke test.
- The mobile app reaches this server at `http://10.0.2.2:1407/` from the emulator, or the dev
  machine's LAN IP from a physical device (see mobile `BuildConfig.SERVER_URL`).
- `deno test` needs `--allow-all` because the model files open a Mongo connection at import time.

## 10. Known gaps / next steps

See the plan for the ordered, checkable list. Headlines:

1. Turn the ad-hoc smoke script into a committed HTTP/e2e suite (`tests/`), so the contracts in §6 are
   regression-tested rather than manually re-proven.
2. **Shakernejad has no public per-ayah source.** Verified negative against cdn.islamic.network
   (403 on every slug spelling), Quran.com v4 (12 reciters, not him), mp3quran (244, not him),
   everyayah (177 dirs, not him; `ShakerNejad_128kbps` is 404). His edition is therefore modelled as
   **juz-granularity tartil** from `dl.meshkatsound.com` (206 audio/mpeg, verified). If a per-ayah
   source appears later, add a *new* edition — do not mutate the existing one, or clients that already
   downloaded juz files lose their manifest.
3. Settings LWW sync, aggregate stats, and a content-version manifest act (references only, never the
   sacred text).
4. ~~Server-side audio proxying/caching~~ — **done 2026-11-15**, as an *optional mirror* rather than a
   proxy: `deno task audio-mirror` fetches and verifies files into `uploads/recitation/`, and the
   manifest offers them to the device. The client still works when nothing is mirrored, which is the
   intended production shape; the mirror exists so a LAN test does not depend on the CDN, and so a
   file's exact size is known before the device downloads it.

   **What is mirrored now** — the first five mushaf pages, for both Persian reciters:

   | Edition | Scope | Files | Bytes |
   |---|---|---|---|
   | `parhizgar-murattal-ayah-48` | `range:1:1-2:29` | 36 | 4,474,501 |
   | `shakernejad-tartil-juz-24` | `juz:1` | 1 | 12,651,123 |

   Those 36 ayahs (1:1–1:7, 2:1–2:5, 2:6–2:16, 2:17–2:24, 2:25–2:29) all sit inside juz 1, so for a
   juz edition the first five pages *are* juz 1. Verified live: `range 1:1-2:29` →
   `ayahCount 36, fileCount 36, mirroredFileCount 36`; `juz 1` → `ayahCount 148, fileCount 1`; both
   served `200` at the byte counts above.

   **Caveat for the next mirror run:** `dl.meshkatsound.com`'s certificate is expired (`notAfter`
   2026-11-13). The payload is genuine `audio/mpeg`, so the run used
   `--unsafely-ignore-certificate-errors=dl.meshkatsound.com` — **that host only**. Verify the bytes
   before blaming the mirror, and never widen the bypass.

## 11. Doc map & precedence

| File | Role |
|---|---|
| `../AGENTS.md` | sector rules / conventions |
| `docs/summary.md` | this file — orientation & ground truth |
| `docs/plans/01-*.md` | roadmap **and** progress record — update after every small task |
| `docs/prompts/01-*.md` | the original user brief(s) — the "why" |
| `../../AGENTS.md` | root product invariants |
| `../../docs/ROADMAP.md` | cross-sector phased plan |

Precedence: **code > plan > AGENTS.md > docs.** Where this summary and the code disagree, the code is
right and this file is a bug — fix the file.