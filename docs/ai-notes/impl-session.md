# AI notes: SDK implementation session (step 4)

## Tools
- Claude Code (desktop app, Code tab), model Claude Opus 5.5 (`claude-opus-5-5`).
- Sub-agents (web fetch) to read the official Android documentation for Room 3, WorkManager and
  Preferences DataStore.

## Inputs
Only the requirements allowed by `CLAUDE.md` §11: the public API and KDoc of `sdk-core` and `sdk`,
`CLAUDE.md` and the assignment PDF. No tests were read or written.

## Key prompts
- Session brief: "You are the SDK implementation session (step 4) … implement every
  `TODO("Not implemented yet")` in `sdk-core` and `sdk` without changing the public API or KDoc;
  check library APIs against official documentation; record ambiguities in `docs/impl-notes.md`."
- Documentation look-ups: "Read the official Android documentation for Room 3 (`androidx.room3`) …
  report exact signatures for builders, `setDriver(AndroidSQLiteDriver())`, transactions,
  `InvalidationTracker.createFlow`, DELETE/UPDATE returning `Int`"; the same for
  `enqueueUniquePeriodicWork` / `ExistingPeriodicWorkPolicy` and `PreferenceDataStoreFactory`.

## Where AI helped
- Room 3 differences from Room 2 were confirmed from the release notes: DAO functions must be
  `suspend` (or reactive), `InvalidationTracker.Observer` is gone and replaced by
  `createFlow(vararg tables, emitInitialState)`, `@Transaction` works on open functions of an
  abstract DAO class.
- `ExistingPeriodicWorkPolicy.REPLACE` is deprecated; `KEEP` is the right policy for "no duplicate
  jobs, keep the existing schedule".
- Expedited one-time work would need `getForegroundInfo()` on API < 31; avoided.
- The limit cleanup SQL (`LIMIT -1 OFFSET :maxEvents` sub-select, keeping unseen rows) was checked
  with the `sqlite3` CLI on a small table before relying on it.

## Where AI was wrong or unsure, and the fix
- The first engine draft defined a private `emitAll` extension that duplicated
  `kotlinx.coroutines.flow.emitAll`; replaced by the library function.
- The documentation sub-agent could not confirm `PreferenceDataStoreFactory.create` and the package
  of `@Index` from the fetched pages; both were verified by compiling against the pinned library
  versions (DataStore 1.3.0, Room 3.0.3).
- The first `RoomEventStore.create` used the reflective `databaseBuilder<T>(context, name)`; the
  generated `AuraLoggerDatabase_Impl` turned out to be an `internal` class of the same module, so
  it is now passed as an explicit factory (no reflection, no R8 keep rule).

## Verification
- `./gradlew check assembleDebug assembleRelease` (includes `checkKotlinAbi` for `sdk-core`, so the
  public API is unchanged).
- Room schema exported to `sdk/schemas/` and inspected (AUTOINCREMENT sequence, unique id index,
  `(timestamp, sequence)` index).
- No unit or instrumented tests in this session by design (§11); behaviour is verified at the
  reconciliation step.

## Time
- About 1 hour of wall-clock time for reading requirements, documentation look-ups,
  implementation, build verification and notes.
