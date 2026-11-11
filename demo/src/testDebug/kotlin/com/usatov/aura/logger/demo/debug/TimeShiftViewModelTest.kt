package com.usatov.aura.logger.demo.debug

import com.usatov.aura.logger.demo.debug.TimeShiftContract.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimeShiftViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newClock() = TimeShiftClock().apply {
        systemClock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(START_MILLIS + currentTime)
        }
    }

    @Test
    fun `displayed time advances without an offset change`() = runTest(dispatcher) {
        val viewModel = TimeShiftViewModel(newClock(), { ZoneOffset.UTC })
        val ticker = launch { viewModel.keepTimeFresh() }
        runCurrent()
        assertEquals("01/01/2030 10:00:00", viewModel.state.value.shiftedNow)

        testScheduler.advanceTimeBy(3.seconds.inWholeMilliseconds)
        runCurrent()
        assertEquals("01/01/2030 10:00:03", viewModel.state.value.shiftedNow)
        ticker.cancel()
    }

    @Test
    fun `shift and reset update the state immediately`() = runTest(dispatcher) {
        val viewModel = TimeShiftViewModel(newClock(), { ZoneOffset.UTC })
        runCurrent()

        viewModel.onIntent(Intent.ShiftBy(1.hours))
        runCurrent()
        assertEquals(1.hours, viewModel.state.value.offset)
        assertEquals("01/01/2030 11:00:00", viewModel.state.value.shiftedNow)

        viewModel.onIntent(Intent.Reset)
        runCurrent()
        assertEquals(kotlin.time.Duration.ZERO, viewModel.state.value.offset)
        assertEquals("01/01/2030 10:00:00", viewModel.state.value.shiftedNow)
    }

    private companion object {
        // 2030-01-01T10:00:00Z
        const val START_MILLIS = 1_893_492_000_000L
    }
}
