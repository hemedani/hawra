# AGENTS.md — Hawraa Android (mobile/android)

Native **Kotlin + Jetpack Compose**, Android only. No Flutter, no React Native, no WebView-based UI. minSdk 29, targetSdk 36, **compileSdk 37** (the latest AndroidX/core-ktx and OkHttp require API 37). AGP 9.x with **built-in Kotlin** (do NOT apply `org.jetbrains.kotlin.android`), KSP for Room/Hilt.

## Build & run

```sh
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on the connected device (owner tests frequently)
./gradlew test                 # JVM unit tests
```

- JDK: `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` (set `JAVA_HOME` if the default java is 1.8).
- SDK: `~/Library/Android/sdk` (via `local.properties`, gitignored). Installed manually via the `redirector.gvt1.com` CDN (platforms android-36 & android-37.0, build-tools 36.0.0, platform-tools) because `dl.google.com` is blocked. `android.builder.sdkDownload=false` is set so a missing component fails fast instead of a doomed auto-download. If you add a platform by hand, it needs a `package.xml` or AGP/sdkmanager won't see it.
- **Repositories** (`settings.gradle.kts`): `dl.google.com` is unreachable here, so Google/AndroidX artifacts come from the Aliyun google mirror. **Order matters: `mavenCentral()` first, then the Aliyun mirror.** The mirror returns HTTP 502 (not 404) for artifacts it lacks, which aborts Gradle's fall-through — so it must sit after mavenCentral (which returns clean 404s). Kotlin/KSP/Hilt/Ktor resolve from mavenCentral; AGP/AndroidX fall through to the mirror. Do not reorder or add the Aliyun `public`/`gradle-plugin` mirrors (they 502).

## Package layout (`ir.hawraa.quran`)

```
app/src/main/kotlin/ir/hawraa/quran/
├── HawraaApp.kt            # @HiltAndroidApp
├── MainActivity.kt         # single activity, Compose entry
├── core/                   # cross-cutting helpers (result types, time, dispatchers)
├── data/
│   ├── local/              # Room: HawraaDatabase, entities, DAOs — source of truth
│   ├── download/           # DownloadManager (manifest → queue → filesDir), AudioAutoCache
│   ├── remote/             # LesanClient (Ktor): wire format { model, act, details:{set,get} }, token header
│   │                       #   + LesanException, AuthApi/LesanAuthApi (register, login, me)
│   ├── repository/         # single-owner repositories; screens never touch DB/network directly
│   │                       #   IdentityRepository (persisted session), AuthRepository (the gate)
│   └── sync/               # WorkManager SyncWorker + sync engine (pending→syncing→synced|failed)
├── domain/                 # plain Kotlin models + pure logic (session tracking, recitation queue,
│                           #   download scopes, presets — no Android imports)
├── di/                     # Hilt modules
└── ui/
    ├── theme/              # ALL design tokens: Color.kt, GlassColors, QuranColors, Geometry, Theme, Type
    ├── navigation/         # HawraaNavHost
    ├── components/         # shared composables — GlassSurface, GlassChip, GlassSegments, AppDock,
    │                       #   SelectionActionBox, EyeGlyph/PasswordReveal, *Icons.kt
    ├── quran/              # mushaf rendering: QuranText, AyahOrnament, MushafBanner, QuranFont
    └── screens/            # one package per screen (auth/, home/, reader/, history/, settings/,
                            #   downloads/, presets/)
```

## Design system — read `docs/DESIGN.md` before any UI work

`docs/DESIGN.md` is the **single source of truth for look, feel and layout rules** (colour, type,
geometry, the glass recipe, motion, every component, and the Do/Don't list). Read it before writing
or changing any composable; it is not optional background reading. If a change makes the code and
the document disagree, fix the document in the same commit — the design doc is part of the deliverable.

Non-negotiables it encodes:

- **No literal values in UI code.** Colours only from `ui/theme/` (`GlassColors` / `QuranColors`),
  radii/spacing/control sizes only from `HawraaGeometry`. A raw `Color(0x…)`, `28.dp` corner or
  `56.dp` control size outside `ui/theme/` is a bug.
- **Two colour families that never mix.** Chrome (glass: translucent, floats) and the reading
  surface (opaque: scripture is read, not decorated). Never paint Quran text on a glass token.
- **One accent = "this is the current thing"**; gold (`mark`) = "the reader put this here" and is
  never interactive. Don't invent a third emphasis colour.
- **The mushaf is one continuous scroll.** No pager, no page numbers, no page-turn chrome.
- **Selection actions float over the selection** (`SelectionActionBox`), driven by a
  `List<SelectionAction>` — never a permanent bottom bar. Playback + navigation live in the dock.
- **The dock is one panel with two halves** (mini-player + nav), owned by the app shell. A screen
  owes it `geometry.dock.dockInset` of bottom padding and nothing else.
- **Icons**: use the semantic `ICON_GLYPHS` catalog (`ui/components/Icon.kt`), drawn from Material
  **Rounded** in `material-icons-extended`. This is the accepted exception to the old "core only"
  rule (D3): Pejvak's catalog needs exact Rounded glyphs, and the ~10 MB APK cost is a stated,
  deliberate trade for visual fidelity. Only `Icon.kt` imports an icon set; screens name a semantic
  glyph, and a missing glyph is added to the catalog — never hand-drawn per screen.
- **A chip is a filter, `GlassSegments` is a choice.** Anything selecting one-of-N uses
  `GlassSegments` (glass palette, filled `accent` selection); chips toggle. Mixing them makes the app
  feel imprecise, and `SegmentedPill`'s Material palette is one shade off every glass pane around it.
- **A UI change is not done until a screenshot proves it.** `ReaderScreenshotTest` renders the real
  composables through Robolectric native graphics into `app/build/screenshots/`; add or update a
  capture for anything visual, in both themes.

## Product rules (see root AGENTS.md for the full list)

- **Offline-first**: write local first; sync is background, batched (≤50), idempotent on `clientId`, never blocks or errors the UI.
- **The account gate**: nothing runs until someone is signed in (`AuthState` from `AuthRepository`, drawn by `HawraaShell`). After that the app is offline-first again — the cached session opens it with no connection, and **only a server answer may sign a reader out** (`LesanException`, i.e. "the server replied and refused"; a network failure is not a refusal). Never call `IdentityRepository.token()` directly for a request: use `AuthRepository.token()`, which returns null unless the state is `SignedIn`, so a signed-out device cannot push rows under a stale credential. The gate's server messages are the only ones the UI reads — see the mapping note in the backend's `AGENTS.md` §4.9 before rewording one.
- **Recitation granularity is a property of the edition** (`ReciterGranularity.AYAH | JUZ`). A juz
  edition gets `RecitationQueue.buildJuz` — **one track per distinct juz the page touches** — never a
  silent substitution into the per-ayah queue: a 70-minute file cannot start at verse 17, so it
  publishes `playingJuz` instead of `playingVerse`, draws no verse highlight, writes **no**
  `sessions.progress`, and the dock row says `جزء N` (tile included). Adding a `SURAH` edition means
  a third branch here, not a fudge.
- **Audio downloads are a side effect of playing** (`AudioAutoCache.ensure`), and they never throw:
  they return `Queued | NothingToDo | NoEdition | Failed`, dedupe on `editionKey|scopeKey` per
  process, and drop the token on failure so a retry is possible. Playback streams whether or not the
  cache succeeds — caching must never be able to break play.
- **The wire has four download scopes** (`ayah | surah | juz | range`) and **no page scope**. A page
  belongs to the mushaf text the client ships, so `DownloadsViewModel.resolveScope` converts a page
  range into a verse range. Do not teach the backend about pages.
- **Session tracking**: pure state machine in `domain/` (no Android imports), driven by real UI/player events — never `Timer`/polling. Checkpoint every 10 s; recover orphaned sessions on launch as `interrupted`. Sessions append-only after finalize. Integer seconds for durations, epoch millis for timestamps.
- **i18n/RTL**: every user-facing string in `res/values/strings.xml` (fa default) + `res/values-en/`. No hardcoded direction — use `LocalLayoutDirection`/start-end. Quran text itself is always RTL. Per-app locales via `localeConfig` (fa, en).
- **Theming**: no color literals outside `ui/theme/`. Light + dark from day one; theme mode (light/dark/system) is a setting. The full token contract — palette, glass recipe, geometry, typography — is `docs/DESIGN.md`.

## Quran typography (the hard part — Phase 1/2)

Goal: Uthman Taha mushaf look with **independently colorable letters and diacritics**, plus markers (verse numbers, hizb, sajdah…) in mushaf positions.

- Compose `Text` cannot color diacritics separately from base letters. Plan: custom rendering — either per-glyph `TextLayoutResult` painting (classify code points: base letter vs. combining mark U+064B–U+065F, U+0670, U+06D6–U+06ED, etc.) or a two-layer layout. Decide in Phase 1 with a spike; keep the renderer behind an interface so the font can change.
- Font candidates: KFGQPC Uthman Taha Naskh (verify license), or per-page QCF fonts (qurancomplex). Bundle as assets; never fetch sacred text at runtime.
- Content for now: **first 5 mushaf pages only**, Ansarian translation, Shakernejad recitation.

## Version pins (verified stable, Nov 2026)

AGP 9.5.1 · Gradle 9.6.0 · Kotlin 2.4.21 (built-in) · KSP 2.3.13 · Hilt 2.60.1 · Compose BOM 2026.09.00 · Room 2.8.5 · Media3 1.11.1 · WorkManager 2.12.0 · Navigation 2.10.2 · Lifecycle 2.11.0 · Activity 1.13.0 · DataStore 1.3.0 · Ktor 3.6.1 · kotlinx-serialization 1.13.0 · coroutines 1.11.0. Bump only with a stated reason; check AGP compatibility first (KSP ≥ 2.3.6, Hilt ≥ 2.59.2 for AGP 9).

## Documentation & plan discipline

Sector docs live in `docs/`: `summary.md` (orientation, stack, build quirks), **`DESIGN.md` (the UI/UX
contract — read before any UI work)**, `plans/01-*.md` (roadmap **and** progress record),
`prompts/01-*.md` (the original user brief — the "why"). Read `docs/summary.md` first when picking up
work, then `docs/DESIGN.md` before touching the UI.

**⚠️ Update the plan file after EVERY small task** — tick the box and append a progress-log entry. Not
at the end of a phase or session; after each small task. Precedence: **code > plan > AGENTS.md > docs**;
where a doc and the code disagree, the code is right and the doc is a bug.

## Done criteria

A change is done when `./gradlew assembleDebug` passes, unit tests for touched pure logic pass, and (for UI) it has been looked at on a device/emulator in both themes and both locales.