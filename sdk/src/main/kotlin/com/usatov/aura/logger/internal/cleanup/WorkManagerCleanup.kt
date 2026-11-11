package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.spi.CleanupScheduler

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

    override fun schedulePeriodic(): Unit = TODO("Not implemented yet")

    override fun scheduleOneTime(): Unit = TODO("Not implemented yet")
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

    override suspend fun doWork(): Result = TODO("Not implemented yet")
}
