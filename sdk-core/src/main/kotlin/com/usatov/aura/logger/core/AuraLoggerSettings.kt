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
 * @property enforceLimitOnWrite `true`: the [EventTrackerConfig.maxEvents] limit is applied right
 * after every write on the background write queue (and by the cleanup); `false`: only by the
 * cleanup, so the number of events can exceed the limit until the next cleanup run.
 * @property limitOverflowStrategy what happens to new events when the limit is reached and no
 * event can be deleted to make room. See [LimitOverflowStrategy].
 *
 * Why [enforceLimitOnWrite] and [limitOverflowStrategy] are settings: the assignment states that the
 * cleanup deletes events when their number exceeds the limit, requires rapid tracking "without data
 * loss" and forbids clearing events the user has never seen. These requirements conflict when the
 * limit is reached with unseen events only. The defaults keep every new event and apply the limit
 * immediately. If the client (the assignment owner) wants another trade-off, change the defaults
 * here; if SDK users should choose themselves, these settings can be moved to the public
 * [EventTrackerConfig].
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
    val enforceLimitOnWrite: Boolean = true,
    val limitOverflowStrategy: LimitOverflowStrategy = LimitOverflowStrategy.KEEP_NEW_EVENTS,
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

/**
 * Behaviour when a write would exceed [EventTrackerConfig.maxEvents] and no stored event can be
 * deleted to make room (all are protected by [UnseenEventsProtection.onLimit]).
 * Applies only when [AuraLoggerSettings.enforceLimitOnWrite] is `true`.
 */
@InternalAuraLoggerApi
public enum class LimitOverflowStrategy {
    /** Store the new events anyway; the limit is exceeded until events are seen. No data loss. Default. */
    KEEP_NEW_EVENTS,

    /**
     * Drop the new events that do not fit, with a warning. Events are considered in call order: an
     * event is stored if it fits, otherwise the oldest seen event is deleted to make room, and only
     * if no seen event is left is the new event dropped. Earlier calls therefore win over later ones.
     */
    DROP_NEW_EVENTS,
}
