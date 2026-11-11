package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.usatov.aura.logger.core.AuraLoggerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class WorkManagerCleanupSchedulerTest {

    private lateinit var workManager: WorkManager
    private val settings = AuraLoggerSettings()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun active(name: String) = infos(name).filter { !it.state.isFinished }

    @Test
    fun periodicCleanupRunsEvery24HoursWithAOneHourInitialDelay() {
        WorkManagerCleanupScheduler(workManager, settings).schedulePeriodic()

        val info = active(settings.periodicCleanupWorkName).single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.HOURS.toMillis(24), info.periodicityInfo?.repeatIntervalMillis)
        assertEquals(TimeUnit.HOURS.toMillis(1), info.initialDelayMillis)
    }

    @Test
    fun repeatedPeriodicSchedulingKeepsTheExistingWork() {
        val scheduler = WorkManagerCleanupScheduler(workManager, settings)
        scheduler.schedulePeriodic()
        val firstId = active(settings.periodicCleanupWorkName).single().id

        scheduler.schedulePeriodic()
        WorkManagerCleanupScheduler(workManager, settings).schedulePeriodic()

        val all = infos(settings.periodicCleanupWorkName)
        assertEquals(1, all.size)
        assertEquals(firstId, all.single().id)
    }

    @Test
    fun oneTimeCleanupIsEnqueuedWithoutDelay() {
        WorkManagerCleanupScheduler(workManager, settings).scheduleOneTime()

        val info = infos(settings.oneTimeCleanupWorkName).single()
        assertEquals(0L, info.initialDelayMillis)
        assertEquals(null, info.periodicityInfo)
    }

    @Test
    fun repeatedOneTimeSchedulingReplacesThePendingWork() {
        // Workers must not finish inside scheduleOneTime(), otherwise there is nothing to replace:
        // the worker executor queues the work without running it, so the first request stays RUNNING.
        val neverRuns = Executor { }
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(neverRuns)
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerCleanupScheduler(workManager, settings)

        scheduler.scheduleOneTime()
        val first = active(settings.oneTimeCleanupWorkName).single()

        scheduler.scheduleOneTime()

        val pending = active(settings.oneTimeCleanupWorkName)
        assertEquals(1, pending.size)
        assertNotEquals(first.id, pending.single().id)
        // KEEP would keep the first id and APPEND_OR_REPLACE would leave two unfinished works, so the
        // assertions above fail for both. The replaced work is cancelled; WorkManager 2.12 also
        // removes its record, so it is either CANCELLED or gone.
        val replaced = workManager.getWorkInfoById(first.id).get()
        assertTrue(replaced == null || replaced.state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun periodicAndOneTimeWorkAreIndependent() {
        val scheduler = WorkManagerCleanupScheduler(workManager, settings)

        scheduler.schedulePeriodic()
        scheduler.scheduleOneTime()

        assertEquals(1, active(settings.periodicCleanupWorkName).size)
        assertEquals(1, infos(settings.oneTimeCleanupWorkName).size)
    }

    @Test
    fun customSettingsAreUsed() {
        val custom = AuraLoggerSettings(
            cleanupPeriodHours = 48,
            cleanupInitialDelayHours = 3,
            periodicCleanupWorkName = "custom_periodic",
            oneTimeCleanupWorkName = "custom_once",
        )

        WorkManagerCleanupScheduler(workManager, custom).schedulePeriodic()

        val info = active("custom_periodic").single()
        assertEquals(TimeUnit.HOURS.toMillis(48), info.periodicityInfo?.repeatIntervalMillis)
        assertEquals(TimeUnit.HOURS.toMillis(3), info.initialDelayMillis)
        assertTrue(infos(settings.periodicCleanupWorkName).isEmpty())
    }
}
