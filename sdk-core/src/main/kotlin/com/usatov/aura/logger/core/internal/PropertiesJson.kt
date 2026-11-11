package com.usatov.aura.logger.core.internal

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts event properties to and from the JSON object string stored with an event.
 *
 * The iteration order of the properties is preserved in both directions. Thread-safe.
 */
internal object PropertiesJson {

    /** Encodes [properties] as a JSON object of strings; `{}` when empty. */
    fun encode(properties: Map<String, String>): String =
        JsonObject(properties.mapValues { (_, value) -> JsonPrimitive(value) }).toString()

    /**
     * Decodes a JSON object of strings written by [encode].
     *
     * @throws IllegalArgumentException if [json] is not such an object.
     */
    fun decode(json: String): Map<String, String> {
        if (json.isEmpty()) return emptyMap()
        return try {
            Json.parseToJsonElement(json).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Malformed properties JSON", e)
        }
    }
}
