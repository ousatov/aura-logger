package com.usatov.aura.logger.core.spi

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.InternalAuraLoggerApi

/** Persistent storage of the [EventTrackerConfig]. Implemented by the `sdk` module (DataStore). */
@InternalAuraLoggerApi
public interface ConfigStore {
    /** Returns the persisted config, or `null` if none was ever written. */
    public suspend fun read(): EventTrackerConfig?

    /** Persists [config], replacing the previous one. Returns after the write is durable. */
    public suspend fun write(config: EventTrackerConfig)
}

/** Schedules background cleanup runs. Implemented by the `sdk` module (WorkManager). */
@InternalAuraLoggerApi
public interface CleanupScheduler {
    /**
     * Schedules the unique periodic cleanup: every 24 hours, first run 1 hour after scheduling.
     * If it is already scheduled, the existing schedule is kept unchanged.
     */
    public fun schedulePeriodic()

    /** Schedules a unique one-time cleanup as soon as possible, replacing a pending one. */
    public fun scheduleOneTime()
}

/** Generates unique event ids. Must be thread-safe. */
@InternalAuraLoggerApi
public fun interface IdGenerator {
    public fun nextId(): String
}

/** Provides the current device time zone. Must be thread-safe. */
@InternalAuraLoggerApi
public fun interface TimeZoneProvider {
    /** Returns an IANA time zone id, for example `"Europe/Kyiv"`. Read on every use: it can change at runtime. */
    public fun currentTimeZoneId(): String
}

/** Diagnostic logger of the SDK. Must be thread-safe and must not throw. */
@InternalAuraLoggerApi
public interface AuraLogger {
    public fun warn(message: String, throwable: Throwable? = null)
    public fun error(message: String, throwable: Throwable? = null)
}
