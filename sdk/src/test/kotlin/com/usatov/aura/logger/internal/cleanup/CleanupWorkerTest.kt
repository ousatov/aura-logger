package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.internal.AuraLoggerRuntime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Instant

/** The worker must run in a process where `EventTrackerSDK.init` was never called. */
@RunWith(RobolectricTestRunner::class)
class CleanupWorkerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val originalClock = AuraLoggerRuntime.clock

    @Before
    fun setUp() = runBlocking {
        AuraLoggerRuntime.eventStore(context).deleteAll(keepUnseen = false)
        Unit
    }

    @After
    fun tearDown() {
        AuraLoggerRuntime.clock = originalClock
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<CleanupWorker>(context).build().doWork()

    @Test
    fun succeedsWithoutInitAndWithoutPersistedConfig() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), runWorker())
    }

    @Test
    fun canRunRepeatedly() = runBlocking {
        repeat(3) { assertEquals(ListenableWorker.Result.success(), runWorker()) }
    }

    @Test
    fun appliesThePersistedConfigWithoutInit() = runBlocking {
        val now = 1_773_568_800_000L // 15/03/2026 10:00 UTC
        AuraLoggerRuntime.clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
        val store = AuraLoggerRuntime.eventStore(context)
        store.insert(
            listOf(
                NewEventRecord("old", "old", "{}", now - 30L * 86_400_000, "13/02/2026"),
                NewEventRecord("a", "a", "{}", now - 3, "15/03/2026"),
                NewEventRecord("b", "b", "{}", now - 2, "15/03/2026"),
                NewEventRecord("c", "c", "{}", now - 1, "15/03/2026"),
            ),
        )
        AuraLoggerRuntime.configStore(context).write(
            EventTrackerConfig(
                retentionDays = 7,
                maxEvents = 2,
                unseenEventsProtection = UnseenEventsProtection(onRetention = false, onLimit = false),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(listOf("c", "b"), store.getNewest(10).map { it.id })
    }

    @Test
    fun failureReturnsRetry() = runBlocking {
        AuraLoggerRuntime.clock = object : Clock {
            override fun now(): Instant = throw IllegalStateException("clock failure")
        }

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }
}
