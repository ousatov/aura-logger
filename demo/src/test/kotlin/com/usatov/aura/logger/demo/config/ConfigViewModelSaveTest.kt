package com.usatov.aura.logger.demo.config

import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventPage
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.core.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelSaveTest {

    @BeforeEach
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failed SDK update rolls back storage and the save can be retried`() = runTest {
        val repository = FakeRepository(EventTrackerConfig())
        val tracker = FakeTracker(failuresLeft = 1)
        val viewModel = ConfigViewModel(repository, tracker, NoopAppEvents)
        advanceUntilIdle()

        viewModel.onIntent(ConfigContract.Intent.RetentionChanged("14"))
        viewModel.onIntent(ConfigContract.Intent.Save)
        advanceUntilIdle()

        // Storage is back to the old value, so it matches the SDK, and Save is possible again.
        assertEquals(EventTrackerConfig(), repository.stored.value)
        assertEquals(EventTrackerConfig(), viewModel.state.value.savedConfig)
        assertEquals("14", viewModel.state.value.retentionInput)
        assertTrue(viewModel.state.value.canSave)

        viewModel.onIntent(ConfigContract.Intent.Save)
        advanceUntilIdle()

        val expected = EventTrackerConfig(retentionDays = 14)
        assertEquals(expected, repository.stored.value)
        assertEquals(listOf(expected), tracker.applied)
    }

    private class FakeRepository(initial: EventTrackerConfig) : DemoConfigRepository {
        val stored = MutableStateFlow(initial)
        override val config: Flow<EventTrackerConfig> = stored
        override suspend fun current(): EventTrackerConfig = stored.value
        override suspend fun save(config: EventTrackerConfig) {
            stored.value = config
        }
        override val trackNavigation: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun saveTrackNavigation(enabled: Boolean) = Unit
    }

    private class FakeTracker(private var failuresLeft: Int) : EventTracker {
        val applied = mutableListOf<EventTrackerConfig>()

        override suspend fun updateConfig(config: EventTrackerConfig) {
            if (failuresLeft > 0) {
                failuresLeft--
                error("update failed")
            }
            applied += config
        }

        override fun track(name: String, properties: Map<String, String>) = Unit
        override fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>) = Unit
        override fun markSeen(eventIds: Collection<String>) = Unit
        override suspend fun getRecentEvents(limit: Int): List<TrackedEvent> = emptyList()
        override suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage = error("unused")
        override suspend fun getStats(): EventStats = error("unused")
        override suspend fun clearAll(): Int = 0
        override val changes: Flow<Unit> = emptyFlow()
    }

    private object NoopAppEvents : AppEvents {
        override fun processStarted() = Unit
        override fun sectionSelected(section: Section) = Unit
        override fun configChanged(config: EventTrackerConfig) = Unit
        override fun eventsCleared(deletedCount: Int) = Unit
        override fun testEvent() = Unit
        override fun testEvents(count: Int) = Unit
        override fun testEventWithProperties() = Unit
    }
}
