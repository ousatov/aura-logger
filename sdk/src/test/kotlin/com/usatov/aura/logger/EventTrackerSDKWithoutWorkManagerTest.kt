package com.usatov.aura.logger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.usatov.aura.logger.core.EventTrackerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The host app disabled WorkManager's default initializer without providing a configuration:
 * tracking must keep working without background cleanup. Own sandbox (SDK 34), see
 * [EventTrackerSDKAfterInitTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventTrackerSDKWithoutWorkManagerTest {

    @Test
    fun initTrackAndUpdateConfigWorkWithoutWorkManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        EventTrackerSDK.init(context, EventTrackerConfig())
        EventTrackerSDK.track("no_work_manager")
        EventTrackerSDK.updateConfig(EventTrackerConfig(maxEvents = 50))

        assertTrue(EventTrackerSDK.isInitialized)
        assertTrue(EventTrackerSDK.getRecentEvents(10).any { it.name == "no_work_manager" })
    }
}
