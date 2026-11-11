@file:OptIn(InternalAuraLoggerApi::class)

package com.usatov.aura.logger.demo.testing

import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventPage
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.InternalAuraLoggerApi
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.DemoConfigRepository
import com.usatov.aura.logger.demo.core.model.Section
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared, ordered log of calls across fakes, to check the order of side effects. */
class CallLog {
    val calls = CopyOnWriteArrayList<String>()
    fun add(call: String) {
        calls += call
    }
}

data class TrackCall(val name: String, val properties: Map<String, String>)

/** Fake SDK: records writes, returns configured reads, failures can be injected. */
class FakeEventTracker(private val log: CallLog = CallLog()) : EventTracker {
    val tracked = CopyOnWriteArrayList<TrackCall>()
    val markedSeen = CopyOnWriteArrayList<List<String>>()
    val updatedConfigs = CopyOnWriteArrayList<EventTrackerConfig>()
    val pageRequests = CopyOnWriteArrayList<Pair<EventCursor?, Int>>()

    var stats: EventStats = EventStats(0, 0, emptyList())
    var statsFailure: Throwable? = null
    var statsCalls = 0
        private set

    /** All events, newest first; pages are cut from this list. */
    var events: List<TrackedEvent> = emptyList()
    var pageFailure: Throwable? = null

    var clearResult: Int = 0
    var clearFailure: Throwable? = null
    var clearGate: CompletableDeferred<Unit>? = null
    var clearCalls = 0
        private set

    var updateConfigFailure: Throwable? = null

    val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun track(name: String, properties: Map<String, String>) {
        log.add("track:$name")
        tracked += TrackCall(name, properties.toMap())
    }

    override fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>) = track(name, codec.encode(properties))

    override fun markSeen(eventIds: Collection<String>) {
        log.add("markSeen")
        markedSeen += eventIds.toList()
    }

    override suspend fun getRecentEvents(limit: Int): List<TrackedEvent> = events.take(limit)

    override suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage {
        pageRequests += cursor to pageSize
        pageFailure?.let { throw it }
        // The fake cursor stores the index of the last returned event as its sequence.
        val start = cursor?.let { it.sequence.toInt() + 1 } ?: 0
        val slice = events.drop(start).take(pageSize)
        val lastIndex = start + slice.size - 1
        val next = if (lastIndex + 1 < events.size) EventCursor(slice.last().timestamp, lastIndex.toLong()) else null
        return EventPage(slice, next)
    }

    override suspend fun getStats(): EventStats {
        statsCalls++
        statsFailure?.let { throw it }
        return stats
    }

    override suspend fun clearAll(): Int {
        clearCalls++
        log.add("clearAll")
        clearGate?.await()
        clearFailure?.let { throw it }
        return clearResult
    }

    override suspend fun updateConfig(config: EventTrackerConfig) {
        log.add("updateConfig")
        updateConfigFailure?.let { throw it }
        updatedConfigs += config
    }

    override val changes: Flow<Unit> get() = changeFlow
}

/** Records every catalog call. */
class FakeAppEvents(private val log: CallLog = CallLog()) : AppEvents {
    val calls = CopyOnWriteArrayList<String>()

    private fun record(call: String) {
        calls += call
        log.add("appEvents:$call")
    }

    override fun processStarted() = record("processStarted")
    override fun sectionSelected(section: Section) = record("sectionSelected:${section.key}")
    override fun configChanged(config: EventTrackerConfig) = record("configChanged:${config.retentionDays}/${config.maxEvents}")
    override fun eventsCleared(deletedCount: Int) = record("eventsCleared:$deletedCount")
    override fun testEvent() = record("testEvent")
    override fun testEvents(count: Int) = record("testEvents:$count")
    override fun testEventWithProperties() = record("testEventWithProperties")
}

class FakeDemoConfigRepository(
    initial: EventTrackerConfig = EventTrackerConfig(),
    private val log: CallLog = CallLog(),
) : DemoConfigRepository {
    val state = MutableStateFlow(initial)
    var saveFailure: Throwable? = null
    var currentFailure: Throwable? = null
    var currentGate: CompletableDeferred<Unit>? = null
    val saved = CopyOnWriteArrayList<EventTrackerConfig>()

    override val config: Flow<EventTrackerConfig> get() = state

    override suspend fun current(): EventTrackerConfig {
        currentGate?.await()
        currentFailure?.let { throw it }
        return state.value
    }

    override suspend fun save(config: EventTrackerConfig) {
        log.add("save")
        saveFailure?.let { throw it }
        saved += config
        state.value = config
    }
}

class FixedClock(var millis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

fun millisOf(zone: String, y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

fun event(id: String, timestamp: Long, name: String = "e_$id", properties: Map<String, String> = emptyMap(), seen: Boolean = false) =
    TrackedEvent(id, name, properties, timestamp, "01/01/2026", seen)
