package com.usatov.aura.logger.core.spi

import com.usatov.aura.logger.core.InternalAuraLoggerApi

/**
 * Event to be inserted into an [EventStore]. Field values are final; the store does not change them.
 *
 * @property id unique event id.
 * @property name event name.
 * @property propertiesJson properties encoded as a JSON object, `{}` when there are none.
 * @property timestamp creation time in Unix epoch milliseconds.
 * @property createdDate creation day in `DD/MM/YYYY` format.
 */
@InternalAuraLoggerApi
public data class NewEventRecord(
    val id: String,
    val name: String,
    val propertiesJson: String,
    val timestamp: Long,
    val createdDate: String,
)

/**
 * Event as stored in an [EventStore].
 *
 * @property sequence strictly increasing number assigned by the store on insert, in insertion
 * order. Never reused, also not after deletions. Breaks ties between equal timestamps.
 * @property seen whether the event was marked seen.
 */
@InternalAuraLoggerApi
public data class StoredEventRecord(
    val sequence: Long,
    val id: String,
    val name: String,
    val propertiesJson: String,
    val timestamp: Long,
    val createdDate: String,
    val seen: Boolean,
)

/**
 * Number of events with one `createdDate`.
 *
 * @property latestTimestamp the newest timestamp among these events.
 */
@InternalAuraLoggerApi
public data class DayCountRecord(
    val createdDate: String,
    val latestTimestamp: Long,
    val count: Int,
)
