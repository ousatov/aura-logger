package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.testing.millisOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EngineTrackingTest {

    private suspend fun TestScope.startedHarness(settings: AuraLoggerSettings = AuraLoggerSettings()): EngineHarness =
        EngineHarness(this, settings).also {
            it.start()
            runCurrent()
        }

    @Nested
    inner class Recording {
        @Test
        fun `tracked event is stored with generated id, timestamp, date and properties`() = runTest {
            val h = startedHarness()

            h.engine.track("button_clicked", mapOf("button_id" to "submit"))
            runCurrent()

            val stored = h.store.all.single()
            assertEquals("id-1", stored.id)
            assertEquals("button_clicked", stored.name)
            assertEquals(T0, stored.timestamp)
            assertEquals("15/03/2026", stored.createdDate)
            assertEquals(mapOf("button_id" to "submit"), decode(stored.propertiesJson))
            assertEquals(false, stored.seen)
        }

        @Test
        fun `event without properties is stored as an empty JSON object`() = runTest {
            val h = startedHarness()

            h.engine.track("app_opened")
            h.engine.track("app_closed", emptyMap())
            runCurrent()

            h.store.all.forEach { assertEquals("{}", it.propertiesJson) }
        }

        @Test
        fun `id and timestamp are taken at call time, not at write time`() = runTest {
            val h = startedHarness()

            h.engine.track("first")
            h.clock.advanceBy(5_000)
            h.engine.track("second")
            h.clock.advanceBy(60_000)
            runCurrent()

            val byName = h.store.all.associateBy { it.name }
            assertEquals(T0, byName.getValue("first").timestamp)
            assertEquals(T0 + 5_000, byName.getValue("second").timestamp)
            assertEquals("id-1", byName.getValue("first").id)
            assertEquals("id-2", byName.getValue("second").id)
        }

        @Test
        fun `events are written in call order`() = runTest {
            val h = startedHarness()

            repeat(20) { h.engine.track("e$it") }
            runCurrent()

            assertEquals((0 until 20).map { "e$it" }, h.store.all.sortedBy { it.sequence }.map { it.name })
        }

        @Test
        fun `createdDate uses the device time zone at call time with leading zeros`() = runTest {
            val h = startedHarness()
            h.timeZone.zoneId = "Pacific/Kiritimati" // UTC+14
            h.clock.nowMillis = millisOf("UTC", 2026, 1, 4, 11) // 05/01/2026 01:00 local

            h.engine.track("e")
            h.timeZone.zoneId = "UTC"
            runCurrent()

            assertEquals("05/01/2026", h.store.all.single().createdDate)
        }

        @Test
        fun `createdDate at midnight boundaries`() = runTest {
            val h = startedHarness()
            h.timeZone.zoneId = "Europe/Kyiv"
            h.clock.nowMillis = millisOf("Europe/Kyiv", 2025, 12, 31, 23, 59, 59, 999)
            h.engine.track("last_ms_of_year")
            h.clock.nowMillis = millisOf("Europe/Kyiv", 2026, 1, 1)
            h.engine.track("first_ms_of_year")
            runCurrent()

            val byName = h.store.all.associateBy { it.name }
            assertEquals("31/12/2025", byName.getValue("last_ms_of_year").createdDate)
            assertEquals("01/01/2026", byName.getValue("first_ms_of_year").createdDate)
        }

        @Test
        fun `property order is preserved`() = runTest {
            val h = startedHarness()
            val properties = linkedMapOf("zeta" to "1", "alpha" to "2", "mid" to "3", "beta" to "4")

            h.engine.track("ordered", properties)
            runCurrent()

            val event = h.engine.getRecentEvents(1).single()
            assertEquals(listOf("zeta", "alpha", "mid", "beta"), event.properties.keys.toList())
        }

        @Test
        fun `special characters survive the JSON round trip`() = runTest {
            val h = startedHarness()
            val properties = linkedMapOf(
                "quote\"key" to "value with \"quotes\"",
                "back\\slash" to "C:\\path\\file",
                "multi\nline" to "line1\nline2\r\n\ttab",
                "unicode" to "Привіт 👋 日本語",
                "json" to "{\"nested\": [1, 2]}",
                "null" to "null",
                "empty" to "",
                "control" to "\u0000\u0001\u001f",
                "html" to "<script>alert('x')</script>",
            )

            h.engine.track("weird \"name\"\n👋", properties)
            runCurrent()

            val event = h.engine.getRecentEvents(1).single()
            assertEquals("weird \"name\"\n👋", event.name)
            assertEquals(properties, event.properties)
            assertEquals(properties, decode(h.store.all.single().propertiesJson))
        }

        @Test
        fun `duplicate tracking of the same name produces distinct events`() = runTest {
            val h = startedHarness()

            repeat(3) { h.engine.track("same", mapOf("k" to "v")) }
            runCurrent()

            assertEquals(3, h.store.all.map { it.id }.toSet().size)
        }
    }

    @Nested
    inner class Validation {
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   ", "\t", "\n", " \t\n "])
        fun `blank names are dropped with a warning`(name: String) = runTest {
            val h = startedHarness()

            assertDoesNotThrow { h.engine.track(name) }
            runCurrent()

            assertTrue(h.store.all.isEmpty())
            assertTrue(h.logger.warnings.isNotEmpty())
        }

        @Test
        fun `name length limit is inclusive`() = runTest {
            val h = startedHarness()

            h.engine.track("a".repeat(128))
            h.engine.track("b".repeat(129))
            runCurrent()

            assertEquals(listOf("a".repeat(128)), h.store.all.map { it.name })
            assertEquals(1, h.logger.warnings.size)
        }

        @Test
        fun `property count limit is inclusive`() = runTest {
            val h = startedHarness()

            h.engine.track("fifty", (1..50).associate { "k$it" to "v" })
            h.engine.track("fifty_one", (1..51).associate { "k$it" to "v" })
            runCurrent()

            assertEquals(listOf("fifty"), h.store.all.map { it.name })
        }

        @Test
        fun `property key rules`() = runTest {
            val h = startedHarness()

            h.engine.track("ok_key", mapOf("k".repeat(128) to "v"))
            h.engine.track("long_key", mapOf("k".repeat(129) to "v"))
            h.engine.track("empty_key", mapOf("" to "v"))
            h.engine.track("blank_key", mapOf("  " to "v"))
            runCurrent()

            assertEquals(listOf("ok_key"), h.store.all.map { it.name })
        }

        @Test
        fun `property value rules`() = runTest {
            val h = startedHarness()

            h.engine.track("ok_value", mapOf("k" to "v".repeat(1024)))
            h.engine.track("empty_value", mapOf("k" to ""))
            h.engine.track("blank_value", mapOf("k" to "   "))
            h.engine.track("long_value", mapOf("k" to "v".repeat(1025)))
            runCurrent()

            assertEquals(setOf("ok_value", "empty_value", "blank_value"), h.store.all.map { it.name }.toSet())
        }

        @Test
        fun `one invalid property drops the whole event, nothing is truncated`() = runTest {
            val h = startedHarness()

            h.engine.track("mixed", mapOf("good" to "v", "bad" to "x".repeat(2000)))
            runCurrent()

            assertTrue(h.store.all.isEmpty())
            assertEquals(1, h.logger.warnings.size)
        }

        @Test
        fun `validation uses the injected settings`() = runTest {
            val h = startedHarness(
                AuraLoggerSettings(maxEventNameLength = 3, maxPropertyCount = 1, maxPropertyKeyLength = 2, maxPropertyValueLength = 2),
            )

            h.engine.track("abc", mapOf("ab" to "xy"))
            h.engine.track("abcd")
            h.engine.track("abc", mapOf("a" to "1", "b" to "2"))
            h.engine.track("abc", mapOf("abc" to "1"))
            h.engine.track("abc", mapOf("a" to "123"))
            runCurrent()

            assertEquals(1, h.store.all.size)
        }

        @Test
        fun `valid events after an invalid one are still stored`() = runTest {
            val h = startedHarness()

            h.engine.track("")
            h.engine.track("valid")
            runCurrent()

            assertEquals(listOf("valid"), h.store.all.map { it.name })
        }
    }

    @Nested
    inner class NullSafety {
        // Java or unchecked-cast callers can pass nulls despite the Kotlin types; track must never throw.

        @Suppress("UNCHECKED_CAST")
        @Test
        fun `map with a null value does not throw`() = runTest {
            val h = startedHarness()
            val properties = mapOf("k" to null) as Map<String, String>

            assertDoesNotThrow { h.engine.track("nullable_value", properties) }
            runCurrent()
        }

        @Suppress("UNCHECKED_CAST")
        @Test
        fun `map with a null key does not throw`() = runTest {
            val h = startedHarness()
            val properties = mapOf(null to "v") as Map<String, String>

            assertDoesNotThrow { h.engine.track("nullable_key", properties) }
            runCurrent()
        }

        @Suppress("UNCHECKED_CAST")
        @Test
        fun `markSeen with null ids does not throw`() = runTest {
            val h = startedHarness()
            val ids = listOf("a", null) as Collection<String>

            assertDoesNotThrow { h.engine.markSeen(ids) }
            runCurrent()
        }

        @Suppress("UNCHECKED_CAST")
        @Test
        fun `codec returning a map with nulls does not throw`() = runTest {
            val h = startedHarness()
            val codec = PropertiesCodec<Unit> { mapOf("k" to null) as Map<String, String> }

            assertDoesNotThrow { h.engine.track("codec_null", Unit, codec) }
            runCurrent()
        }
    }

    @Nested
    inner class TypedProperties {
        private val codec = PropertiesCodec<Purchase> { mapOf("sku" to it.sku, "price" to it.price.toString()) }

        @Test
        fun `typed overload stores the encoded properties`() = runTest {
            val h = startedHarness()

            h.engine.track("purchase_completed", Purchase("sku-1", 199), codec)
            runCurrent()

            assertEquals(mapOf("sku" to "sku-1", "price" to "199"), decode(h.store.all.single().propertiesJson))
        }

        @Test
        fun `codec runs synchronously on the calling thread`() = runTest {
            val h = startedHarness()
            val callerThread = Thread.currentThread()
            var encodeThread: Thread? = null
            var calls = 0

            h.engine.track("e", Unit, PropertiesCodec { calls++; encodeThread = Thread.currentThread(); emptyMap() })

            assertEquals(1, calls)
            assertEquals(callerThread, encodeThread)
        }

        @Test
        fun `codec exception drops the event with a warning and is not propagated`() = runTest {
            val h = startedHarness()

            assertDoesNotThrow {
                h.engine.track("broken", Unit, PropertiesCodec { throw IllegalStateException("boom") })
            }
            h.engine.track("after")
            runCurrent()

            assertEquals(listOf("after"), h.store.all.map { it.name })
            assertTrue(h.logger.warnings.isNotEmpty())
        }

        @Test
        fun `codec Error subclasses are not propagated either`() = runTest {
            val h = startedHarness()

            assertDoesNotThrow { h.engine.track("broken", Unit, PropertiesCodec { throw NotImplementedError() }) }
        }

        @Test
        fun `codec result is validated like a plain map`() = runTest {
            val h = startedHarness()

            h.engine.track("e", Unit, PropertiesCodec { mapOf("k" to "v".repeat(1025)) })
            runCurrent()

            assertTrue(h.store.all.isEmpty())
        }
    }

    @Nested
    inner class StorageFailures {
        @Test
        fun `insert failure loses the event, logs an error and the queue keeps working`() = runTest {
            val h = startedHarness()
            h.store.failNextInsert = RuntimeException("disk full")

            assertDoesNotThrow { h.engine.track("lost") }
            runCurrent()
            h.engine.track("kept")
            runCurrent()

            assertEquals(listOf("kept"), h.store.all.map { it.name })
            assertTrue(h.logger.errors.isNotEmpty())
        }

        @Test
        fun `persistent insert failure never throws to the caller`() = runTest {
            val h = startedHarness()
            h.store.failAllInserts = RuntimeException("db closed")

            assertDoesNotThrow { repeat(50) { h.engine.track("e$it") } }
            runCurrent()

            assertTrue(h.store.all.isEmpty())
            assertTrue(h.logger.errors.isNotEmpty())
        }
    }

    private data class Purchase(val sku: String, val price: Int)

    private fun decode(json: String): Map<String, String> =
        Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
}
