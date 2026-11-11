package com.usatov.aura.logger.internal.store

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

/** Queries of the events table. "Newest first" is `timestamp DESC, sequence DESC`. */
@Dao
internal abstract class EventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(events: List<EventEntity>)

    @Query("SELECT * FROM events ORDER BY timestamp DESC, sequence DESC LIMIT :limit")
    abstract suspend fun getNewest(limit: Int): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE timestamp < :beforeTimestamp OR (timestamp = :beforeTimestamp AND sequence < :beforeSequence)
        ORDER BY timestamp DESC, sequence DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun getOlderThan(beforeTimestamp: Long, beforeSequence: Long, limit: Int): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    abstract suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM events WHERE timestamp >= :fromInclusive AND timestamp < :toExclusive")
    abstract suspend fun countInRange(fromInclusive: Long, toExclusive: Long): Int

    @Query(
        """
        SELECT created_date, MAX(timestamp) AS latest_timestamp, COUNT(*) AS event_count
        FROM events
        GROUP BY created_date
        ORDER BY latest_timestamp DESC
        """,
    )
    abstract suspend fun countByDay(): List<DayCountRow>

    @Query("UPDATE events SET seen = 1 WHERE seen = 0 AND id IN (:ids)")
    protected abstract suspend fun markSeenChunk(ids: List<String>): Int

    /** Marks [ids] seen in one transaction, in chunks that stay below the SQLite variable limit. */
    @Transaction
    open suspend fun markSeen(ids: List<String>) {
        for (chunk in ids.chunked(MAX_IDS_PER_STATEMENT)) markSeenChunk(chunk)
    }

    @Query("DELETE FROM events WHERE :keepUnseen = 0 OR seen = 1")
    abstract suspend fun deleteAll(keepUnseen: Boolean): Int

    @Query("DELETE FROM events WHERE timestamp < :timestamp AND (:keepUnseen = 0 OR seen = 1)")
    abstract suspend fun deleteOlderThan(timestamp: Long, keepUnseen: Boolean): Int

    /**
     * Deletes the oldest events so that at most [maxEvents] remain. With [keepUnseen], only seen
     * events are deleted, oldest first wherever they are in the list (also seen events newer than
     * unseen ones), until the limit is reached or no seen event is left.
     */
    @Transaction
    open suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int {
        val excess = count() - maxEvents
        // SQLite treats a negative LIMIT as "no limit", so the guard is required.
        if (excess <= 0) return 0
        return deleteOldest(excess, keepUnseen)
    }

    /** Deletes the [count] oldest events; with [keepUnseen], the [count] oldest seen events. */
    @Query(
        """
        DELETE FROM events
        WHERE sequence IN (
            SELECT sequence FROM events
            WHERE :keepUnseen = 0 OR seen = 1
            ORDER BY timestamp ASC, sequence ASC
            LIMIT :count
        )
        """,
    )
    protected abstract suspend fun deleteOldest(count: Int, keepUnseen: Boolean): Int

    private companion object {
        /** Below the historical SQLite limit of 999 bound variables per statement. */
        const val MAX_IDS_PER_STATEMENT = 500
    }
}
