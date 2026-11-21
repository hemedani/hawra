# Prompt 01 — Initial Scaffold (Android)

> **Purpose of this folder:** a faithful record of the user's instructions and intent, so any agent
> can recover the *why* behind the code without re-asking. Prompts are historical record — they are
> not edited to match later changes; later decisions are appended as new dated entries.
>
> Companion: [`../summary.md`](../summary.md) (what exists), [`../plans/01-buildable-skeleton.md`](../plans/01-buildable-skeleton.md) (what's next).

---

## 1. Original brief (2026-11-12, verbatim intent)

The user is building **Hawraa (حوراء)**, a Quran application **exclusively for Android**. Key points
of the original request, preserved faithfully:

- **Native, state-of-the-art only.** "I want to utilize the most efficient technologies available;
  therefore, we will not be using frameworks like Flutter or React Native. The technologies chosen
  must be state-of-the-art and the best in their class."
- **Incremental over weeks, frequent device testing.** "We will approach the development step-by-step,
  working toward a final product over the course of several weeks… through a patient process —
  including frequent testing on my own device — we will add the desired features one by one."
- **Backend = the user's own Lesan framework** (see the backend prompt record for the reference repos).
- **Offline-first, but all user activity stored in the backend**: when the user read, the specific
  ayah range read, the settings used during the session, "and so on." Inspiration: Pejvak's
  history/session tracking.
- **Two pillars:** (1) **exceptional readability**, (2) **tracking/logging of each user's individual
  activity.**
- **Content scope for now:** only the **first five pages of the Quran**; the rest later.
- **Recitation:** Master **Hamed Shakernejad** (قرائت استاد حامد شاکرنژاد).
  **Translation:** **Ansarian** (ترجمه استاد انصاریان).
- **Readability reference app:** https://cafebazaar.ir/app/com.ketabmobin.app (**Ketab Mobin**) — "in my
  opinion, it is more readable than other Quranic applications."
- **From the ground up:** support **multiple languages** and **both RTL and LTR** layouts, and the
  infrastructure for **both light and dark themes** from the very beginning.
- **Font (verbatim):** "We still have a lot of work to do on the font; I want to be able to easily
  change the color of the diacritics (vowel marks) and the letters themselves (حروف و اعراب آنها در کلمات)."
- **Markers & supplementary information are crucial** — "for instance, the layout style of the
  'Uthman Taha' script."
- **The first unique feature (verbatim intent):** "the arrangement of the verses, translations, and
  commentaries (طرز قرارگیری آیه، ترجمه ها و تفسیرهای اون آیه). It allows for the simultaneous display
  of the verse itself alongside multiple translations and commentaries — regardless of how much
  scrolling is required."
- **This first task:** "for now, just set up the initial structure and the folder organization,"
  scaffolded as `back/`, `mobile/android/`, and a root `AGENTS.md`.

## 2. Clarifying decisions (2026-11-12)

Answers the user gave to the scaffold questionnaire:

| Question | Answer |
|---|---|
| App name & package | **Hawraa — حوراء**, package `ir.hawraa.quran` |
| Scaffold depth | **Buildable skeleton** (compiles to an installable APK), not folders-only |
| Minimum Android | **Android 10 — API 29** (minSdk 29) |

Derived/confirmed mobile decisions (see the plan's Decisions table for IDs):
- Native Kotlin + Jetpack Compose; AGP 9 built-in Kotlin; targetSdk 36, compileSdk 37.
- Offline-first; Room source of truth; WorkManager background sync; idempotent on `clientId`.
- Content bundled as assets (first 5 pages), never fetched at runtime.
- Typography renderer approach is an **open decision (D5)** to be resolved by a Phase-1 spike.

## 3. Mobile-specific requirements extracted from the brief

1. **Exceptional readability** is a first-class pillar — benchmark against **Ketab Mobin**.
2. **Uthman Taha mushaf layout** with markers (verse numbers, hizb, sajdah…) in their mushaf positions.
3. **Independently colorable letters and diacritics** — the font/rendering must make this easy. This is
   the hardest technical requirement; Compose `Text` alone cannot do it (custom renderer needed).
4. **Signature layout:** verse + multiple translations + commentaries displayed **simultaneously**,
   each independently scrollable, regardless of total scroll length.
5. **i18n + RTL/LTR + light/dark from day one** — infrastructure, not afterthoughts. fa default (RTL),
   en (LTR); Quran text always RTL.
6. **Offline-first activity tracking:** record every reading session (time, ayah range, settings) locally
   and sync to the backend; never block the UI on the network.
7. **Content:** first 5 pages only, Ansarian translation, Shakernejad recitation.
8. **Incremental, device-testable:** every increment must build to an installable APK; the owner tests
   on his own device frequently.

## 4. Follow-up prompt (2026-11-12) — documentation

> "Please create these MD files: `back/docs/{plans/01-…, prompts/01-…, summary.md}` and
> `mobile/android/docs/{plans/01-…, prompts/01-…, summary.md}`. Provide information as comprehensive
> and all-encompassing as possible, so that I can easily proceed with the task using any agent whenever
> I need to. **Be sure to mention that the plan file must be updated after every small task.**"

This is the instruction that produced the `docs/` structure you are reading. The "update the plan after
every small task" rule is encoded in each plan file's rules of engagement and must be honored by every agent.

---

## 5. Follow-up prompt (2026-11-14) — continue the app, match the reference font

> "Please read `mobile/android/docs/summary.md` and other docs (the MD files) about the phone app and
> continue the develop it. Please just develop mobile app, don't do anything in backend (I will do it
> with another specific AI agent). Also I like the font is used in this screenshots: `ignoreAssets/`."

Two screenshots of **Ketab Mobin** were supplied: the reading page (cream paper, surah/juz banner,
verse + Ansarian translation per block, rosette ayah numbers, green/gold palette) and its
"تنظیمات قرآن" sheet (pill segmented controls, font-size slider, live preview).

Interpretation recorded here:

- **Scope:** `mobile/android/` only. No backend changes — the owner drives `back/` with another agent.
- **Font:** the screenshots' picker has "عثمان‌طه" selected. That family (and every word-ligature
  mushaf face) bakes the marks into the word glyph, which makes the pillar feature — independently
  colouring حروف and اعراب — impossible. The closest face that *can* do it, **Scheherazade New**, was
  chosen; the reasoning and the measurement are in the plan's decision **D5**.
- **Design language:** the warm paper/green/gold palette, the surah/juz plate, the rosette ayah
  markers, the pill segmented controls and the live preview all follow the reference.
- **Persistence of intent:** the reading settings (size, translation on/off, letter ink, diacritic
  ink) were added because the reference exposes exactly those controls, and they are what the
  per-session settings snapshot (product invariant #2) will need to record.
---

## Addendum — 2026-11-17 (playback range)

Owner: "I want the continue playing the Quran when I don't select multiple Aye or Tarjome until the end of Sureh. When I select some Aye or Tarjome ir Tafsir, I want to play just those I selected."

Decisions: D31 (plain play queues to the end of the surah; audio no longer re-queues on scroll —
follow-along keeps the reciting ayah visible) and D32 (a multi-verse or spoken-layer selection
replaces the play action with "Play selection": recitation-only → plain queue of exactly those
verses; with tarjomeh/tafsir → ad-hoc preset sequence through PresetPlayer, never persisted).
Also: removal of the HawraaQA instrumentation + QaPlayerActivity probe from the playback-hunt
session (root causes fixed: missing `prepare()` in `setQueue`, missing `get` projection on
`reciter/gets`, and `file://` URLs routed through the HTTP data source). 