package com.usatov.aura.logger

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.usatov.aura.logger.core.EventTrackerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Hostile environments for `init`: a broken context, a context without an application context,
 * and no WorkManager (the host disabled its default initializer without a configuration).
 * Own sandbox (SDK 34), see [EventTrackerSDKAfterInitTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventTrackerSDKWithoutWorkManagerTest {

    @Test
    fun initSurvivesHostileContextsAndWorksWithoutWorkManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val broken = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = throw IllegalStateException("broken context")
        }
        val withoutApplicationContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context? = null
        }

        EventTrackerSDK.init(broken)
        assertFalse(EventTrackerSDK.isInitialized)
        assertTrue(ShadowLog.getLogsForTag("AuraLogger").any { it.type == android.util.Log.ERROR })

        EventTrackerSDK.init(withoutApplicationContext)
        EventTrackerSDK.track("no_work_manager")
        EventTrackerSDK.updateConfig(EventTrackerConfig(maxEvents = 50))

        assertTrue(EventTrackerSDK.isInitialized)
        assertTrue(EventTrackerSDK.getRecentEvents(10).any { it.name == "no_work_manager" })
    }
}
