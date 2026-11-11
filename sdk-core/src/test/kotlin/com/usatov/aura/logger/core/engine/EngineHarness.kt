package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.testing.FakeCleanupScheduler
import com.usatov.aura.logger.core.testing.FakeConfigStore
import com.usatov.aura.logger.core.testing.FakeEventStore
import com.usatov.aura.logger.core.testing.FakeTimeZoneProvider
import com.usatov.aura.logger.core.testing.MutableClock
import com.usatov.aura.logger.core.testing.RecordingLogger
import com.usatov.aura.logger.core.testing.SequentialIdGenerator
import com.usatov.aura.logger.core.testing.millisOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock

/** 15/03/2026 10:00:00.000 UTC. */
val T0: Long = millisOf("UTC", 2026, 3, 15, 10)

/** Engine wired to fakes and to the scheduler of a [TestScope]. */
class EngineHarness(
    private val testScope: TestScope,
    val settings: AuraLoggerSettings = AuraLoggerSettings(),
    zone: String = "UTC",
    startMillis: Long = T0,
) {
    val preInitClock = MutableClock(startMillis)
    val clock = MutableClock(startMillis)
    val timeZone = FakeTimeZoneProvider(zone)
    val logger = RecordingLogger()
    val idGenerator = SequentialIdGenerator()
    val store = FakeEventStore()
    val configStore = FakeConfigStore()
    val scheduler = FakeCleanupScheduler()

    val engine = EventTrackerEngine(
        idGenerator = idGenerator,
        timeZoneProvider = timeZone,
        logger = logger,
        initialClock = preInitClock,
        settings = settings,
    )

    fun dependencies(config: EventTrackerConfig = EventTrackerConfig(), clock: Clock = this.clock) =
        EngineDependencies(
            config = config,
            eventStore = store,
            configStore = configStore,
            cleanupScheduler = scheduler,
            clock = clock,
            scope = testScope.backgroundScope,
            readDispatcher = StandardTestDispatcher(testScope.testScheduler),
        )

    fun start(config: EventTrackerConfig = EventTrackerConfig()): Boolean = engine.start(dependencies(config))
}
