# Hawraa — Roadmap

Incremental phases. Each phase ends with a device-testable build. Do not start a later phase before the owner confirms the current one.

## Phase 0 — Scaffold (current)
- Monorepo structure, AGENTS.md files, buildable Android skeleton (Compose + Material 3, light/dark, fa-RTL/en-LTR infrastructure, Room, Hilt, WorkManager, Ktor).
- Lesan backend skeleton: `user` + `readingSession` models, idempotent batch sync act, history read act.
- No Quran content yet.

## Phase 1 — Content pipeline + first 5 pages
- Vet and import source data for mushaf pages 1–5: Uthmani text with verse boundaries and markers (source candidates: Tanzil.net Uthmani text, qurancomplex.gov.sa data), Ansarian Persian translation, Shakernejad per-ayah audio URLs.
- Store content as bundled assets (offline) with a versioned content schema; server keeps only references, never the sacred text itself (integrity).
- Decide and document the Quran typography approach (Uthman Taha font, separate letter/diacritic colors, marker rendering).

## Phase 2 — Reader core
- Page view of the first 5 pages with exceptional readability; verse highlighting.
- The signature layout: verse + multiple translations + commentaries displayed together, each independently scrollable.
- Reading-session tracking wired end-to-end (open page/ayah range → finalize → sync).

## Phase 3 — Recitation
- Media3 playback of Shakernejad recitation, per-ayah follow-along highlighting, audio sessions tracked like reading sessions.

## Phase 4 — History & settings
- History screens (sessions, ranges, streaks), settings sync (LWW), per-app language switcher UI.

## Phase 5+ — Later (not committed)
- Remaining 599 pages, more reciters/translations/commentaries, bookmarks, notes, search, khatm plans, widgets.

## Open decisions (ask the owner when the phase starts)
- Exact Uthman Taha font file + license (KFGQPC fonts are free for Quran apps; verify).
- Audio hosting: stream from an existing per-ayah CDN vs. download-for-offline per page.
- Account model: anonymous device identity first, optional login later?