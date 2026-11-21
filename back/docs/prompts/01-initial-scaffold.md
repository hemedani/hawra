# Prompt 01 — Initial Scaffold (Backend)

> **Purpose of this folder:** a faithful record of the user's instructions and intent, so any agent
> can recover the *why* behind the code without re-asking. Prompts are historical record — they are
> not edited to match later changes; later decisions are appended as new dated entries.
>
> Companion: [`../summary.md`](../summary.md) (what exists), [`../plans/01-foundation-and-tracking-api.md`](../plans/01-foundation-and-tracking-api.md) (what's next).

---

## 1. Original brief (2026-11-12, verbatim intent)

The user is building **Hawraa (حوراء)**, a Quran application **exclusively for Android**. Key points
of the original request, preserved faithfully:

- **Native, state-of-the-art only.** "We will not be using frameworks like Flutter or React Native.
  The technologies chosen must be state-of-the-art and the best in their class."
- **Incremental over weeks.** "We will approach the development step-by-step… we aren't aiming to
  implement everything at once… through a patient process — including frequent testing on my own
  device — we will add the desired features one by one."
- **Backend = the user's own framework, Lesan.** Reference projects he has built with Lesan:
  - `/Users/syd/work/sitak/lesanSatek`
  - `/Users/syd/work/katiraei/ziwound`
  - `/Users/syd/work/madani/nejat/lesan`
  - `/Users/syd/work/Pejvak`
- **Offline-first, but all user activity stored in the backend.** Specifically:
  - when the user read the Quran,
  - the specific range of verses (Ayahs) read,
  - the settings used during the reading session,
  - "and so on."
  - "You can draw inspiration from the history and session-tracking features implemented in
    `/Users/syd/work/Pejvak`."
- **Two pillars:** (1) exceptional readability, (2) tracking/logging of each user's individual activity.
- **Content scope for now:** only the **first five pages of the Quran**; the rest comes later once mature.
- **Recitation:** Master **Hamed Shakernejad** (قرائت استاد حامد شاکرنژاد).
  **Translation:** **Ansarian** (ترجمه استاد انصاریان).
- **Readability reference app:** https://cafebazaar.ir/app/com.ketabmobin.app (Ketab Mobin — the user
  finds it more readable than other Quran apps).
- **From the ground up:** multiple languages, **RTL and LTR**, and **light/dark themes** infrastructure
  in place from the very beginning.
- **Font:** "we still have a lot of work to do on the font; I want to be able to easily change the
  color of the diacritics (vowel marks) and the letters themselves (حروف و اعراب آنها در کلمات)."
- **Markers & supplementary info are crucial** — e.g. the layout style of the **Uthman Taha** script.
- **The signature feature:** the arrangement of verse + translations + commentaries — simultaneous
  display of the verse alongside multiple translations and commentaries, "regardless of how much
  scrolling is required."
- **This first task:** "for now, just set up the initial structure and the folder organization,"
  scaffolded as `back/` (with AGENTS.md), `mobile/android/` (with AGENTS.md), and a root `AGENTS.md`.

## 2. Clarifying decisions (2026-11-12)

Answers the user gave to the scaffold questionnaire:

| Question | Answer |
|---|---|
| App name & package | **Hawraa — حوراء**, package `ir.hawraa.quran` |
| Scaffold depth | **Buildable skeleton** (compiles to an installable APK), not folders-only |
| Minimum Android | **Android 10 — API 29** (minSdk 29) |

Derived/confirmed backend decisions (see the plan's Decisions table for IDs):
- Anonymous **device identity** first (no login), one user row per `deviceId`.
- Server stores **activity + references, never the sacred Quran text**.
- Idempotent batched sync on client `clientId`; epoch-millis timestamps, integer-second durations.
- Port **1407**, db **hawraa** (avoids the owner's other project ports).

## 3. Backend-specific requirements extracted from the brief

1. **Record every reading session**: start/end time, exact ayah range (surah + from/to ayah, plus
   mushaf page range), and a snapshot of the reading settings in effect.
2. **Offline-first ingest**: the client writes locally first and syncs in the background; the server
   must accept batched, retryable, **idempotent** uploads (a lost response must not duplicate rows).
3. **Readable history**: the user must be able to query their own reading history (paginated, newest
   first, filterable by time).
4. **Match Lesan conventions** from the reference repos (especially Pejvak) — do not invent new patterns.
5. **Grow incrementally**: settings sync, stats, and a content-version manifest come in later phases;
   do not build them ahead of the mobile need.

## 4. Follow-up prompt (2026-11-12) — documentation

> "Please create these MD files: `back/docs/{plans/01-…, prompts/01-…, summary.md}` and
> `mobile/android/docs/{plans/01-…, prompts/01-…, summary.md}`. Provide information as comprehensive
> and all-encompassing as possible, so that I can easily proceed with the task using any agent whenever
> I need to. **Be sure to mention that the plan file must be updated after every small task.**"

This is the instruction that produced the `docs/` structure you are reading. The "update the plan after
every small task" rule is encoded in each plan file's rules of engagement and must be honored by every agent.

---

*End of record. Append new dated prompts/decisions below as the project evolves; do not rewrite history.*