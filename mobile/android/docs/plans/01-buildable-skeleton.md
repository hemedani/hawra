# Plan 01 — Android Buildable Skeleton → Reader

> **This file is both the roadmap and the record.** Work is done by taking the first unchecked box,
> doing it, ticking it, and appending one entry to the [Progress log](#progress-log).
>
> **⚠️ Update this plan after EVERY small task.** Not at the end of a phase, not at the end of a
> session — after each small task: tick the box, log what changed, note any decision. A future agent
> (or you, after a break) must be able to read the last log entry and know exactly where things stand.
>
> A phase is not finished until its **"Done when"** line is verified — not when its boxes are ticked.
> Where this plan and the code disagree, **the code is right and this file is a bug.**

> **Plan 02 supersedes this plan's UI/design portions.**
> [`02-pejvak-design-system.md`](02-pejvak-design-system.md) adopts the Pejvak Android design system
> (frosted header, dock, mini-player/full player, motion, icons) and rewrites the `DESIGN.md`
> contract. Where the two disagree on UI, Plan 02 and `DESIGN.md` win; Plan 01 remains the record for
> data, content, typography, sync and sessions.

**Status:** Phases 0–2 complete and verified. **Phase 3 (recitation) complete 2026-11-15** —
verse-by-verse streaming with follow-along highlighting, listening sessions and a reciter picker.
**2026-11-15:** the reader is now one **continuous scroll** with **per-pane selection** (D14, D15) —
the pager and the page counter are gone, and the verse and each translation are separately
selectable, which is the groundwork the multi-pane layout needs.
**2026-11-15 (later):** the whole UI was rebuilt on a **glass design language** and specified in
[`../DESIGN.md`](../DESIGN.md) (D16), with the dock + floating selection box taking over the bottom of
the screen from the old transport bar (D17).
Open: the multi-translation/commentary layout (needs a second translation and a commentary in the
asset), server-side history restore (needs a backend read act), background playback (needs a
foreground service), and Shakernejad audio (no public per-ayah source — see D13).
**Sector:** `mobile/android/` · **Companion docs:** [`../summary.md`](../summary.md),
[`../DESIGN.md`](../DESIGN.md) (UI contract), [`../../AGENTS.md`](../../AGENTS.md)

---

## 0. How to use this document

| I want to… | Read |
|---|---|
| Know what exists, the stack, and the build quirks | [`../summary.md`](../summary.md) |
| **Change anything visual** | [`../DESIGN.md`](../DESIGN.md) — the UI contract, first |
| Start work | [§3 Phases](#3-phases) — first unchecked box |
| Understand the typography challenge | [`../summary.md §9`](../summary.md#9-quran-typography--the-hard-part-phase-12) |
| Continue after a break | [Progress log](#progress-log) — last entry |
| Check a convention | [`../../AGENTS.md`](../../AGENTS.md) |

**Rules of engagement**
1. One box at a time. If a box needs more than a session, split it and note the split in the log.
2. Never tick a box whose "Done when" you have not verified — run the gate, don't assume.
3. **Update this file after every small task** (tick + log entry). This is non-negotiable.
4. A decision that changes the architecture goes in [§2 Decisions](#2-decisions) with an ID.
5. A change is not "done" until `./gradlew assembleDebug` passes, unit tests for touched pure logic
   pass, and (for UI) it has been **looked at** in both themes and both locales — on a device, or
   through the Robolectric screenshots in `app/build/screenshots/` (this machine has no emulator, see
   D8). Looking at it is not optional; a UI box ticked without a screenshot inspected is not ticked.
6. Build with `JAVA_HOME` set to the Homebrew OpenJDK 22 path (see summary §4). Do not "fix" the
   repository ordering or re-enable SDK auto-download.

## 1. Goal & scope

Deliver an Android-only native Quran app with exceptional readability and complete activity tracking,
incrementally. Phase 0 = buildable skeleton with all cross-cutting infra. Phase 1 = content pipeline +
typography spike for the **first 5 mushaf pages**. Phase 2 = the real reader + session tracking wired
end-to-end. Phase 3 = Shakernejad recitation. Phase 4 = history & settings polish.

**In scope now:** the skeleton (done) and then the first-5-pages reader.
**Out of scope now:** the remaining 599 pages, extra reciters/translations, search, bookmarks, khatm plans.

## 2. Decisions

| ID | Decision | Rationale | Status |
|---|---|---|---|
| D1 | Native Kotlin + Compose; no Flutter/RN/WebView UI | Owner requirement: state-of-the-art native | resolved 2026-11-12 |
| D2 | AGP 9 built-in Kotlin; minSdk 29, targetSdk 36, compileSdk 37 | Latest stable; core-ktx/OkHttp force compileSdk 37 | resolved 2026-11-12 |
| D3 | Offline-first; Room is source of truth; WorkManager background sync | Owner requirement; matches Pejvak | resolved 2026-11-12 |
| D4 | Repos: mavenCentral → Aliyun google mirror; sdkDownload=false | dl.google.com blocked; mirror 502s on missing artifacts | resolved 2026-11-12 |
| D5 | **Typography: Scheherazade New, split into two derived "ink layer" fonts** (`quran_letters.ttf` = letters only, `quran_marks.ttf` = marks only, identical GSUB/GPOS/cmap/hmtx) | The obvious approach — draw the full verse in the diacritic colour, then redraw the verse with marks stripped in the letter colour — **does not work**: HarfBuzz picks different glyphs/ligatures when the marks are gone ("لِلَّهِ" ligates with the shadda, and to a different glyph without it), so the layers drift. Verified against Scheherazade, Amiri, Amiri Quran and KFGQPC Hafs. Splitting the *font* instead keeps the glyph run bit-identical, so registration is exact. Word-ligature mushaf faces (KFGQPC Uthmanic Hafs, QCF) bake the marks into the glyph and cannot colour them at all. | resolved 2026-11-14 |
| D6 | Content bundled as assets, versioned; never fetch sacred text at runtime | Integrity + offline | resolved 2026-11-12 |
| D7 | **Content provenance: Tanzil Uthmani (text) + Tanzil `fa.ansarian` (translation) + Quran.com API v4 (page/juz/hizb/sajdah metadata), compiled by `tools/build-quran-assets.py` into `assets/quran/pages-v1.json`** | Reproducible, verifiable, licence-recorded; the script asserts the only edit it makes (Tanzil embeds the basmala inside the first ayah of each surah, which the reader draws as its own line) | resolved 2026-11-14 |
| D9 | **Per-app language: `ContextThemeWrapper` with an override configuration, never `createConfigurationContext`, and never `Locale.setDefault`** | `hiltViewModel()` walks the `baseContext` chain looking for an Activity and throws `IllegalStateException` on a bare `ContextImpl` — replacing `LocalContext` with a configuration context therefore killed the app on every launch once Persian was persisted. A `ContextThemeWrapper` keeps the Activity as its base and applies the locale as an override, so resources translate *and* the chain resolves. `Locale.setDefault` was removed too: mutating the process default from composition turned `"%013d".format(...)` into Persian digits inside a primary key. | resolved 2026-11-15 |
| D10 | **Silent letters are muted, never removed** — detected from three sources: the Uthmani U+06DF/U+06E0 markers, the `ٱل` + sun-letter rule, and a small lexicon | The text already marks the silent wāw of `أُو۟لَٰٓئِكَ` and the silent alif of `قَالُوا۟`; the assimilated article (`ٱلشَّمْس` = ash-shams) is *not* marked and must be a rule. Muting is done with a `SpanStyle` inside one shaped layout — splitting the string would break joining and change glyph selection, the same trap as D5. | resolved 2026-11-15 |
| D11 | **Ayah marker is a licensed icon, not generated geometry**: Delapouite's "Rub el Hizb" from game-icons.net (CC BY 3.0), as `res/drawable/ayah_marker.xml` | The hand-drawn rosette never read as a printed marker. The icon brings the corner ticks, edge notches and double-octagram rule; the only edit is dropping its central ring so the number fits. CC BY obliges visible attribution, so it is in Settings. | resolved 2026-11-15 |
| D12 | **Recitation streams per-ayah audio through a size-capped Media3 `SimpleCache`; it does not bulk-download** | An ayah played once is replayed from disk, and a page listened to twice costs one page of data. A "download this page" flow would need storage management for 6236 files and a UI to explain it, for a five-page reader. 200 MB LRU ≈ 60 pages at 48 kbps. `SimpleCache` is a `@Singleton` because it file-locks its directory. | resolved 2026-11-15 |
| D13 | **The reciter catalogue is a bundled, verified asset (`assets/quran/recitations-v1.json`), not a hardcoded list; Shakernejad is recorded as unavailable** | Probed every public per-ayah host on 2026-11-15 (islamic.network, verses.quran.com, everyayah, Quran.com v4, mp3quran v3 ×244, quranicaudio ×177): none carries Shakernejad. Shipping four *verified* reciters (Parhizgar default — Persian murattal — plus al-Afasy, al-Husary, al-Minshawi) beats shipping one dead URL. `tools/verify-recitation-audio.py` is the gate; adding Shakernejad later is one catalogue entry and no code change. | resolved 2026-11-15 |
| D14 | **Selection is a *layer-keyed set*, not a boolean**: `VerseElement(surah, ayah, layer, key)` with `layer ∈ {VERSE, TRANSLATION, COMMENTARY}` and `key` naming *which* translation; `VerseSelection.selected` is a set and `active` is the single last-tapped element | Owner requirement: the verse and its translation must hold **independent** state, because the planned layout shows three translations and one commentary at once and the reader must be able to point at any one of them. A boolean (or one selected ayah) cannot express that. The set also makes "select two translations, not the verse" a natural state rather than a special case. `active` stays single because it answers two different questions that are genuinely singular: which verse to peek at, and where "play" starts. Independence is proven by `VerseSelectionTest` (10 cases) **before** any layout depends on it. | resolved 2026-11-15 |
| D15 | **The reader is one continuous `LazyColumn` over flattened domain items (`ReaderItems`); the horizontal pager is deleted, and no page number is ever shown** | Owner requirement: no numerical pagination, infinite scrolling. Pages stay the unit *underneath* (sessions, recitation queues, resume) because that is what the content, the audio CDN and the tracker already speak — but they are no longer a UI concept. A surah plate is emitted **when the surah changes, not per page**: with continuous scrolling a plate at every page break would reinstate pagination in disguise. "Which page am I on" becomes a lookup on the first visible item rather than a pager side effect, which also makes it testable without Compose. | resolved 2026-11-15 |
| D16 | **The UI is a glass design language, specified in `docs/DESIGN.md` — one document that is the contract for colour, type, geometry, motion and every component** | Owner requirement: move away from the look of existing Quran apps toward a modern aesthetic with room for an Iranian design layer later. That is not achievable by tweaking the old Material defaults screen by screen; it needs a *system*. So the tokens were rebuilt first (`GlassColors`, `QuranColors`, `Geometry` — three `CompositionLocal`s, with the Material scheme derived from them) and the rules written down in one place, because a design language that lives only in code gets violated by the next feature. `DESIGN.md` is referenced from `AGENTS.md`, `summary.md` and this plan so any agent reads it before touching UI. It also carries the *why* for the numbers (why the strong fill is 98 %, why the light border is dark, why the dock is one panel), which is what stops them being "tidied" back. | resolved 2026-11-15 |
| D17 | **The dock lives in the app shell, and the reader publishes into a `NowPlayingStore` instead of owning it; selection actions float over the selection instead of living in a bottom bar** | Owner requirement: the bottom area must be a menu bar + mini-player (as in `Pejvak/mobile`), and actions must appear in a box over whatever is selected. A dock drawn by the reader would disappear on every other screen and would force the mini-player to be re-implemented per screen; a shell-level dock cannot reach into the reader's engine either. So ownership is inverted: `ReaderViewModel` publishes a `NowPlaying` snapshot and binds the transport callbacks into a `@Singleton` store, and the shell only draws and forwards. `openControls` is a `SharedFlow`, not state, so recomposition cannot reopen the sheet. The action box is driven by a `List<SelectionAction>`, so the glossary action later is an entry, not a layout change. The old full-width transport bar is deleted. | resolved 2026-11-15 |
| D21 | **The app is gated on a signed-in account, and the gate is a `StateFlow`, not a route** — `AuthState { Restoring \| SignedOut \| SignedIn }`; `Restoring` draws the canvas alone, `SignedOut` draws the auth screen with no dock, `SignedIn` draws the app | History written before sign-in belongs to nobody, so it either gets attributed to the wrong account later or has to be thrown away; the owner asked for a forced sign-in for exactly that reason. A route would leave the back stack able to return to a screen the reader may not see, and would let the dock navigate into the app. Everything after the gate stays offline-first: `restore()` publishes the cached account first and asks `user/me` behind it, and signs out **only** on a `LesanException` (the server replied and refused) — a network failure keeps the session, because a reader in a lift is not a credential problem. `AuthRepository.token()` returns null unless the state is `SignedIn`, so a signed-out device can never push rows under a stale credential. | resolved 2026-11-15 |
| D8 | **Visual gate: Robolectric native-graphics Compose screenshots on the JVM** (`./gradlew testDebugUnitTest` → `app/build/screenshots/*.png`) | No emulator/system image on this machine and `dl.google.com` is blocked; this still proves real Skia + HarfBuzz rendering, so typography regressions are caught before the APK reaches the device | resolved 2026-11-14 |
| D18 | **A preset is compiled into a flat, fully-resolved cue list before anything plays** (`PresetSequenceBuilder`), and the player walks that list one cue at a time | A preset mixes two completely different sound sources — a reciter's audio file and TTS for translations/tafsir — and they cannot share one Media3 queue. Resolving repeats, gaps, speeds and texts up front means the player loop is "play cue, wait gap, next cue" with no branching, and it means the whole thing is testable on the JVM without an audio stack. Gaps are carried as `gapBeforeMs` on the *next* cue, so a trailing gap at the end of a run cannot exist. | resolved 2026-11-15 |
| D19 | **One `presetSession` row per sitting** (counters + a 200-event cap), opened by the first play and closed by leaving — never one row per press | The owner's requirement in as many words: *"this should not create a new history entry every time the user presses play or pause."* Presses become `playCount`/`pauseCount`/`seekCount`; the timeline is a capped `events[]` for the detail screen. Time is billed only while `running`, clamped to a 90 s idle timeout, so pausing and walking away does not record an hour. Mirrors backend D8 exactly. | resolved 2026-11-15 |
| D20 | **Presets travel through the backend with last-write-wins on `updatedAtMs`; coverage is a separate monotonic ledger keyed by global ayah** | Multi-device editing needs no lock, and coverage must survive preset edits (editing a preset must not erase what you have heard with it). Absolute high-water marks (`$max`/`$min`) make every retry idempotent and let two devices converge. Mirrors backend D7/D9. | resolved 2026-11-15 |
| D22 | **Granularity is a property of the edition (`AYAH \| SURAH \| JUZ`), and a juz edition gets its own queue (`RecitationQueue.buildJuz`) instead of being shoehorned into the per-ayah one** | Shakernejad has no public per-ayah source — only whole-juz files — and a 70-minute track cannot start at verse 17. Substituting it into the per-ayah queue would make the follow-along highlight point at a verse that is not sounding, and would bill "progress at 1:1" while a whole juz plays, rewinding the session. So `buildJuz` emits **one track per distinct juz the page touches** (a page straddling a boundary yields two), `ReaderViewModel` publishes `playingJuz` instead of `playingVerse`, the dock row and its identity tile both say `جزء N`, and **no `sessions.progress` is written from a juz track**. | resolved 2026-11-15 |
| D23 | **Downloading is triggered by play, not by a button (`AudioAutoCache`), and a juz edition's unit is the whole juz** | The owner asked for "download the audio automatically when I press the play button". Making it a side effect of playback keeps the mental model ("play caches it") without turning play into a blocking download: `ensure()` returns `Queued \| NothingToDo \| NoEdition \| Failed`, never throws, dedupes on `editionKey\|scopeKey` per process under a `Mutex`, and drops its dedupe token on failure so a retry is possible. The unit is honest — 36 files for a per-ayah reciter, one 12 MB file for a juz reciter — because a juz file has no smaller part. | resolved 2026-11-15 |
| D24 | **The page scope is resolved on the client; the wire keeps four scopes (`ayah \| surah \| juz \| range`)** | A page is a property of the *mushaf text the app ships*, not of the audio catalogue: the backend has no page concept, and adding one would make it authoritative about a pagination it does not own. `DownloadsViewModel.resolveScope` turns a page range into a verse range from the bundled pages, so the request stays inside the contract the manifest already speaks while the screen offers the unit readers actually think in. | resolved 2026-11-15 |

## 3. Phases

### Phase 0 — Buildable skeleton  *(complete)*

**Done when:** `./gradlew assembleDebug` succeeds, unit tests pass, and the app installs and shows
Home with working theme switch + navigation in fa-RTL and en-LTR. *(Build/test verified; on-device
visual check is the owner's to do.)*

- [x] Gradle KTS + version catalog + wrapper; AGP 9 built-in Kotlin; repository ordering for this network
- [x] Compose + Material 3; light/dark `ColorScheme`; all colors in `ui/theme/`
- [x] i18n: fa default (RTL) + en (LTR); `localeConfig`; all strings in resources
- [x] Room: `HawraaDatabase`, `ReadingSessionEntity`, `ReadingSessionDao`, `SyncStatus`
- [x] Hilt `AppModule`; Ktor `LesanClient` + wire DTOs; `SettingsRepository` (DataStore)
- [x] WorkManager `SyncWorker` (@HiltWorker, Phase-0 stub) + manifest initializer override
- [x] Navigation: Home / Reader(placeholder) / History / Settings; theme switcher persisted
- [x] Unit tests (ThemeMode + SyncStatus) pass; `assembleDebug` green, zero warnings
- [ ] **Owner:** install on device (`installDebug`), confirm fa-RTL/en-LTR + light/dark look right

### Phase 1 — Content pipeline + typography  *(complete 2026-11-14)*

**Done when:** pages 1–5 render from bundled, vetted data with the chosen renderer, letters and
diacritics independently colorable, in both themes; a written typography decision (D5) is recorded.
**→ verified:** `reader-light.png`, `reader-dark.png`, `reader-ink-variant.png` in
`app/build/screenshots/` show the first five pages rendering from the asset with letters and
diacritics in independent colours in both themes.

- [x] Vet & import source data for pages 1–5: Uthmani text + verse boundaries + markers
      (Tanzil Uthmani; Quran.com v4 for the page/juz/hizb/sajdah mapping), Ansarian Persian
      translation (Tanzil `fa.ansarian`). Provenance + licences recorded in the asset and in D7.
- [x] Define a versioned content schema; bundle as assets (offline). Server keeps references only.
      `assets/quran/pages-v1.json` (`schemaVersion: 1`), built by `tools/build-quran-assets.py`.
- [x] **Typography spike:** per-glyph rendering with separate letter/diacritic colours; compared
      Scheherazade New, Amiri, Amiri Quran, KFGQPC Uthmanic Hafs and QCF2001.
- [x] Decide D5 (renderer approach + font) and record it here; the renderer is `QuranText` behind
      one composable, the face in `QuranFont.kt`.
- [x] Verify verse-number marker rendering (rosette drawn as geometry, Persian digits).
- [ ] Hizb / rub-el-hizb / sajdah markers in their mushaf positions (none fall inside pages 1–5;
      the data already carries `hizb`, `rubElHizb`, `sajdah` per verse).

### Phase 2 — Reader core + session tracking

**Done when:** the first 5 pages are readable with the signature verse+translation+commentary layout,
and a reading session is recorded locally → finalized → synced to the backend end-to-end.

- [x] Page view of pages 1–5 with exceptional readability (swipe between pages, scroll within a page,
      surah/juz banner, ayah rosettes, Ansarian translation under each verse, reading settings applied).
      → **Changed 2026-11-15 (D15):** "swipe between pages, scroll within a page" is now **one
      continuous scroll** through the whole mushaf — no pager, no page counter, no page-boundary
      furniture; a surah plate appears only where a surah starts. `ReaderItems` flattens the pages into
      `SurahHeaderItem` / `BasmalaItem` / `VerseItem`, each carrying its `page`, so sessions, the
      recitation queue and the resume point stay page-based while nothing on screen counts pages.
      **Verified:** `reader-page-boundary.png` — al-Fatiha's last ayah runs straight into the al-Baqarah
      plate; `ReaderItemsTest` (8 cases) pins one item per verse, a plate per surah start, a single
      al-Baqarah plate across pages 2–5, and the resume/follow-along lookups.
- [x] Verse highlighting.
      → **Superseded 2026-11-15 (D14):** the verse and its translations no longer share one state.
      Tapping the Arabic selects the **verse layer only** (wash, 3 dp leading-edge bar, filled gold
      rosette, and its translations revealed even when translations are off — a peek, not a mode
      change); tapping a translation selects **that translation only** and leaves the Arabic plain.
      State is `VerseSelection`: a set of `VerseElement(surah, ayah, layer, key)` plus one `active`
      element (last tap) that drives the peek and "play from here". Tags `verse-<key>` and
      `translation-<key>-<translationKey>`.
      **Verified:** `reader-selected-verse.png` (Arabic washed, translation plain) vs
      `reader-selected-translation.png` (translation washed, Arabic plain); `VerseSelectionTest`
      (10 cases) proves the independence before layout depends on it.
- [x] Silent letters drawn in a lighter shade.
      → `domain/quran/SilentLetters.kt` (pure, 11 tests): mutes the letter after U+06DF/U+06E0,
      both letters of `ٱل` before a sun letter (looking past one proclitic, so `وَٱلشَّمْس` and
      `بِٱللَّهِ` work), and the lexicon cases the orthography leaves unmarked (`أُولَٰئِكَ` wāw,
      `مِائَة` alif, `عَمْرٌو` wāw). `QuranText` applies them as a `SpanStyle` on the single shaped
      layout, so registration with the mark layer is unchanged. Invariant test walks all 36 verses:
      ordered, in bounds, letters only, ≥40 muted characters.
      **Verified:** `reader-light.png` crop — the `ٱل` of ٱللَّه / ٱلرَّحْمَٰن / ٱلرَّحِيم is
      visibly lighter while the words stay legible.
- [ ] Signature layout: verse + **multiple** translations + commentaries visible together, each
      independently scrollable, regardless of total scroll length. *(One translation is in place and
      each pane is now independently selectable (D14), so the state half of this is done; what is
      missing is a second translation and a commentary in `assets/quran/` and the side-by-side
      arrangement.)*
- [x] Pure session-tracking state machine in `domain/` (open → progress → finalize; checkpoint 10s;
      orphan recovery on launch). No `Timer`/polling.
      → `domain/session/SessionModels.kt` + `ReadingSessionTracker.kt`, 25 tests in
      `ReadingSessionTrackerTest`. Rules: idle gaps bill at most `IDLE_TIMEOUT_MS` (90 s), checkpoints
      at most every `CHECKPOINT_INTERVAL_MS` (10 s) of activity, range grows monotonically, a finish
      needs `MIN_COMPLETION_SEC` (3 s) of dwell so a fast swipe cannot mark a page read.
      Wired to the UI through `ReadingSessionRepository` (Room) driven by **the first visible item's
      page** (a `snapshotFlow` on the scroll state — this replaced pager-settle when the pager was
      removed, D15) plus `ON_STOP`; `onCleared` finalizes on the application scope so a popped screen
      still closes its row. `HawraaApp` recovers orphans before anything else can open a session.
- [x] Wire `SyncWorker` for real: registerDevice → syncReadingSessions (batched ≤50, idempotent),
      token persistence, `pending→syncing→synced|failed`, never blocks UI.
      → `data/sync/ReadingSessionSync.kt` + `SyncWorker`/`SyncScheduler` + `IdentityRepository`.
      Payloads mirror `back/src/sessions/syncReadingSessions/*.val.ts` exactly; one re-register retry
      on rejection; periodic work every 6 h with a network constraint, plus an immediate push after
      each finalized session.
- [x] Settings snapshot captured per session.
      → frozen into `reading_sessions.settingsJson` at open time (font size, translation toggle,
      both ink choices, theme, language); the wire `setting` object is derived from it at sync time.

### Phase 3 — Recitation (Shakernejad)

**Done when:** per-ayah playback with follow-along highlighting; audio sessions tracked like reading.

- [x] Media3/ExoPlayer playback of per-ayah audio; stream vs. download decided.
      → **stream + disk cache** (D12). `data/recitation/Media3RecitationEngine.kt` builds one queue of
      single-item media sources per page, behind a `RecitationEngine` interface so the ViewModel has no
      Media3 types. Reciter catalogue is a bundled asset (D13) with `{ayah}` URL templates addressed by
      the **global** ayah number from `domain/recitation/AyahNumbering.kt` (pure, Hafs table, total
      pinned to 6236 and cross-checked against the asset's own surah lengths).
      **Verified:** `tools/verify-recitation-audio.py` → 4/4 probes per reciter (206 + mp3 frame);
      `RecitationEngineTest` (7 cases) builds the real player + cache on the JVM.
- [x] Follow-along verse highlighting synced to playback.
      → the current ayah *is* the media item index, so the highlight moves on
      `onMediaItemTransition` — no position poll (the "no timers" rule holds). The playing verse gets a
      green wash + leading bar + filled rosette (`quran.playing`, deliberately green so it cannot be
      confused with the gold of a tapped verse) and is scrolled into view with a
      `BringIntoViewRequester` (RTL- and inset-correct without measuring offsets). A swipe while
      playing re-queues the new page, so audio always matches what is on screen.
      **Verified:** `reader-recitation.png` / `reader-recitation-dark.png` — 1:3 and 1:5 highlighted.
- [x] Audio sessions recorded (mode="listen") with playbackSpeed in the settings snapshot.
      → `ReadingSessionRepository.markListening(speed, reciterKey)` rewrites `mode` + the snapshot on the
      still-open row; `ReadingSessionSync` now sends `reciterKey`/`playbackSpeed` (the wire DTO already
      had both). Each ayah transition feeds `progress()` for that verse, so listening bills time and
      grows the range through the same pure tracker as scrolling.
- [x] Reciter picker, speed presets (0.5×–1.5×) and repeat-ayah, persisted in DataStore.
      → `RecitationSettings` + chips in Settings; the transport bar cycles speed and toggles repeat.
      **Verified:** `settings-recitation.png` (chips) and an assertion that both attribution lines are
      in the product.

### Phase 4 — History & settings polish

- [x] History screen backed by real data (ranges, durations, streaks).
      → `domain/session/SessionStats.kt` (pure, 13 tests): totals, distinct pages touched, completed
      count, calendar-day grouping in a supplied `ZoneId`, and a streak that survives "last read
      yesterday" but dies on a full missed day. `HistoryScreen` shows a three-stat summary card
      (reading time / pages / day streak), then day sections (Today / Yesterday / date) and one row
      per session with surah name, ayah span in Persian digits, duration and completed/interrupted.
      Persian strings were previously hardcoded in this screen; they now come from resources.
      **Verified:** `history-light.png` / `history-dark.png`.
- [ ] Pull/restore history from the server (needs a read act on the backend — owner's side).
- [x] Per-app language switcher UI.
      → `AppLanguage` (system / فارسی / English) persisted in DataStore; `LocalizedApp` wraps the
      Activity in a `ContextThemeWrapper` carrying an override configuration (see D9) and provides
      `LocalContext`, `LocalConfiguration` and `LocalLayoutDirection`, so every `stringResource`
      below it resolves the chosen locale and Persian gets RTL. No appcompat dependency was added.
      `LocalizedAppTest` (5 cases incl. a runtime switch) and `LanguageSwitchThroughUiTest` pin it.
      **Fixed 2026-11-15:** the first implementation used `createConfigurationContext`, which threw
      `IllegalStateException: Expected an activity context…` from `hiltViewModel()` on every launch
      with Persian persisted. `MainActivityLaunchTest` is the regression gate: it launches the real
      activity with `app_language=fa` seeded and fails if anything reaches the process-wide
      uncaught handler.
      **Verified:** `settings-light.png`; 3 launch/switch tests green.
- [x] Landing page.
      → `HomeContent`: a hero (wordmark in the mushaf face on a 7 % octagram lattice that fades
      into the paper, gold rule with a diamond), a continue card that shows the **real opening
      verses** of the page the reader stopped on rather than a label, a today strip (minutes, a
      goal bar that grows from the leading edge, streak pill), one filled action and two ghost ones.
      `HomeViewModel` now supplies `ResumePoint.preview` (first two verses of the page) and the
      basmala fallback, so the card is never empty on first run.
      **Verified:** `home-light.png`, `home-dark.png` (dark also covers a part-finished goal).
- [ ] Settings sync (LWW) — needs a backend act for reading settings; owner's side.
- [x] Reading settings UI (font size, letter/diacritic colors, translation toggle).
      → `SettingsScreen` with a live `QuranText` preview, 22–44 sp in 2 sp steps, letter ink ×3,
      diacritic ink ×4, translations on/off. Commentary selection waits on the content pipeline.
- [ ] Multi-translation + commentary pane layout (needs a second translation and a commentary in
      `assets/quran/`; the pipeline currently ships Ansarian only).
- [x] Glass design language + app shell (D16, D17).
      → `docs/DESIGN.md` written as the contract (17 sections: intent, principles, ambient canvas,
      colour, type, shape/spacing incl. the dock geometry contract, the glass recipe, elevation,
      iconography, motion, every component, screen specs, the accessibility contrast contract, light/
      dark, RTL/Persian, Compose notes, Do/Don't, enforcement table).
      Tokens rebuilt: `Color.kt` (raw palette, two non-interchangeable families), `GlassColors.kt`
      (chrome), `QuranColors.kt` (reading surface), `Geometry.kt` (`Radius`/`Spacing`/`Controls`/
      `IconSizes`/`DockGeometry` with `dockInset = 146`), `Theme.kt` (derives the Material scheme from
      the glass tokens and provides all three locals).
      Components: `GlassSurface` (fill → 2 dp sheen → hairline, no `RenderEffect`), `AmbientCanvas`,
      `BubbleButton`, `GlassChip`, `AppDock`, `SelectionActionBox`, `RecitationSheet`
      (replaces `RecitationBar`, deleted).
      Shell: `HawraaShell` (canvas + NavHost + dock), `ShellViewModel`, `NowPlayingStore`; the reader
      lost its `onBack` and its bottom bar, gained a floating `ReaderTopBar` and the anchored action
      box; Home/History/Settings dropped their opaque backgrounds so the canvas shows through.
      **Verified:** `reader-shell.png`, `reader-shell-idle.png` (nav-only height), `reader-shell-dark.png`,
      plus the existing mushaf captures; ghosting through the dock measured to 1.6 % (light) / 3 lum
      (dark) after the fill was raised to 98 % (it was 4 % legible at 96 %, i.e. readable words under
      the icons). Identity tile digit raised from 14 sp to 18 sp semibold in the UI face after the
      mushaf face turned «۱» into an unreadable slanted stroke.

### Phase 5 — Recitation presets & offline downloads

**Done when:** a user can build several independent presets (verse → translation → tafsir,
verse twice → translation → verse, …), play them over any range, see how much of the Quran
each preset has covered, get one history row per sitting rather than per button press, and
download a reciter's audio verse-by-verse, surah-by-surah or juz-by-juz for offline use.

**Why a new phase rather than more Phase 3 boxes:** Phase 3 was one reciter, one ayah at a
time. This phase adds a *recipe* language, a second audio source (TTS), a coverage ledger and
a download manager — none of which fit inside "per-ayah playback".

- [x] **Domain, pure and tested** (`domain/preset/`): `Preset`/`PresetStep` (mirrors
      `back/models/preset.ts` field for field), `PresetSequenceBuilder` (flattens a preset over
      a verse range into resolved cues), `PresetSittingTracker` (one row per sitting; presses
      become counters), `PresetCoverage` (high-water-mark merge, contiguous ranges).
      **Verified:** `PresetSequenceTest` (16), `PresetSittingTest` (10), `PresetCoverageTest`
      (13) — 39 tests, 0 failures. The two recipes from the brief are asserted verbatim.
- [x] Room v2: `presets`, `preset_sessions`, `preset_ayah_progress`, `downloads` + DAOs,
      repositories, and Lesan sync for presets (LWW), sittings and progress.
      **Verified:** `PresetPersistenceTest` (11 tests) against real SQLite; migration SQL
      diffed byte-for-byte against Room's generated schema by `tools/verify-room-migration.py`.
- [x] Preset playback: `RecitationEngine` for `ayah` cues, Android `TextToSpeech` for
      `translation`/`tafsir` cues, gaps as real waits; wired into `ReaderViewModel` and the dock.
      **Verified:** `PresetPlayerTest` (10 tests) with a fake engine and a fake speech engine;
      `./gradlew assembleDebug testDebugUnitTest` BUILD SUCCESSFUL, zero warnings.
- [x] Manifest-driven offline downloads (verse / surah / juz) with WorkManager, storage
      accounting and a downloads screen.
      **Verified:** `DownloadDomainTest` (23), `DownloadPersistenceTest` (7) against real
      SQLite, `DownloadsScreenshotTest` (3) in both themes and both locales.
- [x] Presets list + editor screens, per-preset coverage card in History, fa/en strings,
      navigation entries, screenshots.
      **Deviation:** coverage lives on the **presets** screen, not in History — coverage is a
      property of a preset ("how much have I heard with this recipe"), while History answers
      "what did I do on Tuesday". Two screens, two questions.
      **Verified:** `PresetsScreenshotTest` (3) in both themes and both locales.

### Phase 6 — Accounts, juz audio & download management

**Done when:** the app opens on a designed gate and signs in against the local server, Shakernejad's
juz audio plays from the backend mirror and is cached on play, and the reader can see, scope and
delete exactly what is on the device.

**Why a new phase rather than more Phase 5 boxes:** Phase 5 made downloads *possible*; this phase
makes them *usable*. It adds a second identity (the account), a second granularity (the juz), and
turns a download form into a management screen — three changes that touch the shell, the queue, the
manifest contract and the dock at once.

- [x] **Account gate (D21)** — `AuthState { Restoring | SignedOut | SignedIn }`, `AuthRepository`,
      `AuthApi`/`LesanAuthApi`, `IdentityRepository`, `DeviceRegistrar` deleted.
      **Verified:** `AuthWireTest` (13), `AuthRepositoryTest` (13), `AuthGateLaunchTest` (2).
- [x] **The gate, designed** — `AuthMedallion` (drawn eight-point khatam + glow), hand-built
      `GlassField` with the label above the box, **two password fields each with its own eye**
      (`PasswordReveal`), a local mismatch check that never reaches the wire, and the error as a
      tinted strip. `GlassSegments` replaces `SegmentedPill` (glass palette, filled selection).
      **Verified:** `AuthViewModelTest` (16) incl. `a_mismatched_confirmation_never_reaches_the_server`
      and `a_stale_confirmation_cannot_block_a_sign_in`; `AuthScreenshotTest` (4 captures, incl. the
      mismatch state).
- [x] **Backend audio mirror** — Parhizgar `range 1:1–2:29` (36 files, 4.47 MB) and Shakernejad
      `juz 1` (1 file, 12.65 MB) stored under `/uploads/recitation/…`, served statically, resolved
      from a server-relative template so no address is baked into the asset.
      **Verified:** live `getDownloadManifest` for both editions (`fileCount 36` / `1`,
      `mirroredFileCount 36`), `static:200` on both files. See `back/AGENTS.md` §5.4.
- [x] **Juz-granular playback (D22)** — `ReciterGranularity`, `RecitationQueue.buildJuz`,
      `playingJuz`, juz-shaped dock row, no verse progress from a juz track.
      **Verified:** `RecitationQueueTest` (14, five of them juz), `AyahNumberingTest` (10, incl. the
      juz table summing to 6236), `DockNowPlayingTest` (1, title and tile name the same juz).
- [x] **Auto-download on play (D23)** — `AudioAutoCache`, fired from `setQueue`/`cacheForPlayback`.
- [x] **Downloads as a management screen (D24)** — four scopes, two page sliders, a "first five
      pages" chip, live ayah count and size estimate, holdings grouped per edition with a coverage
      bar and per-scope / per-edition delete.
      **Verified:** `DownloadDomainTest` (26), `DownloadPersistenceTest` (8), `DownloadsScreenshotTest`
      (4, incl. the juz-only collapse).
- [ ] Shakernejad per-ayah audio — still no public source; the mirror holds juz 1 only.

### Phase 7 — Preset playback that works, download-from-anywhere, full-screen player

**Triggered by the owner (2026-11-16):** "download audios for تلاوت is not working when I click play
on floating windows; make حامد شاکرنژاد the default قاری; the play preset is not right in backend and
mobile; selecting a Telavat via a preset must pin the reciter and, if that reciter's audio is not
downloaded, auto-download + play it verse-by-verse (same for ترجمه‌گویا / تفسیر‌گویا); the app crashes
when I pick a preset from the mini-player; and tapping the mini-player should open a beautiful
full-screen player."

**Owner decisions (this session):**
- **Shakernejad default vs verse-by-verse:** he has *no* per-ayah source anywhere (re-confirmed against
  iranseda / meshkat / ramezan). The app must "have anything we want" sourced from the **Ketab Mobin**
  ecosystem (`ketabmobin.com`), with **ترجمه‌گویا** (spoken translation) and **تفسیر‌گویا** (spoken tafsir)
  as *audio*, not TTS. The backend is to be set up **once** to hold/serve all of it (recitation +
  tarjomeh-guya + tafsir-guya) so the app just downloads from the backend.
- **Sequencing:** crash fix first as its own increment, then the rest.

**Plan of record (each box is its own verified increment):**
- [x] **Preset-selection crash (D25)** — the preset run loop drove the Media3 engine on the IO
      `@ApplicationScope`; ExoPlayer threw `Player accessed on the wrong thread` and the process died.
      Fixed by marshalling every engine call in the loop onto an injected `@Main` dispatcher and making
      the loop exception-safe (a throwing cue ends the run as `failed`, never a crash). Also stopped
      the reader's plain-queue mirror from blanking the dock/highlight while a preset runs.
      **Verified:** `assembleDebug` green (Hilt graph resolves `@Main`); `PresetPlayerTest` now 11
      (added `an_engine_that_throws_ends_the_run_as_failed_not_a_crash`); full suite **297/297**.
- [x] **Reciter-pinned presets + per-ayah auto-download-then-play (D26)** — `onPlayPreset` now honours
      `preset.reciterKey` via a pure `presetVoice(...)` resolver (pinned per-ayah → current selection
      if per-ayah → catalogue per-ayah default; a pinned juz-only voice like Shakernejad is never
      substituted into a verse queue). Each recitation cue resolves its URL **disk-first, then
      download-and-wait, then stream** through a new `VerseAudioProvisioner` seam, so a verse the
      reader has never cached is fetched and awaited before it sounds — verse by verse — while a
      verse that cannot be provisioned still streams rather than stalling.
      **Verified:** `assembleDebug` green, zero main warnings; `PresetVoiceTest` (5) + a new
      `PresetPlayerTest` case `a_verse_that_is_not_on_disk_is_provisioned_and_played_from_the_file`;
      full suite **303/303**.
- [ ] **Backend content pipeline for guya audio (D27)** — generalise the catalogue + mirror to carry
      recitation, ترجمه‌گویا and تفسیر‌گویا per-ayah audio (seed once, serve via the manifest). Research
      the Ketab Mobin / Tafsir-e Guya sources first; this amends `back/AGENTS.md` §1 D2 (audio-only,
      never sacred text).
- [x] **Default reciter = Shakernejad (D28)** — flipped the catalogue default in both places: the
      mobile asset `recitations-v1.json` `defaultReciter` → `shakernejad`, and the backend `seed.ts`
      `isDefault` moved from `parhizgar-murattal-ayah-48` to `shakernejad-tartil-juz-24`. His
      granularity stays honestly **juz**: plain listening plays whole-juz files (D22), and a
      verse-by-verse preset falls back to a per-ayah voice (D26 `presetVoice`) rather than pretending a
      juz MP3 is a verse queue. Splitting his juz files to per-ayah is deferred to D27.
      **Verified:** mobile `assembleDebug` + JVM suite **306/306** green (no test asserts the default;
      the `parhizgar` test fixtures are unrelated index lookups); backend `deno check seed.ts` + `deno
      fmt --check` + `deno lint` pass. **Owner action:** re-run `deno task seed` to apply the backend
      `isDefault` to the DB (idempotent upsert); the app default itself is already baked into the APK.
- [x] **Download-from-anywhere (D29)** — an explicit download action now sits in the reader's floating
      selection box (a second `SelectionAction` beside Play) and in the reader's top bar (a download
      bubble next to settings), in addition to the player's "download this page". All three call the
      one `downloadCurrentPage()` → the same page-scope logic the silent auto-cache uses, so the
      affordances can never disagree about what "this page" means for the current voice. A drawn
      `TransportIcons.Download` glyph (DESIGN §8), no icon dependency added.
      **Verified:** `assembleDebug` + JVM suite **306/306** green; `reader-shell.png` re-inspected —
      top bar's two bubbles spaced cleanly, action box shows Play + Download with no internal clipping.
      (The box floating over adjacent translation text is the documented §10.6 behaviour, not a
      regression: it must not cover the *selected* verse, which it does not.)
- [x] **Full-screen now-playing player (D30)** — tapping the mini-player now opens a full-screen
      `NowPlayingScreen` (the small `RecitationSheet` is retired) showing the current verse on the
      opaque reading surface (mushaf face, independently colorable inks), its translation, the running
      preset's step, a determinate progress bar + clock, transport (prev/play/next), speed + repeat, a
      wrapping reciter switcher, a wrapping preset picker, a "download this page" action, and a
      while-playing equaliser pulse. The shell hides the dock while it is open (a single
      `NowPlayingStore.expanded` flag); position is read on a while-visible ticker in a leaf (DESIGN
      §15), never pushed through the app-wide store. Engine gained `currentPositionMs()/currentDurationMs()`
      (interface defaults, so fakes are unaffected).
      **Verified:** `assembleDebug` green, zero main warnings; `NowPlayingScreenshotTest` (3: light-fa,
      dark-fa, dark-en) — PNGs inspected, chip rows wrap with no clipping (the first pass had a
      horizontal-scroll row that clipped the last chip; fixed by switching to `FlowRow`); full suite
      **306/306**.
- [x] **Mobile consumes the media catalogue — guya audio in presets (D27b)** — the app now reads
      `mediaEdition/gets` (`MediaCatalogueClient`/`Repository`, in-memory cache) and a preset's
      `translation`/`tafsir` step plays **real narrated guya audio** for the verse when the backend
      has a verified edition, falling back to on-device TTS when it does not (never skipped).
      `PresetPlayer` gained a `GuyaAudioSource` seam + a shared `playUrl` (recitation and guya audio
      now play the same way); `MediaGuyaAudioSource` resolves disk-first → server-relative stream,
      ayah-granularity only. Bound in `DownloadModule`.
      **Verified:** `assembleDebug` + `compileDebugKotlin` **zero warnings**; `MediaCatalogueTest` (7:
      selection policy + DTO decode) + `PresetPlayerTest` +2 (`a_translation_step_plays_guya_audio_
      when_available_instead_of_tts`, `..._falls_back_to_tts_when_no_guya_audio_exists`); full suite
      **315/315**. **Deferred (D27c):** offline download-then-play of guya (enqueue via a media
      manifest) — the disk-first branch is the seam it fills; streaming from the backend works today.
      **Device/file-gated:** real guya playback needs the owner to drop the per-ayah mp3s + mark the
      edition verified; until then every translation/tafsir step uses TTS (the tested fallback).

### Phase 8 — Playback range: to the end of the surah, or only what I selected

**Triggered by the owner (2026-11-17):** "Now I can play the Quran. I want to continue playing the
Quran when I don't select multiple ayah or tarjomeh, until the end of the surah. When I select some
ayah or tarjomeh or tafsir, I want to play just those I selected."

**Owner decisions (this session):**
- **D31 — Plain play is surah-scoped.** A play with no multi-verse selection queues the starting
  ayah through the **end of its surah** (bounded by the bundled text while only 5 pages ship —
  the same code covers a whole surah when the full mushaf lands). The old "audio follows the page
  on screen" re-queue is retired: the queue runs on, and follow-along scrolling keeps the reciting
  ayah visible. Auto-cache extends a page ahead as the queue crosses pages.
- **D32 — A selection plays only the selection.** When the selection covers two or more verses,
  or any translation/tafsir layer, the action box offers **Play selection**:
  recitation-only → a plain queue of exactly those verses (juz reciters: the juzes containing
  them); with spoken layers → an **ad-hoc preset run** (verse → each selected tarjomeh → each
  selected tafsir, per verse) through the existing `PresetPlayer`, **not persisted** — no preset
  row, no sitting, no coverage ledger entries; a one-shot listening, not a recipe.
- Single-verse play keeps its label ("Play ayah N") but now means "from here to the end of the
  surah"; the selection action is a different label so the two promises never blur.

**Plan of record (each box is its own verified increment):**
- [x] **Surah-wide queue (D31)** — `RecitationQueue.build/buildJuz` take per-verse pages
      (`VerseItem`), `ReaderViewModel` builds from the starting verse to the surah's end, the
      scroll re-queue in `onPageVisible` is removed, and the reciter-switch rebuild keeps the
      surah scope.
      **Verified:** `assembleDebug` green zero warnings; `RecitationQueueTest` updated to the
      `VerseItem` signature + a new `a_surah_wide_run_spans_pages_in_reading_order` (asserts each
      track carries its own page); full suite **322/322**. Device: plain play from 2:11 ran to
      2:20, crossing the page-3/page-4 boundary (2:16|2:17) with the highlight following —
      impossible under the old page-scoped queue.
- [x] **Play selection (D32)** — pure `selectedPlaybackPlan()` on `VerseSelection` (ordered
      verses + union of layers), `ReaderViewModel.onPlaySelection` (plain queue or ad-hoc preset
      sequence), the action box swaps Play→Play selection when the plan is a selection, strings
      fa/en.
      **Verified:** `assembleDebug` green; `VerseSelectionTest` +5 (empty / single-verse not a run
      / mushaf order / spoken-layer / per-layer collection); full suite **322/322**. Device: a
      two-verse pick showed "Play selection", played exactly those two (`AudioTrack state:started`
      → advanced 1→2 → stopped, dock held at "Ayah ۲", 0 players started after).
- [x] **Instrumentation cleanup** — remove the `HawraaQA` logs from `Media3RecitationEngine` and
      the debug `QaPlayerActivity` probe left from the "nothing plays" hunt.
      **Verified:** no `HawraaQA`/`QaPlayerActivity` references remain; `clean assembleDebug` green.

## 4. Verification gates (run before ticking any box)

```sh
cd mobile/android
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug && ./gradlew testDebugUnitTest
# Screenshots of the reader + settings land in app/build/screenshots/ (no device needed).
# For UI boxes: install on device/emulator; check both themes AND fa-RTL + en-LTR.
```

## 5. Conventions reminder

Offline-first local write; record every session; append-only after finalize; `clientId` idempotency;
integer seconds / epoch millis; no color literals outside `ui/theme/`; no strings outside resources;
no hardcoded direction; pure logic in `domain/`; single-owner repositories. Full rules:
[`../../AGENTS.md`](../../AGENTS.md). Build quirks: [`../summary.md §4`](../summary.md#4-build-environment-this-machine--read-before-building).

---

## Progress log

> Append one entry per small task. Newest at the bottom. Format: date — what changed — verification.

- **2026-11-12** — Phase 0 skeleton authored: Gradle (AGP 9.5.1 built-in Kotlin, version catalog,
  wrapper), Compose + Material 3 light/dark theme, fa-RTL/en-LTR i18n, Room (ReadingSessionEntity/DAO),
  Hilt DI, Ktor LesanClient, DataStore SettingsRepository, WorkManager SyncWorker stub, and
  Home/Reader/History/Settings navigation with a persisted theme switcher. Resolved build blockers:
  Aliyun mirror ordering (502), manual SDK install + android-37.0 package.xml, compileSdk 37,
  material-icons-core, and the moved hiltViewModel package.
  **Verified:** `./gradlew assembleDebug` BUILD SUCCESSFUL; `testDebugUnitTest` 4/4 pass; zero warnings;
  `./gradlew` wrapper path works. **Next:** owner on-device check, then Phase 1 content + typography spike.

- **2026-11-14** — Content pipeline. `tools/build-quran-assets.py` compiles Tanzil Uthmani text +
  Tanzil `fa.ansarian` translation + Quran.com v4 page/juz/hizb/sajdah metadata into the versioned
  asset `assets/quran/pages-v1.json` (5 pages, 36 verses, 26 KB, schema v1). Checksums and licences
  are recorded inside the asset. Found and handled a real source quirk: Tanzil embeds the basmala
  **inside** the first ayah of every surah that has one, so the script asserts the exact prefix and
  strips it (recorded under `transformations.basmalaStrippedFromSurahOpening`), because the reader
  draws the basmala as its own line. **Verified:** asset parses, 2:1 is `الٓمٓ`.

- **2026-11-14** — Typography spike (D5). Tested the "draw full text, then redraw with marks stripped"
  trick with CoreText (looked perfect) and then with HarfBuzz, which is what Android actually uses:
  **it does not hold** — removing marks changes glyph selection (the "لِلَّهِ" ligature) and advances,
  so layers drift. Re-tested Scheherazade New, Amiri, Amiri Quran and KFGQPC Hafs: none survives.
  Chose Scheherazade New (OFL, closest visual match to the owner's Ketab Mobien reference, full
  Uthmani coverage) and split the **font** instead: `tools/build-quran-fonts.py` derives
  `quran_letters.ttf` (mark outlines emptied) and `quran_marks.ttf` (letter outlines emptied) from
  one source face, leaving every shaping table and metric untouched.
  **Verified:** both derived faces shape all 36 verses to the identical glyph run and advances as the
  source; contour accounting is an exact partition (64 = 54 + 10) — no contour lost or duplicated.

- **2026-11-14** — Reader + reading settings. `QuranText` (two stacked layouts, mark layer under
  letter layer), `AyahOrnament` (8-lobe rosette drawn as geometry, Persian digits), `MushafBanner`
  (surah/juz plate), swipeable page pager with scroll-per-page, Ansarian translation under each verse,
  `SegmentedPill` controls, and persisted reading settings (font size 22–44 sp, translation on/off,
  letter ink ×3, diacritic ink ×4) wired through `SettingsRepository`. Warm mushaf palette replaced
  the placeholder colours; Material scheme now maps primary/secondary/tertiary to green/gold/bronze.
  Also added the JVM screenshot gate (D8, Robolectric native graphics).
  **Verified:** `assembleDebug` green, zero warnings, 16/16 unit tests; screenshots of the reader in
  light/dark/custom-ink and of page 2 (standalone basmala) and of the settings screen in both themes.
  **Next:** session tracking (Phase 2) and the multi-translation/commentary layout.
- **2026-11-14** — Session tracking + sync (Phase 2). `domain/session/` holds a pure state machine
  (`ReadingSessionTracker`) with no clock, Android or I/O: idle gaps bill at most 90 s, checkpoints
  fire at most once per 10 s of accumulated activity (anchored to the last checkpoint, so one long
  burst cannot emit several), the ayah range grows monotonically, and a finish requires 3 s of dwell
  so swiping through five pages in two seconds cannot record five "read" pages. Fixed three defects
  found while testing it: the idle-resume branch of `accumulate` was a no-op, `merge` carried dead
  code on its backward-surah path, and `CHECKPOINT` re-fired on every event after the first 10 s.
  `ReadingSessionRepository` turns effects into Room rows (`checkpoint` query added), freezes the
  settings snapshot at open time, and recovers orphans at launch. `data/sync/` gained the real engine:
  `registerDevice` (idempotent, `get: {token:1}`) → `syncReadingSessions` in batches of 50 with
  `pending→syncing→synced|failed`, one re-register retry, periodic 6 h work plus an immediate push
  after each finalize. Reader events are pager-settle, page-scroll and `ON_STOP` only — no timers;
  `onCleared` finalizes on the application scope so a popped screen still closes its row.
  **Verified:** `compileDebugKotlin` clean, 25/25 tracker tests, full `testDebugUnitTest` green.
  **Next:** verse highlighting, then the multi-translation/commentary pane layout.

- **2026-11-14** — Verse highlighting + History. Tapping a verse washes it in `quran.selection`, draws
  a 3 dp bar on the leading edge (right in RTL), fills its rosette and reveals its translation even
  when translations are off; the tag `verse-<key>` makes it screenshot-testable. History is now real:
  `domain/session/SessionStats.kt` computes totals, distinct pages, completed count, day buckets and a
  streak against a **supplied** "today" (so it is deterministic in tests, 13 new cases), and
  `HistoryScreen` renders a summary card plus day sections with surah name, ayah span, duration and
  status. Removed hardcoded Persian strings from that screen. Also closed a real tracking hole:
  Reader → Settings kept the activity started, so `ON_STOP` never fired and up to 90 s of settings
  time could be billed to the session — the reader now finalizes on composition exit.
  **Verified:** `assembleDebug` green, zero warnings, **55/55** unit tests; `history-light.png` and
  `history-dark.png` inspected; `reader-selected-verse.png` inspected.
  **Next:** multi-translation/commentary layout (needs more content), then Phase 3 recitation.

- **2026-11-14** — Home + language switcher. Home is no longer a placeholder: the wordmark is set in
  the mushaf face at 64 sp, and below it a "pick up where you left off" card (last session's page and
  surah), a today/streak card driven by real sessions, the primary read action, and History/Settings.
  "Continue reading" needed a start page, so `Routes.reader(pageIndex)` passes a 0-based index that
  `ReaderViewModel` reads from `SavedStateHandle` and clamps against the loaded page list, and the
  pager jumps to it once content arrives (not via `initialPage`, which would be applied while
  `pageCount` is still 0).
  Language switching: `AppLanguage` + `LocalizedApp` provide a locale-specific context and layout
  direction to the whole tree, with no appcompat dependency; `MainActivity` wraps the theme in it.
  Two pitfalls hit and fixed: `settings_language` already existed (resource merger error), and a test
  cannot call `setContent` twice in one method.
  **Verified:** `assembleDebug` green, zero warnings, **59/59** unit tests; `home-light.png` /
  `home-dark.png` and `settings-light.png` inspected; `LocalizedAppTest` proves the override really
  translates and flips direction rather than only persisting a preference.
  **Next:** Phase 3 recitation (Shakernejad audio + follow-along highlighting), then the
  multi-translation/commentary layout once a second translation and a commentary are in the asset.

- **2026-11-15** — Launch crash, landing page, real icon, silent letters.
  1. **The Persian crash (D9).** `LocalizedApp` replaced `LocalContext` with
     `createConfigurationContext`, which returns a bare `ContextImpl`. `hiltViewModel()` walks the
     `baseContext` chain looking for an Activity and threw
     `IllegalStateException: Expected an activity context for creating a HiltViewModelFactory but
     instead found: android.app.ContextImpl` — so the app died as soon as Persian became active, and
     kept dying on every launch because the choice was persisted. Reproduced by launching the real
     activity under Robolectric with the DataStore protobuf seeded, and by capturing the
     process-wide uncaught handler (a plain `buildActivity().setup()` looked fine because the throw
     happens inside the recomposer coroutine). Fixed with a `ContextThemeWrapper` + override
     configuration, and `Locale.setDefault` was removed — it had been turning `"%013d".format(...)`
     into Persian digits inside the session primary key. `MainActivityLaunchTest`,
     `LanguageSwitchThroughUiTest`, `LocalizedAppTest`, `SettingsRepositoryTest` are the gates.
  2. **Silent letters (D10).** `SilentLetters` + a `SpanStyle` in `QuranText`; 11 tests including an
     invariant sweep of all 36 verses.
  3. **Ayah marker (D11).** Delapouite's "Rub el Hizb" (CC BY 3.0) as a vector drawable, path
     normalised to absolute and its central ring dropped; attribution in Settings.
  4. **Landing page.** Hero, continue card with real verses, today strip, one primary action.
  **Verified:** `assembleDebug` green, zero warnings, **80/80** tests, APK 52.7 MB, 11 screenshots
  regenerated and inspected. `git`-clean `testDebugUnitTest --rerun-tasks` used because the build
  cache restores results without re-writing the PNGs.

- **2026-11-15** — Phase 3: recitation.
  1. **Source first.** Shakernejad has no public per-ayah audio: probed `cdn.islamic.network`
     (`ar.shakernejad`), `verses.quran.com` and `everyayah.com` with four spellings each, Quran.com
     v4's recitation list, mp3quran v3 (244 reciters) and quranicaudio (177 qaris). Nothing. Rather
     than ship a dead URL, the catalogue is a bundled asset (D13) with four **verified** reciters —
     Parhizgar (default, Persian murattal), al-Afasy, al-Husary, al-Minshawi — and Shakernejad
     recorded under `"unavailable"` with the probe results. `tools/verify-recitation-audio.py` is the
     gate that keeps a dead template from shipping: ranged GET, 206 + mp3 frame sync, 4 ayahs per
     reciter (1, 2:255, 2:286, 114:6).
  2. **Global ayah numbering is the join key.** Per-ayah CDNs name files by the global ayah number, so
     `AyahNumbering` holds the Hafs table (total pinned to 6236, inverse round-trip for all 6236, and
     cross-checked against the asset's own `ayahCount`s). A wrong entry here would play a neighbouring
     verse with nothing on screen to show it.
  3. **One page = one queue.** `Media3RecitationEngine` sets a page's verses as single-item media
     sources, so the current item index *is* the current ayah — the follow-along highlight rides
     `onMediaItemTransition` and no timer or position poll is introduced. Playback streams through a
     200 MB `SimpleCache` (D12). `RecitationEngine` is an interface, so the ViewModel never imports
     Media3.
  4. **Listening is a session.** Pressing play upgrades the open row to `mode="listen"` and freezes
     `reciterKey`/`playbackSpeed` into the snapshot; every ayah transition feeds `progress()`, so
     listening bills time and grows the range through the same pure tracker as scrolling. A swipe
     while playing re-queues the new page (audio follows the page on screen); navigating to Settings
     keeps audio playing (`onScreenLeft`) while `ON_STOP` pauses it, because there is no foreground
     service yet.
  5. **UI.** A transport bar (previous / play / next + speed + repeat chips) under the page, a green
     playing wash with a leading bar and a filled rosette, `BringIntoViewRequester` for follow-along
     scrolling, and a reciter picker in Settings. Transport glyphs are built from Material's own 24 dp
     outlines (`TransportIcons`) because `material-icons-core` only ships `PlayArrow`.
  **Verified:** `assembleDebug` green, zero warnings, **106/106** tests (+26), APK 52.7 MB;
  `reader-recitation.png`, `reader-recitation-dark.png`, `settings-recitation.png` inspected; both
  attribution lines asserted present in the product.
  **Next:** multi-translation/commentary layout (needs content), then background playback via
  `MediaSessionService` if the owner wants audio to survive leaving the app.

- **2026-11-15** — Continuous scroll + per-pane selection (D14, D15). Two owner requirements, one
  layout change:
  1. **Independent state for verse and translation.** Replaced the single "selected ayah" with
     `VerseSelection` — a set of `VerseElement(surah, ayah, layer, key)` (`layer ∈ VERSE /
     TRANSLATION / COMMENTARY`) plus one `active` element (last tap) for the peek and for "play from
     here". `VerseBlock` is now a stack of independent `Pane`s, one for the Arabic and one per
     translation, so tapping either highlights only that one. This is what the planned
     three-translations-plus-commentary layout needs; the state half of that box is now done.
  2. **No pagination.** Deleted the `HorizontalPager`, `PageBar`, the page counter and its three
     strings. `ReaderItems.build` flattens pages into `SurahHeaderItem` / `BasmalaItem` / `VerseItem`
     rendered by one `LazyColumn`; the visible page is a `derivedStateOf` lookup on
     `firstVisibleItemIndex`, and the reading clock is driven by a `snapshotFlow` on the scroll
     position (still no timers). Follow-along scrolls only when the reciting ayah is actually off
     screen, so a scrolling reader is never yanked; `BringIntoViewRequester` was removed because
     inside a lazy list it could fight `animateScrollToItem`.
  3. `QuranVerse.translations: List<VerseTranslation>` (keyed) with `translationFa` kept as a derived
     convenience; the DTO accepts both shapes so the asset can keep emitting the old field. Reverted
     an over-eager change that defaulted `juz`/`hizb`/`rubElHizb`/`sajdah` — a corrupt asset must
     fail loudly, not silently parse as juz 1.
  **Verified:** `assembleDebug` green, zero warnings, **126/126** tests (+20: `VerseSelectionTest` ×10,
  `ReaderItemsTest` ×8, 2 new reader screenshots), APK 52.9 MB; `reader-selected-verse.png` (Arabic
  washed, translation plain) and `reader-selected-translation.png` (the reverse) inspected side by
  side, plus `reader-page-boundary.png` showing al-Fatiha running into the al-Baqarah plate with no
  break and no page number. Two stale assertions fixed rather than deleted: `LocalizedAppTest` still
  expected "Page 1 of 5" and `PersianLaunchTest` still expected the Persian page label — both now
  assert on the juz marker, which is the chrome that actually survives.
  **Next:** the multi-pane layout once a second translation and a commentary land in the asset.

- **2026-11-15** — Glass design language + app shell (D16, D17). Three owner requirements, one
  rebuild of the UI layer:
  1. **Leave the Quran-app look.** New tokens (`Color.kt` raw palette, `GlassColors`, `QuranColors`,
     `Geometry`), a `GlassSurface` recipe (fill → 2 dp specular sheen → hairline, deliberately no
     `RenderEffect` blur), an `AmbientCanvas` of three static blooms, and `docs/DESIGN.md` as the
     contract — referenced from `AGENTS.md`, `summary.md` and this plan so it is read before any UI
     work. The mushaf plate lost its green gradient and gold border (a flat `bannerFill` pane with a
     double rule and terminal diamonds); the rosette now carries a *hue* rather than a boolean,
     because selection (`mark`) and playback (`accent`) are different meanings.
  2. **A floating box over the selection.** `SelectionActionBox` anchored to the focused verse's
     `boundsInRoot()` — above it when there is room, below it otherwise, pinned above the dock as a
     last resort, hidden when the verse scrolls away. Actions are a `List<SelectionAction>`, so the
     glossary action is an entry, not a layout change; play is only added when the catalogue loaded,
     so there is no dead button.
  3. **Menu bar + mini-player, as in `Pejvak/mobile`.** `AppDock` is **one panel with two halves**
     (58 + 58, seam inset 12, one radius, one shadow) that collapses to nav-only when nothing is
     playing — a permanent empty row would be a permanent lie about the dock's size. Four icon-only
     peers, selected = filled accent disc with the glyph knocked out and a 3 dp rim light; no labels,
     no raised centre action. The reader no longer draws a bottom bar at all; it publishes a
     `NowPlaying` snapshot into a `@Singleton NowPlayingStore` and binds the transport callbacks, so
     the shell can own the dock without owning the engine. `RecitationBar` is deleted; speed/repeat
     moved into `RecitationSheet`, opened from the mini-player through a one-shot `SharedFlow`.
  4. Three defects found by **measuring the rendered PNGs** rather than eyeballing them, and fixed:
     the strong glass fill let the page's translation through at **4 % legible** (92 % → 96 % → 98 %,
     dark 90 % → 94 % → 98 %; the ghost is now 1.6 % / 3 lum); the identity tile's digit was 14 sp and
     vanished (18 sp semibold in the UI face — the mushaf face draws «۱» as a slanted stroke that
     reads as a smudge at tile size); and the dock screenshot fixture passed `Icons.Filled.Settings`
     for both *History* and *Settings*, which meant the hand-drawn History clock had never
     been rendered by any capture.
  5. Two more found only because the screen captures were made honest. Every screen is transparent by
     design (the shell paints the canvas), so `HomeScreenshotTest`/`HistoryScreenshotTest`/
     `SettingsScreenshotTest` were photographing a white window the app never shows. They now compose
     through a shared `TestShell` (canvas + screen + dock, with the shell's real glyphs and labels),
     which immediately exposed a hard white seam along the bottom of Home's hero: its lattice faded to
     `quran.page` while sitting on the grey canvas. The lattice is now stroked with a gradient brush,
     so it fades into the canvas by itself.
  **Verified:** `assembleDebug` green, **129/129** tests (2 new: the idle dock and the dark dock),
  APK 49.8 MB; `reader-shell.png`, `reader-shell-idle.png` (nav-only height, no leftover seam),
  `reader-shell-dark.png` inspected, plus the existing mushaf captures. The one remaining build
  warning is the manifest merger's spurious
  `meta-data#androidx.work.WorkManagerInitializer … no other declaration present`: the removal **does**
  apply (the merged manifest contains no initializer) and it is load-bearing — without it WorkManager
  would initialise with the default configuration and ignore `HawraaApp`'s `Configuration.Provider`.
  Dropping `tools:node="merge"` from the provider changes neither the merge nor the warning, so the
  node stays and the warning is accepted rather than "fixed" by deleting it.
  **Next:** the multi-pane layout once a second translation and a commentary land in the asset.

- **2026-11-15** — Phase 5 started: the preset **domain**, pure and tested before any UI or
  audio work (`domain/preset/`).
  1. **`Preset`/`PresetStep`** mirror `back/models/preset.ts` field for field, because presets
     travel between devices through the backend and a mapping surprise would silently change
     someone's recitation on their other phone. `sanitized()` is the tolerance boundary: a
     preset from a newer server or an older build is clamped and its impossible steps dropped,
     but never *played wrong* — a translation step with no key is removed rather than read as
     silence, and a blank name falls back to the id rather than to an empty row.
  2. **`PresetSequenceBuilder`** is the heart. It folds a preset over a range of verses into a
     flat list of resolved cues, so the player never re-derives a repeat, a gap, a speed or a
     text. Two decisions shaped it: gaps are carried as `gapBeforeMs` on the *next* cue (a
     trailing gap then cannot exist), and missing translations are **reported**
     (`missingTextKeys`) instead of being silently skipped — a preset that asks for a
     translation the bundle does not carry must be able to say why it sounds shorter.
     Input is `VerseItem` rather than `QuranVerse` because a cue needs the page and a verse
     does not know its own page. The pages overload is `buildForPages`, not `build`: both
     erase to the same JVM signature.
  3. **`PresetSittingTracker`** implements D19: `event()` only ever *adds to* a sitting, and
     only `start`/`finalize` change the row count. Time is billed only while `running` and is
     clamped to a 90 s idle gap, so a paused user who walks away is not recorded as listening
     for an hour. Counters stay exact even after the 200-event cap trims the timeline.
  4. **`PresetCoverage`** merges with high-water marks and treats `firstAt = 0` as *unknown*
     rather than as the beginning of time, so a client that never recorded a stamp cannot win
     the `min`. `ranges()` coalesces consecutive ayahs into runs that may cross a surah
     boundary, which is what "the specific parts I have listened to" actually looks like.
  **Verified:** `PresetSequenceTest` (16), `PresetSittingTest` (10), `PresetCoverageTest` (13)
  — **39 tests, 0 failures**. Three initial failures were all mine, not the code's: a lambda
  that did not convert digits, an `ayahs()` compared against bare ints, and a `verseCount`
  expectation that contradicted the documented meaning of the field.

- **2026-11-15** — Phase 5, part 2: **Room v2 and the sync path**.
  1. **Four new tables** (`presets`, `preset_sessions`, `preset_ayah_progress`, `downloads`) and
     a real `MIGRATION_1_2` rather than `fallbackToDestructiveMigration`. The reason is not
     tidiness: `reading_sessions` is the user's reading history, and a version bump that wiped
     it would be the one data loss this app cannot apologise for. Room does not check a
     migration's SQL at compile time — it fails at runtime, on the upgrade, on the device —
     so `tools/verify-room-migration.py` diffs the hand-written statements against
     `app/schemas/…/2.json`, the schema Room itself would create. **8 statements, exact match.**
  2. **Steps and events are JSON columns, not child tables.** A preset is always read, written
     and played as a whole; no query wants "step 3 of some preset", so a child table would buy
     nothing and cost a join plus ordering bookkeeping. The same DTOs are used for the JSON
     column *and* the Lesan payload — one shape for both, so a step cannot round-trip through
     the server unchanged but through local storage lossily.
  3. **The "one row" rule is enforced in SQL**, not in the caller: `PresetSessionDao.updateProgress`
     increments `playCount`/`pauseCount`/`seekCount` on the open row and is guarded by
     `endedAt IS NULL`, so a late player callback cannot reopen a closed sitting.
  4. **`setDefault` bumps `updatedAtMs` on the demoted rows too.** A first draft demoted them
     without a new stamp — which would have looked correct locally and then lost the change to
     the client's own older copy on the next sync. This is the subtlest bug of the phase: the
     LWW token is part of *every* mutation, not just the one the user is looking at.
  5. **`PresetSync` handles three streams with three different merge rules**: presets are
     last-write-wins and the client **adopts the server's copy even when it lost** (keeping the
     local row would re-send the losing edit forever); sittings are replayed as
     `start` → one `logEvent` per recorded event → `finalize`, so the server rebuilds its
     counters from the same rules instead of trusting a finished row; coverage is grouped by
     preset because the act takes one preset at a time.
  **Verified:** `PresetPersistenceTest` — 11 tests against real SQLite (one row for six presses,
  append-only after finalize, single default, archive-vs-delete, step round trip, unknown step
  kind dropped, coverage high-water merge, 200-event cap). `./gradlew compileDebugKotlin` green,
  no warnings. Also moved `newClientId` into `data/local/ClientIds.kt` so sessions and sittings
  share one implementation of the id that must never contain Persian digits.

- **2026-11-15** — Phase 5, part 3: **preset playback** (`data/preset/PresetPlayer.kt`,
  `data/recitation/SpeechEngine.kt`).
  1. **Two engines behind one loop.** A cue is either an `ayah` (audio file from
     `RecitationEngine`) or spoken text (Android `TextToSpeech`). Rather than a second player,
     `SpeechEngine.speak()` *suspends until the utterance finishes*, so both kinds of cue look
     identical to the sequential loop: wait the gap, run the cue, ask whether the user paused,
     move on. The alternative — an event-driven TTS listener feeding a state machine — would have
     duplicated the pause/resume/seek logic that audio already had.
  2. **Pause is a state, not a cancellation.** `awaitAudio` waits on `engine.state.first { it.ended
     || it.failed }`, but only after checking `audioStartedFor`. If the user pauses *between*
     cues, the loop parks on a flag and never starts the next file; if they pause *during* a
     cue, the audio is paused and the loop is still waiting on the same cue. Both resume
     correctly, and neither one skips a repeat.
  3. **`onCueFinished` is where history and coverage are born.** The player reports each finished
     cue with its elapsed time; the ViewModel classifies it as `STEP` or `AYAH` by asking the
     compiled sequence whether the *next* cue belongs to a different ayah. That is why the
     sequence is compiled once up front: the classification needs neighbours, and the player
     would otherwise have to know about ayah boundaries it does not care about.
  4. **`stop()` restores `restoreSpeed`.** Playback speed belongs to the user's reader settings,
     not to the preset — leaving a preset at 0.75× would silently change ordinary reading after
     the preset ends, which is exactly the kind of leak that is impossible to attribute later.
  **Verified:** `PresetPlayerTest` — 10 tests (order, repeat, gaps, pause mid-cue, pause between
  cues, next skips the remaining recipe, previous re-enters at the top of the ayah, seek by ayah,
  finished, failure surfaces). `./gradlew assembleDebug testDebugUnitTest` BUILD SUCCESSFUL,
  zero warnings. Two failures along the way were both test-side: `backgroundScope` is **not**
  advanced by `advanceUntilIdle` (fixed with a `playerScope()` helper on
  `StandardTestDispatcher(testScheduler)`), and an assertion that forgot a recipe has four cues
  per verse, not one.


- **2026-11-15** — Phase 5, part 4: **the offline download manager** (`data/download/`,
  `domain/download/`, `ui/screens/downloads/`).
  1. **A persisted queue, not a download call.** The Quran is 6236 files, so "download this
     surah and report progress" is not a shape that survives a phone call. One row per file is
     written *before* any bytes move; the worker walks the queue, and a killed process, a
     reboot or a flight-mode switch resumes at the next `queued` row. Rows are keyed
     `(editionKey, fileKey)` and inserted with `IGNORE`, so asking for surah 2 twice costs one
     download — which is also what makes "retry" mean *retry the failures*, not *start over*.
  2. **The server owns the arithmetic, the client owns the files.** `urlTemplate`, granularity
     and the `relativePath` all come from `recitationEdition/getDownloadManifest`; the app
     stores the URL it was given and never builds one. A template on the client would be a
     second implementation of the same arithmetic, and the two would drift silently — the
     symptom being audio that downloads successfully and is the wrong verse.
  3. **`HttpURLConnection`, not OkHttp.** One ranged GET is the whole requirement, and it is in
     the platform. The resume protocol is deliberately conservative: bytes land in `.part`, a
     `Range` is sent only when `.part` has bytes, and a `200` answer truncates and restarts
     rather than appending — the difference between a correct file and a corrupt one. The
     rename happens only after a complete read, so *a file that exists is a file that finished*.
  4. **The playback index is a snapshot.** "Is this ayah on disk?" is asked once per ayah while
     building a queue, so it is an in-memory lookup rebuilt after each run and at launch. Two
     rules keep it correct: only per-ayah and per-surah files are indexed (a whole-juz MP3
     covers an ayah but cannot *start* at it), and a download only serves a reciter whose
     `style` matches — serving a downloaded *mujawwad* to someone who chose *murattal* is the
     wrong recitation, which is worse than streaming the right one. The style comes from a
     cached catalogue rather than a new DB column, so no migration was needed for it.
  5. **One definition of "prefer the download".** `LocalAudioIndex.urlFor` is used by both the
     reader's queue and the preset player. Two copies of that rule would eventually disagree,
     and the symptom would be audio that works in one screen and not the other.
  6. **The estimate is honest about being one.** The server does not store file sizes, so the
     confirmation line multiplies bitrate by ayahs by a documented nominal 7 s per ayah. It is
     labelled "about", it is used only before a download starts, and the downloads screen shows
     real bytes afterwards.
  **Verified:** `DownloadDomainTest` — 23 tests (scope keys round-trip and reject nonsense,
  granularity rules, catalogue ordering, size estimate linearity and order of magnitude, index
  precedence/style/juz exclusion, URL fallback). `DownloadPersistenceTest` — 7 tests against
  real SQLite (re-request is a no-op, a finished file is not reset, retry keeps successes,
  batch totals, delete is scoped, verse-beats-surah lookup). `DownloadsScreenshotTest` — 3
  captures (fa light, en dark, juz scope) which caught a real bidi defect: `48 kbps · ayah`
  rendered as `kb · ayah 48` in an RTL paragraph, fixed with an LTR text direction on that
  token. `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL, 222 tests, 0
  failures, zero warnings**.

- **2026-11-15** — Phase 5, part 5: **preset screens, and the registration bug they exposed**
  (`ui/screens/presets/`, `data/remote/DeviceRegistrar.kt`).
  1. **The editor offers only what can speak.** `TAFSIR` is not in the add-step list because no
     tafsir text ships in the bundle, so a tafsir step added here would be a step that cannot
     say anything. The model and the server still carry it, and a preset that already has one
     keeps it — the editor just does not invent one. A translation step is created with the one
     bundled key for the same reason.
  2. **Repeat is a stepper, not a slider.** The meaningful range is 1…10 and a tap is more
     precise than a drag for a number that small. It also keeps the row's geometry stable,
     which matters when six steps are stacked.
  3. **The list shows the recipe as a line** (`آیه ×۲ ← ترجمه ← آیه`), because the *order* is the
     feature. Coverage sits on the same card — percentage, bar, and the runs themselves — since
     "how much of the Quran have I covered with this preset" is a question about the preset, not
     about a sitting.
  4. **The registration bug.** Extracting this work surfaced something that had been broken
     since Phase 0: device registration lived *inside* `ReadingSessionSync`, so it was only
     reached when there were reading sessions to push. A fresh install with no history never
     registered, and every feature that needs a token — presets, coverage, the recitation
     catalogue, download manifests — silently did nothing. It is now `DeviceRegistrar`, called
     once at launch, idempotent on `deviceId`, with `refresh()` for the case where the server
     rejects a stored token. This is the kind of defect that never shows up in a unit test and
     always shows up on the first device.
  **Verified:** `PresetsScreenshotTest` — 3 captures (fa light, en dark, editor) which caught a
  real layout defect: a full-width pill with a start-aligned label pushes the text against the
  border in RTL, so both new primary actions are centred. `./gradlew assembleDebug
  testDebugUnitTest` → **BUILD SUCCESSFUL, 225 tests, 0 failures, zero warnings**.

- **2026-11-15** — Phase 6, part 1: **the app was talking to the wrong URL, and nothing said so.**
  The owner asked to run the debug APK on a phone against the backend on this Mac. Preparing that
  turned up three defects that only appear off the emulator:

  1. **The base URL was the origin, not the endpoint.** `BuildConfig.LESAN_BASE_URL` was
     `http://10.0.2.2:1407/` and `LesanClient` posted to it verbatim; Lesan answers
     `501 {"message":"Invalid request: Expected POST to /lesan, got POST to /"}` for anything that is
     not `/lesan`. Every call in the app is wrapped in `runCatching` (correctly — sync failures must
     never reach the UI), so the failure was **completely silent**: the app looked like it was offline
     with a working server. The backend plan had already recorded "Lesan answers at `/lesan` (not `/`
     — that cost an hour)" in Phase 0; the client never got the memo. Now `SERVER_URL` is an origin
     and `LesanClient` appends `/lesan`, in the one place that constructs it.
  2. **The URL was hardcoded**, so a physical device needed a source edit. It now comes from
     `hawraa.baseUrl` in `local.properties` (untracked, per machine), overridable with
     `-Phawraa.baseUrl=…`.
  3. **Cleartext HTTP was refused**, and had been since the app started using a plain-HTTP backend.
     Android 9+ blocks it by default and the exception looks exactly like a dead server. Allowed in
     `src/debug/AndroidManifest.xml` only — a `main`-manifest `usesCleartextTraffic` would ship to
     release and quietly permit unencrypted traffic in production.

  Also landed: the manifest's `mirrorPath`/`bytes` are carried into the domain (`DownloadCodec`
  resolves the mirror against `SERVER_URL`, so nothing absolute is ever persisted), and a finished
  download is now checked against the size the server measured. `expectedBytes` is a schema column
  (`MIGRATION_2_3`), not a transient — a retry happens in a later process, and a check that only
  existed in memory would be gone by then.

  **Verified:** `ManifestSourceTest` (10 cases) covers the two ways a URL goes wrong — mirror ignored,
  mirror resolved wrongly — and the four size outcomes. `DownloadPersistenceTest` proves `null` stays
  `null` rather than becoming `0`, which would fail every CDN download. `verify-room-migration.py`
  gained an ALTER branch and reports `v2 -> v3: exact match`. `./gradlew assembleDebug
  testDebugUnitTest` → **BUILD SUCCESSFUL, 235 tests, 0 failures**; `usesCleartextTraffic=true`
  confirmed in the shipped APK's manifest, `SERVER_URL` confirmed in the generated `BuildConfig`.

- **2026-11-15** — Phase 6, part 2: **the account gate** (D21). The owner asked for login and
  register screens, and for the app to *force* one of them before use so server-side history is
  attributed correctly, while staying offline-first afterwards.

  1. **The gate is a state, not a route.** `AuthRepository` publishes
     `AuthState { Restoring | SignedOut | SignedIn(account) }` and `HawraaShell` collects it:
     `Restoring` draws the canvas alone, `SignedOut` draws `AuthScreen` **and no dock**,
     `SignedIn` draws the app. Making it a route would have left the back stack able to return to
     a screen the reader is not allowed to see, and would have let the dock navigate into the app.
  2. **The cache draws, the server only invalidates.** `restore()` publishes the cached account
     first, then asks `user/me`. It signs out **only** when the answer is a `LesanException` —
     i.e. the server replied and refused. Anything else (no route, DNS, timeout) means "we could
     not ask" and keeps the session, because the app is offline-first and a reader in a lift is
     not a credential problem. A stale answer that arrives after a fresh sign-in is discarded by
     comparing the token it validated against the token that is stored now.
  3. **`AuthRepository.token()` is gated on the state**, not just on the stored value. That is what
     makes "signed out" and "no usable credential" impossible to drift apart: a sync that runs in
     the window after a rejection gets null and does nothing, instead of pushing rows under a
     credential the server has already refused. `ReadingSessionSync` checks it *before* marking a
     batch `syncing`, so a signed-out device leaves its queue untouched rather than stranding rows.
  4. **`DeviceRegistrar` is deleted.** It existed to register an anonymous device, which is exactly
     what the gate replaces; keeping it would have left a path that creates a user nobody signed
     in as. `refresh()` had no meaning without it, so `ReadingSessionSync` lost its
     "re-register and retry" branch — a retry with the same rejected token was never going to
     work.
  5. **`AuthApi` is an interface** (`LesanAuthApi` implements it), like `FileFetcher`, so the
     repository's rules can be tested against a fake. The rules worth testing are precisely the
     ones that need a controlled answer: refusal vs. unreachable.
  6. **Errors are mapped, not displayed.** The server's sentence is developer English, so
     `AuthViewModel` turns it into an `AuthError` and the screen localises that. The mapping
     matches a few distinctive fragments, which is brittle by nature — recorded in the backend's
     `AGENTS.md` §4.9 so a reworded message cannot quietly degrade the form to "something went
     wrong".
  7. **`LanguageSwitchThroughUiTest` was replaced by `AuthGateLaunchTest`.** Its subject — Settings
     — is now behind the gate, and a JVM test has neither a server nor a Hilt test binding to get
     through it. The crash it guarded (`hiltViewModel()` under `LocalizedApp`) is the gate's own
     surface, so the new test launches the real app in both locales and asserts the gate renders
     and the process survives. Live re-translation stays covered by `LocalizedAppTest`.

  **Verified:** `AuthWireTest` (13) — DTO decoding pinned to payloads the live server actually
  returned, `_id`/`SerialName`, null display name omitted from the encoded form, error-envelope
  extraction; `AuthRepositoryTest` (12) — a fresh device, a cached session that survives an
  unreachable server, a refusal that clears the token, a token with no cached account, a stale
  launch answer not undoing a fresh sign-in, sign-up trimming; `AuthViewModelTest` (13) — local
  validation, every mapped error, an unreachable server not being a credential problem;
  `AuthScreenshotTest` (3 captures, fa light / fa dark error / en light) which caught the primary
  action reading as *disabled* and produced the three-state ladder. `./gradlew assembleDebug
  testDebugUnitTest` → **BUILD SUCCESSFUL, 278 tests, 0 failures**. Backend: 45 tests, and the three
  acts exercised live with `curl` (`register`, duplicate, short password, bad email, `login` with
  case/whitespace, identical answers for wrong password and unknown address, `me` with a good and a
  garbage token).

- **2026-11-15** — Phase 6, part 3: **the gate got a design, and a juz file stopped pretending to be
  a verse.** The owner asked for an "ultra beautiful" register/login screen with two password fields
  and an eye toggle, for Shakernejad and Parhizgar audio for the first five mushaf pages to live on
  our backend, for play to download automatically, and for a separate page to manage the audio.

  1. **The gate is three bands, and the ornament is drawn.** `AuthMedallion` is an eight-point khatam
     as **one closed polygon with alternating radii**, not two overlapping squares — same figure, one
     path, and the stroke joins cleanly at every vertex instead of leaving the doubled corners. The
     radial `accent` glow behind it is what stops it reading as a sticker on the canvas. It is a
     `Canvas` rather than a PNG because it has to work in both themes at any density, and a bitmap
     would be three assets and a mismatch waiting to happen.
  2. **The fields are hand-built, and the label is outside the box.** Material's outlined field draws
     its own 1 dp box, its own 16 sp label and its own floating-label animation — none of which can
     be restyled into the glass language. More importantly, a floating label *disappears* the moment
     there is text, which is exactly when someone checking a form wants to know which field they just
     filled. `GlassField` is a `BasicTextField` in a hairline pane whose border, label and leading
     icon all warm to `accent` on focus — one focus cue, and the whole pane is the touch target.
  3. **Two passwords, two eyes, and the mismatch is caught locally.** A single reveal toggle for both
     fields would be one control doing two jobs, and the confirmation exists precisely because the
     reader cannot see what they typed. The reveal flags are `rememberSaveable`, so a rotation while
     typing does not silently expose the password. The check sits **after** the length check and
     **only** in register mode: "too short" is more useful than "they differ" when both are true, and
     a server that accepted a mismatched pair would still lock the reader out next attempt. A stale
     confirmation must never block a sign-in, which is its own test.
  4. **`material-icons-core` has no eye.** `Visibility`/`VisibilityOff` are in the extended set, which
     is a 10 MB dependency for two glyphs. `EyeGlyph` draws the almond as two quadratics plus either
     a pupil or a corner-to-corner slash — same reasoning as `TransportIcons`, and now written down
     in `DESIGN.md` §8 so the next icon request starts from "is this two shapes in a `Canvas`?".
  5. **Granularity is honoured literally (D22).** Shakernejad's only public audio is whole juz files,
     and a 70-minute track cannot start at verse 17. Rather than silently substituting it into the
     per-ayah queue — which would make the follow-along highlight point at a verse that is not
     sounding and would bill "progress at 1:1" while a whole juz plays, rewinding the session —
     `buildJuz` emits one track per distinct juz the page touches, `ReaderViewModel` publishes
     `playingJuz`, the dock says `جزء N`, and a juz track writes no progress at all. The first five
     mushaf pages turn out to be entirely inside juz 1, so the owner's two requirements are the same
     download: `range 1:1 → 2:29`, 36 ayahs.
  6. **The page scope is resolved on the client (D24).** The wire speaks `ayah | surah | juz | range`
     and has no page concept; a page belongs to the mushaf text the app ships, not to the audio
     catalogue. `resolveScope` turns a page range into a verse range, so the backend never becomes
     authoritative about a pagination it does not own — and the screen can still lead with the unit
     readers think in.
  7. **A juz-only edition collapses the scope control to one segment.** Offering `آیه` for an edition
     that cannot serve a single verse would be a control that lies. `scopeModes` decides; the screen
     carries no granularity conditionals beyond the note.
  8. **The mirror taught a lesson about TLS, not about audio.** Shakernejad's host answered
     `invalid peer certificate: certificate expired` while serving a genuine `audio/mpeg` of
     12,651,123 bytes. The payload was verified separately (`HTTP/2 200`, `content-type`,
     `content-length`), the clock was checked against the certificate's `notAfter`, and the bypass was
     scoped to that host (`--unsafely-ignore-certificate-errors=dl.meshkatsound.com`) rather than
     applied globally. Recorded in `summary.md` §10 — the upstream certificate still needs renewing.
  9. **The estimate was 40 % low, and that is a bug worth a test.** `bitrate × assumed-duration` with
     a 45-minute juz came out at 77 MB against a real 12.65 MB per juz; `NOMINAL_JUZ_SECONDS` is now
     3,600, calibrated against the actual file. A number printed on a download button that is 40 %
     wrong is worse than no number, because it is trusted.
  10. **The dock's identity tile had to agree with its own title.** With `tileNumber = surahNumber`,
     a juz track showed `جزء ۵` beside a tile reading `۴` (the juz's starting surah) — two different
     numbers in one 58 dp row with nothing to say which is which. The tile now carries the juz, and
     `DockNowPlayingTest` pins it with an exact-match assertion on the tile's text.

  **Verified:** `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL, 296 tests,
  0 failures** (up from 278). New coverage: `AuthViewModelTest` 16, `AuthRepositoryTest` 13,
  `AuthWireTest` 13, `AyahNumberingTest` 10 (juz table, 6236, first-five-pages ⊆ juz 1),
  `RecitationQueueTest` 14 (five juz cases), `DownloadDomainTest` 26, `DownloadPersistenceTest` 8,
  `DownloadsScreenshotTest` 4, `DockNowPlayingTest` 1. Screenshots inspected: `auth_register_fa`,
  `auth_register_mismatch_fa`, `auth_register_en`, `downloads-juz-only-fa`, `downloads-light-fa`,
  `downloads-scope-juz`, `dock-juz-fa`. Backend: mirror 36/36 Parhizgar + 1/1 Shakernejad, live
   manifest `fileCount 36` / `1`, `static:200` on both, and `./build_debug.sh` →
   `app_debug.apk (52M)`.

- **2026-11-16** — **Phase 7 opens; the preset-selection crash is fixed (D25).** The owner reported the
  app closing the instant a preset is chosen from the mini-player. Root cause: `PresetPlayer.start()`
  runs its cue loop on `@ApplicationScope` (`SupervisorJob() + Dispatchers.IO`, `AppModule`), and the
  loop called the Media3 `RecitationEngine` (`setSpeed`/`setQueue` in `awaitAudio`) directly from there.
  ExoPlayer is single-threaded by contract and throws `IllegalStateException: Player accessed on the
  wrong thread`; with no `CoroutineExceptionHandler` on that scope the throw killed the process. The JVM
  `PresetPlayerTest` never caught it because it drives a fake engine, not real Media3.
  1. Added a `@Main` `CoroutineDispatcher` qualifier + provider (`Dispatchers.Main`), injected into
     `PresetPlayer`.
  2. Every engine call in the run loop now runs under `withContext(main)`; `setSpeed` moved into
     `awaitAudio` so a spoken cue no longer touches the player at all.
  3. Wrapped each cue in `try/catch` (rethrowing `CancellationException`) so a bad URL / engine / TTS
     fault ends the run as `failed` instead of crashing — the preset half of the existing
     "caching must never break play" rule.
  4. Guarded `ReaderViewModel.onPlaybackChanged` with `if (activePreset != null) return`: the preset
     drives the same singleton engine, and the plain-queue mirror was reading a stale `queue` and
     blanking the dock/highlight the preset had just published.
  **Verified:** `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL, 297 tests, 0 failures**
  (up from 296). `PresetPlayerTest` 10 → 11, new case
  `an_engine_that_throws_ends_the_run_as_failed_not_a_crash`. The Hilt graph compiles with the new `@Main`
  binding. **Not yet on a device** (no emulator here) — the owner will confirm the tap-through on his
  phone; the wrong-thread crash is a deterministic ExoPlayer contract violation, so the fix is
  high-confidence, but the *visual* follow-along-during-preset behaviour is device-verify.
  **Next:** D26 reciter-pinned presets + per-ayah auto-download-then-play, then the Ketab Mobin /
  guya-audio backend research (D27).

- **2026-11-16** — **D30: the full-screen now-playing player.** The owner asked that tapping the
  mini-player open a beautiful, high-performance full-screen player showing the current verse /
  translation / commentary, not the small bottom sheet. Read `DESIGN.md` first (§4 colour, §7 glass
  recipe, §9 motion, §10 components, §16 Do/Don't, §15 impl notes) and built to it:
  1. **Ownership.** The player is a **reader-owned full-screen overlay** (`ui/screens/reader/
     NowPlayingScreen.kt`, stateless so it is screenshot-testable) — the reader owns the engine and the
     verses, so it draws the player and feeds it from `ReaderUiState`. Opening it sets a single
     `NowPlayingStore.expanded` flag; the shell hides the `AppDock` while it is true (the only shell
     chrome that yields), so the player covers the screen. `onCleared` resets it.
  2. **The two colour families separated (DESIGN §16).** The verse sits on the opaque **reading
     surface** (`LocalQuranColors.page`, the reader's ink via the inherited provider), never on glass;
     all chrome is glass. Quran text renders through `QuranText` (letters/diacritics independently
     colorable) — the mushaf face is for scripture.
  3. **Position without breaking the no-poll rule.** Added `currentPositionMs()/currentDurationMs()`
     to `RecitationEngine` as **interface defaults** (so `FakeEngine` and the JVM tests are untouched),
     overridden in Media3. The progress bar is read on a `while(true) { … delay(500) }` ticker **inside
     the player leaf** (DESIGN §15), so the app-wide store and the dock never recompose per second. It
     is a determinate read, not draggable — intra-verse seek is a later nicety; the verse is the unit of
     navigation (prev/next).
  4. **It is also the preset hub.** The old sheet's preset picker moved into the player (a wrapping
     `FlowRow` of chips: plain + each preset), plus a reciter switcher, speed, repeat, and an explicit
     **"download this page"** action (`downloadCurrentPage`, reusing the same scope logic as the silent
     auto-cache so the two cannot disagree). A small equaliser `PlayingPulse` breathes while sounding.
  5. **The visual gate caught a real bug.** The first pass used `horizontalScroll` rows for the preset
     and reciter chips; a screenshot showed the last chip clipped off the right edge ("Memorising" →
     "Memorisin"). Switched both rows to wrapping `FlowRow` (stable, no opt-in) and bumped the clock
     contrast from `textTertiary` to `textSecondary`. Re-captured: PASS, no clipping, balanced in both
     themes and both locales.
  6. **Retired `RecitationSheet.kt`** (moved to `~/.Trash`, recoverable — not a git repo) — superseded
     by the player; no other references remained.
  **Verified:** `assembleDebug` green, `compileDebugKotlin` zero warnings; `NowPlayingScreenshotTest`
  (3 captures) inspected by eye; full suite **306/306, 0 failures**. **Not device-tested** (no emulator):
  the live progress ticker, the pulse animation, and the dock-hide-on-open must be confirmed on the
  phone; the layout/typography/colour separation is screenshot-proven. **Next:** D27 (Lesan media model
  for guya audio + future video/images, source-agnostic), D28 (default reciter), D29 (download affordance
  in more surfaces).

- **2026-11-16** — **D28: Hamed Shakernejad is now the default reciter.** The owner asked for him as
  the app's default قاری. Changed the catalogue default in the two places that carry it:
  1. `mobile/.../assets/quran/recitations-v1.json` — `defaultReciter: parhizgar → shakernejad`. This is
     what the reader's `RecitationRepository` resolves when `RecitationSettings.reciterKey` is null, so
     the app opens on Shakernejad.
  2. `back/seed.ts` — `isDefault: true` moved from `parhizgar-murattal-ayah-48` to
     `shakernejad-tartil-juz-24`, so the download catalogue's "preferred edition" agrees.
  The juz-only consequence is honest, not papered over: plain recitation builds a whole-juz queue (D22 —
  no verse highlight, dock reads «جزء N»), and a verse-by-verse preset falls back to a per-ayah voice via
  the D26 `presetVoice` resolver rather than substituting a 70-minute juz file into a verse queue. Splitting
  his juz files to per-ayah is a D27 item, not a silent fudge.
  **Verified:** mobile `assembleDebug` + full JVM suite **306/306, 0 failures** (no test asserts the
  default; the `parhizgar` strings in `DownloadDomainTest` are unrelated `LocalAudioIndex` fixtures);
  backend `deno check seed.ts`, `deno fmt --check`, `deno lint` all clean. **Owner action:** re-run
  `deno task seed` to apply the backend change to the DB (idempotent); the app-side default is already
  in the APK. **Next:** D27 (Lesan media model) / D29 (download affordance in more surfaces).

- **2026-11-16** — **D29: download from anywhere in the reader.** The owner asked to "download the
  audios easily from any page and functionality related to the manage and play Quran." The player
  already had a download action; this surfaced it in the two reader places a person actually looks:
  1. **The floating selection box** — a second `SelectionAction` ("دانلود این صفحه", outlined bubble)
     appears beside Play whenever a verse is tapped and the recitation catalogue is available. The
     action list is data-driven (`List<SelectionAction>`), so this added an entry, not a layout.
  2. **The reader top bar** — a download bubble next to settings, shown only when recitation is
     available (no dead button).
  Both call the existing `ReaderViewModel.downloadCurrentPage()`, which routes to the same
  `cacheForPlayback` page-scope the silent auto-cache fires on play — so the explicit button and the
  automatic cache can never disagree about what "this page" is for the current voice. Added a drawn
  `TransportIcons.Download` (arrow-into-tray, two paths) rather than pulling `material-icons-extended`.
  **Verified:** `assembleDebug` + full JVM suite **306/306, 0 failures**; `reader-shell.png` re-rendered
  and inspected — top bar's two bubbles spaced cleanly, action box shows Play + Download with no
  internal clipping. The box overlapping *adjacent* translation text is the documented §10.6 behaviour
  (it floats over the page, anchored to the selection); it does not cover the selected verse. **Not
  device-tested:** the tap-to-enqueue and the resulting download progress must be confirmed on the
  phone (the Downloads screen already shows queue state). **Next:** D27 (the Lesan media model — guya
  audio + future video/images, source-agnostic).

- **2026-11-16** — **D26: presets pin their reciter and download-then-play verse by verse.** The
  owner's rule — "selecting a Telavat via a preset must explicitly say which reciter's version plays,
  and if that reciter's audio is not downloaded the system must auto-download, save and play it, verse
  by verse" — was not implemented: `onPlayPreset` used the reader's *currently selected* reciter and
  `awaitAudio` only streamed (never fetched a missing verse first).
  1. **Reciter pinning.** New pure `presetVoice(pinnedKey, catalog, selected)` (top-level in
     `ReaderViewModel.kt`): the preset's `reciterKey` wins when it is per-ayah, else the current
     selection when per-ayah, else the catalogue's per-ayah default. A pinned **juz-only** voice
     (Shakernejad) is deliberately never handed to a verse-level queue — a whole-juz MP3 cannot start
     at verse 17, and substituting it would make the follow-along lie (the D22 rule, now enforced on
     the preset path too). `onPlayPreset` loads the resolved voice, records it on the sitting, and
     shows its name in the dock/sheet; the standing selection's name is restored on exit/finish.
  2. **Download-then-play.** New `domain/download/VerseAudioProvisioner` seam (pure) +
     `data/download/AutoCacheVerseProvisioner` (production): on-disk → return path; no per-ayah
     edition → null immediately (stream); else `AudioAutoCache.ensure(Ayah)` + suspend on
     `DownloadManager.index` until that exact verse lands (15 s timeout). `PresetPlayer.awaitAudio`
     now resolves each cue **disk → provision → stream**. Bound in `DownloadModule`.
  **Verified:** `assembleDebug` green, `compileDebugKotlin` **zero warnings**; `PresetVoiceTest` (5,
  incl. the pinned-juz fallback and the no-catalogue case), `PresetPlayerTest` 11 → 12
  (`a_verse_that_is_not_on_disk_is_provisioned_and_played_from_the_file`); full suite **303/303, 0
  failures**. **Not device-tested** (no emulator): the provision path reaches real WorkManager +
  `DownloadManager` only on a phone, so the owner should confirm a pinned per-ayah reciter's verse
  downloads and plays offline on first listen. **Next:** D30 full-screen player (owner chose
  "preset rework + player next"); D27 backend media model (guya audio, future video/images) stays in
   Lesan per the owner, source-agnostic — the owner supplies the per-ayah files once.

- **2026-11-16** — **D27b: the app consumes the media catalogue — guya audio in presets, TTS as the
  fallback.** With the backend `mediaEdition` model + acts in place (D27), a preset's
  translation/tafsir step now plays real narrated audio verse by verse when the backend has it,
  instead of always synthesising:
  1. **Catalogue read** (`data/media/`): `MediaCatalogueClient` calls `mediaEdition/gets`;
     `MediaCatalogueRepository` caches in-process and exposes `editionFor(contentType, textKey)`;
     `MediaCodec` maps the wire row (an unknown `contentType` decodes to null and is dropped, never
     guessed). The selection policy is a pure `chooseMediaEdition(verified, textKey)` so it is
     testable without a client or a coroutine.
  2. **The seam** `domain/media/GuyaAudioSource` — `resolveUrl(contentType, textKey, surah, ayah) →
     String?`. `null` is a normal answer ("no audio, speak it"), not an error.
  3. **Production resolver** `MediaGuyaAudioSource`: verified edition → **disk first** (a guya file
     already downloaded, keyed by the edition) → else the server-relative template resolved against
     the app's base URL (stream from the backend's own `uploads/`). **Ayah-granularity only** — a
     whole-surah/juz narration cannot start at verse N, the same honesty rule as recitation.
  4. **PresetPlayer**: a `Speak` cue calls `playSpeak` — guya URL → play it through the engine; none →
     `speech.speak` (TTS). Recitation and guya audio now share one `playUrl` (the single place that
     hands a URL to the engine, waits for terminal state, and keeps the D25 `main`-thread hop). Bound
     in `DownloadModule`.
  **Verified:** `assembleDebug` + `compileDebugKotlin` **zero main warnings**; `MediaCatalogueTest` (7)
  and `PresetPlayerTest` 12 → 14 (`a_translation_step_plays_guya_audio_when_available_instead_of_tts`,
  `a_translation_step_falls_back_to_tts_when_no_guya_audio_exists`); full suite **315/315, 0 failures**.
  **Honest scope:** offline *download*-then-play of guya (enqueue via a media manifest) is D27c — the
  disk-first branch is the seam it fills; streaming from the backend works now. Real guya playback is
  **file-gated**: it needs the owner to drop the per-ayah mp3s and mark the edition verified; until
  then every translation/tafsir step uses the tested TTS fallback. **Next:** D27c (guya offline
  download) and surfacing guya editions in the Downloads / preset-picker UI.

- **2026-11-16** — **D28 revised: Shakernejad now has REAL verse-by-verse audio (Inoor), and the
  "nothing plays" root cause found.** The owner reported still being unable to play or download
  anything, and correctly insisted a per-ayah Shakernejad source exists (Inoor / قرآن اینور). I was
  wrong earlier that none does.
  1. **Root cause of "nothing works": the macOS application firewall is ON and Deno is not in its
     allow-list**, so inbound connections from the phone to `*:1407` are dropped — the backend
     serves fine from the Mac (loopback AND the LAN IP) but the phone is blocked at the door. That
     alone makes the Downloads page fail to fetch the catalogue and backend-hosted play fail. Fix is
     the owner's to apply (needs sudo): add `/Users/syd/.deno/bin/deno` to the firewall allow-list and
     unblock it. (Reported, not run — it is a security-relevant system change.)
  2. **Inoor per-ayah Shakernejad located + mirrored.** Found the API by reading the Nuxt bundle:
     `ListRecitor?language=fa&surahId=N`, `RecitationList?recitorId=ID&surahId=N` (per-ayah `FileUrl`),
     audio = `https://apiquran.inoor.ir` + `/File/Sound/Recitation/<id4>/<surah3><ayah3>.mp3`.
     **Shakernejad = recitorId 1014.** Mirrored the first five mushaf pages (36 ayahs, 3.2 MB) into the
     backend via `deno task audio-mirror --edition=shakernejad-murattal-ayah-1014 --scope=range:1:1-2:29`
     → **36/36 stored, 0 failed**. Verified the backend serves a mirrored file (HTTP 200 audio/mpeg) and
     the manifest returns the Inoor `url` + backend `mirrorPath` + exact bytes.
  3. **Mobile default flipped to per-ayah Shakernejad.** `recitations-v1.json`: the `shakernejad` entry
     is now granularity **ayah** with template
     `https://apiquran.inoor.ir/File/Sound/Recitation/1014/{surah3}{ayah3}.mp3` (the juz edition kept as
     `shakernejad-juz`); `defaultReciter: shakernejad`. The app now recites verse-by-verse (follow-along
     highlight works) and streams from Inoor — reachable from Iran WITHOUT the Mac backend, so playback
     works even before the firewall is fixed. Added `{surah3}{ayah3}` support to
     `Reciter.urlFor(surah, ayah, globalAyah)` and threaded surah/ayah through `RecitationQueue.build` +
     `PresetPlayer.resolveReciteUrl`. The D28 "juz-only, degrade to a per-ayah voice" note is now moot —
     Shakernejad IS per-ayah.
  **Verified:** mobile `assembleDebug` + `compileDebugKotlin` **zero warnings** + full JVM suite
  **316/316** (added `an_inoor_style_template_fills_the_in_surah_ayah_placeholder`); backend
  `deno check/lint/fmt` clean, re-seeded, mirrored 36/36, manifest + static serving exercised over
  HTTP. **Device action required:** (a) run the firewall allow commands so the phone reaches the backend
  for Downloads/offline; (b) confirm verse-by-verse Shakernejad now plays on the phone (streams from
  Inoor, so it should work even before (a)).

- **2026-11-17 — Playback range (D31 + D32) + the three "nothing plays" root causes.** Drove the app
  on the API-35 emulator to reproduce the owner's report. Three independent defects made recitation
  unplayable, all fixed and device-confirmed: (1) `Media3RecitationEngine.setQueue` relied on
  `setMediaItems` + `playWhenReady` to auto-prepare — on Media3 1.11.1 it reports `STATE_BUFFERING`
  then the internal thread goes idle and nothing loads; added an explicit `prepare()`. (2) the
  downloads catalogue was empty because `DownloadCatalogueClient.reciters()` sent no `get` projection,
  so `reciter/gets` answered pure fields only (no `editions`) → dead pickers; it now requests the
  `editions` sub-projection. (3) downloaded verses are `file://` URIs but the single
  `DefaultMediaSourceFactory(cacheDataSourceFactory)` fed them to the HTTP loader
  (`ERROR_CODE_IO_UNSPECIFIED`); added `SchemeRoutingDataSourceFactory` (file→`FileDataSource`,
  else cache-then-network). Then shipped the two new behaviours: **D31** plain play queues the
  starting ayah → end of its surah across pages (scroll no longer re-queues; auto-cache extends a
  page at a time as the run crosses into it), and **D32** a multi-verse or spoken-layer selection
  swaps the action to "Play selection" — recitation-only → a queue of exactly those verses, with
  tarjomeh/tafsir → an ad-hoc `PresetSequence` run by `PresetPlayer` that persists nothing (no
  preset row, no sitting, no coverage; gated by a `selectionRun` flag). Removed the `HawraaQA`
  instrumentation and the debug `QaPlayerActivity` probe.
  **Verified:** `clean assembleDebug` + `compileDebugKotlin` **zero warnings**; full JVM suite
  **322/322** (RecitationQueueTest moved to the `VerseItem` signature + surah-wide span case;
  VerseSelectionTest +5 plan cases). Emulator: 36 ayahs downloaded to `filesDir` and
  `file://`-played (`AudioTrack state:started`); plain play 2:11→2:20 crossed the page-3/4
  boundary; two-verse selection played exactly 1→2 and stopped. **Not device-tested:** the
  spoken-layer (translation/tafsir) selection path needs guya audio or a Persian TTS voice on the
  emulator — the logic is unit-tested (`PresetSequenceBuilder` + plan), the playback of a real
  tarjomeh/tafsir selection is owner-confirmation on a phone with TTS.

- **2026-11-17 follow-up — "still doesn't advance / repeats the verse" triage.** The owner reported
  play not advancing and a single verse looping, in both plain and selection modes. Re-tested the
  shipped D31/D32 build on the API-35 emulator with temporary engine logging: **plain play** →
  `setQueue n=7 repeat=0` advancing 1:1→1:2→…→1:5; **two-verse selection** → `setQueue n=2 repeat=0`
  playing 1:1→1:2 then `STATE_ENDED` (stops, no repeat). After removing the logs, a clean run from
  verse 1 reached **Ayah ۷** (end of al-Fatiha) and stopped. So the queue/auto-advance logic is
  correct. The reported symptom is the signature of `Player.REPEAT_MODE_ONE` — the reader's
  **"Repeat ayah" toggle is ON** (repeatAyah defaults false; when on it loops the current track and
  by design cannot continue to the next) — or an APK older than the D31/D32 build (selection-play
  only exists from D32). No code change; diagnosis documented. Environment note: the emulator reaches
  the backend only via `10.0.2.2` while the macOS firewall blocks the LAN IP for a freshly launched
  `deno` (not in the allow-list); on-device sign-in here needs a `-Phawraa.baseUrl=http://10.0.2.2:1407`
  build, while the committed `local.properties` (LAN IP) is for the phone.
