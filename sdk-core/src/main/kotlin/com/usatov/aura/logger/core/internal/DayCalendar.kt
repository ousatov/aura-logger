package com.usatov.aura.logger.core.internal

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar-day arithmetic in a given IANA time zone.
 *
 * All functions are pure and thread-safe. An unknown time zone id falls back to the JVM default
 * time zone.
 */
internal object DayCalendar {

    /** Pattern of [com.usatov.aura.logger.core.TrackedEvent.createdDate]. */
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    /** Formats the day of [epochMillis] in [zoneId] as `DD/MM/YYYY`. */
    fun formatDate(epochMillis: Long, zoneId: String): String =
        localDate(epochMillis, zoneId).format(DATE_FORMATTER)

    /**
     * Returns the start (first millisecond) of the day that is [daysBefore] days before the day
     * of [epochMillis], in [zoneId].
     */
    fun startOfDay(epochMillis: Long, zoneId: String, daysBefore: Long = 0): Long {
        val zone = zone(zoneId)
        return localDate(epochMillis, zone).minusDays(daysBefore).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Returns the start of the day after the day of [epochMillis], in [zoneId]. */
    fun startOfNextDay(epochMillis: Long, zoneId: String): Long {
        val zone = zone(zoneId)
        return localDate(epochMillis, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun localDate(epochMillis: Long, zoneId: String): LocalDate = localDate(epochMillis, zone(zoneId))

    private fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private fun zone(zoneId: String): ZoneId =
        try {
            ZoneId.of(zoneId)
        } catch (_: DateTimeException) {
            ZoneId.systemDefault()
        }
}
