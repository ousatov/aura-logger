package com.usatov.aura.logger.core

/**
 * Technical settings of the SDK that clients cannot change. All of them live here so that they
 * are changed in one place; the SDK passes [AuraLoggerSettings] with default values to its
 * components, tests can pass other values (for example, a small pre-init buffer).
 *
 * The client-facing retention policy (retention period, `maxEvents` limit, unseen protection) is
 * not here: it is [EventTrackerConfig], passed by the client.
 *
 * When changing a validation value, also update the KDoc of [EventTracker.track], which states
 * the values for clients.
 *
 * @property preInitBufferCapacity maximum number of events kept in memory when
 * [EventTracker.track] is called before initialisation. On overflow the oldest buffered event is
 * dropped and a warning is logged.
 * @property maxEventNameLength validation: maximum length of an event name (names must also not be blank).
 * @property maxPropertyCount validation: maximum number of properties of one event.
 * @property maxPropertyKeyLength validation: maximum length of a property key (keys must also not be blank).
 * @property maxPropertyValueLength validation: maximum length of a property value (empty values are allowed).
 * @property cleanupPeriodHours period of the background cleanup.
 * @property cleanupInitialDelayHours delay of the first background cleanup after it is scheduled.
 * @property databaseName file name of the events database.
 * @property configFileName file name of the persisted [EventTrackerConfig].
 * @property periodicCleanupWorkName unique name of the periodic cleanup work.
 * @property oneTimeCleanupWorkName unique name of the one-time cleanup work scheduled by
 * [EventTracker.updateConfig].
 */
@InternalAuraLoggerApi
public data class AuraLoggerSettings(
    val preInitBufferCapacity: Int = 500,
    val maxEventNameLength: Int = 128,
    val maxPropertyCount: Int = 50,
    val maxPropertyKeyLength: Int = 128,
    val maxPropertyValueLength: Int = 1024,
    val cleanupPeriodHours: Long = 24,
    val cleanupInitialDelayHours: Long = 1,
    val databaseName: String = "aura_logger_events.db",
    val configFileName: String = "aura_logger_config",
    val periodicCleanupWorkName: String = "aura_logger_periodic_cleanup",
    val oneTimeCleanupWorkName: String = "aura_logger_one_time_cleanup",
) {
    init {
        require(preInitBufferCapacity > 0) { "preInitBufferCapacity must be positive" }
        require(maxEventNameLength > 0) { "maxEventNameLength must be positive" }
        require(maxPropertyCount >= 0) { "maxPropertyCount must not be negative" }
        require(maxPropertyKeyLength > 0) { "maxPropertyKeyLength must be positive" }
        require(maxPropertyValueLength >= 0) { "maxPropertyValueLength must not be negative" }
        require(cleanupPeriodHours > 0) { "cleanupPeriodHours must be positive" }
        require(cleanupInitialDelayHours >= 0) { "cleanupInitialDelayHours must not be negative" }
    }
}
