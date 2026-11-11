# SDK implementation notes

Ambiguities found while implementing the SDK from the public API, KDoc, the assignment and
`CLAUDE.md`, and the reading chosen for each. Every entry describes the final code, including the
review fixes and the reconciliation fixes for issues #4, #5 and #6. Paths are relative to the
repository root.

| # | Question | Chosen reading | Where |
|---|----------|----------------|-------|
| 1 | A `suspend` call with an invalid argument before initialisation: `IllegalArgumentException` or `IllegalStateException`? | Arguments are checked first, so `IllegalArgumentException` wins. | `EventTrackerEngine.getRecentEvents`, `getEventsPage` |
| 2 | "Reads see every write that was queued before the read started": how? | Every read first queues a no-op barrier on the serial write queue and waits for it, then reads on `readDispatcher`. Writes queued after the barrier may also be visible. | `EventTrackerEngine.awaitQueuedWrites` |
| 3 | Does `updateConfig` go through the serial queue? | Yes. This keeps it ordered after the config persisted by `start`, so an early `updateConfig` is never overwritten by the init config. `clearAll` uses the latest config's `onClearAll`. | `EventTrackerEngine.updateConfig` |
| 4 | `updateConfig`: the config was persisted but scheduling the one-time cleanup failed. | The call returns normally (the config is persisted, as the KDoc promises) and the scheduling error is logged. A failed write is thrown to the caller. | `EventTrackerEngine.updateConfig` |
| 5 | `EventPage.nextCursor` must be `null` when the page reached the oldest event, also when the page is exactly full. | The store is asked for `pageSize + 1` rows; the extra row only decides whether a next cursor exists. | `EventTrackerEngine.getEventsPage` |
| 6 | `getStats` consistency: total, today and per-day are three queries. | Not in one transaction; a concurrent cleanup can make them differ slightly. Accepted for a statistics snapshot. | `EventTrackerEngine.getStats` |
| 7 | Retention cut-off date arithmetic without `kotlinx-datetime` (not an allowed dependency of `sdk-core`). | `java.time` is used internally in `sdk-core` (it is a JVM module); no `java.*` type appears in public signatures. A KMP port would replace `DayCalendar` with `kotlinx-datetime`. An unknown time zone id falls back to the JVM default zone. | `sdk-core/.../internal/DayCalendar.kt` |
| 8 | Locking in `sdk-core` (KMP-ready). | JVM `synchronized` and `java.util.concurrent.atomic.AtomicBoolean` are used internally. Same KMP note as above. | `EventTrackerEngine`, `SerialWriteQueue` |
| 9 | One failing event in a batch insert. | Bursts of `track` are written in batches of up to 500 in one transaction. If a batch fails, its events are inserted one by one, so only the failing event is lost (and logged). | `SerialWriteQueue.insert` |
| 10 | Track call ordering with concurrent callers. | Id, timestamp, date and enqueueing happen under one short lock, so the insertion sequence follows the order in which calls took the lock; ties of equal timestamps are broken by that sequence. Validation and JSON encoding run outside the lock. | `EventTrackerEngine.track` |
| 11 | The properties map can be mutated by the caller after `track` returns. | A snapshot (`toMap()`) is taken at call time. | `EventTrackerEngine.track` |
| 12 | `markSeen` with an empty collection or duplicate ids. | Empty: no-op. Duplicates are removed. Ids are updated in chunks of 500 per statement in one transaction (SQLite variable limit). | `EventTrackerEngine.markSeen`, `EventDao.markSeen` |
| 13 | Stored properties JSON that cannot be parsed (corrupted row). | The event is returned with empty properties and a warning is logged. | `EventTrackerEngine.decodeProperties` |
| 14 | `changes` must "never complete and not fail". | Store errors are logged and the observation is retried after 1 s; if the store flow ever completes, the flow suspends forever. The Room flow is created with `emitInitialState = false`, so there is no initial emission. | `EventTrackerEngine.changes`, `RoomEventStore.observeChanges` |
| 15 | Limit with `onLimit` protection: which events are "above" the limit? | As clarified by the KDoc (#4): the excess is `count - maxEvents`, and that many events are deleted oldest first (`timestamp ASC, sequence ASC`). With `keepUnseen`, only seen events are candidates, wherever they are in the list (also newer than unseen ones); fewer are deleted if not enough seen events exist. Count and delete run in one transaction. The same store call is used by the cleanup and by the limit on write (#31). The cleanup logs a warning when the limit cannot be reached. | `EventDao.deleteOldestAbove`, `EventCleaner.cleanup` |
| 16 | Persisted config written by another version, or partially written. | A schema version key is stored; a different version, a missing key or a value rejected by `EventTrackerConfig` makes `read()` return `null` (the worker then uses defaults). I/O errors are thrown. | `DataStoreConfigStore` |
| 17 | WorkManager cannot be obtained (not initialised because the app disabled the default initializer without a configuration, or any other failure of `WorkManager.getInstance`). | `init` still starts the tracker with a scheduler that only logs errors, so tracking keeps working without background cleanup. | `AuraLoggerRuntime.cleanupScheduler` |
| 18 | Cleanup worker failure policy. | `Result.retry()` on any failure, as stated in the KDoc (WorkManager backoff applies). | `CleanupWorker.doWork` |
| 19 | Is the one-time cleanup expedited? | No. Expedited work needs `getForegroundInfo()` (a notification) on API < 31; a plain one-time request runs soon enough. | `WorkManagerCleanupScheduler.scheduleOneTime` |
| 20 | R8 and reflection. | The Room database is built with an explicit factory (`AuraLoggerDatabase_Impl()`), so Room needs no reflection. The worker class gets an explicit keep rule because WorkManager persists its class name. See "Production risk review". | `RoomEventStore.create`, `sdk/consumer-rules.pro` |
| 21 | Second `init` call. | `EventTrackerSDK.init` checks `isStarted` under its own lock and logs a warning; the clock and stores of the first call stay in use. | `EventTrackerSDK.init` |
| 22 | Clock of the cleanup worker. | The worker uses the clock passed to `init` in this process (kept in `AuraLoggerRuntime.clock`), the system clock otherwise. | `CleanupWorker.doWork` |
| 23 | The write queue is cancelled while an insert batch is being written, possibly after part of it was written one by one. | Only the unwritten events are reported lost: the queue counts the events of the in-flight batch that were neither written, nor logged as failed, nor dropped by the limit policy. A whole-batch insert that is cancelled is rolled back, so all its events count. | `SerialWriteQueue.consume`, `SerialWriteQueue.abandonInFlight` |
| 24 | The KDoc says "a missing, incomplete or invalid config returns `null`". Does that cover a corrupted file, and other I/O errors? | A corrupted file counts as an invalid config. The process-wide DataStore replaces it with empty preferences (and logs an error); `read()` also maps `CorruptionException` to `null` for stores built without that handler. Other `IOException`s are still thrown. A transient read error then makes the cleanup worker retry, instead of deleting events with the default policy while the real config may be stricter. | `AuraLoggerRuntime.dataStore`, `DataStoreConfigStore.read` |
| 25 | What does "the queue is stopped" mean when a `CancellationException` reaches the queue? | Only cancellation of the queue's own scope (`currentCoroutineContext().isActive == false`). A `CancellationException` thrown by a store call while the scope is active (for example a `withTimeout` inside it) is an ordinary failure of that operation and the queue keeps running. For a task with an awaiting caller, it is wrapped in `IllegalStateException("Failed to …")`, so that the caller does not mistake it for its own cancellation. For an insert, it is handled like any other insert failure. | `WriteOp.Task.run`, `SerialWriteQueue.insert` |
| 26 | The consumer stops because of a non-cancellation `Throwable` (for example a logger that breaks the "must not throw" contract). | Operations already taken out of the channel are abandoned in a `finally` block, and each cleanup step is guarded so that one failing log call does not skip the others. The exception still propagates to the scope; later submissions are then abandoned through the cancelled channel. The queue is not restarted. | `SerialWriteQueue.consume` |
| 27 | `DROP_NEW_EVENTS`: the KDoc describes a per-event loop (store if it fits, else delete the oldest seen event, else drop). | Implemented as an equivalent batch step. If the batch does not fit, `deleteOldestAbove(max(0, maxEvents - batchSize), keepUnseen = true)` frees up to the needed number of oldest seen events; then the earliest events that fit are stored and the rest are dropped with one warning. Same result, with a constant number of queries per batch. The equivalence holds because no event can be marked seen in the middle of a batch. | `LimitEnforcingInsertPolicy.writeDroppingOverflow` |
| 28 | What does the public KDoc guarantee about new events and the limit? | Since the KDoc fix after review (exception to §11 authorised by the user): with `onLimit` (default) a new event is unseen and never deleted by the limit; with `onLimit = false` the newest `maxEvents` events are kept, so a new event is deleted right after its write if at least `maxEvents` stored events are newer (for example, buffered pre-init events). The internal settings (`enforceLimitOnWrite = false`, `DROP_NEW_EVENTS`) are described in the `AuraLoggerSettings` KDoc, which states that the public guarantees assume their defaults. The earlier wording "never rejected because of the limit" was too strong and was removed. | `EventTracker.track`, `EventTrackerConfig`, `AuraLoggerSettings` (KDoc) |
| 29 | A storage error while applying the limit on write. | Never causes data loss: if the room for `DROP_NEW_EVENTS` cannot be computed, the whole batch is stored; a failing `deleteOldestAbove` after the insert is logged and the queue continues. The next write or cleanup applies the limit again. | `LimitEnforcingInsertPolicy` |
| 30 | `init` with a context whose `applicationContext` is `null` (#6). | The passed context is used as the fallback everywhere. The config file is resolved from that context as `File(filesDir, "datastore/<configFileName>.preferences_pb")`, the same path that `preferencesDataStoreFile` uses (checked in the AndroidX source), so existing installs keep their config. A failure of `WorkManager.getInstance` disables background cleanup (#17). The cleanup worker uses the same process-wide DataStore, or the same path in a new process. | `AuraLoggerRuntime.configFile`, `AuraLoggerRuntime.cleanupScheduler` |
| 31 | How is the limit applied right after every write (#5)? | The write queue takes an insert policy. With `enforceLimitOnWrite` (default), `LimitEnforcingInsertPolicy` writes the batch and then calls `deleteOldestAbove(maxEvents, keepUnseen = onLimit)` with the latest config, inside the same queue step, so no later queued operation or read runs in between. This covers the buffered events written on `start`. With `enforceLimitOnWrite = false`, the plain policy only inserts. | `LimitEnforcingInsertPolicy`, `EventTrackerEngine.start` |

## Known limitations and TODO

- No unit, instrumented or UI tests were written in this session (by design); behaviour is covered only by the reconciliation tests written elsewhere.
- The SDK was never run on a device or emulator from this session; WorkManager scheduling, StrictMode, the minified runtime and the cancellation paths are reasoned about only.
- `DROP_NEW_EVENTS` (non-default) has no dedicated test known to this session; its batch equivalence (#27) is argued, not proven.
- Multi-process host apps are not supported: see "Processes" in the production risk review (defect).
- `init` may touch the disk on the calling thread through `Room.databaseBuilder(...).build()` (unverified); see "Main thread" below.
- Room schema version 1 has no migration strategy; the first schema change needs a `Migration` (or an explicit destructive fallback), otherwise opening the database fails.
- Renaming or moving `CleanupWorker` breaks work already scheduled on existing installs (WorkManager persists the class name); it needs a compatibility shim.
- The periodic cleanup uses `ExistingPeriodicWorkPolicy.KEEP`, so a change of period or initial delay in `AuraLoggerSettings` never reaches existing installs (would need `UPDATE`).
- The write queue is not restarted after an unexpected error stops its consumer (#26); later writes are abandoned with an error log until the process restarts.
- `getStats` is three queries without a transaction (#6); numbers can differ slightly during a concurrent cleanup.
- Events still queued when the process dies are lost (as specified); there is no flush on `onTrimMemory` or similar.
- Events tracked before `init` always use the system clock, also when `init` passes another clock (as specified).
- `sdk-core` uses `java.time`, `synchronized` and `AtomicBoolean` internally (#7, #8); a Kotlin Multiplatform port must replace them.
- Logs contain event names (not property values); a host that puts personal data into event names leaks it to Logcat. There is no log-level switch.
- No public ABI check for the `sdk` module (AGP 9 limitation, see `CLAUDE.md` §4).

## Production risk review

Written review only; nothing was run on a device.

**R8 / consumer rules — OK at build time, not verified at runtime.**
- `CleanupWorker`: kept by name with its `(Context, WorkerParameters)` constructor through
  `sdk/consumer-rules.pro`; WorkManager also ships its own consumer rules.
- Room: the database implementation is created by an explicit factory, not by reflection.
- Other code: the SDK uses no reflection or class-name lookup of its own. kotlinx-serialization is
  used only through `JsonObject`/`JsonPrimitive`, with no generated serializers.
- Evidence: the minified demo release build (`demo/build/outputs/mapping/release/mapping.txt`)
  keeps `com.usatov.aura.logger.internal.cleanup.CleanupWorker` and
  `com.usatov.aura.logger.internal.store.AuraLoggerDatabase_Impl` under their original names.
- Not verified: the minified app was never run, and on this branch the demo does not call the
  SDK yet.

**Main thread — mostly no I/O, one unverified point.**
- No I/O on the calling thread: `EventTrackerSDK.init` only builds objects and calls
  `engine.start`. Persisting the config, scheduling the periodic work and writing buffered events
  are queued on the background write queue. The DataStore file is resolved lazily in
  `produceFile` on DataStore's IO scope, and `WorkManager.getInstance` does no I/O when WorkManager
  was already initialised by App Startup (the default).
- Not verified: whether Room 3's `RoomDatabase.Builder.build()` touches the file system on the
  calling thread. The Android `build()` was not read; if it resolves the database path through
  `Context.getDatabasePath`, the platform may create the databases directory, which StrictMode
  would report as disk I/O.
- `track` calls `UUID.randomUUID()` and `TimeZone.getDefault()` on the caller thread. Neither is
  expected to be flagged, but this was not checked under StrictMode.

**Processes — defect: multi-process host apps are not supported.**
- Single-process apps (WorkManager's default): the worker runs in the main process and shares the
  process-wide Room database and DataStore with the SDK.
- Worker in another process: if a host runs WorkManager in a separate process (for example with
  `Configuration.setDefaultProcessName`), that process creates its own Room database instance and
  its own single-process DataStore for the same config file. This causes three problems:
  - The main process's `changes` flow is not notified of the deletions. This is documented ("not
    reported"), but a visible list goes stale until the next local change.
  - The worker uses the system clock instead of the clock passed to `init`.
  - The DataStore documentation forbids a single-process DataStore for one file used from several
    processes.
- `init` in every process: a host that calls `init` from `Application.onCreate` runs it in every
  process. Each process then writes the config through its own single-process DataStore, which
  can lose writes or corrupt the file (the corruption handler would reset it to defaults). Room
  itself is safe across processes through SQLite locking, but it has no multi-instance
  invalidation.
- Not fixed here (documentation-only round). Possible fixes:
  - move the config to `MultiProcessDataStoreFactory`;
  - enable Room's `enableMultiInstanceInvalidation()`;
  - initialise only in the main process;
  - at minimum, document that `init` must be called only in the main process.
