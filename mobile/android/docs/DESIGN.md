# DESIGN.md — Hawraa Visual Language

> **The single source of truth for how the app looks.** Every screen, component and token is
> built to this spec. If code and this file disagree, **the code is right and this file is a bug** —
> fix the file in the same change.
>
> **Read this before writing any UI.** Not optional, not "later": a new screen that does not use
> these tokens is a regression even if it compiles.
>
> Companion docs: [`summary.md`](summary.md) (orientation, stack, build quirks),
> [`plans/01-buildable-skeleton.md`](plans/01-buildable-skeleton.md) (roadmap + progress record),
> [`../AGENTS.md`](../AGENTS.md) (engineering rules).

**Status:** adopted 2026-11-15. Supersedes the warm-paper "mushaf" look that shipped through
Phases 0–3 (cream paper, deep-green banner, gold-bordered plate). That look is what the owner
called *"the typical look of existing Quranic apps"*; this document is the deliberate departure.

---

## 1. Design intent

Hawraa is a **reading surface**, not a control panel. The previous design borrowed the visual
grammar of printed mushaf apps — warm paper, an ornamental green banner, gold rules — which is
handsome but anonymous: it looks like every other Quran app on the store.

The new language comes from the owner's reference (`ignoreAssets/sample.jpg`: a frosted-glass audio
player) and from the shipping Pejvak app (`/Users/syd/work/Pejvak/mobile`), whose dock and glass
recipe are the concrete target. We adopt its **material language** — light, floating frosted panes
over a soft ambient field — and adapt it to a text-first app.

Three ideas carry everything:

1. **The page floats.** Chrome does not touch the edges of the screen. The reading surface is a
   pane with generous corners, the top bar is a pill, the dock and the action box float above the
   content with a visible gap. Nothing is bolted to a bezel.
2. **Glass is for chrome, never for scripture.** Frosted panes carry navigation, controls and
   metadata. The Quran text sits on the most stable, most opaque surface in the app, because
   legibility of sacred text outranks any material effect. (See §5.5 — this is the one place we
   deliberately diverge from the reference.)
3. **One accent means "current".** A single filled teal circle marks the current destination, the
   playing ayah, and the primary action in the action box. Warm gold is reserved for *what the
   reader marked* and for ornament — it is never an interactive colour.

### 1.1 What we are leaving behind

| Old (pre-2026-11-15) | New |
|---|---|
| Cream paper `#FBF6EC` | Near-white glass `#FDFDFE` on a cool canvas `#E4E9EF` |
| Deep-green banner with gold border | Teal-tinted glass plate, hairline edge, no gradient |
| Gold rosette ornament | Neutral ring that fills with the active hue |
| Full-width bottom transport bar | Floating action box over the selection + a floating dock |
| Green primary / gold secondary / bronze tertiary | Teal accent / gold "marker" / neutral chrome |
| Edge-to-edge opaque screens | Floating panes over an ambient canvas |

### 1.2 What we are NOT doing yet

Authentic Iranian design elements — tilework geometry, `تذهیب` illumination, Persian blue
(`لاجوردی`) fields, arabesque borders — are the **planned next layer**, not this one. Two hooks are
already in place so that layer can be added without re-cutting the foundation:

- the accent is turquoise (`فیروزه`), the most Iranian of the cool hues;
- gold survives as a *marker* colour, so ornament can return without re-theming the app.

Do not add half-hearted "Persian" decoration ad hoc. When that layer lands it gets its own section
here.

---

## 2. Principles

1. **Legibility is the ceiling on every effect.** Any glass, blur, tint or gradient that reduces
   the contrast of Quranic text or its diacritics is wrong, no matter how good it looks.
2. **Float, don't frame.** Floating panes over an ambient field. No hard full-bleed bars, no 1 px
   screen-edge dividers.
3. **One radius ramp, one spacing rhythm.** Never hard-code a corner or a gap; ask the geometry
   tokens (§7).
4. **One accent, one meaning.** Filled teal = "this is the current thing". If a second element on
   screen is also filled teal, one of them is wrong.
5. **Motion explains, never decorates.** Springs for press, a height change when the dock gains or
   loses its player half. No looping animation except the ambient canvas wash, and that stops under
   reduced motion.
6. **The chrome is disposable; the text is permanent.** Chrome may fade, collapse or slide away.
   The mushaf never moves unexpectedly.
7. **Every string in resources, every colour in `ui/theme/`.** No literals anywhere else (§17).

---

## 3. The ambient canvas

Glass needs something to refract. A frosted pane over flat white is invisible; over a softly
coloured field it reads as glass. So every screen renders an **ambient canvas** behind its content.

- **`AmbientCanvas`** — a full-bleed field of 2–3 very large, very soft radial blooms drawn from
  the theme's canvas ramp. Low frequency, low contrast, no noise, no hard edges.
- A **base tint** sits under the blooms so the field never reaches pure white or pure black.
- The canvas is **static** (it does not drift). A slow drift is tempting and was considered, but a
  reading app that moves behind the text is a reading app that distracts. If it is ever animated it
  must be ≤ 0.02 alpha/second and off under reduced motion.
- Content scrolls **over** the canvas; the canvas does not scroll.

**Canvas values** (see §5.1). The light canvas is a **cool pale grey-blue**, not cream and not
white — cream would drag the whole app back to the mushaf look, and white would kill the glass.

---

## 4. Colour system

All values live in `ui/theme/`. Nothing outside that package may contain a colour literal.

### 4.1 Canvas

| Token | Light | Dark | Role |
|---|---|---|---|
| `canvas` | `#E4E9EF` | `#060910` | the field everything floats on |
| `canvasElevated` | `#EDF1F6` | `#0E1520` | one step up, behind a floating pane |
| `canvasBloomA` | `#C9DCE6` | `#0D2C33` | cool bloom (teal) |
| `canvasBloomB` | `#D3DAEC` | `#12203A` | cool bloom (periwinkle) |
| `canvasBloomC` | `#E4DCEC` | `#241C33` | soft bloom (lilac) |

### 4.2 Glass

| Token | Light | Dark | Role |
|---|---|---|---|
| `glassFill` | `rgba(255,255,255,0.78)` | `rgba(28,37,50,0.72)` | a normal floating pane |
| `glassFillStrong` | `rgba(255,255,255,0.98)` | `rgba(30,40,54,0.98)` | dock, sheets, action box — anything that must read over busy content |
| `glassBorder` | `rgba(20,32,39,0.13)` | `rgba(226,238,255,0.22)` | the hairline edge |
| `glassHighlight` | `rgba(255,255,255,0.95)` | `rgba(255,255,255,0.24)` | the specular top edge |
| `shadow` | `rgba(20,32,39,0.16)` | `rgba(0,0,0,0.55)` | shadow tint |

**Note the border direction.** In light mode the hairline is *dark* at 13 %, not white: over a
light canvas a white border disappears. In dark mode it is *light* at 22 %. This asymmetry is
deliberate and is the single detail that makes the panes read as glass in both themes.

**Why `glassFillStrong` is 98 % and not 90 %.** The dock, the action box and sheets sit over text
that scrolls behind them. Measured on a rendered screenshot, a 92–96 % fill left the page's Persian
translation **4 % legible** through the panel — enough to read whole words under the dock's icons,
which looks like a rendering fault rather than like glass (dark needed the same treatment: at 94 %
the ghost measured an 8-lum lift, more visible on a flat dark panel). The fill only has to occlude;
the **border, the 2 dp sheen and the shadow** are what make the material read. Translucency is for
`glassFill` (78 %), which sits over the ambient canvas and is meant to show it.

### 4.3 Text

| Token | Light | Dark | Contrast target |
|---|---|---|---|
| `text` | `#0E1720` | `#F0F5F4` | ≥ 4.5:1 on any pane |
| `textSecondary` | `#41505A` | `#B4C4CB` | ≥ 3:1 |
| `textTertiary` | `#5B6A75` | `#8B9EA8` | ≥ 1.25:1 vs secondary |
| `icon` | `#2C3A43` | `#D6E2E6` | resting glyphs |

`textSecondary` and `textTertiary` must stay **measurably** different. The pre-glass app shipped two
greys at ~1.07:1 and every two-line row read as one flat block.

### 4.4 Accent & semantic

| Token | Light | Dark | Use |
|---|---|---|---|
| `accent` | `#0A635C` | `#5FD9C6` | current destination, playing ayah, primary action, links |
| `accentSoft` | `rgba(10,99,92,0.12)` | `rgba(95,217,198,0.16)` | washes, selected chip fill |
| `onAccent` | `#FFFFFF` | `#04231F` | glyph knocked out of a filled accent |
| `mark` (gold) | `#A98436` | `#D9BB78` | **selection only** + ornament rules |
| `markSoft` | `rgba(169,132,54,0.14)` | `rgba(217,187,120,0.18)` | selection wash |
| `danger` | `#B02F45` | `#FF8296` | destructive, failure |
| `track` | `rgba(65,80,90,0.18)` | `rgba(180,196,203,0.20)` | progress track, dividers |

**`mark` is not an interactive colour.** It never appears on a button, a chip, a toggle or a nav
item. It means *the reader put this here* — a selected verse, a rule under a surah plate, a
diamond bullet. If gold starts looking interactive, the accent has lost its monopoly on "current".

### 4.5 The mushaf surface (Quran-specific)

The reader has its own token set (`QuranColors`) because scripture has requirements chrome does not.
It is provided alongside the glass tokens and never mixed with them.

| Token | Light | Dark | Role |
|---|---|---|---|
| `page` | `#FDFDFE` | `#101720` | the reading pane — the most opaque surface in the app |
| `pageVariant` | `#F4F7F9` | `#16222C` | inset rows inside the pane |
| `letter` | `#16202A` | `#EDE9E0` | Quranic letters (default ink) |
| `diacritic` | `#8A6A28` | `#D9BB78` | the marks (default ink) |
| `translation` | `#41505A` | `#A8B4BE` | translation line |
| `ornamentRing` | `accent @ 40 %` | `accent @ 45 %` | ayah rosette, unfilled |
| `ornamentNumber` | `#41505A` | `#A8B4BE` | ayah number, unfilled |
| `bannerFill` | `accentSoft` | `accentSoft` | surah plate |
| `bannerEdge` | `accent @ 35 %` | `accent @ 35 %` | surah plate hairline |
| `bannerText` | `accent` | `accent` | surah name |
| `bannerSubText` | `textSecondary` | `textSecondary` | juz / surah number |
| `selection` | `markSoft` | `markSoft` | wash behind a tapped pane |
| `selectionEdge` | `mark` | `mark` | leading bar of a tapped pane |
| `playing` | `accentSoft` | `accentSoft` | wash behind the reciting ayah |
| `playingEdge` | `accent` | `accent` | leading bar of the reciting ayah |
| `letterMuted` | `letter @ 34 %` | `letter @ 34 %` | silent letters (§6.3) |

**Selection and playback must never look alike.** Selection is warm (`mark`); playback is cool
(`accent`). Selection outranks playback when both apply to the same pane — a verse the reader
deliberately marked keeps its gold while the recitation walks past it.

### 4.6 Ink presets (user choice)

The reader may override `letter` and `diacritic` from Settings (`LetterInk`, `DiacriticInk`). Those
presets are **reader preference, not theme**: they are exempt from the accent rules, must keep their
own contrast against `page`, and every new preset needs its light and dark variant before it ships.

---

## 5. Typography

### 5.1 Faces

| Role | Face | Notes |
|---|---|---|
| Quran text | **Scheherazade New**, split into `quran_letters.ttf` + `quran_marks.ttf` | Two derived ink layers with identical metrics. See `summary.md` §9 — do not "simplify" this to one font. |
| UI (Latin) | platform sans (Roboto) | no bundled Latin face |
| UI (Persian) | platform Naskh | *future:* bundle **Vazirmatn** (OFL) for UI chrome; it is a visible quality win for the Persian UI, which is the default locale |

### 5.2 Scale

| Token | Size / line | Weight | Use |
|---|---|---|---|
| `hero` | 40 / 46 | 700 | landing wordmark, empty-state hero |
| `display` | 32 / 40 | 700 | screen title |
| `title` | 22 / 30 | 600 | surah name in the top bar |
| `heading` | 18 / 26 | 600 | section headers, sheet titles |
| `body` | 15 / 22 | 400 | primary copy |
| `bodyStrong` | 15 / 22 | 600 | emphasized copy |
| `label` | 13 / 18 | 500 | buttons, chips, mini-player title |
| `caption` | 12 / 16 | 400 | metadata, mini-player subtitle |
| `overline` | 11 / 14 | 600, +1.2 tracking | eyebrows, group labels |
| `numeric` | 13 / 18 | 500, **tabular** | durations, counters, ayah spans |

**Rules:** anything that counts uses tabular figures so digits do not jitter. Persian numerals are
rendered with `toPersianDigits()` at the display layer only — never in identifiers or storage.
Quran text sizes are a reader setting (22–44 sp) and are **never** taken from this table.

### 5.3 Quran text sizing

`fontSizeSp` is user-controlled. `quranLineHeight(sp)` derives the line height (marks need vertical
room — a tight line height clips the very diacritics the app is built to show). Never set an
absolute line height on Quran text.

---

## 6. Shape & spacing

### 6.1 Radius ramp

One strictly-increasing ramp. Never hard-code a corner.

| Token | dp | Alias |
|---|---|---|
| `xs` | 8 | badge |
| `sm` | 12 | tile, badge |
| `md` | 16 | inline control, text field |
| `lg` | 20 | inset row, segmented track |
| `card` | 28 | **cards, floating panels, the dock** (`panel`) |
| `xl` | 32 | collapsed sheet, reading pane |
| `xxl` | 40 | expanded sheet, hero |
| `pill` | 999 | chips, top bar, search |

### 6.2 Spacing — a 4 dp rhythm

`0, 2, 4, 8, 12, 16, 20, 24, 32, 44, 64` (`none, xxs, xs, sm, md, lg, xl, xxl, xxxl, huge, giant`).

Screen padding 20–24 · card gap 12–16 · card inner padding 16.

| Group | Tokens (dp) |
|---|---|
| Controls (circular) | `sm 32, md 36, lg 40, xl 44, play 52` |
| Tiles | `sm 32, md 40, lg 48` |
| Icons | `xs 13, sm 15, md 18, lg 22, xl 26, hero 44` |
| Hairlines | 1 dp (`1.dp`), or `Dp.Hairline` for dividers inside a pane |
| Max content width | 760 |

### 6.3 Dock geometry — the numbers are the contract

```
playerHeight 58   navHeight 58   indicator 40
icon 22           iconActive 20  touchTarget 44
margin 10         inset 12       clearance 20
```

- Both halves are **equal height** (58) so the seam divides the panel in half rather than cutting a
  tall nav off a short player.
- The selected indicator is a **true circle** (40), not a lozenge. A pill-shaped indicator reads as
  a filter tag, not as "you are here".
- `iconActive` (20) is **smaller** than `icon` (22) on purpose: it sits inside a 40 dp disc, so 20 dp
  is exactly half the disc and rhymes with the mini-player's play bubble. Trust the number; do not
  "fix" it to match `icon`.

**`dockInset = margin + playerHeight + navHeight + clearance = 146`**

Screens owe the dock `dockInset`, sized for the dock **at its tallest**, never a literal and never
the collapsed height. A screen cannot know whether something will start playing while it is open,
and reserving later would make its list jump.

---

## 7. Depth — the glass recipe

Depth is **not** a drop shadow. It is a three-part recipe, in order of importance:

1. **Blur** of whatever is behind the pane. On API 31+ this is a real
   `RenderEffect.createBlurEffect`; below 31 the fill alone must carry it.
2. **Translucent fill** (`glassFill` / `glassFillStrong`) — gives the pane its colour and keeps text
   legible over busy content.
3. **Specular top edge + hairline border** — the 2 dp sheen gradient (`glassHighlight` → transparent,
   running slightly diagonally: `x 0 → 0.35`) plus a 1 dp `glassBorder` stroke.

A soft shadow may sit under the **dock and sheets only**, to lift them off content:

| Elevation | Offset | Radius | Use |
|---|---|---|---|
| `none` | — | — | content cards, rows |
| `dock` | y 6 | 18 | the dock |
| `panel` | y 12 | 28 | action box, floating panes |
| `float` | y 16 | 34 | sheets, menus |

**The dock's shadow is deliberately shallower than `float`.** A floating *control* is lifted off the
page; a dock is a *surface*. The deep drop reads as the dock hovering above the screen rather than
floating in it, and darkens the canvas along the bottom edge of every list.

**API 29–30 fallback:** no blur. Raise the fill opacity (light `0.88`, dark `0.80`), keep border and
sheen. Legibility must never depend on blur.

---

## 8. Iconography

- **One family:** Material Symbols / Material Icons, `Outlined` for resting states and `Filled` for
  the selected state. Screens ask for a semantic name through `HawraaIcon`; never import an icon set
  in a screen.
- **Interactive icons live in circles.** A filled accent circle is the primary action; outlined
  circles are secondary. A bare glyph is only acceptable inside a larger tappable row.
- **Directional glyphs are auto-mirrored** (`Icons.AutoMirrored`) so back/next stay correct in RTL.
- A glyph whose meaning changes (play ↔ pause, playing ↔ paused) **cross-fades**; it never swaps
  instantly in place.
- Transport glyphs come from the semantic `ICON_GLYPHS` catalog (`ui/components/Icon.kt`, D3), drawn
  from Material Rounded in `material-icons-extended`.
- The same rule covers the password eye (`ui/components/EyeGlyph.kt`, §10.11): `material-icons-core`
  has no `Visibility`/`VisibilityOff`, so the glyph is two quadratics and a disc. **Before adding an
  icon dependency, check whether the glyph is two shapes in a `Canvas`** — it usually is, and the
  dependency is always bigger than the drawing.

---

## 9. Motion

Governed by the OS "remove animations" setting. When motion is off, substitute the **final value** —
never rely on the platform flag alone.

| Interaction | Motion |
|---|---|
| Press | spring scale `1 → 0.92 → 1.04` (overshoot), ~120 ms |
| Dock height change | height animation 220 ms, emphasized easing |
| Action box appear | fade + 8 dp rise, 160 ms |
| Sheet open/close | spring, 280 ms, no bounce on dismiss |
| Tab / destination switch | ocean current: lateral tab drift, push rise, pop reverse; WaveSweep crest + ambient surge, `OceanSwellEasing` |
| Playing-ayah wash | 180 ms cross-fade of the wash colour only |
| Follow-along scroll | `animateScrollToItem`, and only when the verse is off screen |

**Never animate:** Quran text size changes, scroll position after a deliberate reader scroll, or the
canvas at idle (the navigation surge is its only movement). Under reduced motion the switch is
instant and the crest is absent.

---

## 10. Components

Every component is a glass pane unless stated. Compose names in parentheses.

### 10.1 `GlassSurface`

The base pane. `glassFill` + `glassBorder` hairline + 2 dp specular sheen + optional blur and
shadow. Variants: `tone = surface | surfaceStrong`, `elevated = none | dock | panel | float`.

```kotlin
GlassSurface(
    shape = RoundedCornerShape(geometry.radius.card),
    strong = true,          // dock / sheets / action box
    elevation = Elevation.Dock,
) { … }
```

### 10.2 `BubbleButton`

Circular glass icon button. `filled = true` → solid `accent` disc with `onAccent` glyph (the primary
action); `filled = false` → outlined disc with `glassBorder` and an `icon`-coloured glyph. Every
variant pads out to a 44 dp touch target even when it draws smaller.

### 10.3 `GlassChip` and `GlassSegments`

A **chip** is a filter/toggle pill: `radius.pill`, `accentSoft` fill when selected with an `accent`
hairline and `accent` label; `glassFill` + `glassBorder` + `textSecondary` when not. A chip is a
*filter*; a segmented control is a *choice*. Do not use a chip where the reader is choosing one of
two.

A **segmented control** (`GlassSegments`) is one `glassFill` track with a hairline border and 4 dp of
inner padding, and the selected segment is a **filled `accent`** pill with an `onAccent` label, both
animated. The fill — not a tint — is what makes "which one am I in" survive a glance. It reads
`LocalGlassColors` rather than `MaterialTheme.colorScheme`; `SegmentedPill` does the same job with
Material's palette, and on a screen whose whole surface is glass that difference shows as a track
one shade off from every pane around it. **Anything choosing one-of-N uses `GlassSegments`.**

### 10.4 `AppDock` — the menu bar + mini-player

**One panel, two halves.** This is the centrepiece and it is copied structurally from the reference
app, because that structure exists for a reason worth repeating:

> The now-playing row and the navigation used to be two separate floating panels. Two panels means
> their geometry is a negotiation, and the negotiation went wrong: the bar's raised centre button
> ended up 32 dp underneath the now-playing row. Inside one panel the question cannot be asked —
> one surface, one radius, one shadow, no clearance to get wrong.

**Anatomy**

```
╭─────────────────────────────────────────╮  FrostedSurface, radius.xl (pill when nav-only)
│  ▢  Surah al-Fatiha          (▶)  (⏭)  │  playerHeight 58   ← only while playing
│  ▢  ayah 3 · Parhizgar                  │
├─────────────────────────────────────────┤  seam: HairlineWave, inset 12
│    ⌂        ▤         ◷        ⚙        │  navHeight 58
╰─────────────────────────────────────────╯
   margin 10 from the safe-area edge, inset 12 horizontal
```

- **Collapses when idle.** Height animates between `navHeight` (nothing playing) and
  `playerHeight + navHeight + hairline` (playing). One shared value drives both the panel height and
  the row's visibility, so they can never disagree.
- **The player half fades in and out as one group**, seam included, so the divider cannot outlive
  the row it divides.
- **Navigation is icon-only, 4–5 peers.** No labels, no raised centre action. Selected = filled
  `accent` circle (`indicator` 40) with the glyph knocked out in `onAccent` at `dock.iconActive`.
  Unselected = `textSecondary` glyph at `dock.icon`; every peer has a 44 dp touch target.
- **The navigation current explains the switch.** A dock tap drifts laterally along the tab order,
  a pushed screen rises and a pop reverses; the WaveSweep crest rides the same current. Under
  reduced motion the switch is instant and the crest is absent.
- The dock is a **sibling** of the scrolling content, never inside it, so the content's scroll does
  not drag the dock and the dock's shadow does not clip.

**Hawraa's destinations:** `Home` · `Read` · `History` · `Settings`. Four peers is within the range
Material specifies for icon-only docks, and it leaves room for a fifth (`Search`) without a redesign.

### 10.5 `MiniPlayerRow`

The dock's player half. Identity tile 40 dp (`geometry.tiles.md`, `radius.sm`), title (`label`) over
subtitle (`caption`, `textSecondary`), then trailing controls: **play/pause** (filled accent bubble,
40) and **next** (outlined, 36). A `WavyProgress` wave sits under the title/subtitle inside the leading
region, and the whole leading region is one tap target that opens the player.

- Tapping the row opens the player / reader context; the trailing buttons must not steal that tap.
- While buffering, the play bubble shows a spinner and **keeps its press** — the same control, same
  job.
- The row is a plain `Row` with **no surface of its own**. The dock owns the panel.
- Hawraa has no artwork: the tile is a deterministic **identity tile** (`PaletteTile`) — an aurora
  ramp derived from the number, with the numeral in the UI face, centred. No image assets, no
  network; the same number paints the same tile on every device.
  - For a **juz-granularity reciter** the tile carries the **juz** number, matching the title beside
    it. The juz's starting surah would put `جزء ۵` and `۴` in the same 58 dp row with nothing to say
    which is which.
  - It is **not** knocked out of a filled disc: three filled accent objects in one 58 dp row (tile,
    play bubble, selected destination) turns the accent into decoration and loses the rule that
    "filled accent = the current thing".
  - It is **not** set in the mushaf face. Scheherazade draws «۱» as one thin calligraphic stroke;
    at 19 sp inside a 40 dp tile that is a slanted smudge rather than a numeral, and it reads as a
    rendering artefact. **The mushaf face is for scripture; chrome uses the UI face.**

### 10.6 `SelectionActionBox` — the floating action box

This replaces the old full-width bottom transport bar. It is the component the owner asked for:
**a small floating pane that appears over whatever is selected**, carrying the actions that apply to
that selection.

**Anatomy**

```
        ╭──────────────────────────────╮   glassFillStrong, radius.panel,
        │  ╭───╮                       │   elevation.panel, padding 6
        │  │ ▶ │   Play ayah 3        │   ← primary: filled accent bubble + label
        │  ╰───╯  ╭───╮  ╭───╮         │
        │         │ 📖 │  │ ⋯ │         │   ← future actions (glossary, notes…)
        │         ╰───╯  ╰───╯         │
        ╰──────────────────────────────╯
                 ↑ anchored above the dock, near the selection
```

**Behaviour**

- **Appears when something is selected**, disappears when the selection is cleared. Driven by
  `VerseSelection.active` — the same single element that already answers "which verse to peek at".
- **Anchored, not docked.** It floats in the region above the dock and, where the selected pane is
  on screen, sits as close to it as it can without covering it. If the selection is under the box,
  the box flips below it; if there is no room either way, it pins to the bottom above the dock.
- **Never covers the selection.** This is the whole point of the change: the old bar took a strip of
  the screen permanently, and it was never where the reader was looking.
- **Extensible by construction.** Actions are a list (`SelectionAction`), not a hard-coded row. Today
  the list is `Play`; the glossary/terminology button, "copy", "note" and "share" are later entries
  and require no layout change. The first action is always the filled accent bubble; the rest are
  outlined.
- **Dismissal:** tapping elsewhere in the page, scrolling away from the selected verse, or pressing
  back. Dismissing clears the selection.
- **The label says what Play does.** "Play ayah 3" — not a bare glyph. The box is transient and the
  reader did not necessarily arrive at it by pressing play.
- Only appears when a queue exists (recitation catalogue available); otherwise the box shows the
  actions that are available and hides play rather than showing a dead button.

### 10.7 `ReaderTopBar`

A floating pill, not a bar: `radius.pill`, `glassFill`, 44 dp tall, inset 12 from the edges.
Contents: surah name (`title`) · diamond bullet · juz (`label`, `textSecondary`). Trailing settings
bubble. No back arrow — the dock owns navigation, and a floating dock plus a back arrow is two ways
to do the same thing.

### 10.8 `AyahOrnament`

The verse number marker: a drawn 8-lobe rosette (`res/drawable/ayah_marker.xml`, Delapouite's "Rub
el Hizb", CC BY 3.0). Unfilled: `ornamentRing` stroke + `ornamentNumber` digits. Selected: filled
`mark` with knocked-out number. Playing: filled `accent` with knocked-out number. Numbers are
Persian digits, always.

### 10.9 `VerseBlock` / `Pane`

One verse is a **stack of independently selectable panes**: the Arabic pane, then one pane per
translation. Each pane owns its selection state (§11.3) and draws its own wash and leading-edge bar.
This is what makes "three translations and a commentary, each highlighted separately" possible.

- Pane radius `lg` (20), inner padding 8/6.
- The leading-edge bar is 3 dp, on the **start** edge (right in RTL).
- Translations are indented past the rosette (34 dp start) — the translation belongs to the verse,
  not to the ornament beside it.

### 10.10 Sheets

`GlassSheet`: `glassFillStrong`, `radius.xl` top corners, a 36 × 4 drag handle, scrim behind
(`#060910` @ 40 %). Owns its own insets. Used for reading settings, reciter picker, and any future
glossary sheet — the glossary action in the selection box opens a sheet, it does not navigate.

### 10.11 `PasswordReveal`

The eye that shows and hides a password (`ui/components/EyeGlyph.kt`). The almond is two quadratics
and the pupil is a disc; hidden adds a corner-to-corner slash. Wrapped in a **40 dp** touch target
with `Role.Button` and a state-dependent `contentDescription` ("show password" / "hide password"),
because a 20 dp glyph is not a tap target and an unlabelled toggle is invisible to a screen reader.

It is drawn rather than imported for the same reason as the transport glyphs (§8):
`material-icons-core` ships neither `Visibility` nor `VisibilityOff`. It takes a `tint` rather than
reading a theme colour so it can sit at `textTertiary` in a field and warm to `accent` on focus.

### 10.12 The player morph

The full player is not a second surface that appears over the dock: it is the dock's mini half
**unfolding** into the screen. `NowPlayingSheet` is owned by the shell and mounted as a **sibling**
of the nav host and the dock — never inside either — and it reads `NowPlayingStore`; it knows
nothing about navigation, and the reader keeps the engine and session tracking.

One normalised progress `t` (`0` = the dock capsule, `1` = full screen) drives every channel:

- **Bounds** interpolate from the dock's measured `miniPlayerRect` to the viewport, so the capsule
  starts exactly on the dock row and no surface ever jumps.
- **Corner** is `mini * (1 - t^1.6)`, where `mini` is the dock's `radius.xl`; the capsule lets go
  quickly at first and eases into the square full-screen edge.
- **Cross-fade:** the dock row fades out over `t 0.12 → 0.52` while `PlayerContent` fades in over
  `t 0.34 → 0.90` (scale `0.84 → 1`); hit-test order hands off at `t = 0.5`. A faded layer
  swallows pointers, so the invisible one is never the live target.
- **Glass → opaque:** the card's glass fill, frost scrim and rim fade out as the reading canvas
  fades in; scripture never lands on glass.
- **Hero flight:** the identity is one `PaletteTile` interpolated from the dock tile's rect to the
  hero's measured rect, with both endpoints hiding their own copy during the flight. The hero is
  `geometry.player.heroSize` (180 dp) carrying `Typography.heroNumeral` (56 sp) — no artwork (D5).

**Pull to dismiss.** A downward drag drives the same `t`. Travel is normalised over at least
`pullFloor` (420 dp), so a short screen cannot fling the player away on a nudge. On release the
player dismisses if it has travelled past `pullDismiss` (105 dp) or is flung down faster than
`pullFling` (520 dp); otherwise it springs back to `t = 1`. A slow lift at full screen always
settles open.

**Backdrop.** The shell blurs the content box (nav host + dock) by `frost.backdrop × t` on API 31+;
the player itself sits outside that blur — it is the surface the blur exists to reveal. API 29–30
has no `RenderEffect`, so the sheet fades the same progress into a frost scrim (`scrimMax`, 0.30)
instead; legibility never depends on the blur.

**Reduced motion** snaps every channel to its end value — open, close and pull included — and the
sheet never withholds content to make a transition work.

---

## 11. Screens

### 11.1 Reader — the signature screen

```
┌─────────────────────────────────────────┐  AmbientCanvas
│  ╭───────────────────────────────────╮  │
│  │ فاتحه  ◆  جزء ۱              ⚙    │  │  floating pill top bar
│  ╰───────────────────────────────────╯  │
│                                         │
│  ╭───────────────────────────────────╮  │
│  │  ╭─────────────────────────────╮  │  │  reading pane: glassFillStrong-ish,
│  │  │  سورۀ ۱  فاتحه              │  │  │  radius.xl, page colour, high opacity
│  │  ╰─────────────────────────────╯  │  │
│  │   ١  بِسْمِ ٱللَّهِ …            │  │  │
│  │      به نام خدا…                 │  │  │
│  │   ٢  ٱلْحَمْدُ لِلَّهِ …          │  │  │
│  │      ستایش‌ها…                    │  │  │
│  │        ⋮  (scrolls)              │  │  │
│  ╰───────────────────────────────────╯  │
│         ╭─────────────────────╮         │
│         │ ▶  Play ayah 3      │         │  ← SelectionActionBox (floating)
│         ╰─────────────────────╯         │
│  ╭───────────────────────────────────╮  │
│  │ ▢ فاتحه · آیه ۳        (▶)  (⏭)  │  │  ← AppDock
│  │   ⌂     ▤      ◷      ⚙          │  │
│  ╰───────────────────────────────────╯  │
└─────────────────────────────────────────┘
```

- The reading pane is a **glass pane at high opacity** (`page` over `glassFillStrong`). The canvas
  shows through at its edges, which is enough to read as glass; the text sits on a stable field.
- No page number, no page furniture — the scroll is continuous (see `ReaderItems`).
- The dock is always present; the action box only while something is selected.

### 11.2 Home

Wordmark (`hero`, Quran face), a "continue reading" card showing the **real opening verses** of the
resume page, a today strip (minutes, goal bar, streak), one filled primary action. Cards are glass,
`radius.card`, padding 16.

Behind the wordmark, a lattice of faint octagrams (the ayah marker's eight-point geometry at 7 %),
**stroked with a vertical gradient brush so it fades into the canvas by itself** — never with a box
painted over the top. The hero sits on the ambient canvas, so a fade to the mushaf paper leaves a hard
white band where the hero meets the canvas (it did, until a screenshot showed it), and a fade to
transparent is not a fade at all.

### 11.3 History

Summary card (time / pages / streak) in tabular figures, then day sections (Today / Yesterday / date)
with one glass row per session. Rows use an inset hairline that starts at the text, not at the pane
edge.

### 11.4 Settings

Grouped sections, each one glass card: Appearance (theme, language), Reading (font size, inks,
translation), Recitation (reciter, speed, repeat), About (attributions). Rows are 56 dp, leading
glyph bubble, inset dividers.

Two sections end in a **link row** rather than a control — *Recitation presets* and *Recitation
downloads*. A link row is one line of hint text (`textSecondary`, `bodyMedium`) with a `ChevronIcon`
at the end, pointing left in RTL. It exists because both destinations need a list, a picker and a
queue, which is more than a settings row can honestly express; the section title names the
destination, the hint says what it is for.

### 11.5 Downloads

Purpose: choose a reciter and an edition, choose a scope in the unit the reader thinks in, keep the
audio on the device — and manage everything already there.

```
فضای استفاده‌شده                                     ← one strong glass card
۱۲ مگابایت                            ۳۶ فایل در صف

قاری
[ حامد شاکرنژاد ] [ شهریار پرهیزگار ] [ منذاوی ]     ← GlassChips

نسخه
[ ترتیل — جزء‌به‌جزء ]                                ← chip…
  ۲۴ kbps · juz                                      ← …technical line, LTR
[ مرتل — آیه‌به‌آیه ]
  ۴۸ kbps · ayah
  این نسخه جزء‌به‌جزء است؛ هر جزء یک فایل…            ← granularity note

محدوده
[ صفحه | سوره | جزء | آیه ]                           ← GlassSegments

از صفحهٔ ۱
────●────────────────────────────────────────────    ← NumberPicker (labelled Slider)
تا صفحهٔ ۵
──────────────────────────────●──────────────────
[ پنج صفحهٔ اول ]                                     ← GlassChip

صفحهٔ ۱ تا ۵                                ۳۶ آیه    ← summary row

[           دانلود (حدود ۳٫۸ مگابایت)           ]     ← the one accent action
۲۹ فایل در صف دانلود · ۷ از قبل ذخیره شده بود         ← notice, in place

روی این دستگاه
╭──────────────────────────────────────────────────────╮
│ شهریار پرهیزگار                                 کامل │
│ مرتل — آیه‌به‌آیه                                    │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ │
│ ۱۲ مگابایت · ۲۸۶ از ۲۸۶                      حذف همه │
│ ──────────────────────────────────────────────────── │
│ جزء ۱                                 ۳۶ از ۳۶   حذف │
│ جزء ۲               ۳۴ از ۳۶ · ۲ ناموفق  تلاش دوباره │
╰──────────────────────────────────────────────────────╯
  header = reciter + edition label + `کامل` when finished
  bar    = the edition's coverage across every scope under it
  footer = size · done of total · `حذف همه`; then one BatchRow per scope,
           each with `تلاش دوباره` only when something failed
```

- **The estimate is on the button**, not in a dialog: `دانلود (حدود ۳٫۸ مگابایت)`. A 300 MB download
  started by accident is a bad afternoon, and the number is the only thing that prevents it.
- **Scope is offered in the reader's unit first.** `صفحه` leads because a page is what a mushaf has —
  and the wire has no page scope at all (`ayah | surah | juz | range`), so a page range is resolved
  into a verse range against the bundled mushaf before anything is sent. When the pages have not
  loaded, the picker says so and points at the two scopes that need no text, rather than guessing at
  a boundary.
- **Four scopes, never four at once.** A juz-granularity edition collapses the control to `جزء`
  alone: the other three would describe a download that edition cannot serve. `scopeModes` decides,
  so the screen itself carries no granularity conditional beyond the note.
- **Two sliders, not start-plus-count.** "From page 3 to page 7" is how someone describes a stretch
  of mushaf out loud; a count would have to be converted in the head. The start slider drags the end
  along when it passes it, so the pair can never invert.
- **`پنج صفحهٔ اول` is a chip, not a special case.** It sets the same two sliders to 1 and 5. It
  exists because it is by far the most common request, and asking for it should not require finding
  two thumbs.
- **The technical line is LTR, the note is not.** `۴۸ kbps · ayah` is a token, not prose; in an RTL
  paragraph the bidi algorithm reorders it into `kb · ayah 48`, which reads as a different fact. The
  note below it is a sentence and is set in the locale's own direction. A juz edition's note is
  `textSecondary` rather than `textTertiary` — it is a constraint on what you can do next, not a
  footnote.
- **The summary row is the last chance to stop.** The scope in the reader's words at the start edge,
  the ayah count at the end edge. `صفحهٔ ۱ تا ۵` is not a wire scope, so it is spelled out here rather
  than shown as `۱:۱ تا ۲:۲۹` — the same range, and no help to anyone reading it.
- **Holdings are grouped per edition, not listed per batch.** The header answers "how much of this
  reciter do I have", the bar answers "is it finished" (`کامل` when it is), and the rows below answer
  "which parts". Thirty flat batches are thirty progress bars with no total.
- **Delete is offered twice, on purpose.** Per scope for surgical removal, per edition (`حذف همه`)
  for "free this space" — after a five-page download the second is the only one anyone wants.
- **Retry appears only where something failed**, as `accent` text beside `حذف` in `danger`. The
  download button stays the screen's only filled control.
- **The notice is in place, not a snackbar.** It reports what the queue now holds —
  `۲۹ فایل در صف دانلود · ۷ از قبل ذخیره شده بود` — which is worth reading twice; a snackbar is
  worth reading zero times.
- **A missing catalogue is a tinted strip, not an empty screen.** The pickers step aside and one
  `danger`-tinted row explains why, because a download screen with no reciters looks like a bug
  rather than a connection problem.

### 11.6 Presets

Purpose: see your recipes, how much of the Quran each has covered, and edit one.

```
[                 New preset                 ]           ← one accent action

╭ آیه، ترجمه، المیزان                          پیشفرض ╮
│ آیه ← ترجمه ← تفسیر                                 │  ← the recipe, in order
│ ۲٪ از قرآن · ۱۴۹ آیه                                │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ │
│ بخشها: ۱:۱–۱:۷, ۲:۱–۲:۱۴۱, ۱۸:۱                    │
│ پیشفرض کن   ویرایش   حذف                            │
╰──────────────────────────────────────────────────────╯
```

- **The recipe is a line, with arrows.** Order is the feature; a list of toggles would lose it.
  Repeats are inline (`آیه ×۲`), and the arrow is the text run's, so the line reads correctly in both
  directions without a direction-aware layout.
- **Coverage lives here, not in History.** Coverage is a property of a *preset* — "how much have I
  heard with this recipe" — while History answers "what did I do on Tuesday". The card shows the
  percentage, a bar, and the runs themselves (at most four, then an ellipsis).
- **No coverage is a sentence, not a zero**: "Nothing read with this preset yet."
- **The editor is a name and an ordered list.** Each step row is `1. Verse` with a repeat stepper
  (`−  ۲  +`) and a `Remove` action; silence has no repeat. Below the list, chips add a step. Save is
  the one accent action and is disabled until there is a name and at least one playable step.
- **The stepper uses text glyphs (`−`/`+`), not icons** — `material-icons-core` has `Add` but no
  `Remove`, and the extended icon set is not worth one minus sign.

### 11.7 The account gate

Purpose: get an account before anything else, because history recorded before sign-in belongs to
nobody.

```
┌───────────────────────────────────────────────────┐
│                         ✳                         │
│                       حوراء                       │
│                     قرآن کریم                     │
│                                                   │
│   ╭─────────────────────────────────────────────╮   │
│   │ ساخت حساب تازه                              │   │
│   │ با یک حساب، تاریخچهٔ مطالعهٔ شما…           │   │
│   │                                             │   │
│   │ [    ورود    |    ثبت‌نام    ]              │   │
│   │                                             │   │
│   │ ایمیل                                       │   │
│   │ ╭───────────────────────────────────────────╮ │   │
│   │ │ ✉  you@example.com                        │ │   │
│   │ ╰───────────────────────────────────────────╯ │   │
│   │ گذرواژه                                     │   │
│   │ ╭───────────────────────────────────────────╮ │   │
│   │ │ 🔒  ••••••••                           ◉ │ │   │
│   │ ╰───────────────────────────────────────────╯ │   │
│   │ تکرار گذرواژه                               │   │
│   │ ╭───────────────────────────────────────────╮ │   │
│   │ │ 🔒  ••••••••                           ◉ │ │   │
│   │ ╰───────────────────────────────────────────╯ │   │
│   │ نام (اختیاری)                               │   │
│   │ ╭───────────────────────────────────────────╮ │   │
│   │ │ 👤  نام شما                               │ │   │
│   │ ╰───────────────────────────────────────────╯ │   │
│   │ ⚠  دو گذرواژه یکسان نیستند.                 │   │
│   │                                             │   │
│   │ (           ساخت حساب            )          │   │
│   ╰─────────────────────────────────────────────╯   │
│ پس از ورود، برنامه بدون اینترنت هم…               │
└───────────────────────────────────────────────────┘
  ornament + wordmark = two bands above the card
  card  = GlassSurface(strong, Panel), radius.card
  ◉     = PasswordReveal, 40 dp target, one per password field
  ⚠     = AuthErrorLine, tinted danger strip (not bare red text)
```

- **Three bands: ornament, wordmark, one pane.** The gate is the first thing anyone sees and the only
  screen with no dock, no back and no content, so it carries the whole weight of a first impression.
  The medallion is **drawn**, not shipped: it has to work in both themes at any density, and a PNG
  would be three assets and a mismatch waiting to happen.
- **The medallion is an eight-point khatam, in one path.** Eight points because that is the star of
  the geometric tradition this app's chrome borrows from; drawn as one closed polygon with
  alternating radii rather than two overlapping squares, so the stroke joins cleanly at every vertex
  instead of leaving the doubled corners two squares produce. The radial `accent` glow behind it is
  what stops it reading as a sticker on the canvas.
- **One glass card, centred, `radius.card`, `strong` + `GlassElevation.Panel`.** The gate is the only
  screen with no dock and no top bar: it is not a destination, it is the condition for the rest of
  the app to exist, so it offers no way back and no way past.
- **One screen, two verbs.** Sign in and register differ by three fields and one word, so a segmented
  control switches between them instead of a second screen — two screens would be two copies of the
  same validation and two chances to fix a bug in only one of them. Switching verbs clears the error:
  "this email is taken" is a lie on the sign-in tab.
- **Fields are hand-built, and the label sits above the box.** Material's outlined field draws its own
  1 dp box, its own 16 sp label and its own floating-label animation, none of which belong to this
  app's glass language — and none of which can be restyled into it. A floating label also disappears
  the moment there is text, which is exactly when someone checking a form wants to know which field
  they are looking at.
- **The border is the only focus affordance**: hairline `glassBorder` → `accent`, animated, with the
  label and leading icon warming in step. No outline, no ring, no elevation change — one cue, and it
  is unmistakable. The whole pane is the touch target, not the glyph-and-cursor strip, or tapping the
  padding beside the text feels broken.
- **The email and both passwords are LTR.** An address and a password are Latin even inside a Persian
  sentence; left alone, bidi types `a@b.com` back to front, which looks correct and is not.
- **Two password fields, each with its own eye.** A single reveal toggle for both would be one
  control doing two jobs, and the confirmation exists precisely because the reader cannot see what
  they typed. The reveal flags are `rememberSaveable`, so a rotation while typing does not silently
  expose the password. The eye is drawn on a `Canvas` — `material-icons-core` has no
  `Visibility`/`VisibilityOff` — and lives in a 40 dp target, because a 20 dp glyph is not a tap
  target.
- **The confirmation is checked before the wire, and only when registering.** A server that accepted
  a mismatched pair would still lock the reader out on the next attempt, so catching it locally costs
  a round trip and saves a support ticket. The check sits after the length check: "too short" is more
  useful than "they differ" when both are true. On the sign-in tab there is nothing to confirm, and a
  stale confirmation must never block a sign-in.
- **The primary action has three states, and they must not be confusable:** filled `accent` when it
  can be pressed, `accentSoft` + a spinner while working, flat `glassFill` + `textTertiary` when it
  cannot. (The editor's save action stops at the soft state, which is right for a screen that is
  *editing* something; here a soft mint pill beside a hard green selected segment reads as
  disabled — a screenshot made that obvious.) Busy swaps the label for the spinner in place rather
  than growing the pane, because a button that changes height mid-press makes the form jump.
- **The shadow is only under the live button.** An 8 dp `accent` shadow when it can be pressed, none
  otherwise: a shadow under a dead control suggests it is floating there waiting for a tap that will
  not happen.
- **The error is a tinted strip, not a red line.** `danger` at 0.09 alpha on light and 0.18 on dark,
  a 0.32 border and a `Warning` glyph give the message an edge of its own — a red sentence between
  two glass panes is easy to miss, and this screen has no snackbar to fall back on. It is replaced by
  the next keystroke, because an error that survives the fix is noise.
- **The IME walks the form.** `Next` moves email → password → confirmation → name, and `Done` submits
  from the last field; on the sign-in tab `Done` on the password submits directly, since there is
  nothing after it.
- **The footnote says why the gate exists** ("Once you are signed in, the app works offline"), in
  `textTertiary`. Asking for a password before showing anything is a cost; the sentence is what makes
  it a trade the reader can accept.
- **No language switcher here.** The gate is the first screen and must respect the app's configured
  language, not offer to change it — there is no Settings to reach yet.


---

## 12. Accessibility & the contrast contract

Glassmorphism's failure mode is invisible hierarchy. The contract:

- `text` on any pane ≥ **4.5:1**; `textSecondary` ≥ **3:1**; `textSecondary` vs `textTertiary`
  ≥ **1.25:1**.
- **Quranic text ≥ 7:1** against `page` — stricter than body copy, because diacritics are small
  strokes and the reader may be reading for a long time.
- Filled-bubble glyph (`onAccent` on `accent`) ≥ 4.5:1.
- Every interactive target ≥ **44 dp**, even when the visible glyph is smaller.
- Every icon-only button carries a `contentDescription`; the dock reports
  `Role.Tab` + `selected` state.
- Focus order follows reading order. Persian is RTL: never hard-code `left`/`right`, always
  `start`/`end`.
- **A palette edit that breaks a threshold fails the suite. Never weaken a threshold to make it
  pass.**

---

## 13. Light & dark

Both first-class, switched by `themeMode` (System / Light / Dark). The difference is **canvas and
glass opacity**, not structure:

- **Light** — cool pale canvas (`#E4E9EF`), white glass at 78 % / 98 %, near-black text. Reads as
  frosted panes over a bright sky.
- **Dark** — near-black canvas (`#060910`), dark glass at 72 % / 98 %, near-white text. Reads as
  glass over deep water.

Accent, mark and semantics are shared; only canvas/glass/text alphas differ. No screen may branch on
the theme to pick a colour — ask the token.

---

## 14. RTL & Persian

- Persian is the **default locale** and the only RTL one shipped. English is LTR.
- All layout uses `start`/`end`; `LocalLayoutDirection` decides the leading edge for bars, indents
  and icon mirroring.
- Persian digits via `toPersianDigits()` at display time only.
- Arabic/Quran text is always RTL regardless of UI locale.
- Mixed Persian + Latin strings rely on the platform bidi algorithm; do not hand-insert marks.
- Every string lives in `values/strings.xml` (fa) and `values-en/strings.xml`. A hardcoded string is
  a bug.

---

## 15. Compose implementation notes

- **Tokens:** `GlassColors` + `HawraaGeometry` are `staticCompositionLocalOf` values provided by
  `HawraaTheme`, alongside the existing `QuranColors`. Read them via `LocalGlassColors` /
  `LocalHawraaGeometry`.
- **Blur:** `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(r, r, Clamp) }`
  on API 31+, gated on `Build.VERSION.SDK_INT`. Below 31, raise the fill instead. Blur the
  **background**, never the pane's own text.
- **Sheen:** `drawWithContent` after `drawContent()` — a 2 dp vertical gradient at the top edge.
  Never a highlight over the upper half; it blows out.
- **Dock:** `Column` inside one `GlassSurface(strong = true)`; height animated with
  `animateDpAsState` from one boolean. Nav slots are `Row` children with `weight(1f)` so the row
  cannot overflow by arithmetic.
- **Dock inset:** `contentPadding = PaddingValues(bottom = geometry.dock.dockInset)` on the reader's
  `LazyColumn`. Never a literal.
- **State:** `StateFlow` + `collectAsStateWithLifecycle`. Keep high-frequency ticks (playback
  position) in leaf composables so the dock's title row does not recompose every second.
- **Selection:** `VerseSelection` is a set of layer-keyed elements plus one `active`; the action box
  reads `active`, each pane reads its own membership. Do not add a parallel "isSelected" boolean.

---

## 16. Do / Don't

**Do**
- Read this file before building a screen.
- Use the radius ramp, the 4 dp rhythm, tabular figures, one icon family.
- Keep the reading pane the most opaque surface on the screen.
- Make the filled accent circle mean exactly one thing.
- Reserve `dockInset` on every scrollable screen.
- Check both themes and both locales before calling UI work done.

**Don't**
- Don't put glass behind Quran text; don't lower the reading pane's opacity "for the effect".
- Don't add hard drop shadows to content cards — depth is blur + fill + hairline + sheen.
- Don't hard-code a corner, a colour, a direction, a clearance or a string.
- Don't reintroduce a raised centre dock action, per-tab labels, or a full-width bottom bar.
- Don't use `mark` (gold) as an interactive colour.
- Don't add a page counter or page-boundary furniture; the reader scrolls continuously.
- Don't copy GPL code from reference projects — patterns and measurements only.
- Don't weaken a contrast threshold to make a palette edit pass.

---

## 17. Enforcement

| Rule | Gate |
|---|---|
| No colour literal outside `ui/theme/` | review + `grep` in CI |
| No hardcoded user-facing string | review (`strings.xml` only) |
| Quran text contrast ≥ 7:1 | `ContrastContractTest` (planned, §12) |
| Tier separation ≥ 1.25:1 | same test |
| Screens render in both themes + both locales | `*ScreenshotTest` + `PersianLaunchTest` |
| No page chrome in the reader | `ReaderItemsTest` |
| A capture shows the composition the app really builds (canvas + dock, real glyphs) | `TestShell` used by every `*ScreenshotTest` |
| The dock/sheet fill actually occludes scrolling text | measured from the PNG: ghost ≤ 2 % (§4.2) |

The screenshot suite (`app/build/screenshots/`) is the visual gate: it renders real Skia + HarfBuzz
on the JVM, so a regression is caught before the APK reaches a device. Two rules keep it honest:
**compose what the app composes** (a bare screen on the window's white is a screen the app never
shows), and **measure the pixels** for anything about contrast or occlusion — an impression of a
4 % ghost and a measurement of one lead to the same fix, but only the measurement tells you when
you are done.
