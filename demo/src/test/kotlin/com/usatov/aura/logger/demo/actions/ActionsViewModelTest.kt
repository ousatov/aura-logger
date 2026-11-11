package com.usatov.aura.logger.demo.actions

import app.cash.turbine.test
import com.usatov.aura.logger.demo.actions.ActionsContract.Effect
import com.usatov.aura.logger.demo.actions.ActionsContract.Intent
import com.usatov.aura.logger.demo.testing.CallLog
import com.usatov.aura.logger.demo.testing.FakeAppEvents
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ActionsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val log = CallLog()
    private val appEvents = FakeAppEvents(log)
    private val tracker = FakeEventTracker(log)
    private val vm by lazy { ActionsViewModel(appEvents, tracker) }

    @Test
    fun `track event`() = runTest(main.dispatcher) {
        vm.effects.test {
            vm.onIntent(Intent.TrackEvent)
            runCurrent()
            assertEquals(Effect.Tracked(1), awaitItem())
        }
        assertEquals(listOf("testEvent"), appEvents.calls)
    }

    @Test
    fun `track 100 events`() = runTest(main.dispatcher) {
        vm.effects.test {
            vm.onIntent(Intent.TrackHundredEvents)
            runCurrent()
            assertEquals(Effect.Tracked(100), awaitItem())
        }
        assertEquals(listOf("testEvents:100"), appEvents.calls)
        assertEquals(100, ActionsContract.STRESS_COUNT)
    }

    @Test
    fun `track with properties`() = runTest(main.dispatcher) {
        vm.effects.test {
            vm.onIntent(Intent.TrackWithProperties)
            runCurrent()
            assertEquals(Effect.Tracked(1), awaitItem())
        }
        assertEquals(listOf("testEventWithProperties"), appEvents.calls)
    }

    @Test
    fun `clear asks for confirmation and dismiss closes it without clearing`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.ClearRequested)
        assertTrue(vm.state.value.confirmClear)

        vm.onIntent(Intent.ClearDismissed)
        runCurrent()

        assertFalse(vm.state.value.confirmClear)
        assertEquals(0, tracker.clearCalls)
    }

    @Test
    fun `confirmed clear runs clearAll, tracks the result and reports it`() = runTest(main.dispatcher) {
        tracker.clearResult = 12
        vm.onIntent(Intent.ClearRequested)

        vm.effects.test {
            vm.onIntent(Intent.ClearConfirmed)
            assertFalse(vm.state.value.confirmClear)
            runCurrent()
            assertEquals(Effect.Cleared(12), awaitItem())
        }
        assertFalse(vm.state.value.isClearing)
        assertEquals(listOf("clearAll", "appEvents:eventsCleared:12"), log.calls)
    }

    @Test
    fun `clearing state is set while clearAll runs and further clears are ignored`() = runTest(main.dispatcher) {
        tracker.clearGate = CompletableDeferred()
        vm.onIntent(Intent.ClearRequested)
        vm.onIntent(Intent.ClearConfirmed)
        runCurrent()
        assertTrue(vm.state.value.isClearing)

        vm.onIntent(Intent.ClearRequested)
        assertFalse(vm.state.value.confirmClear)
        vm.onIntent(Intent.ClearConfirmed)
        runCurrent()
        assertEquals(1, tracker.clearCalls)

        tracker.clearGate!!.complete(Unit)
        runCurrent()
        assertFalse(vm.state.value.isClearing)
    }

    @Test
    fun `failed clear reports an error and tracks nothing`() = runTest(main.dispatcher) {
        tracker.clearFailure = IllegalStateException("db")
        vm.onIntent(Intent.ClearRequested)

        vm.effects.test {
            vm.onIntent(Intent.ClearConfirmed)
            runCurrent()
            assertEquals(Effect.ClearFailed, awaitItem())
        }
        assertFalse(vm.state.value.isClearing)
        assertTrue(appEvents.calls.none { it.startsWith("eventsCleared") })
    }

    @Test
    fun `tracking still works while clearing`() = runTest(main.dispatcher) {
        tracker.clearGate = CompletableDeferred()
        vm.onIntent(Intent.ClearRequested)
        vm.onIntent(Intent.ClearConfirmed)
        runCurrent()

        vm.onIntent(Intent.TrackEvent)
        runCurrent()

        assertTrue("testEvent" in appEvents.calls)
    }
}
