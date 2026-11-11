package com.usatov.aura.logger.demo.startup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.testing.CallLog
import com.usatov.aura.logger.demo.testing.FakeAppEvents
import com.usatov.aura.logger.demo.testing.FakeDemoConfigRepository
import com.usatov.aura.logger.demo.testing.FixedClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DefaultSdkStarterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val log = CallLog()
    private val appEvents = FakeAppEvents(log)
    private val clock = FixedClock(1_000)
    private val inits = mutableListOf<Triple<Context, EventTrackerConfig, Any>>()
    private val initializer = SdkInitializer { ctx, config, clk ->
        log.add("init")
        inits += Triple(ctx, config, clk)
    }

    private fun starter(repository: FakeDemoConfigRepository, scope: CoroutineScope) =
        DefaultSdkStarter(context, repository, appEvents, initializer, clock, scope)

    @Test
    fun tracksWakeUpImmediatelyAndInitialisesWithTheStoredConfig() = runTest {
        val config = EventTrackerConfig(retentionDays = 3, maxEvents = 30)
        val starter = starter(FakeDemoConfigRepository(config, log), CoroutineScope(StandardTestDispatcher(testScheduler)))

        starter.start()
        assertEquals(listOf("appEvents:processStarted"), log.calls)

        runCurrent()

        assertEquals(listOf("appEvents:processStarted", "init"), log.calls)
        val (ctx, usedConfig, usedClock) = inits.single()
        assertEquals(config, usedConfig)
        assertSame(clock, usedClock)
        assertSame(context.applicationContext, ctx.applicationContext)
    }

    @Test
    fun doesNotBlockWhileTheConfigIsRead() = runTest {
        val repository = FakeDemoConfigRepository(log = log).apply { currentGate = CompletableDeferred() }
        val starter = starter(repository, CoroutineScope(StandardTestDispatcher(testScheduler)))

        starter.start()
        runCurrent()
        assertTrue(inits.isEmpty())

        repository.currentGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, inits.size)
    }

    @Test
    fun initialisesWithDefaultsWhenTheConfigCannotBeRead() = runTest {
        val repository = FakeDemoConfigRepository(EventTrackerConfig(maxEvents = 5), log).apply { currentFailure = IOException("disk") }
        val starter = starter(repository, CoroutineScope(StandardTestDispatcher(testScheduler)))

        starter.start()
        runCurrent()

        assertEquals(EventTrackerConfig(), inits.single().second)
    }
}
