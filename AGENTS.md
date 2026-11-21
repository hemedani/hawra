# AGENTS.md — Hawraa (حوراء)

Hawraa is an **Android-only Quran application** with two pillars:

1. **Exceptional readability** — Uthman Taha–style mushaf layout, independently colorable letters (حروف) and diacritics (اعراب), verse + multiple translations + commentaries visible together.
2. **Complete activity tracking** — every reading session (which verses, when, with which settings) is recorded locally first and synced to the backend.

Development is **incremental over weeks**: a few features at a time, frequently tested on the owner's physical device. Do not build ahead of the current phase (see `docs/ROADMAP.md`).

## Precedence

Nearest `AGENTS.md` wins for sector detail (`back/`, `mobile/android/`). This root file wins for product invariants and cross-cutting rules. `docs/` is reference material. If a plan document and code disagree, code wins — then fix the plan.

## Architecture

```
Hawraa/
├── AGENTS.md               # this file — product rules
├── docs/ROADMAP.md         # phased plan; the current phase scopes all work
├── back/                   # Lesan (Deno + MongoDB) backend — see back/AGENTS.md
└── mobile/
    └── android/            # native Kotlin + Jetpack Compose app — see mobile/android/AGENTS.md
```

## Tech stack (do not swap without a strong, stated reason)

| Layer | Choice | Notes |
|---|---|---|
| Mobile | **Kotlin + Jetpack Compose** (native Android, no Flutter/RN/WebView UI) | AGP 9.x built-in Kotlin, Material 3, minSdk 29 |
| Local store | Room (SQLite) | offline-first source of truth |
| Sync | WorkManager + Ktor client | batched, idempotent, never blocks UI |
| Audio | Media3 / ExoPlayer | recitation: Hamed Shakernejad (حامد شاکرنژاد) |
| DI | Hilt (KSP) | |
| Backend | **Lesan** (`jsr:@hemedani/lesan`) on Deno 2 + MongoDB | the owner's own framework; conventions copied from his Pejvak/lesanSatek projects |
| i18n | Android string resources + per-app locales | fa (default, RTL) and en (LTR) from day one; every new language must cost ~zero code changes |

## Product invariants

1. **Offline-first.** Every user action writes to the local DB first. The network is never required to read the Quran or to record activity.
2. **Every reading session is recorded**: start/end time, exact ayah range (surah + from/to ayah, and mushaf page), reading settings in effect (font size, translation/commentary selections, theme, reciter, playback speed), completed/interrupted flags.
3. **Sessions are append-only** after finalization; one persistent `clientId` (UUID) per row makes sync idempotent — a timeout must never create a duplicate on the server.
4. **Integer milliseconds for timestamps, integer seconds for durations** everywhere (local DB, wire, server).
5. **Sync state machine**: `pending → syncing → synced | failed`; failed rows stay queued; sync failures are swallowed, never surface as UI errors.
6. **RTL/LTR and light/dark are infrastructure, not features**: no hardcoded layout direction, no hardcoded colors outside the theme, no user-facing string outside resources. Arabic Quran text is always RTL regardless of app locale.
7. **Letters and diacritics are separately colorable.** Any text-rendering decision must preserve this capability (see mobile AGENTS.md, "Quran typography").
8. **Content scope for now: the first 5 mushaf pages only** (Madani 604-page mushaf: p.1 Al-Fatiha, p.2–5 Al-Baqarah 1–29). Translation: Ansarian (انصاریان). Recitation: Shakernejad. Do not fetch or bundle the whole Quran yet.
9. **Sacred text integrity**: Quran text is never modified, reordered, or "corrected" by code. Verse boundaries and markers come from the vetted source data; rendering may style, never alter, the text.

## Workflow

- **Documentation & plan discipline:** each sector has `docs/summary.md` (orientation), `docs/plans/01-*.md`
  (roadmap **and** progress record), and `docs/prompts/01-*.md` (the original brief). Read the sector
  `summary.md` first. **⚠️ Update the plan file after EVERY small task** — tick the box, append a
  progress-log entry. Precedence: **code > plan > AGENTS.md > docs.**
- Backend before client: models → validators → acts → generated types → client consumption.
- Work in small, device-testable increments. After each increment the app must build (`./gradlew assembleDebug`) and the backend must pass `deno check mod.ts && deno lint && deno fmt --check`.
- The owner tests on his own device frequently — keep `assembleDebug` fast and the install path simple; report the APK path after every build.
- Never commit unless explicitly asked. Conventional Commits + Gitmoji when asked. Never commit secrets, `.env`, `local.properties`, or generated `back/declarations/`.
- Never use `git reset` or destructive shortcuts.
- Throwaway scripts live in `ignore-scripts/` (gitignored), never in product folders.

## Environment notes (this machine)

- `dl.google.com` (Google Maven + SDK repository) is unreachable here; **Gradle repositories use the Aliyun mirrors** declared in `mobile/android/settings.gradle.kts`, and SDK packages were installed manually via the `redirector.gvt1.com` CDN. Do not "fix" the mirrors back to `google()` only.
- JDK: Homebrew openjdk (22) at `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Android SDK: `~/Library/Android/sdk`. Deno 2.8.x is on PATH.
- Local ports in use by the owner's other projects: 1370, 1404, 1405, 1406, 8080, 8090. Hawraa backend default: **1407**.

---

### Git Commit Workflow

When I say `git commit` please do the following:

Please act as an expert Git commit assistant. Your task is to carefully review the recent project changes (e.g., via git diff or staged files) and generate a series of clear, conventional commit messages following best practices. Use Gitmoji emojis at the start of each commit message to make them more expressive and readable (e.g., :sparkles: for new features, :bug: for fixes).
Key guidelines:
- Conventional structure: Each commit message should start with a Gitmoji, followed by a type (e.g., feat, fix, refactor, docs, test, chore), a scope in parentheses if applicable (e.g., (ui)), a colon, and a concise description. Include a body if needed for more details, and reference issues if relevant.
- Grouping: Break changes into logical, atomic commits. Group related files or changes together (e.g., one commit for UI updates, another for bug fixes), rather than lumping everything into a single commit. Avoid overly large or unrelated groupings.
- Execution: Directly output and execute the necessary Git shell commands (e.g., git add for specific files, followed by git commit -m "message") to apply these commits. Do not ask for confirmation, additional input, or perform unrelated actions like rebasing, squashing, or amending existing commits. Only create new commits on the current branch.
- Best practices: Ensure messages are imperative, concise (50 chars for subject), and descriptive. Focus on what changed and why, not how.
- Use present tense for the subject line (e.g., "Add feature" not "Added feature")
- Be specific about what was changed (e.g., "Fix user login validation" rather than just "Fix bug")
- When making breaking changes, indicate this with an exclamation mark after the type (e.g., "feat!: Remove deprecated API endpoint")
- Reference issue numbers if applicable (e.g., "fix(auth): Resolve login issue #123")
- For multiple related changes, create separate commits for each logical change
- When updating dependencies, mention the specific packages (e.g., "chore(deps): Update react and react-dom to v18")
- For documentation changes, be clear about what documentation was added or updated
- When changing configuration files, explain the purpose of the changes
- Proceed step-by-step: First, analyze the changes, then propose the grouped commits, and finally execute the Git commands in sequence.

⚠️ **WARNING**: Under no circumstances should you ever use the `git reset` command when performing git operations, as it can permanently erase work that took days to complete. This command has caused significant data loss in the past and should be avoided entirely.

