package com.usatov.aura.logger.demo.debug

import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Intent
import com.usatov.aura.logger.demo.testing.FixedClock
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import com.usatov.aura.logger.demo.testing.millisOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class TimeShiftViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val base = FixedClock(millisOf("UTC", 2026, 3, 15, 10, 0, 0))
    private val clock = TimeShiftClock().apply { systemClock = base }
    private val vm by lazy { TimeShiftViewModel(clock, ZoneProvider { ZoneId.of("UTC") }) }

    @Test
    fun `steps match the contract`() {
        assertEquals(listOf((-1).days, (-1).hours, 1.hours, 1.days), TimeShiftContract.STEPS)
    }

    @Test
    fun `shows the shifted time and offset`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.ShiftBy(1.days))
        runCurrent()

        assertEquals(1.days, vm.state.value.offset)
        assertEquals("16/03/2026 10:00:00", vm.state.value.shiftedNow)
        assertEquals(1.days, clock.offset.value)
    }

    @Test
    fun `reset`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.ShiftBy((-1).hours))
        vm.onIntent(Intent.Reset)
        runCurrent()

        assertEquals(Duration.ZERO, vm.state.value.offset)
        assertEquals("15/03/2026 10:00:00", vm.state.value.shiftedNow)
    }

    @Test
    fun `state follows offset changes made outside the screen`() = runTest(main.dispatcher) {
        vm.state.value
        runCurrent()

        clock.shiftBy(2.hours)
        runCurrent()

        assertEquals(2.hours, vm.state.value.offset)
        assertEquals("15/03/2026 12:00:00", vm.state.value.shiftedNow)
    }
}
