# DESIGN.md — Hawraa Visual Language

> **The single source of truth for how the app looks.** Every screen, component and token is
> built to this spec. If code and this file disagree, **the code is right and this file is a bug** —
> fix the file in the same change.
>
> **Read this before writing any UI.** Not optional, not "later": a new screen that does not use
> these tokens is a regression even if it compiles.
>
> Companion docs: [`summary.md`](summary.md) (orientation, stack, build quirks),
> [`plans/02-pejvak-design-system.md`](plans/02-pejvak-design-system.md) (the design roadmap and
> progress record), [`../AGENTS.md`](../AGENTS.md) (engineering rules).

**Status:** adopted 2026-11-15. Supersedes the warm-paper "mushaf" look that shipped through
Phases 0–3 (cream paper, deep-green banner, gold-bordered plate). That look is what the owner
called *"the typical look of existing Quranic apps"*; this document is the deliberate departure.

The material language is ported from the owner's Pejvak Android app — **patterns and measurements
only**, never code. Hawraa keeps the same glass/frost/ambient vocabulary and rebuilds it around one
fact Pejvak does not have: the screen frequently holds sacred text that must be read, not decorated.

---

## 1. Design intent

Hawraa is a **reading surface**, not a control panel. The visual language is glassmorphism — light,
floating frosted panes over a soft, colourful ambient field — but it is bent to a text-first app.

Three ideas carry everything:

1. **The page floats.** Chrome does not touch the edges of the screen. The reading surface is a
   pane with generous corners, the top bar is a floating capsule, the dock and the action box float
   above the content with a visible gap. Nothing is bolted to a bezel.
2. **Glass is for chrome, never for scripture.** Frosted panes carry navigation, controls and
   metadata. The Quran text sits on the most stable, most opaque surface in the app, because
   legibility of sacred text outranks any material effect (§3.7 — the one deliberate divergence from
   the reference).
3. **One accent means "current".** A single filled white/turquoise circle marks the current dock
   destination, the playing ayah, and the primary action in the action box. Warm gold is reserved
   for *what the reader marked* and for ornament — it is never an interactive colour.

The app is **fa-first and RTL by default**, so the composition is read right-to-left. Every rule
below is stated in *start/end* terms, never left/right.

### 1.1 What we are leaving behind

| Old (pre-2026-11-15) | New |
|---|---|
| Cream paper `#FBF6EC` | Opaque page `#FDFDFE` inside a glass rim, on a deep teal canvas `#33505F` |
| Deep-green banner with gold border | Teal-tinted glass plate, hairline edge, no gradient |
| Gold rosette ornament | Neutral ring that fills with the active hue |
| Full-width bottom transport bar | Floating action box over the selection + a floating dock |
| Green primary / gold secondary / bronze tertiary | White/turquoise accent / gold "marker" / neutral chrome |
| Edge-to-edge opaque screens | Floating panes over an ambient canvas |

### 1.2 What we are NOT doing yet

Authentic Iranian design elements — tilework geometry, `تذهیب` illumination, Persian blue
(`لاجوردی`) fields, arabesque borders — are the **planned next layer**, not this one. Two hooks are
already in place so that layer can be added without re-cutting the foundation:

- the tonal accent is turquoise (`فیروزه`), the most Iranian of the cool hues;
- gold survives as a *marker* colour, so ornament can return without re-theming the app.

Do not add half-hearted "Persian" decoration ad hoc. When that layer lands it gets its own section
here.

---

## 2. The ambient canvas

Glass needs something to refract. A frosted pane over flat white is invisible; over a softly
coloured field it reads as glass. So every screen renders an **ambient canvas** behind its content.

- **`AmbientCanvas`** (`ui/components/AmbientCanvas.kt`) — a full-bleed `#33505F` (light) or
  `#0A0F16` (dark) field with **four** large, soft radial blooms drawn from the cool aurora stops:
  teal, blue, cyan and purple (the `RampTeal`/`RampBlue`/`RampCyan`/`RampPurple` **mid** stops).
  Low frequency, low contrast, no noise, no hard edges.
- **Bloom opacity** is `0.20` in light and `0.38` in dark; a navigation surge lifts it by another
  `0.025` / `0.05` briefly. Cool-only keeps the field dark enough for the contrast contract even
  where two blooms overlap.
- **The canvas is static at rest.** A dedicated `navTrigger` surge (bounded, `NavMotionGeometry`,
  and only when `useMotionEnabled()` is true) is the only movement; it shifts the blooms along the
  navigation current and settles back to zero. There is no idle drift.
- Content scrolls **over** the canvas; the canvas does not scroll. `HawraaShell` registers it as the
  `zIndex = 0f` backdrop source so chrome can sample it (§6).
- A **base tint** sits under the blooms so the field never reaches pure white or pure black.

The light canvas is deliberately a **deep mid-tone cool slate**, not white. This is what lets white
text on glass stay legible in both themes, and it is the single most important colour decision in
this document. Cream would drag the app back to the mushaf look; white would kill the glass.

**Fallback (API 29–30, no `RenderEffect`):** the canvas still renders; frosted chrome paints
`frostFallback` instead of a real blur (§3.3). Legibility never depends on blur.

---

## 3. Colour system

All values live in `ui/theme/`. No colour literal exists outside that package. The contract below is
the shipped token set, not an aspiration.

### 3.1 Canvas

| Token | Light | Dark | Role |
|---|---|---|---|
| `canvas` / `canvasBase` | `#33505F` | `#0A0F16` | the field everything floats on; never near-white |
| `canvasElevated` | `#33505F` | `#0A0F16` | alias for the same field (no second canvas layer) |
| `canvasBlooms` (drawn) | `RampTeal/Blue/Cyan/Purple` **mid** @ 20 % | same mid stops @ 38 % | the four large, static radial blooms |
| `scrim` | `rgba(0,0,0,0.35)` | `rgba(0,0,0,0.35)` | behind modal sheets |

### 3.2 Glass recipe

| Token | Light | Dark | Role |
|---|---|---|---|
| `glassFill` | white 16 % | white 8 % | a normal floating pane |
| `glassFillStrong` | white 26 % | white 14 % | strong glass: sheets, action box, reading rim, cards over busy content |
| `glassFillFallback` | white 30 % | `#141C26` 72 % | API 29–30, no blur |
| `glassBorder` | white 60 % | white 40 % | the bright hairline edge, 1 dp |
| `glassHighlight` | white 75 % | white 55 % | the specular top rim |
| `shadow` | black 16 % | black 55 % | shadow tint (dock/sheet only) |

The **double-border effect** is `glassFill` + a distinct brighter `glassBorder` stroke + a top
`glassHighlight`. That trio makes a pane read as an edge catching light. There is no second dark
line and no gradient plate.

### 3.3 Frost — shell chrome only

Frost is the heavy crystalline material for the **floating header, dock and mini-player**. It samples
the live backdrop through `FrostedSurface` + `Backdrop` (§6). Content panes never use it.

| Token | Light | Dark | Role |
|---|---|---|---|
| `frostBase` | `#284452` 22 % | `#08121A` 24 % | base slate pane behind the sampled blur |
| `frostScrim` | `#1E4752` 22 % | `#0B2830` 24 % | cool crystalline teal tint |
| `frostSheen` | `#E6F9FF` 14 % | `#DFF6FF` 12 % | pale ice surface highlight |
| `frostFallback` | `#33505F` 82 % | `#06090F` 86 % | API 29–30, no blur |

`FrostGeometry`: chrome blur **22 dp** at a **0.5×** sample scale with a **1.22×** saturation
vibrancy chain; the modal backdrop is **18 dp**; the inner top catchlight is capped at **28 dp**;
micro-grain is **0.12** (a deterministic 64 × 64 tileable shader). Base + scrim + sheen stay near
~48–49 % combined opacity so the blurred canvas still glows through while the contrast contract
holds against the brightest bloom.

### 3.4 Text on glass

| Token | Light | Dark | Contrast target |
|---|---|---|---|
| `text` | `#FFFFFF` | `#F2F6F7` | ≥ 4.5:1 over the worst-case canvas |
| `textSecondary` | white 74 % | `#F0F5F6` 72 % | ≥ 3:1 |
| `textTertiary` | white 52 % | `#F0F5F6` 50 % | ≥ 1.25:1 vs secondary (tier separation) |
| `icon` | `text` | `text` | resting glyphs |

`textSecondary` and `textTertiary` must stay **measurably** different. The pre-glass app shipped two
greys at ~1.07:1 and every two-line row read as one flat block.

### 3.5 Accent & semantic

| Token | Light | Dark | Use |
|---|---|---|---|
| `accent` | `#FFFFFF` | `#FFFFFF` | filled primary bubble, selected dock disc, progress fill |
| `onAccent` | `#0E1720` | `#0E1720` | glyph knocked out of a filled accent |
| `accentTeal` | `#0A635C` | `#5FD9C6` | tonal highlights, "synced", links, crest accents |
| `danger` | `#FFD7DD` | `#FF8296` | destructive, failure |
| `mark` (chrome gold) | `#D9BB78` | `#D9BB78` | ornament / marker on dark-teal chrome |
| `markSoft` | `#D9BB78` 18 % | `#D9BB78` 18 % | marker wash on chrome |
| `track` | white 18 % | `#F0F5F6` 20 % | progress track, dividers |

**`mark` is not an interactive colour.** It never appears on a button, a chip, a toggle or a nav
item. It means *the reader put this here* — a selected verse, a rule under a surah plate, a diamond
bullet. If gold starts looking interactive, the accent has lost its monopoly on "current".

The chrome `mark` is the bright gold in both themes because chrome sits on the dark-teal field. The
mushaf `mark` is theme-specific (§3.7) because it sits on opaque paper.

### 3.6 Aurora ramps (identity)

Eight deterministic `[deep, mid, light]` ramps, selected by `paletteIndexFor(seed)`. They paint the
identity tile and give the canvas its cool field. They are not a user-facing palette picker.

| Ramp | deep | mid | light |
|---|---|---|---|
| teal | `#0A635C` | `#159A8C` | `#5FD9C6` |
| blue | `#123A6B` | `#2D6CB5` | `#7FB6F0` |
| purple | `#3A2A6B` | `#6C4FC1` | `#B79CF0` |
| coral | `#7A2E3A` | `#C75B6B` | `#FF9FAE` |
| amber | `#7A4A12` | `#C78A2D` | `#FFD08A` |
| green | `#1E5A34` | `#3E9A5F` | `#8FD9A8` |
| pink | `#6B2A5A` | `#B54F9A` | `#F09FD9` |
| cyan | `#0E4A5A` | `#2D8FA8` | `#7FD9E8` |

### 3.7 The mushaf surface (Quran-specific)

The reader has its own token set (`QuranColors`, `ui/theme/QuranColors.kt`) because scripture has
requirements chrome does not. It is provided alongside the glass tokens and **never mixed with
them**.

| Token | Light | Dark | Role |
|---|---|---|---|
| `page` | `#FDFDFE` | `#101720` | the reading pane — the most opaque surface in the app |
| `pageVariant` | `#F4F7F9` | `#16222C` | inset rows inside the pane |
| `letter` | `#16202A` | `#EDE9E0` | Quranic letters (default ink) |
| `diacritic` | `#8A6A28` | `#D9BB78` | the marks (default ink) |
| `translation` | `#41505A` | `#A8B4BE` | translation line |
| `hairline` | `#41505A` 18 % | `#B4C4CB` 20 % | rule between verse and translation |
| `mark` (paper gold) | `#A98436` | `#D9BB78` | marked verse |
| `markSoft` | `#A98436` 14 % | `#D9BB78` 18 % | marked wash |
| `accent` | `#0A635C` | `#5FD9C6` | playing / current |
| `accentSoft` | `#0A635C` 12 % | `#5FD9C6` 16 % | playing wash / surah plate fill |
| `onAccent` | `#FFFFFF` | `#04231F` | knocked-out glyph on a filled accent |
| `ornamentRing` | accent @ 40 % | accent @ 45 % | ayah rosette, unfilled |
| `ornamentNumber` | `translation` | `translation` | ayah number, unfilled |
| `bannerFill` | `accentSoft` | `accentSoft` | surah plate |
| `bannerEdge` | accent @ 35 % | accent @ 35 % | surah plate hairline |
| `bannerText` | `accent` | `accent` | surah name |
| `bannerSubText` | `translation` | `translation` | juz / surah number |
| `selection` | `markSoft` | `markSoft` | wash behind a tapped pane |
| `selectionEdge` | `mark` | `mark` | leading bar of a tapped pane |
| `playing` | accent @ 12 % | accent @ 12 % | wash behind the reciting ayah |
| `playingEdge` | `accent` | `accent` | leading bar of the reciting ayah |
| `letterMuted` | `letter` @ 34 % | `letter` @ 34 % | silent letters |

**Selection and playback must never look alike.** Selection is warm (`mark`); playback is cool
(`accent`). Selection outranks playback when both apply to the same pane — a verse the reader
deliberately marked keeps its gold while the recitation walks past it.

### 3.8 Ink presets (user choice)

The reader may override `letter` and `diacritic` from Settings (`LetterInk`: ink / green / navy;
`DiacriticInk`: gold / green / grey / burgundy). Those presets are **reader preference, not theme**:
they are exempt from the accent rules, must keep their own contrast against `page`, and every preset
has a light and a dark variant (`ui/theme/Color.kt`). Do not add a preset with only one theme.

---

## 4. Typography

### 4.1 Faces

| Role | Face | Notes |
|---|---|---|
| Quran text | **Scheherazade New** (OFL), split into `quran_letters.ttf` + `quran_marks.ttf` | Two derived ink layers with identical metrics (`ui/quran/QuranFont.kt`). See `summary.md` — do not "simplify" this to one font. |
| UI body | system sans (`Roboto` for Latin; the platform Naskh face for Persian) | the `HawraaTypography` default |
| UI chrome (Persian) | bundled **Vazirmatn** (OFL) | header titles, sheet titles, primary buttons, identity numerals, shared empty/error states |

Vazirmatn is bundled in `res/font/` and wrapped as `Vazirmatn` in `ui/theme/Fonts.kt`. It is applied
where Persian chrome is most visible; body copy remains the system face so the app stays fast and
predictable.

### 4.2 Scale

The shipped scale is Material's slots, customised in `ui/theme/Type.kt`. Sizes are sp / line height
sp.

| Slot | Size / line | Weight | Use |
|---|---|---|---|
| `displayLarge` | 34 / 40 | 700 | large screen title |
| `headlineLarge` | 30 / 38 | 700 | hero headings |
| `headlineMedium` | 20 / 26 | 600 | sheet and dialog titles |
| `titleLarge` | 28 / 34 | 700 | Now Playing title, screen wordmark rows |
| `titleMedium` | 16 / 23 | 700 | card titles, small-tile numerals |
| `bodyLarge` | 16 / 23 | 400 | primary copy, translations in the player |
| `bodyMedium` | 14 / 20 | 400 | secondary copy |
| `bodySmall` | 13 / 18 | 400 | detail line |
| `labelLarge` | 14 / 20 | 500 | buttons, dock mini title |
| `labelSmall` | 11 / 15 | 600, +1.3 tracking | overlines, dock mini subtitle |
| `caption` | 12 / 16 | 400 | scrubber clocks, metadata |
| `heroNumeral` | 56 / 64 | 700 | the full-screen player's identity numeral |

**Rules:** anything that counts (durations, counters, ayah spans) sets
`fontFeatureSettings = "tnum"` so digits do not jitter (`HistoryScreen`, `Scrubber`). Persian
numerals are rendered with `toPersianDigits()` at the display layer only — never in identifiers or
storage. Quran text sizes are a reader setting (22–44 sp, step 2, default 32) and are **never** taken
from this table.

### 4.3 Quran text sizing

`fontSizeSp` is user-controlled. `quranLineHeight(sp)` (`ui/quran/QuranTypeScale.kt`) multiplies it by
**1.85**, because Uthmani marks stack above and below the letters and a tight line height clips the
very diacritics the app is built to show. Never set an absolute line height on Quran text.

---

## 5. Shape & spacing

### 5.1 Radius ramp

One strictly-increasing ramp (`Radius` in `ui/theme/Tokens.kt`). Never hard-code a corner.

| Token | dp | Alias |
|---|---|---|
| `xs` | 8 | — |
| `sm` | 12 | `badge`, `tile` |
| `md` | 16 | inline control, text field |
| `lg` | 20 | `segment`, inset row, segmented track |
| `card` | 28 | **cards, floating panels** (`panel`) |
| `xl` | 32 | dock, sheets, reading pane, header capsule |
| `xxl` | 40 | expanded sheet, hero |
| `pill` | 999 | chips, nav-only dock, search, drag handle |

`tileRadius(size)` scales a tile's corner with its size (8 → 20 dp).

### 5.2 Spacing — a 4 dp rhythm

`0, 2, 4, 8, 12, 16, 20, 24, 32, 44, 64` (`none, xxs, xs, sm, md, lg, xl, xxl, xxxl, huge, giant`).

Screen padding 20 · card gap 12–16 · card inner padding 16.

| Group | Tokens (dp) |
|---|---|
| Tiles | `sm 32, md 40, lg 48, xl 56, hero 76, numeralHero 152` |
| Controls (circular) | `sm 36, md 40, lg 44, xl 52, play 64` |
| Bars | `thin 4, thick 6` (seeker track 2, thumb 12; wave scrubber lives in `ScrubberGeometry`) |
| Icons | `xs 13, sm 15, md 18, lg 22, xl 26, hero 44` |
| Max content width | 760 |

### 5.3 Dock geometry — the numbers are the contract

```
playerHeight 58   navHeight 58   indicator 40
icon 22           iconActive 20  touch 44
margin 10         inset 12
```

- Both halves are **equal height** (58) so the seam divides the panel in half rather than cutting a
  tall nav off a short player.
- The selected indicator is a **true circle** (40), not a lozenge. A pill-shaped indicator reads as
  a filter tag, not as "you are here".
- `iconActive` (20) is **smaller** than `icon` (22) on purpose: it sits inside a 40 dp disc, so 20 dp
  is exactly half the disc and rhymes with the mini-player's play bubble. Trust the number; do not
  "fix" it to match `icon`.

**`dockInset = playerHeight + navHeight + margin × 2 + inset = 148`**
(`navInset = 90` collapsed.)

Screens owe the dock `dockInset` (`DockGeometry`), sized for the dock **at its tallest**, never a
literal and never the collapsed height. A screen cannot know whether something will start playing
while it is open, and reserving later would make its list jump. The shell measures the real dock
height and publishes it as `LocalShellInsets.bottom`; screens use that measured value.

### 5.4 Header & frame geometry

```
margin 10   minHeight 72   contentPadding 20   capsule radius.xl
```

`GlassHeader` owns the status-bar inset and the morphing search capsule. `ScreenFrame` mounts the
header (and an optional floating sub-header) as siblings **outside** the screen body's
`backdropSource`, and reports the combined top inset so lists can scroll smoothly behind both.

---

## 6. Depth: blur, borders & elevation

Depth is **not** a drop shadow. It is a three-part recipe, in order of importance:

1. **Background blur** (custom `Backdrop`, `ui/components/Backdrop.kt`; API 31+). Shell chrome
   samples the ambient canvas (`zIndex = 0f`) and each screen body (`zIndex = 1f`) into sharp
   `GraphicsLayer`s and composites only the region behind each pane into a **0.5×** downscaled,
   padded, offscreen layer with a **22 dp** blur and a **1.22×** saturation boost. Invalidation is
   draw-phase only (`Snapshot.registerApplyObserver`), so scrolling never recomposes the chrome.
2. **Translucent fill** (`glassFill` / `glassFillStrong`, or the frost trio) — gives the pane its
   colour and keeps text legible over busy content.
3. **Bright hairline + top rim** — a 1 dp `glassBorder` stroke plus a **3 dp** top-edge
   `glassHighlight` gradient. Never a highlight over the upper half; it blows out.

**Modal backdrop.** While a sheet is open, or as the full player opens, the shell blurs the content
box (canvas + nav + dock) by up to **18 dp** (`FrostGeometry.backdrop`). The sheet/player is a
sibling *outside* that blur — it is the surface the blur exists to reveal. On API 29–30 there is no
`RenderEffect`, so the sheet fades the same progress into a frost scrim (`scrimMax = 0.30`) instead.

| Elevation | dp | Use |
|---|---|---|
| `None` | 0 | content cards, rows, reading panes |
| `Dock` | 5 | a control lifted like the dock |
| `Panel` | 6 | action box, reading pane, floating panes |
| `Float` | 12 | sheets, menus |

The dock's frost pane itself carries a soft **8 dp** lift (`dockElevation()`), deliberately shallower
than `Float`: a floating *control* is lifted off the page; a dock is a *surface*. Content cards get
**no** shadow — their fill + border + rim draw the edge.

**API 29–30 fallback:** no blur; content glass raises to `glassFillFallback`, chrome paints
`frostFallback`, and border + rim stay. The pane still reads as a distinct surface; legibility never
depends on blur.

---

## 7. Iconography

- **One family:** Material **Rounded**, behind the semantic `ICON_GLYPHS` catalog in
  `ui/components/Icon.kt`. Screens ask for `"play"`, `"history"`, `"back"` — never import an icon set
  directly. Only `Icon.kt` imports one. A glyph that is missing is added to the catalog, never
  hand-drawn per screen. `IconCatalogTest` pins the catalog.
- **Interactive glyphs live in circular bubbles** (`BubbleButton`): outlined by default, filled
  `accent` for the primary action (play, the one main action) and the selected dock destination.
- **Directional glyphs are auto-mirrored** (`Icons.AutoMirrored…`) so back/next stay correct in RTL.
- A glyph whose meaning changes (play ↔ pause, playing ↔ paused) **cross-fades**; it never swaps
  instantly in place.
- The password eye (`ui/components/EyeGlyph.kt`) is drawn from two quadratics and a disc rather than
  imported, because the core icon set has no `Visibility`/`VisibilityOff`. Before adding an icon
  dependency, check whether the glyph is two shapes in a `Canvas` — it usually is.

---

## 8. Motion

Motion is governed by `useMotionEnabled()` (`ui/theme/Motion.kt`) = OS "remove animations" × the
in-app preference (`System` / `Full` / `Reduced`). When false, substitute the **final value** — never
rely on the platform flag alone.

- **Springs** (`HawraaSpring`): bouncy/gentle/sheet/snap/page, plus `playerOpen` (a hair of
  overshoot) and `playerClose` (no bounce on dismiss).
- **Duration ladder** (`HawraaDuration`): instant 120, exit 130, quick 180, base 260, crossfade 400,
  screen 420, scrubber 380, playback tick 1000, pulse 900.
- **Page transitions (ocean current):** dock tab switches glide laterally along `tabOrder` with
  `OceanSwellEasing`; pushes rise while the parent recedes; pops reverse. Each transition fires a
  bounded `AmbientCanvas` surge and a `WaveSweep` crest (aqua band, dual sine crests, a glider with a
  twin wake). Both are absent under reduced motion. `WaveSweep` mirrors the logical tab direction for
  RTL itself.
- **Reveal stagger (`OceanCascade` + `Modifier.oceanEntrance`):** initially visible list items
  (`index < 10`, first 850 ms) enter sequentially (`stagger(..., step 32, max 230)`), rising 14 dp
  with a 5 dp lateral weave and a 0.978 → 1 settle. Items composed later while scrolling skip the
  delay. Layer-only transforms; no relayout.
- **Player morph:** one normalised `t` (`0` = dock capsule, `1` = full screen) drives bounds, corner
  (`mini × (1 − t^1.6)`), the dock-row/content cross-fade, and the hero identity flight. Opening and
  closing are springs; a downward pull moves the same `t` and either settles back or dismisses by
  distance (`pullDismiss = 105 dp`) or velocity (`pullFling = 520 dp`), normalised over at least
  `pullFloor = 420 dp`.
- **`WavyProgress` drift** (`rememberWavePhase`, the mini-player's ambient wave) and the player header's
  **playing equaliser** (`PlayingPulse`) are the only unbounded animations. The wave's callers pass
  `0f` under reduced motion so it holds its resting phase; the equaliser holds `pulseStaticPhase`.
  Everything else is bounded.
- **No travel motion on the dock indicator itself** — a tab tap is a press spring
  (1 → 0.92 → 1.05 → 1) and a haptic, nothing more.
- **Haptics** via `triggerHaptic(intent)` / `rememberHaptics()` (`HapticIntent`: tap, select, press,
  success, warning, error, scrub), with a master switch; fires once on press.

**Never animate:** Quran text size, scroll position after a deliberate reader scroll, or the canvas at
idle (the navigation surge is its only movement).

---

## 9. Component specifications

Every component is a glass/frost pane unless noted. Compose names in parentheses; paths are relative
to `app/src/main/kotlin/ir/hawraa/quran/`.

### 9.1 Surfaces

- **GlassSurface** (`ui/components/GlassSurface.kt`): the base pane — `glassFill` + 1 dp
  `glassBorder` + 3 dp top rim. Variants: `strong` (sheets, action box, reading rim,
  `glassFillStrong`), `filled = false` (outline only), `rimLight = false` (quiet rows), and
  `elevation: None | Dock | Panel | Float`. This recipe is legible without blur.
- **FrostedSurface** (`ui/components/FrostedSurface.kt`): crystalline shell pane. `frostBase` +
  sampled `BackdropBlur` (or `frostFallback` on API 29–30) + `frostScrim` + `frostSheen` +
  deterministic grain + curved inner top catchlight + contour-following bevel border. Used **only**
  by the header, dock, mini-player and player capsule.
- **GlassSheet** (`ui/components/GlassSheet.kt`): modal bottom sheet on `canvasBase`, `radius.xl`,
  hairline border, 36 × 4 drag handle, scrim. It raises `SheetPresence` so the shell frosts the
  content behind it. Title + optional subtitle + icon bubble + close bubble + `HairlineWave`.

### 9.2 Header & frame

- **GlassHeader** (`ui/components/ScreenHeader.kt`): the one floating heavy-frost capsule. Anatomy:
  `[leading bubble] [overline + title + optional subtitle] [weight] [ActionCluster]`. Owns the
  status-bar inset. A trailing search bubble morphs the capsule into an inline search field; query
  and results stay in the view model. `userTitle = true` enables per-text RTL detection
  (`isRtlText`) and the Vazirmatn face for Persian titles.
- **ScreenFrame** (`ui/components/ScreenFrame.kt`): composes a screen's header + optional sub-header
  outside the body's `backdropSource(zIndex = 1f)` and reports the combined top inset.
- **Back bubble policy.** `History` is a top-level peer and shows **no** back bubble. `Settings` is
  also a dock peer but shows one, because it is reachable from Home too and the shell can pop it;
  `Downloads`, `Presets`, the preset editor and `Reader` show one (`BubbleButton` with
  `ICON_GLYPHS["back"]`, `controls.md`) as pushes. Home shows settings, not back.

### 9.3 Controls

- **BubbleButton** (`ui/components/BubbleButton.kt`): circular glass icon button. `filled = true` →
  solid `accent` disc with an `onAccent` glyph (the primary action); `filled = false` → outlined
  disc with `glassBorder` and an `icon`-coloured glyph. `busy` swaps the glyph for in-place
  `SpinnerRing` and keeps the press. Minimum 44 dp touch.
- **PrimaryButton** (`ui/components/Buttons.kt`): the one filled pill for a screen's main action,
  `controls.xl` tall, Vazirmatn label, `onAccent` text; no shadow of its own.
- **GlassChip** (`ui/components/GlassChip.kt`): a **filter/toggle** pill. Selected = `accentSoft`
  fill + `accent` hairline + `accent` label; unselected = `glassFill` + `glassBorder` +
  `textSecondary`. A chip is a filter; a segmented control is a choice.
- **GlassSegments** (`ui/components/GlassSegments.kt`): one-of-N choice. One `glassFill` track with a
  hairline border and 4 dp inner padding; the selected segment is a filled `accent` pill with an
  `onAccent` label. It reads `LocalGlassColors`, not `MaterialTheme.colorScheme`, so it matches every
  pane around it. **Anything choosing one-of-N uses `GlassSegments`.** (`SegmentedPill` is the
  Material-palette legacy sibling; do not reach for it on a glass screen.)
- **Fields** (auth): hand-built inset tracks with a visible label above, hairline border that warms to
  `accent` on focus. No Material outlined field, no floating label.

### 9.4 The dock (`ui/components/AppDock.kt`)

**One panel, two halves.** The mini-player row and the icon nav are one `FrostedSurface`, so their
geometry cannot disagree.

```
╭─────────────────────────────────────────╮  FrostedSurface, radius.xl (pill when nav-only)
│  ▢  فاتحه · آیۀ ۳            (▶)  (⏭)  │  playerHeight 58   ← only while playing
├─────────────────────────────────────────┤  seam: HairlineWave, inset 12
│    ⌂        ▤         ◷        ⚙        │  navHeight 58
╰─────────────────────────────────────────╯
   margin 10 from the safe-area edge, inset 12 horizontal
```

- **Collapses when idle.** Height animates between `navHeight` and `playerHeight + navHeight + seam`
  from one shared value; the seam lives inside the fading group so it cannot outlive the row.
- **Navigation is icon-only, four peers in reading order:** `Home` · `Read` · `History` · `Settings`
  (`DockTab`). No labels, no raised centre action. Selected = filled `accent` circle (40) with the
  glyph tinted `onAccent` at `iconActive` (20); unselected = `textSecondary` at `icon` (22). Every
  peer has a 44 dp target, `Role.Tab`, and a `selected` semantic.
- **The nav current explains the switch** (§8); under reduced motion it is instant and the crest is
  absent.
- **MiniPlayerRow** (same file): identity tile 40 dp, title (`labelLarge`) over subtitle
  (`labelSmall`, `textSecondary`), a `WavyProgress` leaf, then play/pause (filled accent, 40) and next
  (outlined, 36). The whole leading region is one tap target that opens the full player; the trailing
  bubbles sit outside it. No surface of its own — the dock owns the panel.
- The dock is a **sibling** of the scrolling content, never inside it.

### 9.5 Reader parts

- **SelectionActionBox** (`ui/components/SelectionActionBox.kt`): the floating action box over the
  current selection, driven by a `List<SelectionAction>`. One primary (filled accent bubble + label);
  the rest are icon-only. It appears when something is selected and disappears when cleared; the
  reader anchors it above the verse, flips it below when there is no room, and keeps it clear of the
  dock. It replaces the old permanent bottom bar.
- **AyahOrnament** (`ui/quran/AyahOrnament.kt`): the drawn 8-lobe rosette (`res/drawable/ayah_marker.xml`,
  Delapouite's "Rub el Hizb", CC BY 3.0). Unfilled = `ornamentRing` + `ornamentNumber`; selected =
  filled `mark`; playing = filled `accent`, with the number knocked out. Persian digits always.
- **VerseBlock / Pane** (`ui/screens/reader/ReaderScreen.kt`): one verse is a stack of independently
  selectable panes — the Arabic pane, then one per translation. Each pane owns its selection state
  and draws its own wash and 3 dp leading-edge bar (start edge, right in RTL). Translations indent
  past the rosette.
- **MushafBanner** (`ui/quran/MushafBanner.kt`): the surah plate — `bannerFill`, `bannerEdge`
  hairline, `bannerText` name, `bannerSubText` juz/number.
- **QuranText** (`ui/quran/QuranText.kt`): the only entry point for Quran typography, so letters and
  diacritics stay independently colorable. Never draw Quran text with a plain `Text`.

### 9.6 Player parts

- **Scrubber** (`ui/components/player/Scrubber.kt`): a wave-shaped seeker with a glowing head and
  `tnum` clocks; indeterminate while waiting. **WavyProgress** is the dock's ambient wave.
- **TransportControls** (`ui/components/player/Transport.kt`): previous / play-pause / next; play is
  the 64 dp filled bubble (a `SpinnerRing` while waiting), the others 48 dp outlined bubbles.
- **PlaybackStatusLine**: waiting/error state under the scrubber; never a bare caption.
- **PaletteTile** (`ui/components/PaletteTile.kt`): the identity tile. Hawraa ships no bundled cover
  imagery and fetches none, so identity is a deterministic aurora gradient from the number, with the
  numeral in the UI face. The dock tile is 40 dp; the full player's is 180 dp with `heroNumeral`
  (56 sp). It is **not** a filled accent disc and **not** set in the mushaf face — three filled
  accent objects in one row, or a calligraphic «۱» at 19 sp, both read as mistakes.
- **FeaturedVerseCard** (`ui/screens/player/PlayerContent.kt`): the verse on an opaque `quran.page`
  inside a glass frame; the translation is separated by the mushaf hairline. A juz track has no
  featured verse.

### 9.7 States

`EmptyState` / `InlineNotice` / `SkeletonRows` (`ui/components/States.kt`): four distinct states
(empty / loading / failed / offline). The skeleton is static (no shimmer loop); an error is a
danger-tinted notice with retry, never bare text. Never leave a blank list.

---

## 10. Screen specifications

All screens render over the ambient canvas and owe the dock their measured `LocalShellInsets.bottom`.
They mount chrome through `ScreenFrame` + `GlassHeader`. The UI is fa-RTL by default.

### 10.1 Reader — the signature screen

```
┌─────────────────────────────────────────┐  AmbientCanvas
│  ╭───────────────────────────────────╮  │
│  │ ←  فاتحه  ◆  صفحهٔ ۱ · جزء ۱  ⚙… │  │  floating frosted capsule
│  ╰───────────────────────────────────╯  │
│  ╭───────────────────────────────────╮  │
│  │  ╭─────────────────────────────╮  │  │  reading pane: GlassSurface(strong, xl)
│  │  │  سورۀ ۱  فاتحه              │  │  │  with opaque quran.page inside
│  │  ╰─────────────────────────────╯  │  │
│  │   ١  بِسْمِ ٱللَّهِ …            │  │  │
│  │      به نام خدا…                 │  │  │
│  │   ٢  ٱلْحَمْدُ لِلَّهِ …          │  │  │
│  │        ⋮  (scrolls)              │  │  │
│  ╰───────────────────────────────────╯  │
│         ╭─────────────────────╮         │
│         │ ▶  پخش آیه ۳        │         │  ← SelectionActionBox (floating)
│         ╰─────────────────────╯         │
│  ╭───────────────────────────────────╮  │  ← AppDock
│  │ ▢ فاتحه · آیۀ ۳        (▶)  (⏭)  │  │
│  │   ⌂     ▤      ◷      ⚙          │  │
│  ╰───────────────────────────────────╯  │
└─────────────────────────────────────────┘
```

- The reading pane is an **opaque `page` field inside a glass rim** (`radius.xl`). The canvas shows
  through only around the floating pane, so the edge reads as glass while the text sits on the most
  stable field.
- No page number, no page furniture — the scroll is continuous. `LazyColumn` with `MushafBanner`,
  `BasmalaItem`, `VerseItem`; bottom padding is `LocalShellInsets.bottom`.
- The header carries back (push), a page/juz overline, and trailing download (only when recitation is
  available), settings and player bubbles.
- The dock is always present; the action box only while something is selected.

### 10.2 Home

`GlassHeader` (title, overline "فضای خواندن شما", settings bubble, no back). Hero with the wordmark
in the mushaf face (`home.wordmarkSizeSp = 64`), a gold rule broken by a diamond, and a faint
octagram lattice stroked with a vertical gradient so it fades into the canvas. Then a "continue
reading" card showing the real opening verses of the resume page on opaque paper, a today strip
(minutes, goal, streak) in tabular figures, and one filled primary action. Cards are `GlassSurface`,
`radius.card`, padding 16; the verse preview is an opaque `page` inset, never scripture on glass.

### 10.3 History

Top-level peer, **no back bubble**. `GlassHeader` (title + overline). Summary card (time / pages /
streak) in `tnum`, then day sections (Today / Yesterday / date) with one glass row per session.
Rows use an inset hairline that starts at the text, not at the pane edge.

### 10.4 Settings

Push-capable peer (dock tab **and** Home's settings action), so it shows a **back bubble**. Grouped
`GlassSurface` cards (`radius.card`, inner padding 16), each with an inline section title: Theme
(`GlassSegments`), Language, Reading (font size slider, translation switch, letter-ink and
diacritic-ink `GlassSegments`, a live preview on `page`), Recitation (reciter chips), Presets and
Downloads (link rows to their screens). Credits sit as a centred `labelSmall` footnote, not a card.

### 10.5 Downloads

Push (back bubble). Purpose: choose a reciter and edition, choose a scope in the unit the reader
thinks in, keep the audio on the device, and manage everything already there.

- **The estimate is on the button**, not in a dialog; a large download started by accident is a bad
  afternoon.
- **Scope is offered in the reader's unit first** (`صفحه` leads), and a page range is resolved into a
  verse range against the bundled mushaf before anything is sent — the wire has no page scope.
- **Four scopes, never four at once.** A juz-granularity edition collapses the control to `جزء`
  alone; the screen carries no granularity conditional beyond the note.
- **Two sliders, not start-plus-count**; the start drags the end along so the pair cannot invert.
  The most common request is a chip, not a special case.
- **The technical line is LTR, the note is not** (`48 kbps · ayah` is a token, not prose).
- **The summary row is the last chance to stop**; holdings are grouped per edition, not listed per
  batch. Delete is offered per scope and per edition. Retry appears only where something failed. The
  notice is in place, not a snackbar.

### 10.6 Presets

Push (back bubble). The recipe is a line with arrows (`آیه ← ترجمه ← تفسیر`); order is the feature.
Coverage lives here, not in History: percentage, bar, and the runs (at most four, then an ellipsis).
No coverage is a sentence, not a zero. The editor is a name plus an ordered list of steps with a
repeat stepper (`− ۲ +`) and a remove action; save is the one accent action.

### 10.7 Now Playing — the morph

Owned by the shell (`ui/HawraaShell.kt` → `ui/screens/player/NowPlayingSheet.kt`): it is mounted
**above** the blurred shell box (a sibling of the wrapper that holds the nav host, dock and wave
sweep) and reads `NowPlayingStore`. The player is the dock's mini half **unfolding**, not a second
surface appearing over it.

- **Header:** close bubble (start), centred "در حال پخش" with the running preset/step beneath,
  non-interactive playing equaliser (end).
- **Hero:** the 180 dp identity tile with its numeral; it flies from the dock tile's measured rect to
  the player's.
- **Body:** title/subtitle, optional featured verse card (opaque page inside glass), the wave
  scrubber, status line, transport, then options (reciter, presets, speed, repeat, download).
- **Morph channels:** bounds interpolate from `NowPlayingStore.miniPlayerRect` to the viewport; the
  corner releases as `mini × (1 − t^1.6)`; the dock row fades over `t 0.12 → 0.52` while
  `PlayerContent` fades in over `0.34 → 0.90` (scale 0.84 → 1); glass fades through to the opaque
  reading canvas. Pull to dismiss normalises over `pullFloor` and settles by distance/velocity.
- **Ownership:** the shell owns the store and the sheet; the **reader keeps the engine and session
  tracking** (`ui/screens/reader/ReaderViewModel.kt`). The store publishes a `PlayerUi` snapshot,
  binds transport commands, and collects position through a leaf `positionProvider` so a 1 Hz tick
  never recomposes the sheet or dock. `PlayerUi` is null when nothing is queued; a juz track sets
  `juzNumber` and the row says `جزء N` instead of a verse.

### 10.8 The account gate

The one screen with no dock, no back and no content: it is the condition for the rest of the app to
exist. Three bands — a drawn eight-point medallion with a radial accent glow, the wordmark, and one
centred `GlassSurface(strong, Panel)` card at `radius.card`.

- One screen, two verbs (sign in / register) switched by `GlassSegments`; switching clears the error.
- Fields are hand-built with labels above the box; the hairline border is the only focus affordance.
  Email and passwords are LTR even inside a Persian sentence.
- Two password fields, each with its own `PasswordReveal` (drawn eye, 40 dp target,
  `rememberSaveable`).
- The primary action has three states: filled `accent` when pressable, `accentSoft` + spinner while
  working, flat `glassFill` + `textTertiary` when not. The shadow is only under the live button.
- The error is a tinted `danger` strip, not bare red text. The footnote says why the gate exists.
- No language switcher here.

---

## 11. Accessibility & the contrast contract

Glassmorphism's failure mode is invisible hierarchy. The contract:

- `text` on any pane ≥ **4.5:1**; `textSecondary` ≥ **3:1**; `textSecondary` vs `textTertiary`
  ≥ **1.25:1**.
- **Quranic text ≥ 7:1** against `page` — stricter than body copy, because diacritics are small
  strokes and the reader may be reading for a long time.
- Filled-bubble glyph (`onAccent` on `accent`) ≥ 4.5:1.
- Every interactive target ≥ **44 dp**, even when the visible glyph is smaller.
- Every icon-only button carries a `contentDescription`; the dock reports `Role.Tab` + `selected`.
- Focus order follows reading order. Persian is RTL: never hard-code `left`/`right`, always
  `start`/`end`.
- Reduced motion substitutes final values; content is never withheld to make an animation work.
- **A palette edit that breaks a threshold fails review. Never weaken a threshold to make it pass.**

Contrast is computed against the **worst-case** backdrop (the brightest bloom under the thinnest
pane), not a fixed flat colour; the mid-tone light canvas (§3.1) is what lets white text pass.
The shipped gate is the screenshot suite plus review; a dedicated contrast test is still planned.

---

## 12. Light & dark themes

Both are first-class, switched by `themeMode` (`System` / `Light` / `Dark`, resolved in
`ui/theme/Theme.kt`). The difference is **canvas base and glass/frost opacity**, not structure:

- **Light** — deep slate-teal canvas (`#33505F`) with the brighter cool blooms, white glass at
  16 % / 26 %, white text. Reads as frosted panes over a coloured sky.
- **Dark** — near-black canvas (`#0A0F16`) with the dimmer deep blooms, white glass at 8 % / 14 %,
  near-white text. Reads as glass over deep water.

The accent, aurora ramps and semantics are shared; only canvas/glass/text alphas differ. The chrome
`mark` is the bright gold in both; the mushaf `mark` follows the paper (dark gold on light, bright
gold on dark). No screen may branch on the theme to pick a colour — ask the token.

---

## 13. RTL & Persian content

- Persian (`fa`) is the **default locale** and the only RTL one shipped; English (`en`) is LTR. Both
  are declared in `res/xml/locales_config.xml`.
- All layout uses `start`/`end`; `LocalLayoutDirection` decides the leading edge for bars, indents
  and icon mirroring. Directional icons are auto-mirrored.
- Per-text detection (`isRtlText` in `core/TextFold.kt`) lets a user-supplied title opt into
  `LayoutDirection.Rtl` and Vazirmatn even inside an otherwise LTR composition.
- Persian digits via `toPersianDigits()` at display time only.
- Arabic/Quran text is always RTL regardless of UI locale.
- Mixed Persian + Latin strings rely on the platform bidi algorithm; do not hand-insert marks. A
  technical token such as `48 kbps · ayah` is deliberately kept LTR.
- Every string lives in `values/strings.xml` (fa) and `values-en/strings.xml`. A hardcoded string is a
  bug.

---

## 14. Assets

- **No bundled cover imagery.** Identity is a `PaletteTile` from an aurora ramp (§3.6); the same
  number paints the same gradient on every device, offline. There is no network image path.
- **Quran face:** bundle **Scheherazade New** (SIL OFL) as two layers, `res/font/quran_letters.ttf`
  and `res/font/quran_marks.ttf`, built by `tools/build-quran-fonts.py`. Licence in
  `assets/licenses/ScheherazadeNew-OFL.txt`.
- **UI face:** bundle **Vazirmatn** (OFL) under `res/font/` (regular/medium/semibold/bold). Licence in
  `assets/licenses/Vazirmatn-OFL.txt`.
- **Ayah marker:** `res/drawable/ayah_marker.xml` (Delapouite's "Rub el Hizb", CC BY 3.0). Attribution
  in `assets/licenses/ayah-marker-game-icons.txt`.
- Canvas blooms and the auth medallion are drawn in code — no image assets.
- **Sacred text is never fetched at runtime.** It ships with the app and is never modified by code.

---

## 15. Compose implementation

- **Tokens:** `HawraaTheme` provides `LocalHawraaTokens` (`HawraaTokens`), `LocalGlassColors`,
  `LocalQuranColors`, `LocalHawraaGeometry` and `LocalHawraaDarkTheme`. Read them; never carry a raw
  value into a composable.
- **Backdrop:** `Backdrop.kt` uses `Modifier.Node`s (`DrawModifierNode` +
  `GlobalPositionAwareModifierNode` + `requireGraphicsContext()`) to record `AmbientCanvas`
  (`zIndex = 0f`) and the `ScreenFrame` body (`zIndex = 1f`) into sharp `GraphicsLayer`s, invalidated
  at the draw phase via `Snapshot.registerApplyObserver`. Each `FrostedSurface` renders `BackdropBlur`
  into a 0.5×, blur-padded, `CompositingStrategy.Offscreen` layer with
  `RenderEffect.createChainEffect(vibrancy, createBlurEffect(sigma, …))` on API 31+ (API 29–30 paints
  `frostFallback`). While a modal is open the shell blurs the whole content box with `Modifier.blur`;
  `backdrop.paused = true` reuses the cached effect layer without re-recording.
- **Frost pane:** `FrostedSurface` layers `frostBase` + blur/fallback + `frostScrim` + `frostSheen` +
  deterministic grain + top sheen + contour-gradient bevel border.
- **Base pane:** `GlassSurface` uses `clip(shape)` + `background(fill)` + a 3 dp top rim
  (`drawWithContent`) + `border(1.dp, glassBorder, shape)`.
- **Canvas:** `AmbientCanvas` is a `Canvas` painting four radial gradients from the cool ramps, and a
  bounded `Animatable` surge driven by `navTrigger` (snapped to zero under reduced motion).
- **Dock:** one `FrostedSurface` containing a `Column`; the nav row is a `Row` of equal-weight peers,
  so it cannot overflow. The player half is `AnimatedVisibility` and stays composed while exiting so
  its measured rect survives the morph.
- **Player:** `NowPlayingStore` publishes `PlayerUi`; `NowPlayingSheet` reads the store and drives
  every channel from one `t`. `MorphMath.kt` holds the pure, unit-tested morph functions. Position
  ticks are isolated to leaf composables (`ScrubberSection`, `MiniWave`).
- **State:** `StateFlow` + `collectAsStateWithLifecycle`. Keep high-frequency ticks in leaves.
- **Selection:** `VerseSelection` is a set of layer-keyed elements plus one `active`; the action box
  reads `active`, each pane reads its own membership. Do not add a parallel `isSelected` boolean.
- **Verification:** the Robolectric screenshot suite (`app/build/screenshots/`, `*ScreenshotTest`)
  renders real composables in both themes and both locales through a shared `TestShell`. Compose what
  the app composes; measure the pixels for anything about contrast or occlusion.

---

## 16. Do / Don't

**Do**

- Read this file before building a screen.
- Keep the canvas mid-tone in both themes so glass and white text stay legible.
- Use the radius ramp, the 4 dp rhythm, tabular figures, and one icon family.
- Keep the reading pane the most opaque surface on the screen.
- Make the filled accent circle mean exactly one thing.
- Reserve `LocalShellInsets.bottom` on every scrollable screen.
- Gate motion with `useMotionEnabled()` and substitute the final value.
- Check both themes and both locales before calling UI work done.

**Don't**

- Don't put glass behind Quran text; don't lower the reading pane's opacity "for the effect".
- Don't add hard drop shadows to content cards — depth is blur + fill + hairline + rim.
- Don't hard-code a colour literal; a raw corner/gap/clearance is a bug to fix.
- Don't reintroduce a raised centre dock action, per-tab labels, or a full-width bottom bar.
- Don't use `mark` (gold) as an interactive colour.
- Don't add a page counter or page-boundary furniture; the reader scrolls continuously.
- Don't give `History` a back bubble — it is a top-level peer.
- Don't teach the backend about pages, or animate Quran text size / reader scroll.
- Don't copy GPL code from reference projects — patterns and measurements only.
- Don't weaken a contrast threshold to make a palette edit pass.

---

## 17. Enforcement

| Rule | Gate |
|---|---|
| No colour literal outside `ui/theme/` | review (`rg "Color\\(0x"`) |
| No hardcoded user-facing string | review (`strings.xml` only) |
| Token values match this document | review + `ui/theme` |
| Icon name exists in `ICON_GLYPHS` | `IconCatalogTest` |
| Quran text contrast ≥ 7:1 | `ContrastContractTest` (planned, §11) |
| Tier separation ≥ 1.25:1 | same test |
| Screens render in both themes + both locales | `*ScreenshotTest` + `PersianLaunchTest` |
| No page chrome in the reader | `ReaderItemsTest` |
| A capture shows the composition the app really builds (canvas + dock, real glyphs) | `TestShell` used by every `*ScreenshotTest` |
| The dock/sheet fill actually occludes scrolling text | measured from the PNG (§3.2) |

The screenshot suite (`app/build/screenshots/`) is the visual gate: it renders real Skia + HarfBuzz
on the JVM, so a regression is caught before the APK reaches a device. Two rules keep it honest:
**compose what the app composes** (a bare screen on the window's white is a screen the app never
shows), and **measure the pixels** for anything about contrast or occlusion.
