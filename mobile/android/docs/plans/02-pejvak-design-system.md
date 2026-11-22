# Plan 02 — Adopt the Pejvak Android design system

> **Status:** in progress. This file is both the roadmap and the work record.
> **Sector:** `mobile/android/` · **Source of truth for look:** [`../DESIGN.md`](../DESIGN.md)
> (rewritten in Task 9 to mirror `/Users/syd/work/Pejvak/android/DESING.md`).
> **Reference implementation:** `/Users/syd/work/Pejvak/android` — the owner's own app. Porting its
> Compose code is allowed; this is not third-party GPL code.
>
> **Discipline:** tick a box and append a progress entry after each small task. A task is not done
> until `./gradlew assembleDebug` passes, the touched tests pass, and a Robolectric screenshot has
> been rendered and inspected in both themes. Never weaken a contrast threshold to make a test pass.

---

## 1. Goal

Make Hawraa wear the Pejvak visual system exactly:

- the same floating **frosted-glass header** (capsule, overline/title/subtitle, circular bubbles,
  morphing search support);
- the same **app dock** (one frost panel, mini-player half over icon-only nav half, hairline wave
  divider, selected white circle, press spring);
- the same **mini-player** (identity tile, title/subtitle, wave, play/next bubbles);
- the same **full player** (measured-rect morph from the mini-player, hero tile hand-off, wave
  scrubber, transport bubbles, pull-down dismiss);
- the same **motion** (OceanSwell easing, Pejvak springs, OceanCascade/oceanEntrance, page
  transitions, ambient surge, WaveSweep, haptics);
- the same **documentation** structure and design contract as the Pejvak Android sector.

Hawraa's product invariants stay: Persian-first RTL UI, opaque mushaf reading surface, ayah/juz
recitation granularity, offline-first session tracking, no artwork fetching.

## 2. Decisions (binding for this plan)

| ID | Decision | Why | Cost if wrong |
|----|----------|-----|---------------|
| D1 | **UI stays Persian-first RTL.** Pejvak's LTR visual language is applied with start/end and mirrored directional motion. | Hawraa ships fa default; AGENTS.md makes RTL infrastructure, not a feature. | Directional polish (wave travel, chevrons) needs a one-line mirror fix. |
| D2 | **Four dock peers, not five** (Home, Read, History, Settings). | Hawraa has four destinations; a fifth would be an empty tab. The dock component and animation are unchanged. | None — visual rhythm still even. |
| D3 | **Keep `material-icons-extended` and port Pejvak's `ICON_GLYPHS` catalog.** | Pejvak uses Material **Rounded** glyphs; hand-drawing them would not be exact. The 10 MB cost is accepted for visual fidelity. AGENTS.md is updated to match Pejvak. | Larger APK; reversible by replacing the catalog with custom vectors. |
| D4 | **The full player moves to the shell.** Extend `NowPlayingStore` into the single player store: a `PlayerUi` snapshot + command callbacks + a position provider, published by the reader. The reader keeps the Media3 engine and session tracking. | Pejvak's morph requires the dock (shell) and the player (shell) to share measured rects and one state. The current reader-owned player cannot morph from the dock. | Refactor risk around transport binding; covered by `RecitationEngineTest` + device/screenshot checks. |
| D5 | **No artwork.** The identity tile is the surah/juz number on the aurora wash, as today; the player hero is the same tile at hero size. | Hawraa never fetches or bundles artwork. | None. |
| D6 | **No invented search.** `GlassHeader` supports the morphing search capsule, but Hawraa only wires it where a real query exists (none yet). | A search bubble that does nothing is worse than no bubble. | Add search later without touching the header. |
| D7 | **Docs mirror Pejvak's contract.** `docs/DESIGN.md` is rewritten to Pejvak's 16-section structure, adapted for the mushaf exception and RTL. `AGENTS.md` design rules and version pins follow. | Owner asked for the same development documentation as Pejvak's Android sector. | Doc drift; fixed in the same commit as code. |
| D8 | **The mushaf surface is the one non-glass surface.** Chrome is glass; scripture is opaque paper. | Root AGENTS invariant 1 + Pejvak's own "content cards are glass" applies to chrome/cards, not sacred text legibility. | If wrong, the reader loses legibility — unacceptable. |
| D9 | **Port only player features Hawraa has:** play/pause, prev/next, seek within the current track, speed, repeat, reciter, preset, download. No queue, sleep, tone, enhancer, annotations, ±30 s. | Those Pejvak sheets have no data or engine support in Hawraa. | Missing features stay missing until their backend exists. |
| D10 | **Port Pejvak's custom `Backdrop` (draw-node blur), not Haze.** | That is what the reference app actually ships; it is already proven with the same Compose BOM. | A future migration to Haze is isolated in `Backdrop.kt`. |

## 3. Global constraints

These bind every task; a reviewer must treat them as the rubric.

- **No literal design values outside `ui/theme/`.** Colors only from `LocalHawraaTokens` /
  `LocalGlassColors` / `LocalQuranColors`; radii/spacing/sizes only from `HawraaGeometry`. A raw
  `Color(0x…)`, `28.dp` corner, `56.dp` control size, or dock clearance literal in UI code is a bug.
- **Pejvak package map:** `com.pejvak.app.ui.theme.LocalPejvakTokens` → `ir.hawraa.quran.ui.theme.LocalHawraaTokens`;
  `LocalPejvakGeometry` → `LocalHawraaGeometry`; `PejvakSpring`/`PejvakDuration` → `HawraaSpring`/`HawraaDuration`;
  `com.pejvak.app.R` → `ir.hawraa.quran.R`; `com.pejvak.app.ui` → `ir.hawraa.quran.ui`.
- **Sibling rule:** the header, dock, mini-player and full player are **siblings of screen content**,
  never children. Screen bodies are `backdropSource(zIndex = 1f)`; `AmbientCanvas` is `0f`; chrome
  panes are crisp consumers. Port `Backdrop` wholesale — do not replace it with `Modifier.blur`.
- **Glass recipe:** `glassFill` + `glassBorder` + a 3 dp top `glassHighlight`; shell chrome uses
  `FrostedSurface` (live blur on API 31+, `frostFallback` on 29–30). No hard drop shadows on content
  cards; only `elevation.dock` on dock/player.
- **Typography:** Pejvak type scale; Vazirmatn for Persian UI/content; tabular figures for counts and
  the seeker clock. Quran text keeps `QuranText` (never route scripture through UI text styles).
- **Motion:** every animation checks `useMotionEnabled()` and substitutes the final value when false.
  Springs/easing/durations come from `ui/theme/Motion.kt`; no ad-hoc `tween` constants.
- **Strings:** every user-facing string in `res/values/strings.xml` (fa) and `values-en/`; no
  hardcoded direction (`start`/`end`); Quran text always RTL.
- **Offline-first and the account gate are untouched.** No task may make playback, reading, or
  session recording depend on the network.
- **Build gate:** `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug`
  and `./gradlew test` (or `testDebugUnitTest`) must pass. Do not reorder Gradle repositories.
- **Screenshots:** Robolectric native graphics captures in `app/build/screenshots/` are the visual
  evidence on this machine. Add/update a capture for anything visual and inspect it in light + dark.

## 4. Tasks

### Task 1 — Foundation: fonts, icons policy, build gate ✅

**Goal:** the project compiles again and the design dependencies match Pejvak.

**Files**
- `app/src/main/res/font/vazirmatn_regular.ttf`, `vazirmatn_medium.ttf`, `vazirmatn_semibold.ttf`,
  `vazirmatn_bold.ttf` — copy from
  `/Users/syd/work/Pejvak/android/app/src/main/res/font/`.
- `app/src/main/assets/licenses/Vazirmatn-OFL.txt` — copy from Pejvak.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — keep
  `androidx.compose.material.icons.extended` (D3).
- `AGENTS.md` — update the icon rule: Pejvak's semantic `ICON_GLYPHS` catalog uses Material Rounded
  from `material-icons-extended`; the APK cost is an accepted, stated exception.
- `docs/summary.md` — note the Vazirmatn UI face.

**Requirements**
1. `Fonts.kt` resolves all four `R.font.vazirmatn_*` resources.
2. Add a Robolectric/unit test that the `Vazirmatn` `FontFamily` has four fonts and the resources
   load (no exception).
3. `./gradlew assembleDebug` is green.
4. No other UI file changes in this task.

**Done when:** build green; font test green; AGENTS.md reflects D3.

---

### Task 2 — Glass, frost, backdrop, wave and icon primitives ✅

**Goal:** port the material and motion substrate the rest of the port stands on.

**Source files (port with package renames from `/Users/syd/work/Pejvak/android/app/src/main/kotlin/com/pejvak/app/ui/`)**
- `components/Backdrop.kt` — `Backdrop`, `LocalBackdrop`, `rememberBackdrop`,
  `Modifier.backdropSource`, `BackdropBlur`; keep the API 31+ gate and 29–30 fallback exactly.
- `components/FrostedSurface.kt` — `ShellInsets`, `LocalShellInsets`, `FrostedSurface`, `dockElevation`.
- `components/GlassSurface.kt` — replace Hawraa's implementation with Pejvak's; keep an optional
  `elevation: GlassElevation = GlassElevation.None` parameter so existing call sites compile.
- `components/HairlineWave.kt`, `components/Wave.kt`, `components/WaveProgressBar.kt` — verbatim.
- `components/AmbientCanvas.kt` — replace with Pejvak's version (`navTrigger`, `navShiftX/Y`, four
  blooms, 760 ms surge). Keep the canvas colors from `LocalHawraaTokens` / aurora ramps.
- `components/GlassSheet.kt` — `SheetPresence`, `LocalSheetPresence`, `GlassSheet`, `SheetWake`.
- `components/Icon.kt` — semantic `ICON_GLYPHS` + `Icon` wrapper; only this file imports an icon set.
- `components/Buttons.kt` — `BubbleButton` (Pejvak's outlined/filled bubble) and `PrimaryButton`.
  Reconcile with Hawraa's existing `BubbleButton` call sites; keep the Hawraa name and signatures
  where possible, add overloads only when a screen needs the new capability.
- `components/States.kt` — `EmptyState`, `InlineNotice`, `SkeletonRows`.

**Requirements**
1. All primitives read `LocalHawraaTokens` / `LocalHawraaGeometry`; no color or dp literals.
2. `Backdrop` has no dependency on Pejvak data types.
3. `GlassSurface` keeps Hawraa's `GlassElevation` enum (used by the dock) and Pejvak's visual recipe.
4. Add unit tests for pure wave helpers (`waveOffsetAt`, `wavePath`, `ramp`) with the Pejvak
   expectations; add a screenshot capture of `FrostedSurface` + `GlassSurface` + `HairlineWave`.
5. Do **not** wire the backdrop into the shell yet (Task 7).

**Done when:** build green; wave-helper tests green; screenshot shows the new pane material.

---

### Task 3 — ScreenFrame and the floating GlassHeader ✅

**Goal:** every screen shares Pejvak's header anatomy and measured top inset.

**Source files**
- `components/ScreenFrame.kt` — verbatim API (`header`, `subHeader`, `content(topInset)`), body is a
  `backdropSource(zIndex = 1f)` and reports `contentTopInset`.
- `components/ScreenHeader.kt` — `HeaderSearch`, `GlassHeader` (leading slot, overline/title/subtitle,
  trailing action row, search capsule morph via `Crossfade` + `animateContentSize`).

**Requirements**
1. Header floats at `header.margin`, min height `header.minHeight`, content padding
   `header.contentPadding`, capsule radius `radius.xl` (nav-only pill variant not needed here).
2. Uses `FrostedSurface`; status-bar inset owned by the header; `start`/`end` only.
3. Persian titles use the UI face (Vazirmatn) with correct RTL; `userTitle` flag behaves as Pejvak.
4. Search morph is gated by `useMotionEnabled()` and fires a haptic.
5. Add a Robolectric capture `HeaderScreenshotTest` rendering: title-only, overline+subtitle,
   leading back bubble, trailing actions, and search-expanded, in both themes and fa/en.

**Done when:** build green; header capture inspected; no screen uses it yet.

---

### Task 4 — App dock and mini-player ✅

**Goal:** the exact Pejvak dock: one frost panel, collapsible mini half, icon-only nav half.

**Source files**
- `components/AppDock.kt` — port `NowPlayingUi`, `AppDock`, `MiniPlayerRow`, `DockItem`,
  `tabRouteGlyph` equivalent. Four peers (D2).
- Reuse `components/WaveProgressBar.kt` / `WavyProgress` for the mini wave.

**Requirements**
1. One `FrostedSurface`: `radius.xl` with mini present, `radius.pill` when nav-only; halves equal
   height `dock.playerHeight` / `dock.navHeight` (58).
2. Mini presence animates with `expandVertically + fadeIn` / `shrinkVertically + fadeOut`; under
   reduced motion use zero-duration specs. `HairlineWave` divider inside the fading group.
3. Nav: icon-only peers, selected = solid `accent` circle (`dock.indicator`) with `onAccent` glyph at
   `dock.iconActive`, unselected at `dock.icon` in `textSecondary`; press spring
   `1 → 0.92 → 1.05 → 1` + `HapticIntent.Select`; no travelling indicator.
4. Mini row: identity tile (surah/juz number, `PaletteTile` aurora, UI face), title/subtitle, wave,
   filled play/pause bubble, outlined next bubble; whole leading region opens the player.
5. Dock publishes its measured mini-player `Rect` through a callback/store so Task 6 can morph.
6. RTL: nav order follows the existing destination list; wave and press are direction-agnostic.
7. Update `DockNowPlayingTest` / add `DockScreenshotTest` for: nav-only, playing, juz title, busy,
   fa RTL and en LTR, both themes.

**Done when:** build green; dock captures inspected; `NowPlayingStore` still supplies state (Task 6
may extend it, not replace it).

---

### Task 5 — Player parts: scrubber, wave, transport ✅

**Goal:** Pejvak's player controls, reduced to Hawraa's feature set (D9).

**Source files**
- `components/player/Scrubber.kt` — `ScrubberMarker` (may be unused), `Scrubber`, `WavyProgress`.
- `components/player/Transport.kt` — `TransportControls`, `SpinnerRing`, `PlaybackStatusLine`.
- `components/player/PlayerParts.kt` — `HeroArtwork` adapted to an identity tile (`PaletteTile`
  replacement: number on aurora wash), `PlayerChipRow`, `LevelSliderRow` only if a screen uses it.

**Requirements**
1. `Scrubber`: Pejvak's full-loudness strip (one bar per RMS window, played bars in `accent`),
   the played wave with its wake, and the riding playhead dot (5.5 dp dot / 9 dp halo) over the
   strip; remaining-time caption in `caption` tabular figures; while waiting a tapered travelling
   band; seek callback in ms.
2. `WavyProgress`: Pejvak's wave at `MINI_SCALE`; when `waveform == null`, render a deterministic
   decorative wave (never a flat line) so the dock and player still show the water language.
3. `TransportControls`: prev / play-pause / next. Play is the 64 dp filled bubble; prev/next are
   48 dp outlined bubbles; waiting = `SpinnerRing`. No ±30 (D9).
4. All sizes from `HawraaGeometry`; motion gated; haptics on press.
5. Unit tests for the waveform/position mapping pure functions; `PlayerPartsScreenshotTest` capture of
   the transport + scrubber in both themes.

**Done when:** build green; tests green; capture inspected.

---

### Task 6 — Shell-owned player: store, morphing sheet, content ✅

**Goal:** the full player expands from the mini-player exactly as Pejvak's does.

**Architecture (D4)**
- Extend `ui/recitation/NowPlayingStore.kt` (rename only if it improves clarity) to hold:
  - `PlayerUi` snapshot: title, subtitle, surah/juz, identity number, reciter, featured verse +
    translation, active step/preset, isPlaying, busy, failed, speed, repeat, reciters, selected
    reciter, presets, downloadable page.
  - commands: `toggle`, `next`, `previous`, `seek(ms)`, `cycleSpeed`, `toggleRepeat`,
    `selectReciter`, `download`, `playPreset`, `exitPreset`, `close`.
  - `positionProvider: () -> Pair<Int, Int>` (position/duration ms) read by a leaf ticker.
  - `miniPlayerRect: StateFlow<Rect?>` published by the dock.
- `ReaderViewModel` publishes the snapshot and binds commands; it keeps the engine and session
  tracking. `ReaderScreen` no longer mounts `NowPlayingScreen`.
- `HawraaShell` mounts `NowPlayingSheet` above the nav host; dock hides while `expanded`.

**Source files**
- `screens/player/NowPlayingSheet.kt` — port the morph: `expand: Animatable`, open/close springs,
  measured `targetRect`, `cardLeft/Top/Right/Bottom`, `corner = miniCorner * (1 - t^1.6)`,
  `rimAlpha`, `shadow`, `contentAlpha/Scale`, `miniRowAlpha/Scale`, manual glass→opaque cross-fade,
  hero-tile flight, pull-down dismiss with velocity, `onExpandProgress` to the shell.
- `screens/player/PlayerContent.kt` — reduced Hawraa content: header (close bubble, centered title,
  reciter/options), hero identity tile, surah/juz title + reciter, featured verse card with
  translation, scrubber, transport, speed/repeat/reciter/download/preset chips, status line.
  Fixed viewport layout + `graphicsLayer` alpha/scale, as Pejvak.
- `screens/player/StandaloneMiniPlayer.kt` — port for routes without the dock (none currently, but
  keep the component for the shell contract).

**Requirements**
1. Opening from the mini-player starts at the measured rect; closing reverses onto the dock. Reduced
   motion snaps.
2. The shell applies `backdropBlur = max(sheetBlur, frost.backdrop * progress)` to the content box;
   the player is outside the blur. API 29–30 falls back to the frost scrim.
3. Position ticks recompose only the seeker/wave leaf, not the whole sheet or dock.
4. No Pejvak-only sheets (queue/sleep/jump/enhancer/tone/notes) are mounted. The player must not show
   a control that does nothing.
5. Existing behaviour preserved: play/pause, prev/next, speed, repeat, reciter picker, preset
   playback, download page, close. Juz tracks show «جزء N», no verse highlight, no progress writes.
6. Tests: pure morph math (`lerp`, corner/alpha at t = 0, 0.5, 1) unit-tested;
   `NowPlayingScreenshotTest` at mini, mid-morph and full in both themes/locales.

**Done when:** build green; morph tests green; captures inspected; device/emulator check of
open→play→close if available.

---

### Task 7 — Shell and navigation motion

**Goal:** the shell owns the backdrop, modal blur, page transitions, ambient surge and WaveSweep.

**Source files**
- `navigation/HawraaNavHost.kt` — restructure toward `PejvakNavHost`:
  `CompositionLocalProvider(LocalBackdrop, LocalShellInsets, LocalSheetPresence)`; content box with
  `Modifier.blur(backdropBlur)`; `AmbientCanvas(backdropSource 0f)`; NavHost transitions
  (`oceanEnter/Exit/PopEnter/PopExit`); dock/standalone mini siblings; `WaveSweep` overlay; player
  sheet sibling.
- `ui/HawraaShell.kt` — gate stays; mount the nav host and player; dock clearance measured into
  `LocalShellInsets`; remove the old `playerExpanded` dock-hiding hack (the player is a sibling now).
- `components/ScreenFrame.kt` — body wrapper becomes the Haze/backdrop source and applies the sheet
  frost (Pejvak's final arrangement: no `Modifier.blur` on the source node).

**Requirements**
1. Page transitions: tab drift (lateral + vertical + scale), push (rise), pop (reverse), all with
   `OceanSwellEasing` and `useMotionEnabled()`; RTL tab direction mirrors (D1).
2. `AmbientCanvas` surges on every route change; `WaveSweep` draws the aqua band + two crests + wake
   for tab/push/pop; both static under reduced motion.
3. `LocalShellInsets.bottom` is the measured dock clearance; screens stop hardcoding `dockInset`.
4. Opening the player or a sheet blurs the whole shell at `frost.backdrop` (API 31+), while chrome
   panes stay crisp; API 29–30 fallback is the scrim.
5. No regression to the account gate, session tracking, or the reader's scroll.

**Done when:** build green; navigation between all four tabs and pushed screens shows the Pejvak
motion; screenshots of a tab switch and a push in both themes.

---

### Task 8 — Absorb every screen

**Goal:** no screen keeps its old chrome; all use `ScreenFrame` + `GlassHeader` and the new components.

**Screens and required header anatomy**
| Screen | Leading | Title | Trailing |
|---|---|---|---|
| Home | — | overline + «خانه» | settings bubble |
| Reader | back (when pushed) | overline + surah/page context | settings, download, player |
| History | — | overline + «تاریخ» | (existing actions only) |
| Settings | back | «تنظیمات» | — |
| Downloads | back | «دانلودها» | — |
| Presets / Preset editor | back | title | save/delete as today |
| Auth | — | centered brand (exclusion: unchanged layout) | — |

**Requirements**
1. Remove every `Scaffold` + `TopAppBar` header from the listed screens; content top padding comes
   from `ScreenFrame`'s `topInset`.
2. Replace `GlassSurface` cards with the new recipe; add `OceanCascade` + `Modifier.oceanEntrance`
   to initial lists/cards (first 10 items).
3. Reader: keep the continuous scroll, verse/translation selection, `SelectionActionBox`, session
   tracking. The mushaf page remains opaque; only chrome is glass. The reader no longer draws the
   player (Task 6 moved it to the shell), but keeps its publish/bind wiring.
4. Home/History/Presets/Downloads/Settings: existing content and actions, new chrome; no new
   features, no dead controls.
5. Strings: any new header labels go through resources in fa + en.
6. Update every screenshot test to the new shell; inspect both themes and both locales.

**Done when:** build green; all screenshot tests green; every screen visually inspected.

---

### Task 9 — Documentation: mirror the Pejvak Android sector

**Goal:** the docs describe the shipped design, in Pejvak's structure.

**Files**
- `docs/DESIGN.md` — rewrite to Pejvak's `DESING.md` 16-section structure:
  intent, ambient canvas, colour (canvas/glass/frost/text/accent/aurora), typography, shape &
  spacing, depth, iconography, motion, components, screens, accessibility/contrast, themes,
  RTL/Persian content, assets, Compose notes, Do/Don't. Adapt: fa-RTL UI, opaque mushaf surface
  exception, no artwork, ayah/juz player features, four dock peers, Hawraa's strings.
- `AGENTS.md` — update the design section: pointer to `docs/DESIGN.md`, the glass/frost/backdrop
  rules, motion/haptics, icon catalog exception (D3), no-literal-values rule, and the new player
  ownership (shell store, reader engine).
- `docs/summary.md` — update the UI/architecture map and the player ownership note.
- `docs/plans/02-pejvak-design-system.md` — tick boxes, append progress entries.
- `docs/plans/01-buildable-skeleton.md` — add a pointer to Plan 02 and note the design supersession.

**Requirements**
1. Code and docs agree; where they disagree, fix the doc in this task.
2. No Pejvak-specific product claims (library, notes, AI, artwork) leak into Hawraa's doc.
3. The doc is the single source for colors, radii, spacing, motion, and component anatomy.

**Done when:** a new agent can read `docs/summary.md` → `docs/DESIGN.md` and implement a screen
without opening Pejvak.

---

### Task 10 — Final verification and cleanup

**Goal:** prove the port.

**Requirements**
1. `./gradlew assembleDebug` green; APK path reported.
2. `./gradlew test` green (all unit + Robolectric screenshot tests).
3. Render and inspect every screenshot in `app/build/screenshots/` in light + dark, fa + en:
   header, dock (nav/playing/juz/busy), player (mini/mid/full), home, reader, history, settings,
   downloads, presets, auth.
4. Check reduced motion: no entrance animation, no wave sweep, no ambient surge, instant player.
5. Check API 29–30 fallback path by forcing `Build.VERSION.SDK_INT` in a Robolectric test or by
   reviewing `FrostedSurface`/`Backdrop` gates; no invisible panes.
6. Remove dead code from the old design (`Geometry.kt` leftovers, unused components, unused strings).
7. Update the plan progress log and report the APK path.

**Done when:** every gate above is verified with evidence in the plan log.

## 5. Verification matrix

| Area | Command / evidence |
|---|---|
| Build | `JAVA_HOME=… ./gradlew assembleDebug` |
| Unit | `./gradlew test` |
| Screenshots | `app/build/screenshots/*.png`, inspected light+dark, fa+en |
| Header | overline/title/subtitle, leading/trailing, search morph, RTL |
| Dock | nav-only, playing, juz, busy; collapse animation; press spring |
| Player | mini→full morph, hero flight, pull-down, reduced motion |
| Motion | tab drift, push, pop, ambient surge, WaveSweep |
| Fallback | API 29–30 frost scrim, no invisible text |
| Invariants | offline reading, session tracking, account gate, opaque mushaf |

## 6. Progress log

- 2026-11-21 — Plan authored after auditing both apps. Baseline checkpoint commit preserves the
  previous agent's theme-token port. Build is blocked only on missing Vazirmatn fonts.
- 2026-11-21 — **Task 1 complete.** Bundled the four Vazirmatn TTFs (copied byte-for-byte from Pejvak)
  and `Vazirmatn-OFL.txt` under `assets/licenses/`; added `VazirmatnFontTest` (family declares four
  weights, every `R.font` resource resolves through the loader); updated the `AGENTS.md` icon rule to
  the `ICON_GLYPHS` / Material Rounded policy (D3); noted the UI face in `docs/summary.md`.
  `assembleDebug` green; `./gradlew test` 324/324.
- 2026-11-21 — **Task 2 complete.** Ported the Pejvak material/motion substrate with the package map:
  `Backdrop` (draw-node blur coordinator, API 31+ gate and 29–30 fallback verbatim),
  `FrostedSurface` (`ShellInsets`/`LocalShellInsets`/`dockElevation`), `GlassSurface` (Pejvak recipe
  plus Hawraa's `GlassElevation`/soft-shadow compatibility), `Wave`/`HairlineWave`/`WaveProgressBar`,
  `AmbientCanvas` (nav surge, four cool blooms, 760 ms), `GlassSheet`
  (`SheetPresence`/`LocalSheetPresence`/`SheetWake`, Vazirmatn `Text`), the semantic `ICON_GLYPHS`
  catalog + `Icon`, `PrimaryButton`, and `EmptyState`/`InlineNotice`/`SkeletonRows`. Added the
  geometry/token groups they need (`GlassGeometry`, `WaveGeometry`, `SheetGeometry`,
  `SkeletonGeometry`, `Controls.spinner*`, `scrim`) and `action_close` in fa/en. New tests:
  `WaveHelperTest` (9), `IconCatalogTest` (2), `GlassPrimitivesScreenshotTest` (light + dark).
  `assembleDebug` green; `./gradlew test` 337/337; captures at
  `app/build/screenshots/glass-primitives-{light,dark}.png` inspected.
- 2026-11-21 — **Task 3 complete.** Ported `ScreenFrame` (verbatim API, body is
  `backdropSource(zIndex = 1f)`, measured header/sub-header top inset, safe with a null
  `LocalBackdrop`) and `ScreenHeader` (`HeaderSearch`, `GlassHeader`: floating `FrostedSurface`
  capsule at `header.margin` / `header.minHeight` / `header.contentPadding`, `radius.xl`, leading
  slot, overline/title/subtitle, trailing `RowScope` actions, and a search bubble that morphs into
  an inline field via `Crossfade` + `animateContentSize` gated by `useMotionEnabled`, with
  `HapticIntent.Select`/`Tap` and `BackHandler`). `userTitle` swaps the title to Vazirmatn; search
  input/placeholder and all padding use `start`/`end` so RTL flips correctly. Added
  `search_close` (fa/en) and reused the existing `search_action`/`search_clear`/`search_placeholder`.
  New `HeaderScreenshotTest` (8 captures: gallery + expanded search × light/dark × fa/en) at
  `app/build/screenshots/header-{gallery,search}-{light,dark}-{fa,en}.png`, all inspected.
  `assembleDebug` green; `./gradlew test` 345/345. No screen wired yet (Tasks 7–8).
- 2026-11-21 — **Task 5 complete** (executed before Task 4; the dock consumes these parts).
  Ported `PaletteTile` (aurora wash from `paletteIndexFor`, Vazirmatn label, no Coil artwork),
  `Scrubber`/`WavyProgress` (full-loudness bar strip, played wave, riding playhead + halo, wake
  trail, waiting band, ms seek callback, deterministic `sampleWaveformFor` decorative wave when
  `waveform == null`), `TransportControls` (nullable ±30 so the player passes null; 64 dp filled
  play, 48 dp outlined prev/next, `SpinnerRing` while waiting), `PlaybackStatusLine(waiting,
  error)`, and `HeroArtwork`/`PlayerChipRow`. Added `PlayerGeometry`/`ScrubberGeometry`/
  `TransportGeometry`, `HawraaDuration.SCRUBBER`/`PLAYBACK_TICK`, and the fa/en player strings.
  Kept Pejvak's strip + riding dot per the dispatch ruling rather than the older 2 dp/12 dp thumb
  sketch in requirement 1. New `PlayerPartsTest` (fraction/seek mapping, decorative wave
  deterministic/bounded/never-flat/seed-varying) and `PlayerPartsScreenshotTest` (14 captures:
  scrubber playing/waiting, transport playing/paused/waiting/skip, identity × light/dark) at
  `app/build/screenshots/player-parts-*.png`, all inspected. `assembleDebug` green;
  `./gradlew test` 372/372.
- 2026-11-21 — **Task 5 review fixes**. Media skip glyphs now mirror with layout direction
  (`BubbleButton(mirrorInRtl)`; `AutoMirrored.Rounded.SkipNext/SkipPrevious` are absent from this
  Material version) with an explicit fa-RTL transport capture; scrubber callbacks use
  `rememberUpdatedState`, an empty waveform falls back to the decorative strip, unknown durations no
  longer emit `onSeek(0)`, clocks use the `caption` token, and the unused `border` was removed;
  transport status ticks use `HawraaDuration.PLAYBACK_TICK`; `TransportGeometry.playSize` reuses
  `Controls.play`; `PaletteTile` takes an explicit numeral token and ellipsizes. Plan requirement 1
  and the Task 5 test line now match the shipped Pejvak scrubber/`PlayerPartsScreenshotTest`;
  DESIGN.md's transport-glyph sentence points at `ICON_GLYPHS`. `./gradlew test` 374/374,
  `assembleDebug` green.
- 2026-11-21 — **Task 4 complete.** Replaced the old `items: List<DockItem>` dock with the Pejvak
  API: `NowPlayingUi(title, subtitle, number, isPlaying, waiting)`, a four-peer `DockTab` enum
  (`route`, `labelRes`, semantic `glyph`; `matches()` understands the reader's page argument), and
  `AppDock(selectedRoute, onSelect, nowPlaying, miniProgress, onPlayPause, onNext, onExpand,
  onMiniPlayerRect)`. One `FrostedSurface` — `radius.xl` with a mini half, `radius.pill` nav-only —
  with both halves at 58 dp. The mini half animates with `expandVertically + fadeIn` /
  `shrinkVertically + fadeOut` (zero-duration specs under reduced motion), retains its last
  snapshot while exiting, and keeps the `HairlineWave` seam inside the fading group. `MiniPlayerRow`
  is an identity `PaletteTile` + title/subtitle + leaf-collected `WavyProgress` + filled play/pause
  (spinner while waiting) + outlined next; the leading region opens the player. Nav peers are
  icon-only: filled `accent` disc with `onAccent` glyph when selected, `textSecondary` otherwise,
  44 dp touch, press spring `1 → 0.92 → 1.05 → 1` + `HapticIntent.Select`. The dock publishes its
  measured player-half `Rect` in root coordinates through `onMiniPlayerRect`. Added the
  `StandaloneMiniPlayer` pill (`screens/player/`) and the `home`/`read` semantic glyphs
  (`Rounded.Home`, `AutoMirrored.Rounded.MenuBook`). `HawraaShell` now maps `NowPlaying` →
  `NowPlayingUi` and maps the four `DockTab`s to routes; the backdrop/player morph stays in Tasks
  6–7. Updated `DockNowPlayingTest` (juz tile/title agreement + rect publication) and added
  `DockScreenshotTest` (16 captures: nav-only / playing / juz / busy × light/dark × fa/en) at
  `app/build/screenshots/dock-*.png`, all inspected. DESIGN.md §10.4–10.5 now matches the shipped
  dock (FrostedSurface, radius rules, textSecondary nav, wave placement). `assembleDebug` green;
  `./gradlew test` 391/391.
- 2026-11-22 — **Task 6, step 2 — shell owns the player.** `HawraaShell` now mounts
  `NowPlayingSheet(store, backdropBlur = 0f)` above the nav host and the dock (both siblings of
  screen content) when signed in, and keeps `AmbientCanvas` behind; the dock still hides while
  `expanded`. `ShellViewModel` exposes `playerStore`. Removed the reader's route-level overlay:
  `ReaderScreen`/`ReaderContent` lost `playerOpen`, `onClosePlayer`, `playerStore` and their call
  sites, and `screens/reader/NowPlayingScreen.kt` is deleted. `ReaderViewModel.openPlayer`/
  `closePlayer` remain (they flip the store's expanded flag); the mini-player still opens through
  `store.openControls()`. `assembleDebug` + `compileDebugUnitTestKotlin` green (full suite not run).
- 2026-11-22 — **Task 6, step 3 — the morph is under test.** Added `MorphMathTest` (14 cases)
  pinning the corner (`mini` at t=0, `0` at t=1, monotonic through 0.5 and the 1.6 exponent),
  the content/mini-row fades, content scale, and per-edge `lerpRect` including reversed and
  crossing rects plus the empty-window `morphAlpha` step. `NowPlayingSheet` gained a documented
  `progressOverride: Float? = null` hook: non-null pins `t`, skips the springs and the mounted
  gate, and reports the pinned progress to the shell; production callers leave it null.
  `NowPlayingScreenshotTest` now captures `sheet-{mini|mid|full}-{light|dark}-{fa|en}.png` (12)
  from the store's measured dock rect at t=0/0.5/1; all inspected — the capsule starts on the
  dock row, the mid frame hands the mini row off to the flying identity tile, and the full frame
  matches the standalone player in both themes and both scripts. `./gradlew test assembleDebug`
  green, 421/421.
- 2026-11-22 — **Task 6, step 4 — review fixes (behaviour/perf).** `HawraaShell` now holds the
  sheet's `onExpandProgress` in state, wraps the nav host + dock in a content box and blurs that
  box at `frost.backdrop × progress` on API 31+ (skipped at zero), while the player sheet stays a
  sibling outside the blur; the same radius is passed to the sheet as `backdropBlur`, so the
  API 29–30 scrim fallback now actually fades in. The dock still hides while `expanded`. In
  `NowPlayingSheet`, the mini row is always composed and both the mini row (`miniRowAlpha < 0.9`)
  and `PlayerContent` (`contentAlpha < 0.99`) swallow pointer events in the Initial pass while
  faded, so neither faded layer intercepts taps; the mini row's dead `onExpand = {}` became a
  nullable `MiniPlayerRow` target (non-clickable in the sheet), and the misleading "mid-flight
  reversal" comment was replaced with the real close-ownership note. `PlayerUi`, `NowPlaying`,
  `PlayerReciter`, `PlayerPreset` and `PlayerVerse` are `@Immutable`, `NowPlayingStore` is
  `@Stable`; the sheet remembers its `PlayerContent` callbacks and its content/mini-row modifiers,
  and reads `t` from `graphicsLayer`/`offset`/pointer lambdas at layer time, so `PlayerContent` is
  skipped frame after frame even while the card bounds and flying artwork still recompose the
  sheet. `./gradlew test assembleDebug` green, 421/421; all 12 `sheet-*` captures re-inspected.
- 2026-11-22 — **Task 6 complete — the shell-owned player.** (6a store) `NowPlayingStore` now
  carries the full `PlayerUi` snapshot (surah/juz, identity number, reciter, featured verse +
  translation, preset step, play/busy/failed/speed/repeat, reciters/presets, downloadable page),
  the command set (`toggle`, `next`, `previous`, `seek`, `cycleSpeed`, `toggleRepeat`,
  `selectReciter`, `download`, `playPreset`, `exitPreset`, `close`), a `positionProvider`, and the
  dock's measured `miniPlayerRect`; `ReaderViewModel` publishes it and keeps the Media3 engine and
  session tracking, `ShellViewModel` exposes the store. (6b content) `PlayerContent` is the reduced
  Pejvak body — header/close, hero identity tile, surah/juz + reciter, featured verse on the opaque
  reading surface, scrubber, transport, and the speed/repeat/reciter/download/preset chips — with no
  queue/sleep/tone/enhancer controls and a leaf ticker so position never recomposes the sheet.
  (6c sheet/wiring) `NowPlayingSheet` morphs one surface from the dock rect to full screen
  (`corner = mini * (1 - t^1.6)`), cross-fades the dock row and the content, flies the shared
  `PaletteTile` to the measured hero, and owns pull-dismiss (`pullDismiss` 105 dp, `pullFloor`
  420 dp, `pullFling` 520 dp) plus reduced-motion snapping; `HawraaShell` mounts it as a sibling of
  the nav host and dock, `ReaderScreen` no longer owns the player, and
  `screens/reader/NowPlayingScreen.kt` is gone. **Blur:** the shell blurs the content box (nav host
  + dock) at `frost.backdrop × progress` on API 31+ and passes the same radius to the sheet; API
  29–30 fades the frost scrim (`scrimMax` 0.30) instead, and the player stays outside the blur.
  **Review fixes:** the dock keeps its wave during the morph, the mini row stays composed and both
  faded layers swallow pointer events in the Initial pass, card bounds stay continuous at the
  hand-off, `PlayerUi`/`NowPlaying`/reciter/preset/verse are `@Immutable` and the store `@Stable`,
  callbacks and modifiers are remembered and `t` is read at layer time so `PlayerContent` is
  skipped per frame, and every morph/card number is a token. `MorphMathTest` (14) and
  `NowPlayingScreenshotTest` (12 `sheet-*` captures at mini/mid/full × light/dark × fa/en) pass;
  `./gradlew test assembleDebug` green, 421/421. DESIGN.md now documents the morph as §10.12 and
  the tile ramp reads 40 dp.
