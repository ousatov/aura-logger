# AI communication log

How AI tools were used to build Aura Logger (Event Tracker SDK + demo app), what worked, what went
wrong and how the output was verified. Detailed per-session notes are in [`docs/ai-notes/`](docs/ai-notes/).

## 1. Tools and versions

| Tool | Version / model | Used for |
|---|---|---|
| Claude Code, desktop app (Code tab) and headless CLI 2.1.317 | Claude Opus 5.5 (`claude-opus-5-5`) | Main session: planning, build setup, SDK API and KDoc, all tests, reconciliation, demo skeleton, coordination of parallel sessions, release |
| Claude Code side sessions | Opus 5.5 (SDK implementation, Events tab) and Sonnet 5.5 (`claude-sonnet-5-5`, smaller demo tickets) | Independent implementation of the SDK and of each demo feature ticket |
| Claude Code headless (`claude -p`) in a git `post-commit` hook | Sonnet 5.5 | Independent read-only review of every commit |
| Claude (Cowork, cloud session) | Opus 5.5 | Requirements analysis and planning (first hour) |
| Gradle, Android lint, Kover 0.9.9, `apksigner` | — | Verification of AI output |

No other AI tools (Copilot, ChatGPT, Gemini) were used.

## 2. How the work was organised

The core idea: **never trust one reading of the spec.** Every part was produced by one AI session and checked
by another one that could not see the author's reasoning.

1. **Planning (human + AI).** The assignment was analysed point by point; 20 design decisions were agreed before
   any code was written (see README, "Key decisions"). The AI was not allowed to generate anything until the human
   gave an explicit go.
2. **Contract first.** The main session wrote the SDK public API with detailed KDoc and compiling stubs
   (`TODO()`); the human reviewed the KDoc before merge. KDoc became the single source of truth.
3. **Independent tests and implementation.** Tests were written by the main session from the KDoc and kept in a
   local branch that was never pushed. The implementation was written by a separate session in a fresh clone of
   the repository from GitHub, with deny rules for the main folder, so it could not see the tests or the design notes.
4. **Reconciliation.** Tests and implementation were merged; every failure was classified as a test bug, an
   implementation bug or a spec gap. Implementation bugs became GitHub issues for the implementation session;
   spec gaps were resolved by the human and written into KDoc.
5. **Per-commit review.** A local `post-commit` hook ran a fresh, read-only Sonnet reviewer for every commit and
   wrote reports outside the repository; the human read them and forwarded findings as fix prompts.
   148 reviews: 102 clean, 26 with findings, 20 later resolved automatically after fixes.
6. **Parallel demo features.** The main session wrote a demo skeleton with a contract (MVI `Contract`, interfaces,
   KDoc) for every feature; then 6 feature tickets ran as independent sessions in separate git worktrees, in three
   waves that never touched the same files. Demo tests were again written independently in a separate clone.
7. **Manual testing by the human** found UX problems that no unit test could see; they became issues and were
   fixed by further ticket sessions.

Numbers: 19 merged PRs, 18 GitHub issues, 366 unit tests (SDK 254, demo 112), ~150 commits.

## 3. Key prompts (summarised, translated from Ukrainian)

| Phase | Prompt |
|---|---|
| Planning | "Read the assignment in detail, don't create anything yet." Then the human's own plan (MVI demo, interface-first SDK, implementation in a separate session) with a request for criticism. |
| Rules | "Nothing is generated until I explicitly say so. One step at a time, stop after each step for my review. Check library versions in official sources, not from memory." |
| SDK API | "Go" for step 2, followed by review rounds: "Do we have configs in one place?", "Limits must come from outside per the spec!" |
| Tests | "Pay attention to critical points, null pointers, garbage data, and everything needed for 100% coverage of business logic and failure cases." Later: "Why isn't coverage 100%? Let's cover the catches." |
| Reconciliation | "Merge your tests and its implementation without changing anything, even if tests fail." |
| Spec decisions | "Add all new events, delete only seen ones. If the limit can be reached, reach it. Put the disputed points into a config with a comment why." |
| Demo planning | "Decompose the rest into tickets so that several parallel independent sessions can run; sessions must write AI notes but not waste tokens on context." |
| Manual testing | "I tap Track Event many times, switch screens, Clear All deletes 2 instead of 13 — Clear All just doesn't work?"; "no grouping by day"; "the bottom toast queues stale messages". |
| Implementation session brief | "You are the implementation session. Follow CLAUDE.md §11 strictly. Requirements are only the public API and KDoc, CLAUDE.md and the assignment PDF. Do not write or look for tests." |
| Ticket session brief | "Your ticket is issue #N. Follow CLAUDE.md §11a: read only the files listed in the issue; verify once at the end; write short AI notes from the template." |

## 4. Where AI helped

- **Finding ambiguities in the assignment before coding.** Re-init must be ignored but config must change at runtime
  (→ separate `updateConfig`); "unseen events cannot be cleared" needs a seen flag and an API from the UI;
  `DD/MM/YYYY` strings sort wrongly across months; equal millisecond timestamps need a tie-breaker; `clearAll` can
  race with queued writes; the cleanup worker can run in a process where `init` was never called.
- **Verifying versions from primary sources.** Versions were read from Google Maven / Maven Central metadata and AAR
  metadata, not from memory: it found that `core-ktx 1.19` needs `compileSdk 37` and AGP 9.1, that AGP 9.5 needs
  Gradle ≥ 9.6, and that KSP 2.3.13 has a known regression (2.3.12 was pinned).
- **Test design.** The tests covered calendar-day retention across a 23-hour DST day, year-boundary ordering of day
  groups, paging boundaries inside groups of equal timestamps, SQLite's 999-variable limit in `markSeen`, JSON
  round trips of quotes / control characters / emoji, and null input from Java callers.
- **Independent verification paid off.** Reconciliation and reviews found 10 real problems in the SDK
  implementation that its own build checks could not (7 in the write queue's cancellation paths, plus #4–#6:
  a misread limit rule, a broken `applicationContext == null` fallback). The null-context bug was found only because
  the user asked to cover the remaining `catch` blocks.
- **Parallel work without conflicts.** The ticket split (one package + one strings file per ticket, shell owned by
  the skeleton) let 3 sessions work at the same time with no merge conflicts in production code.
- **Speed.** Most demo tickets took a ticket session 10–30 minutes against an estimated 1–2 hours by hand.

## 5. Where AI was wrong, and how it was found and fixed

| Problem | Found by | Fix |
|---|---|---|
| Recommended Room 2.8 over stable Room 3 because "AI knows Room 3 less well" — a tool argument, not a technical one, contradicting the project's own "latest stable" rule | Human asked "why not Room 3 if it is stable?" | Switched to Room 3.0.3 |
| Planned binary-compatibility-validator; it registers no tasks for Android with AGP 9 built-in Kotlin. Then used a removed DSL property of Kotlin's built-in ABI validation | Build errors | ABI validation for `sdk-core` only; `sdk` limitation in TODO |
| Called the `track` validation values "limits"; the human read it as moving the assignment's `maxEvents` limit to an internal setting | Human: "limits must come from outside per the spec!" | Renamed the fields, clarified that `maxEvents` stays public |
| KDoc for the limit ("delete the oldest events so that at most maxEvents remain") allowed two readings; the implementation picked the other one | Reconciliation (Room test failed) | Explicit rule with a worked example in KDoc; issue #4 |
| KDoc claimed new events are "never rejected" by the limit; false with `onLimit = false` | Third review session | KDoc corrected; 2 tests added |
| 35 SDK tests silently checked nothing: the write queue ran in `backgroundScope`, which `advanceUntilIdle()` does not run | Reconciliation, confirmed in kotlinx-coroutines-test source | `runCurrent()` |
| Demo statistics tests hung for 10+ minutes: an infinite refresh loop in `viewModelScope` kept `runTest` advancing | Per-class timed runs | Cancel `viewModelScope` at the end of each test |
| A test asserted `all { … }` on an empty list (paging page size) and could never fail; a REPLACE-policy test could not fail either | Per-commit reviewer | Scroll in the test; keep the first work pending; mutation check against `KEEP` and `APPEND_OR_REPLACE` |
| Skeleton UI had no paddings at all; the theme was placed in a package the AI's own rules did not allow | Human testing / reviewer | Issues #23 and a refactor |
| A ticket session wrote a draft into the main checkout instead of its worktree | Main session (`git pull` blocked) | Files restored; the merged PR itself was correct |
| A later ticket changed a contract and broke a test added by an earlier ticket; the session only ran build + lint | `./gradlew check` on develop | Test-only fix PR #30; verification command for tickets now includes unit tests |
| Fix prompts written by a helper session asked ticket sessions to add tests, against the project's own rule | Per-commit reviewer (only once of three) | Kept as a documented deviation; lesson: re-read current rules before writing a prompt |
| The AI twice started editing files when the human only asked a question | Human | Working rule: answer first, act only on an explicit request |

## 6. How AI-generated code was verified

- **Build and lint:** `./gradlew check assembleDebug assembleRelease` on every step; Android lint caught API-level
  problems (`LocalDate.ofInstant` on minSdk 26) and resource misuse.
- **Independent unit tests:** 366 tests written without seeing the implementation; all failures triaged.
- **Coverage:** Kover — `sdk-core` 99.8 % lines / 95.3 % branches, `sdk` 100 % / 100 %. The remaining branches
  depend on thread interleaving or are compiler-generated.
- **Mutation checks:** for tests that looked too easy to pass, the production code was temporarily broken to prove
  the test fails.
- **Per-commit review** by a different model with fresh context and no access to the author's notes.
- **Library facts** checked in sources: release notes, Maven metadata, AAR metadata, library source jars.
- **Manual testing** of the app by the human; found 8 UX issues that became tickets.
- **Not verified:** see [TODO.md](TODO.md) §2 (StrictMode, minified build on a device, multi-process).

## 7. AI version vs final version

**Room limit rule** (`EventDao`, implementation session): the first version kept a window of the newest `maxEvents`
events and deleted only seen events outside it, so the limit was not reached even when it could be.
```sql
-- AI version: events ranked after the newest maxEvents; unseen ones keep their place in the ranking
WHERE (:keepUnseen = 0 OR seen = 1)
AND sequence IN (SELECT sequence FROM events
                 ORDER BY timestamp DESC, sequence DESC LIMIT -1 OFFSET :maxEvents)
```
```kotlin
// Final: delete exactly the excess, oldest (seen) events first, wherever they are
val excess = count() - maxEvents
if (excess <= 0) return 0 // SQLite treats a negative LIMIT as "no limit"
return deleteOldest(excess, keepUnseen) // ... WHERE :keepUnseen = 0 OR seen = 1
                                        //     ORDER BY timestamp ASC, sequence ASC LIMIT :count
```
(issue #4)

**ABI validation** (build-logic):
```kotlin
// AI version (property removed in Kotlin 2.4)
kotlin { abiValidation { enabled.set(true) } }
// Final
kotlin { @OptIn(ExperimentalAbiValidation::class) abiValidation() }
```

**Engine test harness** (tests):
```kotlin
// AI version: background write queue never runs, assertions checked an empty store
h.engine.track("e"); advanceUntilIdle()
// Final
h.engine.track("e"); runCurrent()
```

**Config "Save" button** (demo contract):
```kotlin
// AI version: "007" enabled Save although it equals the stored 7
retentionInput.trim() != savedConfig.retentionDays.toString()
// Final
retentionInput.trim().toIntOrNull() != savedConfig.retentionDays
```

More snippets: [`docs/ai-notes/impl-session.md`](docs/ai-notes/impl-session.md) and the ticket notes.

## 8. Time

- **Total:** about 11 hours on the day of the assignment — about 1 hour of planning, then 13:55–23:45 by git
  timestamps (build setup, SDK, tests, reconciliation, demo, fixes, release).
- **AI sessions:** the SDK implementation session used about 1 hour of model time; demo ticket sessions 10–30
  minutes each; the per-commit reviewer about 1 minute per commit.
- **Time saved vs spent:** writing the same scope by hand (SDK with tests, demo with 6 features, build setup)
  would take an estimated 3–4 working days. Time "lost" to AI mistakes was roughly 1.5–2 hours: the misread limit
  rule (one extra reconciliation round), the silently passing and hanging tests, the AGP 9 ABI-validation detour,
  and coordinating parallel sessions.
- **What was done manually:** all product decisions and spec resolutions, KDoc review, reading review reports,
  manual testing of the app, merge decisions.

## 9. Lessons

- Ambiguous KDoc costs a whole reconciliation round; worked examples in KDoc prevent misreadings.
- Independent tests + independent implementation + independent review catch different classes of bugs; manual
  testing still catches UX problems none of them can.
- Tests can lie: check that each test can fail (mutation check), and watch for empty collections and background
  scopes in coroutine tests.
- Give parallel AI sessions disjoint files and one verification command that includes unit tests.
- Re-read the current project rules before writing a prompt for another session.
- Check timing claims of AI sessions against git timestamps.
