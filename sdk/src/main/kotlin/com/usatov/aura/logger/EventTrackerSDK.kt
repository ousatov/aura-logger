package com.usatov.aura.logger

import android.content.Context
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventPage
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.core.engine.EngineDependencies
import com.usatov.aura.logger.internal.AuraLoggerRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * Android entry point of the Aura Logger event tracker.
 *
 * Usage:
 * ```
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         EventTrackerSDK.init(this, EventTrackerConfig(retentionDays = 7, maxEvents = 100))
 *     }
 * }
 *
 * EventTrackerSDK.track("button_clicked", mapOf("button_id" to "submit"))
 * ```
 *
 * The behaviour of every [EventTracker] member is described on the interface. This object is the
 * single process-wide tracker; inject it as [EventTracker] to keep client code testable.
 *
 * Diagnostics (dropped events, ignored calls, storage errors) are written to Logcat with the tag
 * `AuraLogger`.
 */
public object EventTrackerSDK : EventTracker {

    private val runtime = AuraLoggerRuntime
    private val engine = runtime.engine
    private val initLock = Any()

    /** `true` once [init] has been called in this process. Thread-safe. */
    public val isInitialized: Boolean
        get() = engine.isStarted

    /**
     * Initialises the tracker. Call it once per process, as early as possible, typically in
     * `Application.onCreate`.
     *
     * Thread-safe and main-safe: it does not block on disk or database I/O. Only the first call in
     * a process has an effect; later calls are ignored and log a warning, even if they pass a
     * different config (use [updateConfig] to change it at runtime). Never throws.
     *
     * The first call:
     * - keeps only `context.applicationContext`, so passing an `Activity` does not leak it;
     * - persists [config], replacing the config persisted by a previous process;
     * - schedules the unique periodic cleanup (WorkManager): every 24 hours, first run 1 hour after
     *   it was first scheduled. Repeated initialisations, also in new processes, keep the existing
     *   schedule and never create duplicate jobs;
     * - writes the events tracked before initialisation, in call order.
     *
     * The cleanup also runs when the process was started by WorkManager and [init] is not called:
     * it then uses the persisted config (or [EventTrackerConfig] defaults if none was persisted).
     *
     * @param context any context; only its application context is used.
     * @param config retention policy; see [EventTrackerConfig].
     * @param clock source of event timestamps and of the current day. Events tracked before
     * [init] always use the system clock. Intended for tests and debug tooling (time travel);
     * keep the default in production.
     */
    public fun init(
        context: Context,
        config: EventTrackerConfig = EventTrackerConfig(),
        clock: Clock = Clock.System,
    ) {
        try {
            synchronized(initLock) {
                if (engine.isStarted) {
                    runtime.logger.warn("EventTrackerSDK.init was already called; this call is ignored. Use updateConfig to change the config.")
                    return
                }
                val appContext = context.applicationContext ?: context
                runtime.clock = clock
                engine.start(
                    EngineDependencies(
                        config = config,
                        eventStore = runtime.eventStore(appContext),
                        configStore = runtime.configStore(appContext),
                        cleanupScheduler = runtime.cleanupScheduler(appContext),
                        clock = clock,
                        scope = runtime.scope,
                        readDispatcher = Dispatchers.IO,
                    ),
                )
            }
        } catch (e: Throwable) {
            runtime.logger.error("EventTrackerSDK.init failed", e)
        }
    }

    override fun track(name: String, properties: Map<String, String>): Unit = engine.track(name, properties)

    override fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>): Unit =
        engine.track(name, properties, codec)

    override fun markSeen(eventIds: Collection<String>): Unit = engine.markSeen(eventIds)

    override suspend fun getRecentEvents(limit: Int): List<TrackedEvent> = engine.getRecentEvents(limit)

    override suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage =
        engine.getEventsPage(cursor, pageSize)

    override suspend fun getStats(): EventStats = engine.getStats()

    override suspend fun clearAll(): Int = engine.clearAll()

    override suspend fun updateConfig(config: EventTrackerConfig): Unit = engine.updateConfig(config)

    override val changes: Flow<Unit>
        get() = engine.changes
}
