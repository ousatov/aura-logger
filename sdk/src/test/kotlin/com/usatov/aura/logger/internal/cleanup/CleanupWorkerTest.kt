package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The worker must run in a process where `EventTrackerSDK.init` was never called. */
@RunWith(RobolectricTestRunner::class)
class CleanupWorkerTest {

    @Test
    fun succeedsWithoutInitAndWithoutPersistedConfig() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<CleanupWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun canRunRepeatedly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        repeat(3) {
            val worker = TestListenableWorkerBuilder<CleanupWorker>(context).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
        }
    }
}
