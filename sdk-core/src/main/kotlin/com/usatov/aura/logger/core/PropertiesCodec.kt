package com.usatov.aura.logger.core

/**
 * Converts a typed properties object into the string map stored with an event.
 * Used by the typed [EventTracker.track] overload.
 *
 * Implementations should be pure and fast: [encode] runs synchronously on the thread that calls
 * [EventTracker.track].
 */
public fun interface PropertiesCodec<in T> {
    /**
     * Returns the properties of [value]. The result is validated like a map passed to
     * [EventTracker.track] directly.
     *
     * If this function throws, the event is dropped and a warning is logged; the exception is not
     * propagated to the caller of [EventTracker.track].
     */
    public fun encode(value: T): Map<String, String>
}
