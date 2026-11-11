package com.usatov.aura.logger.demo.core.mvi

import app.cash.turbine.test
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MviViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private class CounterViewModel : MviViewModel<Int, String, String>(0) {
        override fun onIntent(intent: String) {
            when (intent) {
                "inc" -> setState { this + 1 }
                "say" -> sendEffect("hello $currentState")
            }
        }
    }

    @Test
    fun `initial state and reducer updates`() {
        val vm = CounterViewModel()
        assertEquals(0, vm.state.value)

        repeat(3) { vm.onIntent("inc") }

        assertEquals(3, vm.state.value)
    }

    @Test
    fun `effects sent before collection are delivered once, in order`() = runTest {
        val vm = CounterViewModel()
        vm.onIntent("say")
        vm.onIntent("inc")
        vm.onIntent("say")

        vm.effects.test {
            assertEquals("hello 0", awaitItem())
            assertEquals("hello 1", awaitItem())
            expectNoEvents()
        }
        vm.effects.test { expectNoEvents() }
    }
}
