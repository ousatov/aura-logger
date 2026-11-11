package com.usatov.aura.logger.internal.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.usatov.aura.logger.core.spi.CleanupScheduler

/** Unique work names used by the SDK. */
internal object CleanupWork {
    /** Unique name of the periodic cleanup (24 h period, 1 h initial delay, existing work kept). */
    const val PERIODIC_WORK_NAME: String = "aura_logger_periodic_cleanup"

    /** Unique name of the one-time cleanup scheduled on config updates (pending work replaced). */
    const val ONE_TIME_WORK_NAME: String = "aura_logger_one_time_cleanup"
}

/** [CleanupScheduler] backed by WorkManager unique work, both runs use [CleanupWorker]. */
internal class WorkManagerCleanupScheduler(
    private val workManager: WorkManager,
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
