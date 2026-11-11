package com.usatov.aura.logger.internal.store

import android.content.Context
import com.usatov.aura.logger.core.spi.DayCountRecord
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.spi.StoredEventRecord
import kotlinx.coroutines.flow.Flow

/**
 * [EventStore] backed by a Room 3 database (`AndroidSQLiteDriver`).
 *
 * The events table stores: id (String, unique), name (String), properties as a JSON string,
 * timestamp in Unix epoch milliseconds (Long), created date in `DD/MM/YYYY` format, seen flag
 * and an auto-incremented insertion sequence.
 */
internal class RoomEventStore private constructor() : EventStore {

    override suspend fun insert(events: List<NewEventRecord>): Unit = TODO("Not implemented yet")

    override suspend fun getNewest(limit: Int): List<StoredEventRecord> = TODO("Not implemented yet")

    override suspend fun getOlderThan(
        beforeTimestamp: Long,
        beforeSequence: Long,
        limit: Int,
    ): List<StoredEventRecord> = TODO("Not implemented yet")

    override suspend fun count(): Int = TODO("Not implemented yet")

    override suspend fun countInRange(fromInclusive: Long, toExclusive: Long): Int = TODO("Not implemented yet")

    override suspend fun countByDay(): List<DayCountRecord> = TODO("Not implemented yet")

    override suspend fun markSeen(ids: Collection<String>): Unit = TODO("Not implemented yet")

    override suspend fun deleteAll(keepUnseen: Boolean): Int = TODO("Not implemented yet")

    override suspend fun deleteOlderThan(timestamp: Long, keepUnseen: Boolean): Int = TODO("Not implemented yet")

    override suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int = TODO("Not implemented yet")

    override fun observeChanges(): Flow<Unit> = TODO("Not implemented yet")

    /** Closes the underlying database. The store must not be used afterwards. */
    fun close(): Unit = TODO("Not implemented yet")

    companion object {
        /** File name of the SDK database. */
        const val DATABASE_NAME: String = "aura_logger_events.db"

        /**
         * Creates a store. Production code must use a single process-wide instance per database.
         *
         * @param databaseName database file name, or `null` for an in-memory database (tests).
         */
        fun create(context: Context, databaseName: String? = DATABASE_NAME): RoomEventStore =
            TODO("Not implemented yet")
    }
}
