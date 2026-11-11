package com.usatov.aura.logger.core.testing

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.CleanupScheduler
import com.usatov.aura.logger.core.spi.ConfigStore
import com.usatov.aura.logger.core.spi.DayCountRecord
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.IdGenerator
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.spi.StoredEventRecord
import com.usatov.aura.logger.core.spi.TimeZoneProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Instant

/** Clock that returns a settable time. Thread-safe. */
class MutableClock(initialMillis: Long) : Clock {
    private val millis = AtomicLong(initialMillis)

    var nowMillis: Long
        get() = millis.get()
        set(value) = millis.set(value)

    fun advanceBy(deltaMillis: Long) {
        millis.addAndGet(deltaMillis)
    }

    override fun now(): Instant = Instant.fromEpochMilliseconds(millis.get())
}

/** Returns `id-1`, `id-2`, ... Thread-safe. */
class SequentialIdGenerator(private val prefix: String = "id-") : IdGenerator {
    private val counter = AtomicLong()
    override fun nextId(): String = prefix + counter.incrementAndGet()
}

/** Time zone that tests can switch at runtime. */
class FakeTimeZoneProvider(@Volatile var zoneId: String = "UTC") : TimeZoneProvider {
    override fun currentTimeZoneId(): String = zoneId
}

/** Logger that records messages. Thread-safe. */
class RecordingLogger : AuraLogger {
    val warnings = CopyOnWriteArrayList<String>()
    val errors = CopyOnWriteArrayList<String>()

    override fun warn(message: String, throwable: Throwable?) {
        warnings += message
    }

    override fun error(message: String, throwable: Throwable?) {
        errors += message
    }
}

class FakeConfigStore(initial: EventTrackerConfig? = null) : ConfigStore {
    @Volatile var stored: EventTrackerConfig? = initial
    val writes = CopyOnWriteArrayList<EventTrackerConfig>()

    override suspend fun read(): EventTrackerConfig? = stored

    override suspend fun write(config: EventTrackerConfig) {
        writes += config
        stored = config
    }
}

class FakeCleanupScheduler : CleanupScheduler {
    val periodicCalls = AtomicInteger()
    val oneTimeCalls = AtomicInteger()

    override fun schedulePeriodic() {
        periodicCalls.incrementAndGet()
    }

    override fun scheduleOneTime() {
        oneTimeCalls.incrementAndGet()
    }
}

/**
 * In-memory [EventStore] that follows the documented contract. Thread-safe.
 * Failures can be injected per operation.
 */
class FakeEventStore : EventStore {
    private val lock = Any()
    private val rows = mutableListOf<StoredEventRecord>()
    private var nextSequence = 1L
    private val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Thrown by the next [insert] call, then reset. */
    @Volatile var failNextInsert: Throwable? = null

    /** Thrown by every [insert] call while set. */
    @Volatile var failAllInserts: Throwable? = null

    /** Thrown by every [markSeen] call while set. */
    @Volatile var failMarkSeen: Throwable? = null

    /** Thrown by every delete call while set. */
    @Volatile var failDeletes: Throwable? = null

    val insertedBatches = CopyOnWriteArrayList<List<NewEventRecord>>()

    val all: List<StoredEventRecord>
        get() = synchronized(lock) { rows.toList() }

    /** Rows newest first, as the contract defines it. */
    val newestFirst: List<StoredEventRecord>
        get() = synchronized(lock) { rows.sortedWith(NEWEST_FIRST) }

    override suspend fun insert(events: List<NewEventRecord>) {
        failNextInsert?.let { failNextInsert = null; throw it }
        failAllInserts?.let { throw it }
        synchronized(lock) {
            insertedBatches += events
            events.forEach { e ->
                rows += StoredEventRecord(nextSequence++, e.id, e.name, e.propertiesJson, e.timestamp, e.createdDate, seen = false)
            }
        }
        changeFlow.tryEmit(Unit)
    }

    override suspend fun getNewest(limit: Int): List<StoredEventRecord> = newestFirst.take(limit)

    override suspend fun getOlderThan(beforeTimestamp: Long, beforeSequence: Long, limit: Int): List<StoredEventRecord> =
        newestFirst.filter { it.timestamp < beforeTimestamp || (it.timestamp == beforeTimestamp && it.sequence < beforeSequence) }
            .take(limit)

    override suspend fun count(): Int = synchronized(lock) { rows.size }

    override suspend fun countInRange(fromInclusive: Long, toExclusive: Long): Int =
        synchronized(lock) { rows.count { it.timestamp in fromInclusive until toExclusive } }

    override suspend fun countByDay(): List<DayCountRecord> = synchronized(lock) {
        rows.groupBy { it.createdDate }
            .map { (date, events) -> DayCountRecord(date, events.maxOf { it.timestamp }, events.size) }
            .sortedByDescending { it.latestTimestamp }
    }

    override suspend fun markSeen(ids: Collection<String>) {
        failMarkSeen?.let { throw it }
        synchronized(lock) {
            val set = ids.toSet()
            rows.replaceAll { if (it.id in set) it.copy(seen = true) else it }
        }
        changeFlow.tryEmit(Unit)
    }

    override suspend fun deleteAll(keepUnseen: Boolean): Int = delete { keepUnseen.not() || it.seen }

    override suspend fun deleteOlderThan(timestamp: Long, keepUnseen: Boolean): Int =
        delete { it.timestamp < timestamp && (!keepUnseen || it.seen) }

    override suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int {
        failDeletes?.let { throw it }
        val deleted = synchronized(lock) {
            var excess = rows.size - maxEvents
            if (excess <= 0) return@synchronized 0
            val candidates = rows.sortedWith(NEWEST_FIRST).reversed().filter { !keepUnseen || it.seen }
            var count = 0
            for (candidate in candidates) {
                if (excess == 0) break
                rows.remove(candidate)
                excess--
                count++
            }
            count
        }
        if (deleted > 0) changeFlow.tryEmit(Unit)
        return deleted
    }

    override fun observeChanges(): Flow<Unit> = changeFlow

    /** Test helper: inserts rows directly, bypassing failure injection. */
    fun seed(vararg events: NewEventRecord, seen: Boolean = false) {
        synchronized(lock) {
            events.forEach { e ->
                rows += StoredEventRecord(nextSequence++, e.id, e.name, e.propertiesJson, e.timestamp, e.createdDate, seen)
            }
        }
    }

    private fun delete(predicate: (StoredEventRecord) -> Boolean): Int {
        failDeletes?.let { throw it }
        val deleted = synchronized(lock) {
            val before = rows.size
            rows.removeAll(predicate)
            before - rows.size
        }
        if (deleted > 0) changeFlow.tryEmit(Unit)
        return deleted
    }

    private companion object {
        val NEWEST_FIRST = compareByDescending<StoredEventRecord> { it.timestamp }.thenByDescending { it.sequence }
    }
}

/** Epoch millis of a local date-time in [zone]. */
fun millisOf(zone: String, year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, millis: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000)
        .atZone(ZoneId.of(zone))
        .toInstant()
        .toEpochMilli()

/** A record with a computed `DD/MM/YYYY` date in [zone]. */
fun record(id: String, timestamp: Long, zone: String = "UTC", name: String = "event_$id", propertiesJson: String = "{}"): NewEventRecord {
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.of(zone)).toLocalDate()
    val createdDate = "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    return NewEventRecord(id, name, propertiesJson, timestamp, createdDate)
}
