# Hawraa Android — Summary

> **Sector:** `mobile/android/` · **App:** Hawraa (حوراء), package `ir.hawraa.quran`
> **Read this first** when picking up mobile work with any agent. Then read
> [`../AGENTS.md`](../AGENTS.md) (rules), [`DESIGN.md`](DESIGN.md) (**the UI/UX contract — before any
> UI work**), [`plans/01-buildable-skeleton.md`](plans/01-buildable-skeleton.md) (what to do next), and
> [`prompts/01-initial-scaffold.md`](prompts/01-initial-scaffold.md) (why).
>
> **Plan discipline:** the plan file is a living record. **Update it after every small task** —
> tick the box, append a progress-log entry.

---

## 1. What this app is

An **Android-only, native Kotlin + Jetpack Compose** Quran app. No Flutter, no React Native, no
WebView UI. Two pillars: **exceptional readability** and **complete per-user activity tracking**.
Offline-first: the Quran text/translation/audio live on the device; reading activity is recorded
locally and synced to the Lesan backend in the background.

Development is **incremental over weeks**, with frequent testing on the owner's physical device.
Cross-cutting infrastructure (i18n fa-RTL/en-LTR, light/dark theming, offline-first sync) is wired
from the first scaffold, not retrofitted.

## 2. Current status (Phases 1–8 complete)

**The first five mushaf pages are readable, recitable, tracked and synced.** Uthmani text with
independently colorable letters and diacritics, **silent letters muted**, Ansarian translation,
**one continuous scroll (no pagination)**, licensed ayah markers, **per-pane selection** (the verse and
each translation hold independent state), **verse-by-verse recitation with follow-along highlighting
and listening sessions**, a pure reading-session state machine feeding Room and the Lesan backend,
real History with streaks, a designed landing page, and an in-app language switcher.

The UI was rebuilt on a **glass design language** (`docs/DESIGN.md`): an ambient canvas, floating
translucent panes, one **dock** that is a mini-player + navigation in a single panel (it collapses to
nav-only when nothing is playing), and a **floating action box over the selection** instead of the old
full-width transport bar.

**Phase 5 added the recitation recipe language and offline audio.** Presets are recipes applied to
any range ("verse twice → translation → verse"), stored in the backend so they follow the reader
across devices, with a per-preset coverage ledger and one history row per sitting rather than per
button press. Recitation can now be **downloaded** — verse by verse, surah by surah or juz by juz —
from a server-built manifest, kept in `filesDir`, resumed after an interruption, and played from disk
whenever the reader chooses a reciter they have downloaded.

**Phase 6, part 1 added the account gate.** The app no longer runs anonymously: on first launch it
asks for an email and a password, and nothing is written until someone is signed in — history
recorded before sign-in belongs to nobody. Everything *after* the gate stays offline-first: the
session is cached, the app opens with no connection, and only an answer from the server can sign a
reader out. `DeviceRegistrar` (anonymous device registration) is gone.

**Phase 6, part 2 rebuilt the gate and made juz-granular recitation real.** The gate is now a
designed screen — a drawn eight-point khatam, the wordmark, and one glass pane of hand-built fields
whose labels sit *above* the box, with **two password fields each carrying its own eye** and a local
mismatch check before anything reaches the wire. Shakernejad's juz-granular edition now has audio:
the backend mirrors it (`/uploads/recitation/…`) and the app **auto-downloads on play** — a juz
reciter queues one file per juz rather than pretending a 70-minute track is a verse queue, and the
dock says `جزء N` instead of an ayah number. Downloads became a **management** screen: four scopes
(page / surah / juz / ayah), two page sliders with a "first five pages" chip, a live ayah count and
size estimate, and holdings grouped per edition with a coverage bar, per-scope retry and delete.

Verified: `assembleDebug` green, **296/296 unit tests**, plus JVM screenshots of Home, Reader
(incl. recitation, both selection cases, the shell with the dock in light/dark/idle), History,
Settings, Downloads (incl. a juz-only edition), Presets, the auth gate in both themes and both
locales, and the dock for a juz track (`app/build/screenshots/`).

Not yet: multi-translation/commentary layout (needs more content, and a tafsir bundle — the editor
therefore does not offer tafsir steps), server-side history restore (needs a backend read act), and
background playback (needs a foreground service). **Shakernejad remains juz-granular** — there is no
public per-ayah source for him, so his edition plays and downloads as whole juz files (D13), and the
mirror currently holds juz 1 (the first five mushaf pages, 36 ayahs).

**Phase 7 made recitation actually play, and Phase 8 shaped its range.** Three defects had made
every verse silent — `setQueue` never called `prepare()` (Media3 1.11.1 reports BUFFERING then stalls),
the downloads catalogue asked `reciter/gets` with no `get` projection so the server returned reciters
**without their editions** (dead pickers), and downloaded `file://` verses were fed to the HTTP loader
(`ERROR_CODE_IO_UNSPECIFIED`) until a `SchemeRoutingDataSourceFactory` sent them to `FileDataSource`.
With playback real, **D31** made a plain play a *run* — the starting ayah through the **end of its
surah**, across pages; the scroll no longer re-queues the audio and the auto-cache extends a page at a
time as the run crosses into it. **D32** made a selection mean something: two or more verses, or any
translation/tafsir layer, swaps the action box's play button to **"Play selection"**, which plays
*only* what was picked — recitation-only as a plain queue, or, with spoken layers, as an ad-hoc
preset recipe run by `PresetPlayer` that persists nothing (no preset row, no sitting, no coverage).
Verified: `clean assembleDebug` zero warnings, **322/322 unit tests**, and on the API-35 emulator —
36 ayahs downloaded and `file://`-played, plain play crossing the page-3/4 boundary, and a two-verse
selection playing exactly those two then stopping. (The spoken-layer selection path is unit-tested;
its on-device sound needs guya audio or a Persian TTS voice.)

| Area | State |
|---|---|
| Gradle (KTS + version catalog + wrapper), AGP 9 built-in Kotlin | ✅ done |
| Compose + Material 3, light/dark theme (working switcher) | ✅ done |
| Design system: glass tokens, geometry ramp, `DESIGN.md` contract | ✅ done |
| App shell: ambient canvas + nav host + dock (mini-player + nav, one panel) | ✅ done |
| i18n fa (default, RTL) + en (LTR), per-app locales | ✅ done |
| Room DB + `ReadingSessionEntity` + DAO + sync state machine | ✅ done |
| Hilt DI, WorkManager sync (account token → batched syncReadingSessions) | ✅ done |
| Navigation: Home / Reader (with start page) / History / Settings | ✅ done |
| Content: pages 1–5 bundled as a versioned asset + builder script | ✅ done |
| Typography: Scheherazade New split into letter/mark ink layers, two-layer renderer | ✅ done |
| Reader: continuous scroll, surah plates, rosettes, translation, reading settings | ✅ done |
| Per-pane selection: verse and each translation keep independent state (`VerseSelection`) | ✅ done |
| Selection actions: floating box anchored to the selection (`SelectionActionBox`) | ✅ done |
| Dock: now-playing row + nav, collapses when idle, app-scoped `NowPlayingStore` | ✅ done |
| Session tracking: pure state machine, idle clamping, checkpoints, orphan recovery | ✅ done |
| History: totals, day buckets, streaks, per-session rows | ✅ done |
| Home: wordmark, "continue reading", today + streak | ✅ done |
| In-app language switcher (system / فارسی / English, RTL-aware) | ✅ done |
| Recitation: per-ayah streaming + disk cache, recitation sheet, follow-along highlight | ✅ done |
| Listening sessions (`mode="listen"`, reciter + playbackSpeed in the snapshot) | ✅ done |
| Reciter picker (4 verified reciters), speed presets, repeat-ayah | ✅ done |
| Presets: recipe model, per-preset coverage ledger, one row per sitting, LWW sync | ✅ done |
| Preset playback: reciter audio + TTS translation, gaps as real waits, per-cue history | ✅ done |
| Preset screens: list with coverage, editor (name + ordered steps + repeats) | ✅ done |
| Offline downloads: manifest-driven queue, resumable fetch, storage accounting, local playback | ✅ done |
| Downloads screen: 4 scopes (page/surah/juz/ayah), page sliders, estimate, grouped holdings | ✅ done |
| Auto-download on play (`AudioAutoCache`), per-scope dedupe, juz reciter → whole-juz scope | ✅ done |
| Juz-granular playback: `buildJuz`, `playingJuz`, juz dock row, no verse progress writes | ✅ done |
| Playback fix: explicit `prepare()`, `get` projection on `reciter/gets`, `file://` scheme routing | ✅ done |
| Surah-wide plain play (D31): queue to end of surah across pages, no scroll re-queue, cache-on-cross | ✅ done |
| Play selection (D32): multi-verse / spoken-layer → "Play selection", ad-hoc non-persisted preset | ✅ done |
| Accounts: register / sign in screen, forced gate, offline-first session cache | ✅ done |
| Gate UI: drawn khatam, `GlassField`, confirm password + eye reveal, tinted error strip | ✅ done |
| Auth wire + repository: `AuthApi`/`LesanAuthApi`, `AuthRepository`, `AuthState` | ✅ done |
| Shakernejad per-ayah audio (no public source — see D13), tafsir bundle, background playback | ⬜ next |

## 3. Tech stack (pinned, verified stable Nov 2026)

| Piece | Version |
|---|---|
| AGP | **9.5.1** (built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android`) |
| Gradle | 9.6.0 |
| Kotlin | 2.4.21 |
| KSP | 2.3.13 |
| Hilt | 2.60.1 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Media3 | 1.11.1 |
| WorkManager | 2.12.0 |
| Navigation Compose | 2.10.2 |
| Lifecycle | 2.11.0 |
| Activity | 1.13.0 |
| DataStore | 1.3.0 |
| Ktor | 3.6.1 |
| kotlinx-serialization | 1.13.0 |
| coroutines | 1.11.0 |
| androidx.hilt | 1.4.0 |
| Robolectric (JVM screenshots) | 4.15.1 |
| androidx.test:core (JVM tests) | 1.7.0 |
| fontTools / uharfbuzz (offline tooling only, not in the APK) | 4.68 / latest |

**SDK levels:** `minSdk 29` (Android 10, the owner's choice), `targetSdk 36`, **`compileSdk 37`**
(latest core-ktx 1.19+ and OkHttp 5.5+ require API 37; keep targetSdk lower for stable behavior).

## 4. Build environment (this machine) — READ BEFORE BUILDING

These are non-obvious and will break a naive build. Full detail in [`../AGENTS.md`](../AGENTS.md).

- **JDK:** the default `java` is 1.8. Use Homebrew OpenJDK 22:
  `export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- **Android SDK:** `~/Library/Android/sdk` (set in `local.properties`, gitignored). Installed
  **manually** via the `redirector.gvt1.com` CDN because `dl.google.com` is blocked. Present:
  platforms android-36 & android-37.0, build-tools 36.0.0, platform-tools.
  `android.builder.sdkDownload=false` is set so a missing component fails fast instead of hanging.
  A hand-added platform needs a `package.xml` or AGP/sdkmanager won't see it.
- **Gradle repositories:** `dl.google.com`/Google Maven are unreachable. Order is
  **`mavenCentral()` first, then the Aliyun google mirror.** The mirror returns **HTTP 502 (not 404)**
  for artifacts it lacks, which aborts Gradle's fall-through — so it must come after mavenCentral.
  Kotlin/KSP/Hilt/Ktor resolve from Central; AGP/AndroidX fall through to the mirror. **Do not reorder**
  and do not add the Aliyun `public`/`gradle-plugin` mirrors (they 502).
- **Gradle wrapper distribution** downloads crawl (~5KB/s) here. The cache is pre-seeded at
  `~/.gradle/wrapper/dists/gradle-9.6.0-bin/<hash>/`. If it ever re-downloads, pre-seed the zip +
  extracted dir + `.ok` marker, or build with an already-extracted Gradle.

## 5. Build / run / test

```sh
cd mobile/android
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # install on a connected device (owner tests frequently)
./gradlew test               # JVM unit tests + screenshots
```

**Screenshots without a device:** `./gradlew testDebugUnitTest` renders every screen on the JVM
(Robolectric native graphics — real Skia + HarfBuzz + the bundled fonts) into
`app/build/screenshots/`: 9 × `reader-*` plus 3 × `reader-shell-*`, 2 × `home-*`, 2 × `history-*`,
4 × `settings-*`, 3 × `presets-*`/`preset-editor-*`, 4 × `downloads-*`, 4 × `auth_*`, and
`dock-juz-fa.png` (the dock's player row for a juz track).
This is the visual gate on this machine: there is no emulator installed and `dl.google.com` is blocked,
so a system image cannot be fetched. Look at the PNGs before reporting a UI change as done.
Screen captures go through the shared `TestShell` (ambient canvas + screen + dock), because **every
screen is transparent by design** — a capture of a bare screen photographs a white window the app
never shows.

**Regenerating content / fonts** (only needed when the source data or the face changes):

```sh
python3 tools/build-quran-assets.py   # downloads to ignore-scripts/cache/quran, writes assets/quran/pages-v1.json
│   ├── verify-recitation-audio.py  # probes every reciter URL template (ranged GET + mp3 check)
│   ├── verify-room-migration.py    # diffs hand-written migration SQL against Room's schema JSON
python3 tools/build-quran-fonts.py    # derives res/font/quran_{letters,marks}.ttf from tools/fonts/*.ttf
swift tools/render-font-layers.swift  # visual proof of the layer split (writes to ignore-scripts/font-lab)
```

**Backend URL:** `BuildConfig.SERVER_URL`, built from `hawraa.baseUrl` in `local.properties`
(untracked, so each machine keeps its own) or `-Phawraa.baseUrl=…`. Default `http://10.0.2.2:1407`,
which is the emulator's route to the host.

Two things about it are load-bearing:

- **The value is an origin, not an endpoint.** `LesanClient` appends `/lesan`; the server answers
  anything else with `501 {"message":"Invalid request: Expected POST to /lesan, got POST to /"}`.
  That exact mistake shipped in the base URL until 2026-11-15, and because every call is wrapped in
  `runCatching` it failed **silently** — the app looked offline, not misconfigured.
- **Cleartext HTTP is allowed in debug builds only** (`app/src/debug/AndroidManifest.xml`). Since
  Android 9 a plain-HTTP request is refused by default, and the exception reads exactly like the
  server being down.

For a phone on the same Wi-Fi: put the Mac's LAN address in `local.properties` (the backend prints
it at boot), rebuild, install.

## 6. File map

```
mobile/android/
├── AGENTS.md                       # sector rules (authoritative)
├── settings.gradle.kts             # repo ordering (mavenCentral → Aliyun), include :app
├── build.gradle.kts                # buildscript pins KGP/KSP; plugins apply false
├── gradle.properties               # androidx, sdkDownload=false, jvmargs
├── local.properties                # sdk.dir (gitignored)
├── gradle/libs.versions.toml       # version catalog (all pins)
├── gradle/wrapper/…                # wrapper (Gradle 9.6.0)
├── gradlew, gradlew.bat
├── docs/                           # this summary + plans/ + prompts/
├── tools/                          # offline build-time tooling (never shipped as code)
│   ├── build-quran-assets.py       # Tanzil + Quran.com → assets/quran/pages-v1.json
│   ├── verify-recitation-audio.py  # probes every reciter URL template (ranged GET + mp3 check)
│   ├── verify-room-migration.py    # diffs hand-written migration SQL against Room's schema JSON
│   ├── build-quran-fonts.py        # one face → quran_letters.ttf + quran_marks.ttf
│   ├── render-font-layers.swift    # renders the layer split for visual inspection
│   └── fonts/scheherazade-new-regular.ttf   # unmodified source face (OFL)
└── app/
    ├── build.gradle.kts            # android{} + dependencies; SERVER_URL buildConfigField (from local.properties)
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml # permissions, localeConfig, Hilt WorkManager init override
        │   ├── assets/
        │   │   ├── quran/pages-v1.json          # versioned content: 5 pages, 36 verses, provenance
        │   │   ├── quran/recitations-v1.json    # reciter catalogue (schemaVersion 2): URL templates,
        │   │   │                                 #   granularity (ayah|juz), mirror block, default
        │   │   └── licenses/
        │   │       ├── ScheherazadeNew-OFL.txt
        │   │       └── ayah-marker-game-icons.txt   # CC BY 3.0 attribution
        │   ├── kotlin/ir/hawraa/quran/
        │   │   ├── HawraaApp.kt            # @HiltAndroidApp + WorkManager Configuration.Provider
        │   │   ├── MainActivity.kt         # @AndroidEntryPoint, edge-to-edge, Compose entry
        │   │   ├── MainViewModel.kt        # exposes themeMode + appLanguage StateFlows
        │   │   ├── data/
        │   │   │   ├── local/              # HawraaDatabase, ReadingSessionEntity, ReadingSessionDao, SyncStatus,
        │   │   │   │                       #   DownloadBatch/Dao (the offline download queue)
        │   │   │   ├── quran/              # QuranContentDto + QuranContentRepository (reads the asset)
        │   │   │   │                       #   RecitationCatalogDto + RecitationRepository (reciter catalogue)
        │   │   │   ├── download/           # DownloadManager (manifest → queue → filesDir), AudioAutoCache
        │   │   │   │                       #   (auto-download on play, deduped per edition+scope)
        │   │   │   ├── recitation/         # RecitationEngine (interface + PlaybackState), Media3RecitationEngine
        │   │   │   ├── remote/             # LesanClient (Ktor), LesanModels (wire DTOs), LesanException,
        │   │   │   │                       #   AuthApi/LesanAuthApi + AuthWire (account acts)
        │   │   │   ├── repository/         # SettingsRepository (DataStore: theme + reading settings),
        │   │   │   │                       #   IdentityRepository (token + cached account),
        │   │   │   │                       #   AuthRepository (the gate's state machine)
        │   │   │   └── sync/               # SyncWorker/SyncScheduler, ReadingSessionSync, WireModels
        │   │   ├── domain/
        │   │   │   ├── ThemeMode.kt
        │   │   │   ├── quran/              # QuranContent/Page/Verse/SurahInfo, QuranNumbers (Persian digits),
        │   │   │   │                       #   SilentLetters (muted letters: markers + sun-letter rule + lexicon),
        │   │   │   │                       #   VerseSelection (layer-keyed selection set + active element),
        │   │   │   │                       #   ReaderItems (pages flattened into the continuous scroll)
        │   │   │   ├── recitation/         # AyahNumbering (global ayah 1…6236 + the 30 juz starts),
        │   │   │   │                       #   RecitationModels: Reciter / ReciterGranularity /
        │   │   │   │                       #   RecitationTrack / PlaybackSpeed / RecitationQueue (pure,
        │   │   │   │                       #   build() per verse, buildJuz() one track per juz)
        │   │   │   ├── download/           # DownloadScope (ayah|surah|juz|range), DownloadSizing,
        │   │   │   │                       #   LocalAudioIndex (verse + juz rows, disk before stream)
        │   │   │   ├── auth/               # Account, AuthState (Restoring|SignedOut|SignedIn), AuthRules
        │   │   │   ├── session/            # SessionModels (+ SessionMode read|listen), ReadingSessionTracker,
        │   │   │   │                       #   SessionStats
        │   │   │   └── settings/           # ReadingSettings + LetterInk/DiacriticInk, RecitationSettings
        │   │   ├── di/                     # AppModule (DataStore, HttpClient, LesanClient, Room, DAO)
        │   │   │   #   RecitationModule (SimpleCache + engine binding)
        │   │   └── ui/
        │   │       ├── screens/auth/       # AuthScreen (the gate: sign in / register), AuthViewModel
        │   │       ├── LocalizedApp.kt     # per-app language (ContextThemeWrapper + override config)
        │   │       ├── HawraaShell.kt      # AmbientCanvas + the gate + HawraaNavHost + AppDock
        │   │       │                       #   + NowPlayingSheet (the shell owns the player)
        │   │       ├── ShellViewModel.kt   # bridges the store (snapshot, transport, mini rect)
        │   │       ├── theme/              # ALL tokens: Color.kt (raw palette), GlassColors.kt (chrome),
        │   │       │                       #   QuranColors.kt (reading surface), Geometry.kt (radius,
        │   │       │                       #   spacing, controls, DockGeometry), Motion.kt, Theme.kt, Type.kt
        │   │       ├── navigation/         # HawraaNavHost + Routes
        │   │       ├── components/         # GlassSurface/FrostedSurface, Backdrop (draw-node blur),
        │   │       │                       #   AmbientCanvas, Icon.kt (semantic ICON_GLYPHS catalog),
        │   │       │                       #   GlassChip, GlassSegments (one-of-N), GlassSheet, ScreenFrame,
        │   │       │                       #   EyeGlyph/PasswordReveal, AppDock (four-peer nav + mini-player),
        │   │       │                       #   SelectionActionBox, SegmentedPill, ChevronIcon/DiamondBullet
        │   │       │                       #   player/ (Transport, Scrubber, PlayerParts)
        │   │       ├── quran/              # QuranFont, QuranText (two ink layers + muted spans),
        │   │       │                       #   QuranTypeScale, AyahOrnament (licensed icon),
        │   │       │                       #   MushafBanner, SurahName
        │   │       ├── recitation/         # NowPlayingStore (app-scoped PlayerUi + transport binding)
        │   │       └── screens/            # home/, reader/ (+ReaderViewModel), history/, settings/,
        │   │                               #   downloads/ (4 scopes, grouped holdings), presets/,
        │   │                               #   player/ (NowPlayingSheet, PlayerContent, mini player)
        │   └── res/
        │       ├── values/strings.xml      # fa (default)
        │       ├── values-en/strings.xml   # en
        │       ├── values/{themes,colors}.xml
        │       ├── font/quran_letters.ttf  # generated: letters only
        │       ├── font/quran_marks.ttf    # generated: diacritics only
        │       ├── xml/locales_config.xml  # fa, en
        │       ├── drawable/ayah_marker.xml            # Rub el Hizb by Delapouite (CC BY 3.0)
        │       ├── drawable/ic_launcher_foreground.xml  # Rub el Hizb ۞
        │       └── mipmap-anydpi-v26/ic_launcher.xml
        └── test/kotlin/ir/hawraa/quran/
            ├── CoreLogicTest.kt                    # ThemeMode + SyncStatus round-trip
            ├── QuranDomainTest.kt                  # Persian digits + basmala rule
            ├── VerseSelectionTest.kt               # 10 cases: verse/translation/commentary independence
            ├── ReaderItemsTest.kt                  # 8 cases: one item per verse, a plate per surah start
            ├── SilentLettersTest.kt                # 11 cases incl. a sweep of all 36 verses
            ├── AyahNumberingTest.kt                # 6236 total, inverse round trip, asset cross-check
            ├── RecitationQueueTest.kt              # 14 cases: playlist order/URLs, surah straddle, juz queue
            ├── RecitationEngineTest.kt             # the real Media3 engine on the JVM
            ├── ReadingSessionTrackerTest.kt        # 25 cases, the pure state machine
            ├── SessionStatsTest.kt                 # 11 cases, totals + streaks
            ├── SettingsRepositoryTest.kt           # app-language round trip
            ├── LocalizedAppTest.kt                 # locale override + RTL + runtime switch
            ├── PersianLaunchTest.kt                # every screen composes in Persian
            ├── MainActivityLaunchTest.kt           # real activity, fa persisted, no uncaught throw
            ├── AuthWireTest.kt                     # 13 cases: the account acts' envelope + errors
            ├── AuthRepositoryTest.kt               # the gate's state machine against a fake API
            ├── AuthViewModelTest.kt                # 16 cases: validation, mismatch, submit gating
            ├── AuthGateLaunchTest.kt               # the real app opens on the gate, not the reader
            ├── DockNowPlayingTest.kt               # a juz track: title and identity tile name the same juz
            ├── ReaderScreenshotTest.kt             # 12 PNGs: mushaf in both themes/inks, both selection
            │                                       #   cases, page boundary, recitation (light/dark),
            │                                       #   and the shell (dock light / dark / idle)
            ├── PresetSequenceTest.kt               # 16 cases: the recipe language, both brief recipes
            ├── PresetSittingTest.kt                # 10 cases: one row per sitting, idle clamping
            ├── PresetCoverageTest.kt               # 13 cases: high-water merge, ranges, percent
            ├── PresetPlayerTest.kt                 # 10 cases: order, repeats, gaps, pause, seek
            ├── PresetPersistenceTest.kt            # 11 cases against real SQLite (one row/6 presses)
            ├── DownloadDomainTest.kt               # 26 cases: scopes, sizing, local index precedence
            ├── DownloadPersistenceTest.kt          # 8 cases against real SQLite (queue semantics)
            ├── ManifestSourceTest.kt               # 9 cases: mirror-vs-CDN URL, declared size
            ├── DownloadsScreenshotTest.kt          # 4 downloads PNGs (fa light, en dark, scope, juz-only)
            ├── AuthScreenshotTest.kt               # 4 gate PNGs: sign in, register, mismatch, en
            ├── PresetsScreenshotTest.kt            # 3 preset PNGs (list fa/en, editor)
            ├── SettingsScreenshotTest.kt           # 4 settings PNGs (reciter chips + attributions)
            ├── HistoryScreenshotTest.kt            # 2 history PNGs
            ├── HomeScreenshotTest.kt               # 2 home PNGs
            └── ../resources/robolectric.properties # sdk=34, graphicsMode=NATIVE
```

## 7. Architecture

- **Offline-first data flow:** UI → ViewModel → Repository → Room (source of truth). Sync runs in the
  background via WorkManager; it never blocks or errors the UI.
- **Sync state machine:** `pending → syncing → synced | failed`; failed rows stay queued. Idempotent on
  `clientId`. Sessions are **append-only after finalize**. Integer seconds for durations, epoch millis
  for timestamps. (The pure tracking state machine will live in `domain/`, driven by real UI/player
  events — never `Timer`/polling; checkpoint every 10s; recover orphaned sessions on launch.)
- **Recitation data flow:** `RecitationRepository` (catalogue asset) → `RecitationQueue.build(page)`
  → `RecitationEngine.setQueue` → Media3 → `PlaybackState(index)` → `ReaderViewModel` → highlight.
  The ViewModel owns the queue; the engine owns only the player. Listening time is billed by feeding
  each ayah transition to the same `ReadingSessionTracker` the reader uses for scrolls, and the open
  session is upgraded to `mode="listen"` with `reciterKey`/`playbackSpeed` frozen into its snapshot
  (`ReadingSessionRepository.markListening`). Audio follows the page on screen: scrolling into another
  page while playing re-queues that page rather than playing a page nobody sees.
- **Recitation granularity (D22):** an edition is `AYAH | SURAH | JUZ`. `RecitationQueue.build(page)`
  serves per-ayah editions; `buildJuz(page)` returns **one track per distinct juz the page touches**,
  so a page straddling a boundary yields two. A juz track sets `RecitationTrack.juz` and
  `servesOneVerse = false`: `ReaderViewModel` publishes `playingJuz` instead of `playingVerse`, draws
  no verse highlight, and **skips `sessions.progress`** — billing "progress at 1:1" while a whole juz
  plays would rewind the session. The dock row reads `جزء N`, and its identity tile carries the same
  number. Granularity is never silently substituted: a 70-minute track cannot start at verse 17, so
  pretending it can would make the highlight lie.
- **Auto-download on play (`AudioAutoCache`):** pressing play calls `ensure(reciter, style, scope)` for
  the page's verse range (per-ayah editions) or for each juz it touches (juz editions). It never
  throws — it returns `Queued | NothingToDo | NoEdition | Failed` — dedupes on `editionKey|scopeKey`
  per process under a `Mutex`, and drops the token on failure so a retry is possible. Caching is an
  optimisation: playback streams whether or not it succeeds.
- **Download scopes:** the wire speaks `ayah | surah | juz | range` and has **no page scope**.
  `DownloadsViewModel.resolveScope` turns a page range into a verse range against the bundled mushaf,
  so the backend never learns what a page is — page boundaries belong to the text the client ships.
- **Reader layout (continuous scroll, D15):** `ReaderItems.build(pages, surahs)` flattens the content
  into `SurahHeaderItem` / `BasmalaItem` / `VerseItem`, each carrying its `page`, and one `LazyColumn`
  renders them. A surah plate is emitted **where a surah changes, not at page breaks** — a plate per
  page would be pagination in disguise. "Which page is visible" is `items[firstVisibleItemIndex].page`
  through `derivedStateOf`, so scrolling does not recompose the screen and no pager side effect exists;
  that page number is what `onPageVisible` / `onReadingActivity` / the recitation queue take.
- **Selection (D14):** `VerseSelection` holds a **set** of `VerseElement(surah, ayah, layer, key)`
  (`layer ∈ VERSE | TRANSLATION | COMMENTARY`, `key` naming the translation) plus one `active` element
  = the last tap. Each pane in `VerseBlock` reads only its own membership, so the Arabic and every
  translation highlight independently; `active` answers the two genuinely singular questions — which
  verse to peek at, and where playback starts. Selection outranks playback for colour, so a tapped
  verse keeps its gold while the recitation moves past it.
- **App shell, dock & player (D16, D17; Plan 02 D2/D4):** `HawraaShell` is `AmbientCanvas` +
  `HawraaNavHost` + `AppDock` + `NowPlayingSheet`, drawn **once** for the whole app — the dock is not a
  screen's chrome, so no screen may draw its own bottom bar. The dock is one glass panel with a
  mini-player half over a **four-peer** nav half (Home, Read, History, Settings). Playback UI is
  **shell-owned**: the `@Singleton NowPlayingStore` holds the `PlayerUi` snapshot, the bound transport
  commands, the expanded flag and the dock's measured mini-player rect, and the shell renders the dock
  plus the full-screen `NowPlayingSheet`/`PlayerContent` (morphed from that rect). The reader keeps its
  own Media3 engine and session (`ReaderViewModel`) and only **publishes** into the store, so the engine
  stays owned by the reader while the player UI never leaves the shell. `openControls` is a
  `SharedFlow` (one-shot), not state — otherwise recomposition would reopen the sheet. Screens owe the
  dock `LocalShellInsets.current.bottom` of bottom padding and nothing else.
- **Selection actions (D17):** tapping a pane selects it and `SelectionActionBox` appears **over the
  selection**, anchored by the focused verse's `boundsInRoot()`: above it when there is room, below it
  otherwise, pinned above the dock as a last resort, and hidden entirely when the verse scrolls out of
  the viewport. Actions are a `List<SelectionAction>` built per selection, so the glossary/terminology
  action is a new entry rather than a layout change. Play is only added when `recitation.available` —
  no dead buttons.
- **Design tokens:** five `CompositionLocal`s, provided by `HawraaTheme`: `LocalHawraaDarkTheme`
  (dark/light flag), `LocalHawraaTokens` (raw palette), `LocalGlassColors` (chrome),
  `LocalQuranColors` (the reading surface, with the reader's ink presets applied via
  `withReadingSettings`) and `LocalHawraaGeometry` (radius/spacing/controls/dock). The Material
  `ColorScheme` is *derived* from the glass tokens, so `MaterialTheme.colorScheme` never disagrees with
  the design system. See `docs/DESIGN.md` §4–§7.
- **DI:** Hilt. `AppModule` provides DataStore, Ktor `HttpClient`, `LesanClient`, Room DB, DAO;
  `RecitationModule` provides the `SimpleCache` and binds the engine.
  `SyncWorker` is `@HiltWorker`; the default WorkManager initializer is disabled in the manifest so
  `HawraaApp`'s `Configuration.Provider` is used.
- **Theming:** `HawraaTheme(themeMode)`; light + dark `ColorScheme`; all colors in `ui/theme/`.
  Theme mode persisted via DataStore (`SettingsRepository`), observed in `MainActivity`. The mushaf
  surface reads `LocalQuranColors` (paper, letter, diacritic, ornament, banner); the reader overlays
  the user's ink presets with `withReadingSettings(settings, dark)`.
- **Content:** `QuranContentRepository` is the only reader of `assets/quran/pages-v1.json`; it maps the
  DTO to immutable domain models and caches them for the process. Nothing else touches the asset.
- **Reading settings:** font size (22–44 sp, 2 sp steps), translation on/off, letter ink (ink/green/
  navy) and diacritic ink (gold/green/grey/burgundy) — stored as primitive/preset keys in
  `SettingsRepository`, resolved to per-theme colours in `ui/theme/QuranColors.kt`.
- **i18n/RTL:** all strings in resources (fa default + en). No hardcoded direction; Quran text is always
  RTL regardless of app locale. Per-app locales via `localeConfig`.
- **Lesan wire format:** `LesanClient.callAct(model, act, set, get, token)` POSTs
  `{ model, act, details:{set,get} }` to the server root; JWT in the `token` header (no Bearer);
  response `{ success, body }`; returns `Result` so sync failures never throw into the UI. A
  `success: false` body becomes a `LesanException` carrying the server's own sentence — the only
  place a failure is allowed to be *read* rather than classified.
- **The account gate (D21):** the app refuses to run until someone is signed in, because history
  written before sign-in belongs to nobody. `AuthRepository` publishes an `AuthState`
  (`Restoring → SignedOut | SignedIn`) that `HawraaShell` collects: `Restoring` draws the canvas
  alone (so a returning reader never sees the form flash), `SignedOut` draws `AuthScreen` **without
  the dock**, `SignedIn` draws the app. The cache is authoritative for drawing and the server only
  for invalidating — `restore()` publishes the cached account *first*, then asks `user/me`, and
  signs out only when the answer is a `LesanException`. Everything else (no route, DNS, timeout) is
  "we could not ask" and keeps the session, which is what makes the gate compatible with
  offline-first. `AuthRepository.token()` returns null unless the state is `SignedIn`, so a
  signed-out device can never push rows under a stale credential.

## 8. Data model (local)

**Schema version 3.** `MIGRATION_1_2` creates the four Phase-5 tables; `MIGRATION_2_3` adds
`downloads.expectedBytes`. Both are verified against Room's generated schema by
`tools/verify-room-migration.py` — 8 CREATE statements and 1 ALTER, exact match. (The verifier grew an
ALTER branch for v3: it compares `(table, column, affinity, notNull, default)` tuples rather than SQL
text, because Room validates the resulting column, not the statement that produced it.)

`expectedBytes` is the size the *server* measured for a mirrored file. It exists because a chunked
response declares no length, so "the stream ended" is the only signal a download finished — and a
dropped connection produces the same signal. `null` means the source never said, which is the normal
case for a CDN file and is not a failure.

The download table deliberately keeps **no** style column: the local audio index reads each edition's
style from the cached catalogue instead, which avoided a schema bump for a field the UI cannot vary.

- `presets` — `clientId` (PK), `serverId?`, `name`, `description?`, `stepsJson`, `reciterKey?`,
  `autoAdvance`, `ayahGapMs`, `isDefault`, `isArchived`, `revision`, `updatedAtMs` (the LWW token),
  `syncStatus`, timestamps. Steps are a JSON column, not a child table: a preset is always read,
  written and played as a whole, and the same DTOs serve the JSON column *and* the Lesan payload.
- `preset_sessions` — one row per **sitting**; `playCount`/`pauseCount`/`seekCount` are incremented
  in SQL on the open row (`WHERE endedAt IS NULL`), which is what makes "no new history entry on
  every play/pause" a property of the database rather than of a caller.
- `preset_ayah_progress` — composite PK `(presetClientId, globalAyah)`; a high-water-mark ledger
  (`plays`, `listenedSec`, `firstAt`, `lastAt`), so a retried sync cannot double-count.
- `downloads` — composite PK `(editionKey, fileKey)`; `scope`/`scopeKey` (batch identity), `surah`/
  `ayah`/`juz`, `url`, `relativePath`, `bytes`, `expectedBytes?`, `state` (queued → downloading →
  done | failed), `error`. Rows are written before any bytes move, so an interrupted download resumes.

`ReadingSessionEntity` (`reading_sessions` table): `clientId` (PK, UUID), `serverId?`, `startedAt`,
`endedAt?`, `surah`, `fromAyah`, `toAyah`, `fromPage?`, `toPage?`, `durationReadSec`, `completed`,
`interrupted`, `mode` ("read"/"listen"), `deviceInfo?`, `settingsJson?`, `syncStatus`, `createdAt`,
`updatedAt`. Mirrors the backend `readingSession` model. `mode` and the snapshot may be rewritten
**while the row is still open** (pressing play upgrades a read session to a listen session, adding
`reciterKey` + `playbackSpeed`); everything else stays append-only after finalize. DAO: upsert, byClientId, pendingForSync(limit),
setSyncStatus, markSynced, finalize, observeHistory(Flow), openSessions (orphan recovery).

## 9. Quran typography — how it actually works (decided 2026-11-14, D5)

Goal: **Uthman Taha** mushaf look with **independently colorable letters (حروف) and diacritics (اعراب)**,
plus markers (verse numbers, hizb, sajdah…) in mushaf positions.

**The naive approach does not work.** Drawing the full verse in the diacritic colour and then redrawing
the same verse with the marks stripped in the letter colour looks perfect under CoreText but breaks
under **HarfBuzz**, which is what Android uses: dropping the marks changes glyph selection and advances
("لِلَّهِ" ligates to one glyph *with* the shadda, and to a different "llah" glyph without it), so the
two layers drift apart and the page shows coloured ghosts. Verified for Scheherazade New, Amiri,
Amiri Quran and KFGQPC Hafs — no face survives it.

**The implemented approach: split the font, not the text.** `tools/build-quran-fonts.py` derives two
faces from one source, leaving GSUB/GPOS/cmap/hmtx untouched so they shape any string to the identical
glyph run at the identical positions:

| File | Contents |
|---|---|
| `res/font/quran_letters.ttf` | every mark glyph's outline emptied → letters only |
| `res/font/quran_marks.ttf` | every base glyph's outline emptied → marks only |

`QuranText` measures the same string with both faces and draws the mark layer (diacritic colour) under
the letter layer (letter colour). Registration is exact — confirmed on Android's own stack by
`ReaderScreenshotTest`. Classification rules live in the script: GDEF class 3 plus any glyph reachable
from a combining-mark codepoint, plus an explicit table for marks baked into a letters glyph (the
shadda of the `uni0647.fina.allah` ligature).

- **UI face: Vazirmatn** (SIL, OFL — licence in `assets/licenses/Vazirmatn-OFL.txt`), bundled from
  Pejvak's Android sector; chrome and Persian UI/content use it, scripture never does.
- **Face: Scheherazade New 4.500** (SIL, OFL — licence in `assets/licenses/`). Chosen because it is a
  normal OpenType Naskh (one glyph per letter, marks placed by GPOS), covers the full Uthmani
  repertoire, and is the closest visual match to the owner's Ketab Mobien reference among the faces
  that *can* colour marks. The word-ligature mushaf faces (KFGQPC Uthmanic Hafs, QCF) bake marks into
  the word glyph and **cannot** colour them at all — if the owner ever prefers that exact mushaf look,
  the colour feature has to go with it.
- `QuranText` is the single render entry point (`ui/quran/`), so the face, sizes and colours can change
  in one place. Line height comes from `quranLineHeight()` — deliberately loose, because shadda+haraka
  and dagger alef stack above the letters.
- **Content scope now: first 5 mushaf pages only** (Madani 604-page: p.1 Al-Fatiha, p.2–5 Al-Baqarah
  1–29). Translation: **Ansarian** (`fa.ansarian`). Recitation: verse-by-verse, see below.
- **Silent letters are muted, never removed.** `domain/quran/SilentLetters.kt` finds them from three
  sources: the Uthmani U+06DF/U+06E0 marker that follows a letter (`أُو۟لَٰٓئِكَ`, `قَالُوا۟`), the
  `ٱل` + sun-letter rule (`ٱلشَّمْس` = ash-shams — *not* marked in the text, so it must be a rule,
  and the article may sit behind a proclitic), and a small lexicon for `مِائَة`/`عَمْرٌو`. The ranges
  become `SpanStyle`s on the one shaped layout — splitting the string would break joining, which is
  the §9 trap again.
- Readability reference: **Ketab Mobin** (cafebazaar.ir/app/com.ketabmobin.app).
- **Ayah marker** = Delapouite's "Rub el Hizb" (game-icons.net, CC BY 3.0) as
  `res/drawable/ayah_marker.xml`; the only edit is dropping its central ring so the number fits.
  Attribution is shown in Settings (CC BY requires it) and recorded in
  `assets/licenses/ayah-marker-game-icons.txt`.
- **Recitation is per ayah, streamed, and disk-cached.** `assets/quran/recitations-v1.json` is the
  catalogue; each reciter carries a URL template with a `{ayah}` placeholder addressed by the
  **global** ayah number (1…6236, Hafs order) from `domain/recitation/AyahNumbering.kt`. One mushaf
  page is one queue of single-item media sources, so "which ayah is playing" *is* the media item
  index: the follow-along highlight moves on `onMediaItemTransition`, never on a position poll. Every
  template is verified by `tools/verify-recitation-audio.py` (ranged GET + mp3 frame check) before it
  ships, because a dead URL is invisible until someone presses play.
- **Shakernejad has no public per-ayah source** (probed 2026-11-15: islamic.network, verses.quran.com,
  everyayah, Quran.com v4, mp3quran v3, quranicaudio — none carry it). The catalogue ships
  **Parhizgar** (default, Persian murattal), al-Afasy, al-Husary and al-Minshawi, and records
  Shakernejad under `"unavailable"` with the probe results. Adding it later is one catalogue entry.
- Signature layout: verse + multiple translations + commentaries visible together, each independently
  scrollable, regardless of total scroll length. *(Not built yet — today the reader shows one
  translation under each verse.)*

## 10. Known pitfalls (already hit — don't repeat)

- **The "strip the marks and redraw" colour trick fails under HarfBuzz** (see §9). Do not reintroduce it.
- **Tanzil embeds the basmala inside the first ayah** of every surah that has one. The content script
  asserts and strips that exact prefix; never strip sacred text by hand.
- Recent **Material3 no longer pulls `Icons` transitively** → add `material-icons-core` explicitly.
- **`hiltViewModel` moved** to `androidx.hilt.lifecycle.viewmodel.compose` (add
  `hilt-lifecycle-viewmodel-compose`); the old `androidx.hilt.navigation.compose.hiltViewModel` is deprecated.
- **Aliyun mirrors 502** on missing artifacts → mavenCentral must come first (see §4).
- **New platforms extract to minor-version dirs** (`android-37.0`) and need a hand-written `package.xml`.
- **core-ktx 1.19+/OkHttp 5.5+ force compileSdk 37** — keep targetSdk lower.
- **Media3 1.11.1 does NOT auto-prepare on `setMediaItems` + `playWhenReady = true`.** It reports
  `STATE_BUFFERING` synchronously, then the playback thread idles and the source never loads — silent,
  no error, no timeout. Always call `prepare()` explicitly after setting items.
- **`DefaultMediaSourceFactory` takes ONE data-source factory**, so a `file://` URI (a downloaded verse)
  gets fed to the HTTP loader and dies with `ERROR_CODE_IO_UNSPECIFIED`. Route by scheme
  (`SchemeRoutingDataSourceFactory`: `file`→`FileDataSource`, else cache-then-network). The 1.11.1
  factory has **no `setDataSourceFactory(Map)`** overload — only the single-factory one.
- **`reciter/gets` returns pure fields only when the client sends no `get` projection** (Lesan
  `projectionOrPure`), i.e. reciters with **no `editions`** → empty download pickers. Request the
  `editions` sub-projection explicitly; note the reverse relation is named `editions`, not
  `recitationEditions`.
- **JVM unit tests with a fake `RecitationEngine` cannot catch any of the three above** — they all
  need the real Media3/ExoPlayer on a device/emulator. A probe Activity in `src/debug/` (now removed)
  was what isolated the `prepare()` stall from the app's own state wiring.
- Default `java` is 1.8 → set `JAVA_HOME` to the Homebrew OpenJDK 22 path.
- **`res/font` file names must be `[a-z0-9_]`** — `ScheherazadeNew-Regular.ttf` will not compile there;
  the unmodified source face therefore lives in `tools/fonts/`.
- **Robolectric screenshots need four things**: `testOptions.unitTests.isIncludeAndroidResources = true`,
  `graphicsMode=NATIVE` (`src/test/resources/robolectric.properties`), `@Config(application =
  Application::class)` so Hilt's `HawraaApp` is not instantiated, and the Compose test rule from
  `androidx.compose.ui.test.junit4.v2` (the non-v2 `createComposeRule` is deprecated and would break
  the zero-warning rule).
- **`sips` crop offsets are `--cropOffset Y X`** and are absolute in pixels — screenshots here are
  1233×2673, so preview coordinates need ×2.47. It is also unreliable (see the last pitfall above).
- **A `SimpleCache` may only be opened once per process for a given directory** — it takes a file
  lock and throws if a second instance appears. That is why it is a Hilt `@Singleton` and never built
  per screen or per player.
- **`material-icons-core` ships only `PlayArrow`**; `Pause`, `SkipNext` and `SkipPrevious` live in the
  10 MB `material-icons-extended`. `ui/components/TransportIcons.kt` builds those four glyphs from
  Material's own 24 dp outlines instead of pulling the artifact.
- **Never replace `LocalContext` with `createConfigurationContext` in a per-app-language wrapper.**
  It returns a bare `ContextImpl`, and `hiltViewModel()` walks the `baseContext` chain looking for an
  Activity — it throws `IllegalStateException: Expected an activity context…` and the app dies on
  every launch once the language is persisted. Use `ContextThemeWrapper(base, 0)` +
  `applyOverrideConfiguration(config)` (D9). The failure happens inside the recomposer coroutine, so
  a plain `Robolectric.buildActivity().setup()` looks fine — you must capture
  `Thread.setDefaultUncaughtExceptionHandler` to see it.
- **Never call `Locale.setDefault` from composition.** It makes unrelated formatting follow the app
  language: `"%013d".format(...)` produced Persian digits inside `reading_sessions.clientId`. Use
  `String.format(Locale.ROOT, …)` for identifiers, and `LocalConfiguration.current.appLocale()` for
  display dates.
- **`BringIntoViewRequester` fights `LazyColumn.animateScrollToItem`.** It was used for recitation
  follow-along; inside a lazy list two scroll owners compete and the list can jitter. The reader now
  scrolls explicitly and only when the reciting verse is **not** in
  `layoutInfo.visibleItemsInfo`, so a reader who is scrolling is never yanked back on every ayah.
- **Do not emit a surah plate at page boundaries.** With a continuous scroll that is pagination in
  disguise, and it was the exact thing being removed. A plate belongs where a surah *starts*
  (`ReaderItemsTest` pins this: al-Baqarah gets one plate across pages 2–5, not four).
- **Selection must be a set keyed by layer, not a boolean.** A single "selected ayah" cannot express
  "these two translations are selected but not the verse", which the planned three-translation +
  commentary layout requires. See D14.
- **Don't loosen the content DTO to make a test pass.** Defaulting `juz`/`hizb`/`rubElHizb`/`sajdah`
  turns a corrupt asset into a silently valid one (everything becomes juz 1). Only `translations` has a
  default, and only because the asset still emits the legacy `translationFa`.
- **Changing a layout invalidates assertions, not just screenshots.** Removing the page chrome broke
  `LocalizedAppTest` ("Page 1 of 5") and `PersianLaunchTest` ("صفحه"). Fix the assertion to pin the
  chrome that actually remains — never delete the test.
- **A translucent dock over scrolling text needs ≥ 98 % opacity, and "glass" does not come from the
  transparency.** At 92–96 % the page's Persian translation stayed **4 % legible** through the dock —
  measurable, and enough to read whole words under the nav icons, which looks like a rendering fault.
  Dark needed the same number (94 % left an 8-lum lift). The border, the 2 dp sheen and the shadow are
  what make it read as glass; the fill only has to occlude. **Measure the PNG, don't eyeball it** — a
  column/region histogram over `captureToImage()` output is the honest test.
- **A screenshot fixture that passes the same icon twice hides a glyph nobody ever sees.** The dock
  capture used `Icons.Filled.Settings` for both *History* and *Settings*, so the hand-drawn
  hand-drawn History clock was never rendered by any screenshot. Fixtures must mirror what the real
  screen passes — that is the whole point of a visual gate.
- **Persian «۱» is a single thin slanted stroke in every Arabic-derived face** (Scheherazade *and* the
  system face). In a 38 dp tile at 14 sp it reads as a smudge, not a numeral. The identity tile
  therefore uses the **UI face at 18 sp semibold**: the mushaf face is for scripture, chrome gets the
  UI face, and a lone numeral needs weight more than it needs calligraphy.
- **`sips --cropOffset` silently no-ops** for some crop sizes (it returns the full image). For
  inspecting a screenshot region, a 20-line Java `ImageIO` crop/upscale (or a per-region luminance
  histogram) is reliable and needs no dependency.
- **The manifest merger's `WorkManagerInitializer … no other declaration present` warning is spurious
  and the node is load-bearing.** `tools:node="remove"` on that meta-data *does* apply — check
  `app/build/intermediates/merged_manifest/debug/…/AndroidManifest.xml` and the
  `manifest-merger-debug-report.txt` (`REJECTED from work-runtime`) — and without it WorkManager
  initialises with the default configuration and silently ignores `HawraaApp`'s
  `Configuration.Provider`. Do not "fix" the warning by deleting the node.
- **A mirrored audio file can be perfectly good while its host's TLS certificate is expired.**
  `dl.meshkatsound.com` answered `invalid peer certificate: certificate expired: verification time
  … but certificate is not valid after …` while serving a genuine 12,651,123-byte `audio/mpeg`.
  Check the payload on its own (`curl -I` → `content-type`, `content-length`) before concluding the
  mirror is broken, then scope the bypass to that host
  (`--unsafely-ignore-certificate-errors=<host>`), never globally. The upstream certificate still
  needs renewing.
- **A juz file is not a verse file, and the difference is not cosmetic.** Substituting a whole-juz
  MP3 into a per-ayah queue makes the follow-along highlight point at a verse that is not sounding
  and makes the session recorder rewind to the juz's first verse. Either the edition is per-ayah or
  the UI says juz — there is no third option that is honest.
- **Calibrate a size estimate against a real file, not against a plausible bitrate.** `bitrate ×
  seconds` with an assumed juz length came out **40 % low** (77 MB against a real 12.65 MB per juz)
  because the assumed duration was 45 min and the file is ~70. A number printed on a download button
  that is 40 % wrong is worse than no number, because it is trusted.
- **`IntArray` has no `zipWithNext`.** The juz-table check ("strictly increasing") needs an explicit
  `for (index in 1 until size)` loop; the extension exists for `Iterable`, not for arrays.

## 11. Conventions (condensed — full rules in `../AGENTS.md`)

Offline-first local write; record every session; append-only after finalize; `clientId` idempotency;
integer seconds / epoch millis; no color literals outside `ui/theme/`; no user-facing string outside
resources; no hardcoded layout direction; pure logic in `domain/` (no Android imports); single-owner
repositories/services (screens never touch DB/network directly).

## 12. Doc map & precedence

| File | Role |
|---|---|
| `../AGENTS.md` | sector rules / conventions |
| `docs/summary.md` | this file — orientation & ground truth |
| **`docs/DESIGN.md`** | **the UI/UX contract — read before any UI work** (colour, type, geometry, glass recipe, motion, every component, Do/Don't, enforcement) |
| `docs/plans/01-*.md` | roadmap **and** progress record — update after every small task |
| `docs/prompts/01-*.md` | the original user brief(s) — the "why" |
| `../../AGENTS.md` | root product invariants |
| `../../docs/ROADMAP.md` | cross-sector phased plan |

Precedence: **code > plan > AGENTS.md > docs.** Where this summary and the code disagree, the code is
right and this file is a bug — fix the file.