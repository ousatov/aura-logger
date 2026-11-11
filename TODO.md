# TODO

Open items of the Aura Logger SDK and demo, with the reason for each. Detailed implementation
decisions are in [`docs/impl-notes.md`](docs/impl-notes.md). Everything required by the assignment is
implemented; the items below are known limitations, verifications not yet done on a device, and next steps.

## 1. Planned: multi-process support

The SDK must support host apps with several processes. Today it supports only single-process
apps; the fix is postponed, not dropped.

**What goes wrong today**
- `EventTrackerSDK.init` is usually called from `Application.onCreate`, which runs in every
  process of the app. Each process then opens its own Room database connection and its own
  Preferences DataStore on the same files.
- Preferences DataStore created with the single-process factory must not be used from several
  processes on one file (DataStore documentation). Concurrent config writes can be lost, or the
  file can be corrupted; a corrupted file is reset, so the persisted config falls back to defaults.
- Room invalidation is per process: events written or deleted in one process (including deletions
  by the cleanup worker, if WorkManager runs it in another process) are not reported by
  `EventTrackerSDK.changes` in the other processes.
- The cleanup worker uses the clock passed to `init` only if `init` ran in the worker's process;
  in another process it uses the system clock.
- The serial write queue orders writes within one process only; ordering guarantees in the KDoc
  ("writes are executed in the order in which they were called", read-after-write) do not hold
  across processes.

**Planned fix**
- Config: `MultiProcessDataStoreFactory` (DataStore multi-process support) instead of
  `PreferenceDataStoreFactory`.
- Events: Room `enableMultiInstanceInvalidation()` so that `changes` reports writes from other
  processes; review the write queue and the limit-on-write logic for concurrent writers
  (transactions instead of read-then-delete sequences).
- Cleanup: decide in which process the worker runs (WorkManager `Configuration.setDefaultProcessName`)
  and make its clock source explicit.
- KDoc: update the threading and ordering guarantees of `EventTracker` for multiple processes.
- Tests: Robolectric cannot run several processes; add instrumented tests with a second process
  (for example a service declared with `android:process`).

**Until then:** call `EventTrackerSDK.init` in the main process only. The demo app is single-process.

## 2. Verify on a device or emulator after all implementations

These points are reasoned about or covered by Robolectric only; check them on a real Android
runtime with the release build of the demo before the release.

1. **StrictMode during `init`** (`detectDiskReads`, `detectDiskWrites`): Room 3 `Builder.build()`
   may touch the databases directory on the calling thread (unconfirmed).
2. **Minified release build:** track events, kill the app, reopen, trigger the cleanup worker
   (for example with `adb shell cmd jobscheduler run` or the WorkManager test driver). Confirms
   that R8 keeps `CleanupWorker` and the Room database implementation.
3. **Context without application context** (fixed in #6): `init` with a context whose
   `applicationContext` is `null`, on a real runtime.
4. **`LimitOverflowStrategy.DROP_NEW_EVENTS`:** non-default; covered by unit tests with a fake
   store only. Check against Room if it is ever enabled.
5. **WorkManager schedule:** the periodic cleanup is enqueued once (24 h, first run after 1 h) and
   is not duplicated by repeated app starts.
6. **Events tab on a real device:** smooth fast scrolling (#33, measured in the release build), rows switch to
   "seen" right after they are displayed (#32), every row passed during a fling is marked seen (#24).
7. **Settings switches:** "Track navigation events" and "Clear All also deletes unseen events" are saved when
   leaving the Settings tab and survive a process restart.
8. **Debug time shift:** shifting by days changes "today", the day headers and the statistics; a cleanup after a
   shift of more than the retention period deletes old seen events.

## 3. Known limitations

- **Room migrations:** schema version 1 has no migration strategy; the first schema change needs
  a `Migration` (or an explicit destructive fallback), otherwise opening the database fails.
- **Cleanup period on existing installs:** the periodic work uses `ExistingPeriodicWorkPolicy.KEEP`,
  so changing `cleanupPeriodHours` / `cleanupInitialDelayHours` never reaches existing installs
  (would need `UPDATE`).
- **Worker class name:** renaming or moving `CleanupWorker` breaks work already scheduled on
  existing installs (WorkManager persists the class name); it would need a compatibility shim.
- **Write queue restart:** after an unexpected error stops the queue consumer, later writes are
  abandoned with an error log until the process restarts.
- **Statistics snapshot:** `getStats` runs three queries without a transaction; the numbers can
  differ slightly while a cleanup runs.
- **Process death:** events still queued when the process dies are lost (as specified); there is
  no flush on `onTrimMemory`.
- **Logcat:** warnings contain event names (never property values).
- **KMP:** `sdk-core` uses `java.time` and JVM `synchronized` / atomics internally; a
  Kotlin Multiplatform port must replace them (for example with kotlinx-datetime and atomicfu).
- **Limit settings are internal:** `AuraLoggerSettings.enforceLimitOnWrite` and
  `limitOverflowStrategy` can be moved to the public `EventTrackerConfig` if SDK users should
  choose the trade-off themselves.

## 4. Build and tooling

- **ABI validation of `sdk`:** with AGP 9 built-in Kotlin, the ABI validation of the Kotlin Gradle
  plugin fails for Android library modules and binary-compatibility-validator registers no tasks;
  only `sdk-core` is checked. Re-enable for `sdk` when supported.
- **KSP deprecation warning:** KSP 2.3.12 uses a Gradle API deprecated for Gradle 10
  (`Configuration.setVisible`); update KSP when a fixed release is available (2.3.13 has a known
  regression).
- **`./gradlew tasks --all`** fails on the AGP Compose Preview task of the release variant;
  regular builds are not affected.
- **Coverage gaps:** a few write-queue branches depend on thread interleaving and one branch is
  compiler-generated; they are not deterministically testable.

## 5. Demo app

- **No UI tests:** the demo has 112 unit tests (ViewModels, paging, formatting, storage) but no Compose UI,
  instrumented or screenshot tests; UI behaviour (scrolling, seen reporting from the visible rows, snackbars) was
  checked manually only.
- **No coverage report for the demo:** Kover is configured for the SDK modules only.
- **App icon:** the demo uses the default launcher icon.
- **Seen reporting is time based:** a row counts as seen as soon as it is laid out on screen; there is no minimum
  visible time or visible fraction.
- **Custom events from the UI:** discussed and deliberately not implemented; the catalog of app events is fixed in
  code (`AppEvents`).
- **Limit protection switch:** Settings exposes only the Clear All protection; retention and limit protection stay
  on, as the assignment requires.

## 6. Engineering process

- **No CI:** builds, lint and tests run locally; a GitHub Actions workflow (`./gradlew check assembleRelease`) is the
  next step. The per-commit AI review hook is local and not part of the repository.
- **Release signing is local:** the keystore lives outside the repository; `keystore.properties` (or `AURA_*`
  environment variables) is needed to build a signed release.
- **Process deviation:** two demo unit tests were written by feature ticket sessions with the user's approval
  (`ConfigViewModelSaveTest`, `EventsViewModelSessionTest`), although the ticket rules leave tests to the
  independent test session.
