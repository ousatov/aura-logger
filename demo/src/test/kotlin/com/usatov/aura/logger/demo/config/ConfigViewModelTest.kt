package com.usatov.aura.logger.demo.config

import app.cash.turbine.test
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.config.ConfigContract.Effect
import com.usatov.aura.logger.demo.config.ConfigContract.InputError
import com.usatov.aura.logger.demo.config.ConfigContract.Intent
import com.usatov.aura.logger.demo.testing.CallLog
import com.usatov.aura.logger.demo.testing.FakeAppEvents
import com.usatov.aura.logger.demo.testing.FakeDemoConfigRepository
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException

class ConfigViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val log = CallLog()
    private val repository = FakeDemoConfigRepository(EventTrackerConfig(retentionDays = 7, maxEvents = 100), log)
    private val tracker = FakeEventTracker(log)
    private val appEvents = FakeAppEvents(log)

    private fun TestScope.loaded(): ConfigViewModel = ConfigViewModel(repository, tracker, appEvents).also { runCurrent() }

    @Test
    fun `loads the stored config into the inputs`() = runTest(main.dispatcher) {
        val state = loaded().state.value

        assertEquals("7", state.retentionInput)
        assertEquals("100", state.maxEventsInput)
        assertEquals(EventTrackerConfig(retentionDays = 7, maxEvents = 100), state.savedConfig)
        assertNull(state.retentionError)
        assertNull(state.maxEventsError)
        assertFalse(state.canSave)
    }

    @Test
    fun `a later stored change updates savedConfig but not the inputs being edited`() = runTest(main.dispatcher) {
        val vm = loaded()
        vm.onIntent(Intent.RetentionChanged("9"))

        repository.state.value = EventTrackerConfig(retentionDays = 3, maxEvents = 30)
        runCurrent()

        assertEquals("9", vm.state.value.retentionInput)
        assertEquals("100", vm.state.value.maxEventsInput)
        assertEquals(EventTrackerConfig(retentionDays = 3, maxEvents = 30), vm.state.value.savedConfig)
    }

    @Nested
    inner class Validation {
        @ParameterizedTest
        @CsvSource(
            "'', EMPTY", "'   ', EMPTY", "abc, NOT_A_NUMBER", "1.5, NOT_A_NUMBER", "'7 days', NOT_A_NUMBER",
            "0, OUT_OF_RANGE", "-1, OUT_OF_RANGE", "3651, OUT_OF_RANGE",
        )
        fun `invalid retention`(input: String, error: InputError) = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged(input))

            assertEquals(error, vm.state.value.retentionError)
            assertEquals(input, vm.state.value.retentionInput)
            assertFalse(vm.state.value.canSave)
        }

        @ParameterizedTest
        @CsvSource("'', EMPTY", "x1, NOT_A_NUMBER", "0, OUT_OF_RANGE", "100001, OUT_OF_RANGE")
        fun `invalid max events`(input: String, error: InputError) = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.MaxEventsChanged(input))

            assertEquals(error, vm.state.value.maxEventsError)
            assertFalse(vm.state.value.canSave)
        }

        @ParameterizedTest
        @CsvSource("1", "3650", "' 30 '")
        fun `valid retention boundaries and whitespace`(input: String) = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged(input))

            assertNull(vm.state.value.retentionError)
            assertTrue(vm.state.value.canSave)
        }

        @ParameterizedTest
        @CsvSource("1", "100000")
        fun `valid max events boundaries`(input: String) = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.MaxEventsChanged(input))

            assertNull(vm.state.value.maxEventsError)
            assertTrue(vm.state.value.canSave)
        }

        @Test
        fun `fixing an invalid input clears its error`() = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged("abc"))
            vm.onIntent(Intent.RetentionChanged("5"))

            assertNull(vm.state.value.retentionError)
        }

        @Test
        fun `ranges match the contract`() {
            assertEquals(1..3650, ConfigContract.RETENTION_DAYS_RANGE)
            assertEquals(1..100_000, ConfigContract.MAX_EVENTS_RANGE)
        }
    }

    @Nested
    inner class CanSave {
        @Test
        fun `values equal to the saved config do not enable Save`() = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged("007"))
            vm.onIntent(Intent.MaxEventsChanged(" 100 "))

            assertFalse(vm.state.value.canSave)
        }

        @Test
        fun `not loaded yet`() {
            assertFalse(ConfigContract.State(retentionInput = "5", maxEventsInput = "5").canSave)
        }

        @Test
        fun `not while saving`() {
            val state = ConfigContract.State("5", "5", savedConfig = EventTrackerConfig(), isSaving = true)
            assertFalse(state.canSave)
        }
    }

    @Nested
    inner class Save {
        @Test
        fun `persists, applies and tracks in this order`() = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged("14"))
            vm.onIntent(Intent.MaxEventsChanged("250"))

            vm.effects.test {
                vm.onIntent(Intent.Save)
                runCurrent()
                assertEquals(Effect.Saved, awaitItem())
            }

            val expected = EventTrackerConfig(retentionDays = 14, maxEvents = 250)
            assertEquals(listOf("save", "updateConfig", "appEvents:configChanged:14/250"), log.calls)
            assertEquals(listOf(expected), repository.saved)
            assertEquals(listOf(expected), tracker.updatedConfigs)
            assertEquals(expected, vm.state.value.savedConfig)
            assertFalse(vm.state.value.isSaving)
            assertFalse(vm.state.value.canSave)
        }

        @Test
        fun `save keeps default unseen protection`() = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.MaxEventsChanged("50"))
            vm.onIntent(Intent.Save)
            runCurrent()

            assertEquals(EventTrackerConfig().unseenEventsProtection, tracker.updatedConfigs.single().unseenEventsProtection)
        }

        @Test
        fun `save is ignored when it is not allowed`() = runTest(main.dispatcher) {
            val vm = loaded()
            vm.onIntent(Intent.Save)
            vm.onIntent(Intent.RetentionChanged("0"))
            vm.onIntent(Intent.Save)
            runCurrent()

            assertTrue(log.calls.isEmpty())
        }

        @Test
        fun `persist failure reports an error, keeps the inputs and applies nothing`() = runTest(main.dispatcher) {
            repository.saveFailure = IOException("disk full")
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged("14"))

            vm.effects.test {
                vm.onIntent(Intent.Save)
                runCurrent()
                assertEquals(Effect.SaveFailed, awaitItem())
            }

            assertEquals("14", vm.state.value.retentionInput)
            assertFalse(vm.state.value.isSaving)
            assertTrue(tracker.updatedConfigs.isEmpty())
            assertTrue(appEvents.calls.isEmpty())
            assertTrue(vm.state.value.canSave)
        }

        @Test
        fun `apply failure reports an error`() = runTest(main.dispatcher) {
            tracker.updateConfigFailure = IllegalStateException("SDK not initialised")
            val vm = loaded()
            vm.onIntent(Intent.RetentionChanged("14"))

            vm.effects.test {
                vm.onIntent(Intent.Save)
                runCurrent()
                assertEquals(Effect.SaveFailed, awaitItem())
            }
            assertFalse(vm.state.value.isSaving)
        }
    }
}
