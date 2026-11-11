package com.usatov.aura.logger.demo.events

import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.testing.event
import com.usatov.aura.logger.demo.testing.millisOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId

class EventFormatterTest {

    private var zone = "UTC"
    private val formatter = EventFormatter(ZoneProvider { ZoneId.of(zone) })

    @Test
    fun `maps every field`() {
        val item = formatter.format(event("id-1", millisOf("UTC", 2026, 3, 5, 7, 8, 9), name = "button_click", seen = true))

        assertEquals(EventItem("id-1", "05/03/2026 07:08:09", "button_click", null, seen = true), item)
    }

    @Test
    fun `24-hour clock with leading zeros`() {
        assertEquals("31/12/2025 23:59:59", formatter.format(event("a", millisOf("UTC", 2025, 12, 31, 23, 59, 59))).timestamp)
        assertEquals("01/01/2026 00:00:00", formatter.format(event("b", millisOf("UTC", 2026, 1, 1))).timestamp)
    }

    @Test
    fun `uses the device time zone at display time`() {
        val timestamp = millisOf("UTC", 2026, 3, 15, 23, 30)
        zone = "Asia/Tokyo"

        assertEquals("16/03/2026 08:30:00", formatter.format(event("a", timestamp)).timestamp)
    }

    @Test
    fun `properties as key value pairs in stored order`() {
        val item = formatter.format(event("a", 0, properties = linkedMapOf("screen" to "home", "action" to "submit")))

        assertEquals("screen: home, action: submit", item.properties)
    }

    @Test
    fun `single and empty-value properties`() {
        assertEquals("k: v", formatter.format(event("a", 0, properties = mapOf("k" to "v"))).properties)
        assertEquals("k: ", formatter.format(event("a", 0, properties = mapOf("k" to ""))).properties)
    }

    @Test
    fun `no properties gives null`() {
        assertNull(formatter.format(event("a", 0)).properties)
    }
}
