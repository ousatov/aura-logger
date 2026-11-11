package com.usatov.aura.logger.internal.store

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Row of the events table.
 *
 * [sequence] is an `AUTOINCREMENT` primary key, so it strictly increases in insertion order and
 * is never reused. [id] is the unique public event id.
 */
@Entity(
    tableName = EventEntity.TABLE_NAME,
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["timestamp", "sequence"]),
    ],
)
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sequence")
    val sequence: Long = 0,
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "properties_json")
    val propertiesJson: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "created_date")
    val createdDate: String,
    @ColumnInfo(name = "seen")
    val seen: Boolean = false,
) {
    companion object {
        const val TABLE_NAME = "events"
    }
}

/** Result row of [EventDao.countByDay]. */
internal data class DayCountRow(
    @ColumnInfo(name = "created_date")
    val createdDate: String,
    @ColumnInfo(name = "latest_timestamp")
    val latestTimestamp: Long,
    @ColumnInfo(name = "event_count")
    val count: Int,
)
