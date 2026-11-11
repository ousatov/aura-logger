package com.usatov.aura.logger.demo.debug

import com.usatov.aura.logger.demo.testing.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class TimeShiftClockTest {

    private val base = FixedClock(1_000_000_000L)
    private val clock = TimeShiftClock().apply { systemClock = base }

    @Test
    fun `starts without offset`() {
        assertEquals(Duration.ZERO, clock.offset.value)
        assertEquals(base.millis, clock.now().toEpochMilliseconds())
    }

    @Test
    fun `shifts accumulate, also negative`() {
        clock.shiftBy(1.days)
        clock.shiftBy((-1).hours)

        assertEquals(23.hours, clock.offset.value)
        assertEquals(base.millis + 23.hours.inWholeMilliseconds, clock.now().toEpochMilliseconds())
    }

    @Test
    fun `follows the base clock`() {
        clock.shiftBy(1.hours)
        base.millis += 5_000

        assertEquals(base.millis + 1.hours.inWholeMilliseconds, clock.now().toEpochMilliseconds())
    }

    @Test
    fun `reset returns to the base time`() {
        clock.shiftBy((-3).days)
        clock.reset()

        assertEquals(Duration.ZERO, clock.offset.value)
        assertEquals(base.millis, clock.now().toEpochMilliseconds())
    }

    @Test
    fun `concurrent shifts are not lost`() {
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        repeat(8) {
            pool.execute {
                repeat(1000) { clock.shiftBy(1.milliseconds) }
                done.countDown()
            }
        }
        done.await(10, TimeUnit.SECONDS)
        pool.shutdown()

        assertEquals(8000.milliseconds, clock.offset.value)
    }
}
