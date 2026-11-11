package com.usatov.aura.logger.core.spi

import com.usatov.aura.logger.core.InternalAuraLoggerApi
import kotlinx.coroutines.flow.Flow

/**
 * Persistent storage of events. Implemented by the `sdk` module (Room).
 *
 * "Newest first" always means: by `timestamp` descending, then by `sequence` descending.
 * All functions are main-safe and may throw on storage failures; callers handle errors.
 */
@InternalAuraLoggerApi
public interface EventStore {

    /** Inserts [events] in list order in a single transaction. Ids are unique. */
    public suspend fun insert(events: List<NewEventRecord>)

    /** Returns at most [limit] events, newest first. */
    public suspend fun getNewest(limit: Int): List<StoredEventRecord>

    /**
     * Returns at most [limit] events that are older than the position ([beforeTimestamp],
     * [beforeSequence]) in the newest-first order, newest first.
     */
    public suspend fun getOlderThan(beforeTimestamp: Long, beforeSequence: Long, limit: Int): List<StoredEventRecord>

    /** Returns the number of stored events. */
    public suspend fun count(): Int

    /** Returns the number of events with `fromInclusive <= timestamp < toExclusive`. */
    public suspend fun countInRange(fromInclusive: Long, toExclusive: Long): Int

    /** Returns event counts grouped by `createdDate`, ordered by `latestTimestamp` descending. */
    public suspend fun countByDay(): List<DayCountRecord>

    /** Marks the events with the given ids as seen. Unknown ids are ignored. */
    public suspend fun markSeen(ids: Collection<String>)

    /**
     * Deletes all events; when [keepUnseen] is `true`, unseen events are kept.
     * @return number of deleted events.
     */
    public suspend fun deleteAll(keepUnseen: Boolean): Int

    /**
     * Deletes events with `timestamp < timestamp`; when [keepUnseen] is `true`, unseen events are kept.
     * @return number of deleted events.
     */
    public suspend fun deleteOlderThan(timestamp: Long, keepUnseen: Boolean): Int

    /**
     * Deletes the oldest events so that at most [maxEvents] events remain. When [keepUnseen] is
     * `true`, unseen events are never deleted but still count towards [maxEvents]; then fewer
     * events may be deleted than needed to reach the limit.
     * @return number of deleted events.
     */
    public suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int

    /**
     * Emits after every committed change made through any instance that shares the same database
     * in this process. Conflated; no initial value requirement.
     */
    public fun observeChanges(): Flow<Unit>
}
