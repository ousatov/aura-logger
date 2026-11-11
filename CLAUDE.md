# CLAUDE.md — Aura Logger

Guidance for every Claude session working in this repository. Read it fully before doing anything.

## 1. What this project is

A take-home assignment: an **Event Tracker SDK** (Android library) plus a **demo app** that uses it.

- Sources of requirements, in priority order:
  1. Public API of the SDK and its **KDoc**. Once the API step is merged, KDoc is the single source of truth for SDK behaviour.
  2. The assignment PDF (supplied by the user; it is not committed).
  3. This file (summarises the key rules; if it disagrees with KDoc, KDoc wins and this file must be fixed).
- Deliverables: private GitHub repo, release APK of the demo, `AI_COMMUNICATION.md`, TODO list for unfinished items.
- Deadline: same day. Prefer finishing the whole flow over polishing one part; anything cut goes to the TODO list.

## 2. Hard constraints

- Kotlin only. OOP + SOLID.
- `minSdk = 26`, `targetSdk = 36`.
- Room for event persistence, WorkManager for background cleanup.
- The SDK is a separate Android library module from the demo app.
- Base package: `com.usatov.aura.logger`.
- Everything in the repo is in **English**: code, KDoc, comments, commit messages, PRs, docs.

## 3. Modules

```
build-logic/   included build with convention plugins
sdk-core/      pure Kotlin/JVM module, NO Android imports
sdk/           Android library: the SDK artifact; depends on sdk-core via api(...)
demo/          Android application: Compose + MVI + Hilt
```

### sdk-core (`com.usatov.aura.logger.core`)
- Public SDK model and contracts: `EventTracker` interface, event / stats / config models, `PropertiesCodec<T>`, cursor-based page types.
- Domain logic: validation, tracking pipeline (pre-init buffer, serial write queue), cleanup policy (retention + limit + unseen protection), date handling.
- Ports implemented by `sdk`: event store, config store, cleanup scheduler, `Clock`, id generator, logger.
- Allowed dependencies: Kotlin stdlib, kotlinx-coroutines-core (`api`), kotlinx-serialization-json (`implementation` only).
- Must stay KMP-ready: no `java.*` / `android.*` in public signatures.

### sdk (`com.usatov.aura.logger`)
- Facade `EventTrackerSDK` (object, as in the assignment example) delegating to an `EventTracker` instance.
- Adapters: Room 3 (`androidx.room3`, `AndroidSQLiteDriver`) for events, Preferences DataStore for config, WorkManager (cleanup worker + scheduler).
- Only one DataStore instance per file may exist in a process: the SDK and the cleanup worker share a process-wide instance.
- Own manual composition root. **No DI framework inside the SDK.**
- Room / WorkManager / storage libraries are `implementation` dependencies and never appear in public API.

### demo (`com.usatov.aura.logger.demo`)
- Jetpack Compose + Material3, hand-rolled MVI, Hilt.
- Owns the catalog of app events (names + properties). The SDK knows nothing about concrete events.
- Builds its own Paging 3 `PagingSource` on top of the SDK cursor API.
- Debug-only time-shift tool (offset `Clock` passed to the SDK) in the `debug` source set.

## 4. SDK public API rules

- `explicitApi()` in `sdk-core` and `sdk`. Everything not meant for clients is `internal`.
- Cross-module internals (`sdk` using `sdk-core` internals) are public but marked with a `@RequiresOptIn` annotation `@InternalAuraLoggerApi`.
- Public API contains **no third-party types** except Kotlin stdlib and kotlinx-coroutines (`suspend`, `Flow`).
- Kotlin Gradle plugin ABI validation guards the public API of `sdk-core`: run `./gradlew updateKotlinAbi` intentionally when the API changes and commit `sdk-core/api/`. `check` runs `checkKotlinAbi`. Not available for `sdk` yet (AGP 9 built-in Kotlin); keep the `sdk` public surface minimal (the facade).
- Every public declaration has KDoc covering: behaviour, threading, error handling, edge cases.
- Consumer ProGuard/R8 rules ship with `sdk`.

## 5. Key behavioural rules (summary; KDoc is authoritative)

- **Init**: takes application `Context` and config (retention days, default 7; max events, default 100; unseen-events protection). First call wins; later calls are ignored. Thread-safe. Uses `applicationContext`. Persists config. Schedules unique periodic cleanup (24 h period, 1 h initial delay).
- **Config update**: separate `updateConfig(...)`; persists the new config and enqueues a unique one-time cleanup.
- **track()**: never throws to the caller and never blocks it. Id and timestamp are generated synchronously at call time. Writes go through one serial queue (no data loss for 100+ rapid calls). Calls before `init` go to a bounded in-memory buffer that is flushed on `init` (oldest dropped on overflow, with a warning).
- **Properties**: primary API is `Map<String, String>`; typed overload via `PropertiesCodec<T>`. Stored as a JSON string.
- **Retrieval**: recent events (limit, newest first), stats (total, today, per-day groups), clear all, cursor-based page, change notifications. All suspend / off the caller's thread. Ordering is stable for equal timestamps.
- **Seen events**: the client reports events actually shown to the user (`markSeen`). `UnseenEventsProtection` has three independent flags — `onRetention`, `onLimit`, `onClearAll` — all `true` by default. A protected unseen event is never deleted by that operation.
- **Cleanup**: deletes events older than `startOfDay(today) − retentionDays` and the oldest events above `maxEvents`, honouring the protection flags. The worker must work in a process where `init` was never called (it reads the persisted config).
- **Dates**: `createdDate` stored as `DD/MM/YYYY` (device time zone at creation) for grouping; groups ordered by timestamp, not by the string.

## 6. Demo architecture (MVI)

- Per screen: `Contract` (`State`, `Intent`, `Effect`), `ViewModel` with a pure reducer, Compose UI that only renders `State` and sends `Intent`s.
- One-off effects through a `Channel`/`SharedFlow`, state through `StateFlow`, collected with `collectAsStateWithLifecycle`.
- Stats refresh every 3 s only while the UI is visible.
- SDK is initialised in `Application.onCreate` with the persisted demo config.
- Navigation and screen layout are decided at the UI step.

## 7. Testing

- Unit tests are mandatory for `sdk-core`, `sdk` and `demo`.
- Prefer fakes over mocks; inject `Clock`, dispatchers, id generator.
- JUnit Jupiter (JUnit 6, `org.junit.jupiter.api`) on the JUnit Platform; applied by `aura.unit-test`.
- Use kotlinx-coroutines-test; Turbine for flows. Robolectric / work-testing only where Android is unavoidable (Robolectric needs JUnit 4 + the vintage engine).
- Further test levels (instrumented, UI) are decided later.

## 8. Build

- Version catalog `gradle/libs.versions.toml` is the only place for versions. Latest stable versions are pinned at the setup step (Gradle 9.9.1, AGP 9.5.1 with built-in Kotlin, Kotlin 2.4.21, KSP 2.3.12).
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`. Gradle runs on JDK 21; bytecode targets Java 17.
- Convention plugins in `build-logic`: `aura.jvm.library`, `aura.android.library`, `aura.android.application`, `aura.compose`, `aura.hilt`, `aura.room`, `aura.unit-test`. Third-party Gradle plugins (KSP, Room, Hilt) are applied only through convention plugins.
- Release build: R8 minify + resource shrinking, signing from `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or env vars (`AURA_KEYSTORE_FILE`, `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, `AURA_KEY_PASSWORD`); unsigned without them. Keystore and `keystore.properties` are never committed.
- Verify with `./gradlew check assembleDebug assembleRelease`.
- Optional if time allows: detekt / ktlint.

## 9. Git workflow (git flow)

- Branches: `main` (releases only), `develop` (integration), `feature/<short-name>`, `release/<version>`.
- Every step = feature branch → PR into `develop` → **merge commit** (no squash).
- Commits: atomic and logical, Conventional Commits (`feat:`, `fix:`, `build:`, `test:`, `docs:`, `refactor:`, `chore:`). Neutral messages: describe *what* changed, no design discussion.
- The user merges PRs. Claude merges only when explicitly asked.
- Never commit secrets, keystores, local paths or design notes.
- Commit trailers and PR footer: as given by the session's attribution instructions.

## 10. Plan

| # | Step | Branch |
|---|------|--------|
| 0 | Private GitHub repo, initial commit with README on `main`, create `develop` | `main` |
| 1 | Gradle setup: version catalog, `build-logic`, module skeletons, `.gitignore`, ABI validation, release/R8/signing scaffolding, this file | `feature/project-setup` |
| 2 | SDK public API + KDoc + compiling stubs (`TODO()`), API dump. **User reviews KDoc before merge** | `feature/sdk-api` |
| 3 | SDK unit tests (red). **Kept local, not pushed** until step 9 | `feature/sdk-unit-tests` |
| 4 | SDK implementation — **separate session, see §11** | `feature/sdk-impl` |
| 5 | Demo: module, Hilt, MVI contract + skeleton, event catalog | `feature/demo-skeleton` |
| 6 | Demo UI (Compose) | `feature/demo-ui` |
| 7 | Wire UI → SDK via MVI: paging, seen tracking, config, debug time shift | `feature/demo-wiring` |
| 8 | Demo unit tests | `feature/demo-tests` |
| 9 | Reconciliation: push tests, merge with implementation, run, triage every failure (impl bug / test bug / spec gap) | `feature/sdk-reconcile` |
| 10 | Release hardening: signed release APK, README (decisions, KMP path, TODO), `AI_COMMUNICATION.md` | `release/1.0.0` |
| 11 | Merge to `main`, tag `v1.0.0` | `main` |

Steps 5–8 can run in parallel with step 4 (the demo depends only on the public API).

## 11. Implementation session protocol (step 4)

The SDK implementation is written in a **separate session, from requirements only**, so that tests and implementation are two independent readings of the same spec.

If you are the implementation session:
- Work in a fresh clone of `develop` from GitHub. Do not fetch, check out or inspect any other branch.
- Requirements = SDK public API + KDoc + the assignment PDF + this file. Nothing else.
- Do not look for, read or write unit tests for the SDK, and do not create test fixtures that guess the expected tests.
- Do not change public API or KDoc. If the spec is ambiguous or contradictory, choose the most reasonable reading, implement it, and record it in `docs/impl-notes.md` (question, chosen reading, where in code).
- Record your AI usage for `AI_COMMUNICATION.md` in `docs/ai-notes/impl-session.md`: tools/versions, key prompts, what worked, what was wrong and how it was fixed, time spent.
- Push `feature/sdk-impl` and open a PR into `develop`. Do not merge it.

## 12. AI_COMMUNICATION.md

Required by the assignment. Each session keeps raw notes as it works (tools and versions, key prompts per part, where AI helped, where AI was wrong, how output was verified, AI snippet vs final snippet, time saved vs time spent). Notes are merged into `AI_COMMUNICATION.md` at step 10. Be honest and specific.
