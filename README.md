# Aura Logger

An **Event Tracker SDK** (Android library) and a **demo app** that uses it.
The SDK tracks app events, stores them in Room, gives statistics and a paged history, and cleans up old data
in the background with WorkManager. Events the user has never seen are protected from deletion.

- APKs: see the GitHub release `v1.0.0` (`demo-release.apk` signed, `demo-debug.apk` with the time-shift tool).
- AI usage: [AI_COMMUNICATION.md](AI_COMMUNICATION.md) · Open items: [TODO.md](TODO.md) ·
  Implementation decisions: [docs/impl-notes.md](docs/impl-notes.md)

## Assignment checklist

| Requirement | Where |
|---|---|
| Kotlin, OOP/SOLID, SDK as a separate Android library, minSdk 26, targetSdk 36 | `sdk-core` (pure Kotlin), `sdk` (Android library), `demo` (app) |
| Room for events, WorkManager for cleanup | `sdk/.../internal/store` (Room 3), `sdk/.../internal/cleanup` |
| `init(context, …)` with retention (default 7 days), repeated init ignored, config persisted | `EventTrackerSDK.init`, DataStore config store |
| Periodic cleanup every 24 h, first run after 1 h, unique work | `WorkManagerCleanupScheduler` (unique periodic work, `KEEP`) |
| `track(name, properties)`: thread-safe, non-blocking, generated id + timestamp, 100+ events without loss | `EventTrackerEngine` + serial write queue (tested with 8 threads × 250 events) |
| Recent events (limit, newest first), statistics (total, today, per day), clear all — off the main thread | `EventTracker` suspend API; demo Dashboard and Events tabs |
| Cleanup by retention period **or** event limit | `EventCleaner` + limit applied right after every write |
| Schema: id, name, properties JSON, Unix ms timestamp, `DD/MM/YYYY` date | `EventEntity`, exported schema `sdk/schemas/` |
| Demo initialises the SDK on every process start with the configured retention and limit | `DemoApplication` → `SdkStarter` |
| Statistics auto-refresh every 3 s: total, today, per day for the last 7 days, "No events tracked" | Dashboard → Statistics |
| Configurable retention (default 7) and max events (default 100) | Settings |
| Buttons: Track Event (`test_event_123456`), Track 100 Events, Track with Properties, Clear All Events | Dashboard → Actions |
| Event list: newest first, `DD/MM/YYYY HH:MM:SS`, name, properties, updates on new events | Events tab (paged, grouped by day) |
| App events defined by the app ("section selection", "process wake up", …) | `AppEvents` catalog: `process_wake_up`, `section_selected`, `config_changed`, `events_cleared`, … |
| An event the user never saw cannot be cleared | Seen state reported from the Events list; `UnseenEventsProtection`; "New" badge on unseen rows |

## Using the SDK

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        EventTrackerSDK.init(this, EventTrackerConfig(retentionDays = 7, maxEvents = 100))
    }
}

EventTrackerSDK.track("button_clicked", mapOf("button_id" to "submit"))
EventTrackerSDK.markSeen(listOf(eventId))             // the event was displayed to the user
val stats = EventTrackerSDK.getStats()                 // suspend
val page = EventTrackerSDK.getEventsPage(cursor = null, pageSize = 30)
EventTrackerSDK.changes.collect { /* reload */ }
EventTrackerSDK.updateConfig(EventTrackerConfig(retentionDays = 3, maxEvents = 500))
```

Every public declaration has KDoc covering behaviour, threading, errors and edge cases
(`sdk-core/src/main/kotlin/com/usatov/aura/logger/core/EventTracker.kt` is the entry point).

## Architecture

```
build-logic/  convention plugins (aura.jvm.library, aura.android.library, aura.android.application,
              aura.compose, aura.hilt, aura.room, aura.unit-test, aura.kover)
sdk-core/     pure Kotlin/JVM: public API and models, tracking engine, write queue, cleanup policy, ports
sdk/          Android library: EventTrackerSDK facade, Room store, DataStore config, WorkManager cleanup
demo/         Compose + Material 3, hand-rolled MVI, Hilt; Dashboard / Events / Settings tabs
```

- **SDK:** a facade object implements the `EventTracker` interface and delegates to the platform-independent
  `EventTrackerEngine`. Storage, config, scheduling, clock, ids and logging are ports implemented by the `sdk`
  module (manual composition root, no DI framework inside the library). The public API exposes only Kotlin stdlib
  and kotlinx-coroutines types.
- **Demo:** one `Contract` (`State` / `Intent` / `Effect`) and one `MviViewModel` per screen; the UI only renders
  state and sends intents. Paging 3 is built in the app on top of the SDK cursor API. Debug builds include a
  time-shift clock to see day grouping, "today" and retention without waiting.

## Key decisions

| Decision | Why |
|---|---|
| `init` first call wins + separate `updateConfig` | The assignment requires ignoring repeated `init`, but the demo must change retention and limit at runtime |
| Bounded pre-init buffer (500, drop oldest) | Events tracked before `init` are kept instead of being lost; the SDK never crashes the host |
| Id and timestamp generated on the caller thread; one serial write queue; `clearAll` on the same queue | Keeps call order, never blocks the caller, no loss under rapid tracking; queued events cannot "come back" after a clear |
| Sequence number as a tie-breaker | Equal millisecond timestamps are common under stress; UUIDs carry no order |
| Retention in calendar days in the device time zone | Matches how users read "last 7 days" and the per-day grouping; correct across DST days |
| Day groups ordered by timestamp, not by the `DD/MM/YYYY` string | String order breaks across months and years |
| `UnseenEventsProtection { onRetention, onLimit, onClearAll }`, all on by default | Strict reading of "not eligible for being cleared if the user never saw it", configurable without code changes |
| Limit applied right after every write; new events are never rejected; only seen events are deleted, oldest first, until the limit is reached | The assignment requires "no data loss" for rapid tracking and protects unseen events; with only unseen events the limit is therefore soft. The trade-off is an internal setting (`AuraLoggerSettings`) |
| `Map<String, String>` properties + typed `PropertiesCodec<T>` | `Map<String, Any?>` does not tell callers what is allowed; `KSerializer` would leak kotlinx.serialization into the public API |
| Cursor paging in the SDK, Paging 3 in the app | Keeps Paging out of the SDK's API and dependencies |
| Cleanup worker builds its own dependencies from persisted config | WorkManager can start the process without the app calling `init` |
| Latest stable libraries (Kotlin 2.4.21, AGP 9.5.1, Room 3.0.3, WorkManager 2.12, DataStore 1.3), versions checked in Maven metadata | Project rule; see `gradle/libs.versions.toml` |

## KMP path

`sdk-core` has no Android imports and no `java.*` types in public signatures, so it can become a Kotlin
Multiplatform module: replace `java.time` with kotlinx-datetime, `synchronized`/atomics with atomicfu, and move the
Room 3 database (already KMP-capable with a bundled SQLite driver) behind the existing `EventStore` port. Android-only
parts (WorkManager, Context) stay in `sdk`; an iOS target would add its own scheduler adapter.

## Build

Requirements: JDK 21 (bytecode targets Java 17), Android SDK with platform 37.

```bash
./gradlew check assembleDebug assembleRelease
```

- Debug APK: `demo/build/outputs/apk/debug/demo-debug.apk`
- Release APK (R8 + resource shrinking): `demo/build/outputs/apk/release/demo-release.apk`. Signing is read from
  `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or from the `AURA_KEYSTORE_FILE`,
  `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, `AURA_KEY_PASSWORD` environment variables; without them the release
  APK is unsigned.
- Public API dump of `sdk-core`: `./gradlew updateKotlinAbi` (checked by `check`).
- Coverage: `./gradlew :sdk-core:koverHtmlReport :sdk:koverHtmlReportDebug`.

## Tests

366 unit tests, all written independently of the code they test:

- `sdk-core` (193, JUnit 6): engine lifecycle, pre-init buffer, validation, JSON round trips, concurrency, paging,
  statistics, cleanup (retention, limit, DST, protection flags), failure paths.
- `sdk` (61, Robolectric): a shared `EventStoreContract` run on both the in-memory fake and the Room store, DataStore,
  WorkManager scheduling, the cleanup worker without `init`, the facade before and after `init`.
- `demo` (112): every ViewModel, the event catalog, SDK start, paging and formatting, config storage, time shift.

Coverage (Kover): `sdk-core` 99.8 % lines / 95.3 % branches, `sdk` 100 % / 100 %.

## How it was built

Contract-first and with independent verification: the SDK API and KDoc were reviewed first; tests and
implementation were written by separate AI sessions that could not see each other's work, then reconciled; every
commit was reviewed by an independent AI reviewer; demo features were built in parallel sessions from contracts.
Details, prompts, mistakes and timings: [AI_COMMUNICATION.md](AI_COMMUNICATION.md).
