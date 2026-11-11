package com.usatov.aura.logger.core.internal

import com.usatov.aura.logger.core.AuraLoggerSettings

/** Validates events passed to `track` against the limits of [AuraLoggerSettings]. Thread-safe. */
internal class EventValidator(private val settings: AuraLoggerSettings) {

    /** Returns the reason why the event is invalid, or `null` if it is valid. */
    fun validate(name: String, properties: Map<String, String>): String? {
        if (name.isBlank()) return "name is blank"
        if (name.length > settings.maxEventNameLength) {
            return "name is longer than ${settings.maxEventNameLength} characters"
        }
        if (properties.size > settings.maxPropertyCount) {
            return "more than ${settings.maxPropertyCount} properties"
        }
        for ((key, value) in properties) {
            if (key.isBlank()) return "a property key is blank"
            if (key.length > settings.maxPropertyKeyLength) {
                return "property key '${key.abbreviate()}' is longer than ${settings.maxPropertyKeyLength} characters"
            }
            if (value.length > settings.maxPropertyValueLength) {
                return "value of property '${key.abbreviate()}' is longer than ${settings.maxPropertyValueLength} characters"
            }
        }
        return null
    }
}

/** Shortens a client-provided string for log messages. */
internal fun String.abbreviate(maxLength: Int = 64): String =
    if (length <= maxLength) this else take(maxLength) + "…"
