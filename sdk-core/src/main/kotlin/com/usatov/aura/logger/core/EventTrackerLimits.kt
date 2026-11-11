package com.usatov.aura.logger.core

/**
 * Validation limits applied by [EventTracker.track].
 *
 * An event that violates any limit is dropped as a whole and a warning is logged; nothing is
 * truncated and nothing is thrown to the caller.
 */
public object EventTrackerLimits {
    /** Maximum length of an event name. A name must also not be blank. */
    public const val MAX_NAME_LENGTH: Int = 128

    /** Maximum number of properties of one event. */
    public const val MAX_PROPERTIES: Int = 50

    /** Maximum length of a property key. A key must also not be blank. */
    public const val MAX_PROPERTY_KEY_LENGTH: Int = 128

    /** Maximum length of a property value. Empty values are allowed. */
    public const val MAX_PROPERTY_VALUE_LENGTH: Int = 1024

    /**
     * Maximum number of events kept in memory when [EventTracker.track] is called before the SDK
     * is initialised. When the buffer is full, the oldest buffered event is dropped and a warning
     * is logged.
     */
    public const val PRE_INIT_BUFFER_CAPACITY: Int = 500
}
