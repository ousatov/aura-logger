# AI notes: SDK implementation session (step 4)

Raw notes for `AI_COMMUNICATION.md`. They cover the whole implementation session: the initial
implementation, two review rounds on the write queue, and the fixes for issues #4, #5 and #6 found
at reconciliation.

## Tools and versions
- Claude Code in the Claude desktop app (Code tab), model Claude Opus 5.5 (`claude-opus-5-5`).
  The whole session was one Claude Code session that the planning session had started; the human
  steered it through review findings and issues.
- Claude Code sub-agents of type `web-fetch`, used only to read official documentation and source:
  developer.android.com (Room 3 release notes and reference, WorkManager guides and reference,
  DataStore guide and reference) and the AndroidX source on GitHub (`DataStoreFile.android.kt`,
  `PreferenceDataStoreFile.android.kt`, `room3-runtime`, `WorkManager.kt`).
- Local tools: Gradle (`./gradlew check assembleDebug assembleRelease`), the `sqlite3` CLI (to try
  SQL on small tables), `git`, `gh`.
- Library versions pinned by the project: Room 3.0.3, WorkManager 2.12.0, DataStore 1.3.0,
  kotlinx-coroutines 1.11.0, kotlinx-serialization 1.12.0, Kotlin 2.4.21, AGP 9.5.1.

Inputs were only the ones `CLAUDE.md` §11 allows: the public API and KDoc, `CLAUDE.md`, the
assignment PDF, and later the review findings and GitHub issues. No SDK tests were read or written.

## Key prompts per part (summarised)
1. **Initial implementation brief:** "You are the SDK implementation session (step 4). Follow
   `CLAUDE.md` §11 strictly. Implement every `TODO("Not implemented yet")` in `sdk-core` and `sdk`
   without changing the public API or KDoc. Do not write tests. Check Room 3, WorkManager and
   DataStore APIs against official documentation. Record ambiguities in `docs/impl-notes.md`.
   Open a PR into `develop` and do not merge it."
2. **Documentation look-ups (my prompts to sub-agents):** "Read the official Room 3 docs and
   report exact signatures: builders, `setDriver(AndroidSQLiteDriver())`, transactions,
   `InvalidationTracker.createFlow`, DELETE returning `Int`"; the same for unique periodic work
   policies and `PreferenceDataStoreFactory`; later "show where DataStore, Room 3 and WorkManager
   call `getApplicationContext()` in the AndroidX source".
3. **Review round 1:** three findings where the code did not match its own KDoc: the write queue
   lost operations on cancellation; awaiting callers got a `CancellationException` instead of
   `IllegalStateException`; a corrupted config file made `read()` throw.
4. **Review round 2:** four low-severity gaps in the round 1 fixes: cleanup only on cancellation;
   every `CancellationException` treated as "queue stopped"; silent stop of a task without an
   awaiting caller; a stopped batch over-reporting lost events.
5. **Reconciliation:** merge the clarified specification branch, then fix issue #4
   (`deleteOldestAbove` does not reach a reachable limit), #5 (apply the limit right after every
   write, with two new internal settings) and #6 (`init` with a context whose
   `applicationContext` is `null`).
6. **Documentation:** this file, known limitations and a production risk review.

## Where AI helped
- **Room 3 migration details** (new library, `androidx.room3`): confirmed from the release notes
  that DAO functions must be `suspend` or reactive, that `InvalidationTracker.Observer` was removed
  in favour of `createFlow(vararg tables, emitInitialState)`, and that `@Transaction` works on
  open functions of an abstract DAO. All of this compiled the first time.
- **No reflection for Room:** I noticed that the generated `AuraLoggerDatabase_Impl` is an
  `internal` class of the same module and passed it as an explicit factory. This removes Room's
  reflective lookup, so R8 needs no keep rule for it.
- **WorkManager policies:** `ExistingPeriodicWorkPolicy.REPLACE` is deprecated; `KEEP` matches
  "no duplicate jobs, keep the existing schedule". Expedited one-time work was avoided because it
  needs `getForegroundInfo()` on API < 31.
- **Issue #6 root cause:** the AndroidX source showed that `preferencesDataStoreFile(name)`
  delegates to `dataStoreFile`, which calls `applicationContext.filesDir` again. The fix rebuilds
  the same path from the context in use, so existing installs keep their config.
- **Issue #4 SQL:** both examples from the issue were run with the `sqlite3` CLI before the DAO was
  changed, and both matched. That also surfaced the SQLite rule that a negative `LIMIT` means "no
  limit", so the excess is guarded in code.
- **`DROP_NEW_EVENTS` (#5):** the per-event rule of the KDoc was turned into an equivalent batch
  step with a constant number of queries per batch (`docs/impl-notes.md` #27).

## Where AI was wrong or had to iterate
- **Limit rule (fixed by #4):** I read "the oldest events above `maxEvents`, unseen kept but
  counted" as a window: delete the seen events ranked after position `maxEvents`. With seen events
  newer than unseen ones, the limit was not reached although it was reachable. Worse, my `sqlite3`
  check of that query only confirmed that the SQL matched my own reading; it did not check the
  reading itself. The clarified KDoc and the reconciliation tests caught it.
- **Cancellation in the write queue (two review rounds, 7 findings):**
  - **Ownership:** the first draft relied on `onUndeliveredElement` for every operation and missed
    that `receive()`/`tryReceive()` hand ownership to the consumer. On cancellation an in-flight
    batch was lost without a log line, and a held-back task could leave `clearAll` hanging.
  - **First fix too narrow:** round 1 fixed only the `CancellationException` path; round 2 showed
    that other `Throwable`s skipped the cleanup.
  - **What counts as "stopped":** treating every `CancellationException` as "queue stopped" meant
    a timeout inside a store call would have stopped the queue for good.
  - **Wrong exception type:** awaiting callers received the queue's `CancellationException`, which
    looks like their own cancellation.
  - **Over-reporting:** lost events were over-reported after a partial one-by-one fallback.
- **Corrupted config file:** `read()` let `CorruptionException` escape, so the cleanup worker
  would have failed on every run instead of using the defaults.
- **Context fallback (#6):** `applicationContext ?: context` looked correct, but a library call
  bypassed it internally. I had not checked the library source until the issue pointed at it.
- **Small slips:** the first engine draft defined a private `emitAll` that duplicated
  `kotlinx.coroutines.flow.emitAll`. The first look-up could not confirm `PreferenceDataStoreFactory`
  or the package of `@Index`; compiling against the pinned versions settled both.
- **Wrong time estimate:** my first notes said the initial implementation took "about 1 hour". Git
  timestamps show about 15 minutes; the estimate was not checked when I wrote it.

## How the output was verified
- `./gradlew check assembleDebug assembleRelease` after every change. It includes lint and
  `checkKotlinAbi` for `sdk-core`, so the public API stayed unchanged except for the specification
  merged from `feature/sdk-limit-spec`.
- Room schema exported to `sdk/schemas/` and inspected: AUTOINCREMENT sequence, unique id index,
  `(timestamp, sequence)` index.
- SQL for the limit was tried with the `sqlite3` CLI on small tables (both #4 examples).
- The R8 mapping of the minified demo release build was inspected: `CleanupWorker` and
  `AuraLoggerDatabase_Impl` keep their names.
- Library behaviour was checked against developer.android.com and the AndroidX source, not memory.
- **Not done:** I ran no unit, instrumented or UI tests (by design, §11) and never ran the SDK on a
  device or emulator. Runtime behaviour (WorkManager scheduling, StrictMode, minified runtime,
  cancellation paths, `DROP_NEW_EVENTS`) was only reasoned about here. The reconciliation tests,
  written by another session, are the actual behavioural check, and they found #4–#6.

## AI version vs final version

**1. Limit query (`EventDao.deleteOldestAbove`).** The window reading did not reach a reachable
limit (#4).
```kotlin
// AI version
@Query("""DELETE FROM events WHERE (:keepUnseen = 0 OR seen = 1) AND sequence IN (
    SELECT sequence FROM events ORDER BY timestamp DESC, sequence DESC LIMIT -1 OFFSET :maxEvents)""")
abstract suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int

// Final version
@Transaction
open suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int {
    val excess = count() - maxEvents
    if (excess <= 0) return 0 // a negative LIMIT means "no limit" in SQLite
    return deleteOldest(excess, keepUnseen) // oldest (seen) events, wherever they are
}
```

**2. Queue consumer cleanup (`SerialWriteQueue.consume`).** Operations taken out of the channel
were lost on cancellation, and then on any other `Throwable`.
```kotlin
// AI version
private suspend fun consume() {
    var pending: WriteOp? = null
    while (true) { val op = pending ?: channel.receive(); /* … */ insert(batch) }
}

// Final version
try {
    while (true) { /* … */ inFlight += op; /* … */ writeBatch(batch); inFlight.clear() }
} finally {
    abandonInFlight(inFlight)                 // logs only the unwritten events, fails awaiters
    pending?.let { safely { it.abandon(logger) } }
}
```

**3. Cancellation inside a task (`WriteOp.Task.run`).** Every cancellation stopped the queue, and
the awaiting caller saw a `CancellationException`.
```kotlin
// AI version
} catch (e: CancellationException) {
    result?.completeExceptionally(e)
    throw e
}

// Final version
} catch (e: CancellationException) {
    if (!currentCoroutineContext().isActive) { // the queue's own scope was cancelled
        val error = stoppedError(e)
        if (result != null) result.completeExceptionally(error) else logger.error(error.message.orEmpty(), e)
        throw e
    }
    fail(IllegalStateException("Failed to $description", e), logger) // e.g. a timeout: queue keeps running
}
```

**4. Config file path (`AuraLoggerRuntime`).** The library helper called `getApplicationContext()`
again and failed with the fallback context (#6).
```kotlin
// AI version
produceFile = { appContext.preferencesDataStoreFile(settings.configFileName) }

// Final version
produceFile = { File(appContext.filesDir, "datastore/${settings.configFileName}.preferences_pb") }
```

## Time spent per phase
Taken from the branch reflog and commit timestamps; waiting time between rounds is not counted.

| Phase | Wall-clock | Notes |
|-------|-----------|-------|
| Initial implementation | ~15 min (15:01–15:15) | Requirements, doc look-ups, `sdk-core` and `sdk` code, notes, full build, PR |
| Review round 1 (3 findings) | ~10 min (until 15:26) | Fixes, notes, build, push |
| Review round 2 (4 findings) | ~10 min (until 15:40) | One commit, notes, build, push |
| Reconciliation #4, #5, #6 | ~10–15 min (17:15–17:27) | Spec merge, source look-up, three fixes, notes, build, push |
| Documentation (this round) | ~15 min (estimate) | This file, limitations, risk review, PR description |

About 1 hour of model time in total. The human time for writing the specification, the two
review rounds and the reconciliation tests is not included and is probably larger.

**Time saved vs time spent:** writing the Room 3 / WorkManager / DataStore wiring, the engine and
the notes by hand would take a developer most of a day, so the raw speed-up is large. The cost
appeared later: 7 review findings in the write queue and 3 reconciliation issues, one of them a
misreading of the specification. A plausible but wrong reading cost more than any compile error.
My own verification (build, SQL against my own reading) could not catch it; the separately written
tests and the review did. My view: AI saved most of the implementation time, but only because the
process included an independent review and independent tests. Without them, the first version
would have shipped with a broken limit rule and fragile cancellation handling.
