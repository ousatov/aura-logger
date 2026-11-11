package com.usatov.aura.logger.demo.stats

import com.usatov.aura.logger.core.DailyEventCount
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.stats.StatsContract.DayCount
import com.usatov.aura.logger.demo.stats.StatsContract.Intent
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.FixedClock
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import com.usatov.aura.logger.demo.testing.millisOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.ZoneId

class StatsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val tracker = FakeEventTracker()
    private val clock = FixedClock(millisOf("UTC", 2026, 3, 15, 10))
    private var zone = "UTC"
    private val vm by lazy { StatsViewModel(tracker, clock, ZoneProvider { ZoneId.of(zone) }) }

    private fun day(date: String, latest: Long, count: Int) = DailyEventCount(date, latest, count)

    @Test
    fun `initially loading and not empty`() {
        assertTrue(vm.state.value.isLoading)
        assertFalse(vm.state.value.isEmpty)
        assertEquals(0, tracker.statsCalls)
    }

    @Test
    fun `visible loads immediately`() = runTest(main.dispatcher) {
        tracker.stats = EventStats(totalCount = 5, todayCount = 2, dailyCounts = listOf(day("15/03/2026", clock.millis, 5)))

        vm.onIntent(Intent.Visible)
        runCurrent()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(5, state.total)
        assertEquals(2, state.today)
        assertEquals(listOf(DayCount("15/03/2026", 5)), state.days)
        assertFalse(state.loadFailed)
        assertEquals(1, tracker.statsCalls)
    }

    @Test
    fun `refreshes every 3 seconds while visible and stops when hidden`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.Visible)
        runCurrent()
        advanceTimeBy(3_000); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(3, tracker.statsCalls)

        tracker.stats = EventStats(9, 9, listOf(day("15/03/2026", clock.millis, 9)))
        advanceTimeBy(3_000); runCurrent()
        assertEquals(9, vm.state.value.total)

        vm.onIntent(Intent.Hidden)
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(4, tracker.statsCalls)
    }

    @Test
    fun `no refresh before the interval has passed`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.Visible)
        runCurrent()
        advanceTimeBy(2_999); runCurrent()
        assertEquals(1, tracker.statsCalls)
    }

    @Test
    fun `repeated visible does not start a second loop`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.Visible)
        vm.onIntent(Intent.Visible)
        runCurrent()
        vm.onIntent(Intent.Visible)
        runCurrent()
        advanceTimeBy(3_000); runCurrent()

        assertTrue(tracker.statsCalls <= 3, "calls: ${tracker.statsCalls}")
        val before = tracker.statsCalls
        advanceTimeBy(3_000); runCurrent()
        assertEquals(before + 1, tracker.statsCalls)
    }

    @Test
    fun `visible again after hidden resumes refreshing`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.Visible); runCurrent()
        vm.onIntent(Intent.Hidden); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        vm.onIntent(Intent.Visible); runCurrent()
        advanceTimeBy(3_000); runCurrent()

        assertEquals(3, tracker.statsCalls)
    }

    @Test
    fun `only the last 7 calendar days are shown, newest first`() = runTest(main.dispatcher) {
        tracker.stats = EventStats(
            totalCount = 40, todayCount = 1,
            dailyCounts = listOf(
                day("16/03/2026", millisOf("UTC", 2026, 3, 16, 9), 3), // future (time travel): not in the last 7 days
                day("15/03/2026", millisOf("UTC", 2026, 3, 15, 9), 1),
                day("12/03/2026", millisOf("UTC", 2026, 3, 12, 23, 59), 4),
                day("09/03/2026", millisOf("UTC", 2026, 3, 9, 0, 0), 6), // 6 days before today: included
                day("08/03/2026", millisOf("UTC", 2026, 3, 8, 23, 59, 59), 7), // 7 days before: excluded
                day("31/12/2025", millisOf("UTC", 2025, 12, 31, 12), 19),
            ),
        )

        vm.onIntent(Intent.Visible)
        runCurrent()

        assertEquals(
            listOf(DayCount("15/03/2026", 1), DayCount("12/03/2026", 4), DayCount("09/03/2026", 6)),
            vm.state.value.days,
        )
        assertEquals(40, vm.state.value.total)
    }

    @Test
    fun `the 7-day window uses the device time zone`() = runTest(main.dispatcher) {
        zone = "Europe/Kyiv" // UTC+2 in March before DST
        clock.millis = millisOf(zone, 2026, 3, 15, 10)
        tracker.stats = EventStats(
            totalCount = 2, todayCount = 0,
            dailyCounts = listOf(
                // 08/03 23:30 UTC = 09/03 01:30 in Kyiv: inside the window there.
                day("09/03/2026", millisOf("UTC", 2026, 3, 8, 23, 30), 2),
            ),
        )

        vm.onIntent(Intent.Visible)
        runCurrent()

        assertEquals(listOf(DayCount("09/03/2026", 2)), vm.state.value.days)
    }

    @Test
    fun `empty when loaded and there are no events`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.Visible)
        runCurrent()

        assertTrue(vm.state.value.isEmpty)
        assertTrue(vm.state.value.days.isEmpty())
    }

    @Test
    fun `a failed refresh keeps the previous values and recovers`() = runTest(main.dispatcher) {
        tracker.stats = EventStats(7, 7, listOf(day("15/03/2026", clock.millis, 7)))
        vm.onIntent(Intent.Visible)
        runCurrent()

        tracker.statsFailure = IllegalStateException("db")
        advanceTimeBy(3_000); runCurrent()
        assertTrue(vm.state.value.loadFailed)
        assertEquals(7, vm.state.value.total)

        tracker.statsFailure = null
        advanceTimeBy(3_000); runCurrent()
        assertFalse(vm.state.value.loadFailed)
    }

    @Test
    fun `a failing first load leaves the screen out of the loading state`() = runTest(main.dispatcher) {
        tracker.statsFailure = IllegalStateException("not initialised")

        vm.onIntent(Intent.Visible)
        runCurrent()

        assertTrue(vm.state.value.loadFailed)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(2, tracker.statsCalls)
    }
}
