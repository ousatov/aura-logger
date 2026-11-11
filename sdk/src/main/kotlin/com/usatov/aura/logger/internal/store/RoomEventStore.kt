package com.usatov.aura.logger.internal.store

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.usatov.aura.logger.core.spi.DayCountRecord
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.spi.StoredEventRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

/**
 * [EventStore] backed by a Room 3 database (`AndroidSQLiteDriver`).
 *
 * The events table stores: id (String, unique), name (String), properties as a JSON string,
 * timestamp in Unix epoch milliseconds (Long), created date in `DD/MM/YYYY` format, seen flag
 * and an auto-incremented insertion sequence.
 */
internal class RoomEventStore private constructor(
    private val database: AuraLoggerDatabase,
) : EventStore {

    private val dao: EventDao = database.eventDao()

    override suspend fun insert(events: List<NewEventRecord>) {
        if (events.isEmpty()) return
        dao.insert(
            events.map {
                EventEntity(
                    id = it.id,
                    name = it.name,
                    propertiesJson = it.propertiesJson,
                    timestamp = it.timestamp,
                    createdDate = it.createdDate,
                )
            },
        )
    }

    override suspend fun getNewest(limit: Int): List<StoredEventRecord> =
        dao.getNewest(limit).map { it.toRecord() }

    override suspend fun getOlderThan(
        beforeTimestamp: Long,
        beforeSequence: Long,
        limit: Int,
    ): List<StoredEventRecord> = dao.getOlderThan(beforeTimestamp, beforeSequence, limit).map { it.toRecord() }

    override suspend fun count(): Int = dao.count()

    override suspend fun countInRange(fromInclusive: Long, toExclusive: Long): Int =
        dao.countInRange(fromInclusive, toExclusive)

    override suspend fun countByDay(): List<DayCountRecord> =
        dao.countByDay().map { DayCountRecord(it.createdDate, it.latestTimestamp, it.count) }

    override suspend fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dao.markSeen(ids.distinct())
    }

    override suspend fun deleteAll(keepUnseen: Boolean): Int = dao.deleteAll(keepUnseen)

    override suspend fun deleteOlderThan(timestamp: Long, keepUnseen: Boolean): Int =
        dao.deleteOlderThan(timestamp, keepUnseen)

    override suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int =
        dao.deleteOldestAbove(maxEvents, keepUnseen)

    override fun observeChanges(): Flow<Unit> =
        database.invalidationTracker
            .createFlow(EventEntity.TABLE_NAME, emitInitialState = false)
            .map { }
            .conflate()

    /** Closes the underlying database. The store must not be used afterwards. */
    fun close() {
        database.close()
    }

    private fun EventEntity.toRecord(): StoredEventRecord = StoredEventRecord(
        sequence = sequence,
        id = id,
        name = name,
        propertiesJson = propertiesJson,
        timestamp = timestamp,
        createdDate = createdDate,
        seen = seen,
    )

    companion object {
        /**
         * Creates a store. Production code must use a single process-wide instance per database.
         *
         * @param databaseName database file name (production:
         * [com.usatov.aura.logger.core.AuraLoggerSettings.databaseName]), or `null` for an
         * in-memory database (tests).
         */
        fun create(context: Context, databaseName: String?): RoomEventStore {
            val appContext = context.applicationContext ?: context
            // The generated implementation is created directly instead of by reflection, so R8
            // needs no keep rule for it.
            val factory = { AuraLoggerDatabase_Impl() }
            val builder = if (databaseName == null) {
                Room.inMemoryDatabaseBuilder<AuraLoggerDatabase>(appContext, factory)
            } else {
                Room.databaseBuilder<AuraLoggerDatabase>(appContext, databaseName, factory)
            }
            return RoomEventStore(builder.setDriver(AndroidSQLiteDriver()).build())
        }
    }
}
