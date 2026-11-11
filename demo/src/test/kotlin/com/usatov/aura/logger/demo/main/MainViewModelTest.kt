package com.usatov.aura.logger.demo.main

import com.usatov.aura.logger.demo.core.model.Section
import com.usatov.aura.logger.demo.main.MainContract.Intent.SelectSection
import com.usatov.aura.logger.demo.testing.FakeAppEvents
import com.usatov.aura.logger.demo.testing.FakeDemoConfigRepository
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val appEvents = FakeAppEvents()
    private val vm = MainViewModel(appEvents, FakeDemoConfigRepository())

    @Test
    fun `starts on the dashboard without tracking`() {
        assertEquals(Section.DASHBOARD, vm.state.value.section)
        assertTrue(appEvents.calls.isEmpty())
    }

    @Test
    fun `selecting another tab switches and tracks the section`() {
        vm.onIntent(SelectSection(Section.EVENTS))
        vm.onIntent(SelectSection(Section.SETTINGS))
        vm.onIntent(SelectSection(Section.DASHBOARD))

        assertEquals(Section.DASHBOARD, vm.state.value.section)
        assertEquals(listOf("sectionSelected:events", "sectionSelected:settings", "sectionSelected:dashboard"), appEvents.calls)
    }

    @Test
    fun `selecting the current tab again tracks nothing`() {
        vm.onIntent(SelectSection(Section.DASHBOARD))
        vm.onIntent(SelectSection(Section.EVENTS))
        vm.onIntent(SelectSection(Section.EVENTS))

        assertEquals(listOf("sectionSelected:events"), appEvents.calls)
    }

    @Test
    fun `section keys are stable analytics values`() {
        assertEquals(listOf("dashboard", "events", "settings"), Section.entries.map { it.key })
    }
}
