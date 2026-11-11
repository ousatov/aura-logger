package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.engine.EventCleaner
import com.usatov.aura.logger.core.spi.CleanupScheduler
import com.usatov.aura.logger.internal.AuraLoggerRuntime
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * [CleanupScheduler] backed by WorkManager unique work; both runs use [CleanupWorker].
 *
 * Periodic: [AuraLoggerSettings.periodicCleanupWorkName], [AuraLoggerSettings.cleanupPeriodHours]
 * period, [AuraLoggerSettings.cleanupInitialDelayHours] initial delay, existing work kept.
 * One-time: [AuraLoggerSettings.oneTimeCleanupWorkName], pending work replaced.
 */
internal class WorkManagerCleanupScheduler(
    private val workManager: WorkManager,
    private val settings: AuraLoggerSettings,
) : CleanupScheduler {

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(settings.cleanupPeriodHours, TimeUnit.HOURS)
            .setInitialDelay(settings.cleanupInitialDelayHours, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(settings.periodicCleanupWorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleOneTime() {
        val request = OneTimeWorkRequestBuilder<CleanupWorker>().build()
        workManager.enqueueUniqueWork(settings.oneTimeCleanupWorkName, ExistingWorkPolicy.REPLACE, request)
    }
}

/**
 * Runs one cleanup with the persisted config (or [com.usatov.aura.logger.core.EventTrackerConfig]
 * defaults if none was persisted).
 *
 * Works in a process where `EventTrackerSDK.init` was never called. Uses the same process-wide
 * database and DataStore instances as the SDK, so the deletions are reported by
 * `EventTrackerSDK.changes`, and the clock passed to `EventTrackerSDK.init` if it was called in
 * this process (system clock otherwise).
 *
 * Returns `Result.success()` after a successful cleanup and `Result.retry()` if it failed.
 */
internal class CleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val runtime = AuraLoggerRuntime
        return try {
            val config = runtime.configStore(applicationContext).read() ?: EventTrackerConfig()
            val cleaner = EventCleaner(
                eventStore = runtime.eventStore(applicationContext),
                clock = runtime.clock,
                timeZoneProvider = runtime.timeZoneProvider,
                logger = runtime.logger,
            )
            cleaner.cleanup(config)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            runtime.logger.error("Event cleanup failed; it will be retried", e)
            Result.retry()
        }
    }
}
