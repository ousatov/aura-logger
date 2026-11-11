package com.usatov.aura.logger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import com.usatov.aura.logger.internal.AuraLoggerRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The facade after `init`. `EventTrackerSDK` is process-wide, so this class runs in its own
 * Robolectric sandbox (SDK 35) and initialises the SDK once with the same arguments.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventTrackerSDKAfterInitTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings = AuraLoggerSettings()

    @Before
    fun ensureInitialised() {
        if (!EventTrackerSDK.isInitialized) {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
            EventTrackerSDK.init(context, CONFIG, FIXED_CLOCK)
        }
    }

    @Test
    fun initMarksTheSdkInitialisedAndSchedulesThePeriodicCleanup() {
        assertTrue(EventTrackerSDK.isInitialized)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(settings.periodicCleanupWorkName).get()
        assertEquals(1, infos.count { !it.state.isFinished })
    }

    @Test
    fun trackedEventsAreStoredWithTheClockPassedToInit() = runBlocking {
        EventTrackerSDK.track("after_init", mapOf("k" to "v"))

        val event = EventTrackerSDK.getRecentEvents(50).first { it.name == "after_init" }
        assertEquals(NOW, event.timestamp)
        assertEquals(mapOf("k" to "v"), event.properties)
    }

    @Test
    fun secondInitIsIgnored() = runBlocking {
        EventTrackerSDK.init(context, EventTrackerConfig(retentionDays = 1, maxEvents = 1), Clock.System)
        EventTrackerSDK.track("after_second_init")

        val event = EventTrackerSDK.getRecentEvents(50).first { it.name == "after_second_init" }
        assertEquals(NOW, event.timestamp)
    }

    @Test
    fun updateConfigPersistsAndSchedulesAOneTimeCleanup() = runBlocking {
        val config = EventTrackerConfig(retentionDays = 3, maxEvents = 500, unseenEventsProtection = UnseenEventsProtection())

        EventTrackerSDK.updateConfig(config)

        assertEquals(config, AuraLoggerRuntime.configStore(context).read())
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(settings.oneTimeCleanupWorkName).get()
        assertTrue(infos.isNotEmpty())
    }

    @Test
    fun changesEmitAfterTrack() = runBlocking {
        EventTrackerSDK.changes.test {
            delay(300)
            EventTrackerSDK.track("observed")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun statsAndPagingWorkAfterInit() = runBlocking {
        EventTrackerSDK.track("stats_event")

        val stats = EventTrackerSDK.getStats()
        val page = EventTrackerSDK.getEventsPage(null, 1)

        assertTrue(stats.totalCount >= 1)
        assertEquals(1, page.events.size)
    }

    private companion object {
        const val NOW = 1_773_568_800_000L
        val CONFIG = EventTrackerConfig(retentionDays = 30, maxEvents = 1000)
        val FIXED_CLOCK = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
        }
    }
}
