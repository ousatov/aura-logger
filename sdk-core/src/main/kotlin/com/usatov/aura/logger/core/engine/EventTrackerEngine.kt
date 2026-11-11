package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.DailyEventCount
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventPage
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.InternalAuraLoggerApi
import com.usatov.aura.logger.core.LimitOverflowStrategy
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.core.internal.DayCalendar
import com.usatov.aura.logger.core.internal.EventValidator
import com.usatov.aura.logger.core.internal.PropertiesJson
import com.usatov.aura.logger.core.internal.SerialWriteQueue
import com.usatov.aura.logger.core.internal.WriteOp
import com.usatov.aura.logger.core.internal.abbreviate
import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.CleanupScheduler
import com.usatov.aura.logger.core.spi.ConfigStore
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.IdGenerator
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.spi.StoredEventRecord
import com.usatov.aura.logger.core.spi.TimeZoneProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Platform-independent implementation of [EventTracker]. The `sdk` module creates one instance
 * per process and delegates to it from the public facade.
 *
 * Before [start], the engine accepts [track] calls into a bounded in-memory buffer of
 * [AuraLoggerSettings.preInitBufferCapacity] events (oldest dropped with a warning on overflow);
 * their ids and timestamps are generated at call time with [idGenerator] and [initialClock].
 * Input validation uses the validation values of [settings].
 *
 * Limit on write: when [AuraLoggerSettings.enforceLimitOnWrite] is `true`, every insert of new
 * events (including the buffered events written on [start]) is followed, on the same write queue
 * and before any later queued operation, by
 * `EventStore.deleteOldestAbove(config.maxEvents, keepUnseen = config.unseenEventsProtection.onLimit)`
 * with the latest config (from [start] or [updateConfig]). With
 * [LimitOverflowStrategy.DROP_NEW_EVENTS], new events that would still exceed the limit after that
 * deletion are dropped with a warning instead of being stored; with
 * [LimitOverflowStrategy.KEEP_NEW_EVENTS] (default) they are always stored.
 *
 * @param initialClock clock used for events tracked before [start].
 */
@InternalAuraLoggerApi
public class EventTrackerEngine(
    private val idGenerator: IdGenerator,
    private val timeZoneProvider: TimeZoneProvider,
    private val logger: AuraLogger,
    private val initialClock: Clock = Clock.System,
    private val settings: AuraLoggerSettings = AuraLoggerSettings(),
) : EventTracker {

    private val lock = Any()
    private val validator = EventValidator(settings)

    /** Events tracked before [start]. Guarded by [lock]. */
    private val preInitBuffer = ArrayDeque<NewEventRecord>()

    /** State after [start]; written once under [lock]. */
    @Volatile
    private var running: Running? = null

    /** Event store of the started engine; drives [changes]. */
    private val startedStore = MutableStateFlow<EventStore?>(null)

    /** `true` after the first successful [start]. Thread-safe. */
    public val isStarted: Boolean
        get() = running != null

    /**
     * Starts the engine. Thread-safe; only the first call has an effect.
     *
     * The first call, without blocking the caller:
     * - switches to [EngineDependencies.clock] for all later events;
     * - starts the serial write queue in [EngineDependencies.scope];
     * - queues, in this order, persisting [EngineDependencies.config], scheduling the periodic
     *   cleanup and writing the buffered events in the order they were tracked.
     *
     * Later calls are ignored and log a warning (use [updateConfig] to change the config).
     *
     * @return `true` if this call started the engine, `false` if it was already started.
     */
    public fun start(dependencies: EngineDependencies): Boolean {
        synchronized(lock) {
            if (running != null) {
                logger.warn("Aura Logger is already initialised; this call is ignored. Use updateConfig to change the config.")
                return false
            }
            val queue = SerialWriteQueue(dependencies.scope, dependencies.eventStore, logger)
            val started = Running(dependencies, queue, dependencies.config)
            queue.submit(WriteOp.Task("persist the config") { dependencies.configStore.write(dependencies.config) })
            queue.submit(WriteOp.Task("schedule the periodic cleanup") { dependencies.cleanupScheduler.schedulePeriodic() })
            if (preInitBuffer.isNotEmpty()) {
                queue.submit(WriteOp.Insert(preInitBuffer.toList()))
                preInitBuffer.clear()
            }
            running = started
        }
        startedStore.value = dependencies.eventStore
        return true
    }

    override fun track(name: String, properties: Map<String, String>) {
        try {
            val snapshot = properties.toMap()
            val invalidReason = validator.validate(name, snapshot)
            if (invalidReason != null) {
                logger.warn("Event '${name.abbreviate()}' dropped: $invalidReason")
                return
            }
            val propertiesJson = PropertiesJson.encode(snapshot)
            synchronized(lock) {
                val started = running
                val timestamp = (started?.dependencies?.clock ?: initialClock).now().toEpochMilliseconds()
                val record = NewEventRecord(
                    id = idGenerator.nextId(),
                    name = name,
                    propertiesJson = propertiesJson,
                    timestamp = timestamp,
                    createdDate = DayCalendar.formatDate(timestamp, timeZoneProvider.currentTimeZoneId()),
                )
                if (started != null) {
                    started.queue.submit(WriteOp.Insert(listOf(record)))
                } else {
                    if (preInitBuffer.size >= settings.preInitBufferCapacity) {
                        val dropped = preInitBuffer.removeFirst()
                        logger.warn(
                            "Pre-initialisation buffer is full (${settings.preInitBufferCapacity} events); " +
                                "dropped the oldest event '${dropped.name.abbreviate()}'",
                        )
                    }
                    preInitBuffer.addLast(record)
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to track event '${name.abbreviate()}'; it is dropped", e)
        }
    }

    override fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>) {
        val encoded = try {
            codec.encode(properties)
        } catch (e: Throwable) {
            logger.warn("Event '${name.abbreviate()}' dropped: PropertiesCodec.encode failed", e)
            return
        }
        track(name, encoded)
    }

    override fun markSeen(eventIds: Collection<String>) {
        try {
            val started = running
            if (started == null) {
                logger.warn("markSeen called before initialisation; it is ignored")
                return
            }
            val ids = eventIds.toSet()
            if (ids.isEmpty()) return
            started.queue.submit(WriteOp.Task("mark events seen") { started.dependencies.eventStore.markSeen(ids) })
        } catch (e: Throwable) {
            logger.error("Failed to mark events seen", e)
        }
    }

    override suspend fun getRecentEvents(limit: Int): List<TrackedEvent> {
        require(limit > 0) { "limit must be positive, was $limit" }
        val started = awaitQueuedWrites()
        return withContext(started.dependencies.readDispatcher) {
            started.dependencies.eventStore.getNewest(limit).map { it.toTrackedEvent() }
        }
    }

    override suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage {
        require(pageSize > 0) { "pageSize must be positive, was $pageSize" }
        val started = awaitQueuedWrites()
        return withContext(started.dependencies.readDispatcher) {
            val store = started.dependencies.eventStore
            // One extra row tells whether an older page exists.
            val fetchLimit = if (pageSize == Int.MAX_VALUE) pageSize else pageSize + 1
            val records = if (cursor == null) {
                store.getNewest(fetchLimit)
            } else {
                store.getOlderThan(cursor.timestamp, cursor.sequence, fetchLimit)
            }
            val page = records.take(pageSize)
            val nextCursor = if (records.size > pageSize) {
                page.last().let { EventCursor(it.timestamp, it.sequence) }
            } else {
                null
            }
            EventPage(page.map { it.toTrackedEvent() }, nextCursor)
        }
    }

    override suspend fun getStats(): EventStats {
        val started = awaitQueuedWrites()
        return withContext(started.dependencies.readDispatcher) {
            val store = started.dependencies.eventStore
            val now = started.dependencies.clock.now().toEpochMilliseconds()
            val zoneId = timeZoneProvider.currentTimeZoneId()
            val todayCount = store.countInRange(
                fromInclusive = DayCalendar.startOfDay(now, zoneId),
                toExclusive = DayCalendar.startOfNextDay(now, zoneId),
            )
            EventStats(
                totalCount = store.count(),
                todayCount = todayCount,
                dailyCounts = store.countByDay().map { DailyEventCount(it.createdDate, it.latestTimestamp, it.count) },
            )
        }
    }

    override suspend fun clearAll(): Int = enqueueAndAwait("clear all events") { started ->
        started.dependencies.eventStore.deleteAll(keepUnseen = started.config.unseenEventsProtection.onClearAll)
    }

    override suspend fun updateConfig(config: EventTrackerConfig): Unit = enqueueAndAwait("update the config") { started ->
        started.dependencies.configStore.write(config)
        started.config = config
        try {
            started.dependencies.cleanupScheduler.scheduleOneTime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Config persisted, but scheduling the one-time cleanup failed", e)
        }
    }

    override val changes: Flow<Unit> = flow {
        val store = startedStore.filterNotNull().first()
        emitAll(
            store.observeChanges().retryWhen { cause, _ ->
                logger.warn("Observing event changes failed; retrying", cause)
                delay(CHANGES_RETRY_DELAY)
                true
            },
        )
        awaitCancellation()
    }.conflate()

    private fun requireStarted(): Running =
        running ?: throw IllegalStateException("Aura Logger is not initialised. Call EventTrackerSDK.init first.")

    /** Waits until every write queued before this call has been executed. */
    private suspend fun awaitQueuedWrites(): Running = enqueueAndAwait("read events") { it }

    private suspend fun <T> enqueueAndAwait(description: String, block: suspend (Running) -> T): T {
        val started = requireStarted()
        val result = CompletableDeferred<T>()
        started.queue.submit(WriteOp.Task(description, result) { block(started) })
        return result.await()
    }

    private fun StoredEventRecord.toTrackedEvent(): TrackedEvent = TrackedEvent(
        id = id,
        name = name,
        properties = decodeProperties(this),
        timestamp = timestamp,
        createdDate = createdDate,
        seen = seen,
    )

    private fun decodeProperties(record: StoredEventRecord): Map<String, String> =
        try {
            PropertiesJson.decode(record.propertiesJson)
        } catch (e: IllegalArgumentException) {
            logger.warn("Stored properties of event ${record.id} are malformed; returning no properties", e)
            emptyMap()
        }

    private class Running(
        val dependencies: EngineDependencies,
        val queue: SerialWriteQueue,
        config: EventTrackerConfig,
    ) {
        /** Current retention policy; read and written only on the serial queue. */
        @Volatile
        var config: EventTrackerConfig = config
    }

    private companion object {
        val CHANGES_RETRY_DELAY = 1.seconds
    }
}

/**
 * Everything [EventTrackerEngine.start] needs.
 *
 * @property config config passed to the SDK initialisation; it is persisted.
 * @property clock clock for timestamps of events tracked after start, for "today" and day boundaries.
 * @property scope scope that runs the serial write queue; the engine never cancels it.
 * @property readDispatcher dispatcher for read operations.
 */
@InternalAuraLoggerApi
public class EngineDependencies(
    public val config: EventTrackerConfig,
    public val eventStore: EventStore,
    public val configStore: ConfigStore,
    public val cleanupScheduler: CleanupScheduler,
    public val clock: Clock,
    public val scope: CoroutineScope,
    public val readDispatcher: CoroutineDispatcher,
)
