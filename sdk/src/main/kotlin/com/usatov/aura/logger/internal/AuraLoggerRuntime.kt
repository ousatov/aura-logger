package com.usatov.aura.logger.internal

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.work.WorkManager
import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.engine.EventTrackerEngine
import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.CleanupScheduler
import com.usatov.aura.logger.core.spi.ConfigStore
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.IdGenerator
import com.usatov.aura.logger.core.spi.TimeZoneProvider
import com.usatov.aura.logger.internal.cleanup.WorkManagerCleanupScheduler
import com.usatov.aura.logger.internal.config.DataStoreConfigStore
import com.usatov.aura.logger.internal.store.RoomEventStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Clock

/**
 * Process-wide composition root of the SDK (manual DI).
 *
 * Holds the single engine, the single database and the single DataStore of the process, so that
 * the public facade and the cleanup worker share them. Storage objects are created lazily on
 * first use and their creation does no disk I/O. Thread-safe.
 */
internal object AuraLoggerRuntime {

    val settings: AuraLoggerSettings = AuraLoggerSettings()

    val logger: AuraLogger = LogcatAuraLogger

    /** Reads the default time zone on every call; Android updates it when the user changes it. */
    val timeZoneProvider: TimeZoneProvider = TimeZoneProvider { TimeZone.getDefault().id }

    val idGenerator: IdGenerator = IdGenerator { UUID.randomUUID().toString() }

    /** Clock passed to `EventTrackerSDK.init`, also used by the cleanup worker of this process. */
    @Volatile
    var clock: Clock = Clock.System

    /** Scope of the serial write queue and of DataStore; never cancelled. */
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            logger.error("Unexpected error in a background task", e)
        },
    )

    val engine: EventTrackerEngine = EventTrackerEngine(
        idGenerator = idGenerator,
        timeZoneProvider = timeZoneProvider,
        logger = logger,
        initialClock = Clock.System,
        settings = settings,
    )

    private var eventStore: RoomEventStore? = null
    private var dataStore: DataStore<Preferences>? = null

    fun eventStore(context: Context): EventStore = synchronized(this) {
        eventStore ?: RoomEventStore.create(context.applicationContext ?: context, settings.databaseName)
            .also { eventStore = it }
    }

    fun configStore(context: Context): ConfigStore = DataStoreConfigStore(dataStore(context))

    /**
     * Returns the WorkManager-based scheduler, or a scheduler that only logs errors when
     * WorkManager is not initialised in this process (for example, the app disabled its default
     * initializer without providing a configuration).
     */
    fun cleanupScheduler(context: Context): CleanupScheduler =
        try {
            WorkManagerCleanupScheduler(WorkManager.getInstance(context.applicationContext ?: context), settings)
        } catch (e: Exception) {
            // IllegalStateException when WorkManager is not initialised; any other failure (for
            // example with a context without an application context) must not prevent init either.
            logger.error("WorkManager is not available; background cleanup is disabled", e)
            UnavailableCleanupScheduler
        }

    private fun dataStore(context: Context): DataStore<Preferences> = synchronized(this) {
        dataStore ?: run {
            val appContext = context.applicationContext ?: context
            PreferenceDataStoreFactory.create(
                // A corrupted file is replaced with an empty one, which reads as "no config".
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    logger.error("Persisted Aura Logger config is corrupted; it is reset", e)
                    emptyPreferences()
                },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { configFile(appContext) },
            )
        }.also { dataStore = it }
    }

    /**
     * File of the persisted config, resolved from the context that is actually used.
     *
     * Same path as `preferencesDataStoreFile(name)` (`filesDir/datastore/<name>.preferences_pb`),
     * which cannot be used because it calls `getApplicationContext()` again and fails when that is
     * `null` (the fallback of `init`).
     */
    private fun configFile(context: Context): File =
        File(context.filesDir, "datastore/${settings.configFileName}.preferences_pb")

    private object UnavailableCleanupScheduler : CleanupScheduler {
        override fun schedulePeriodic() {
            logger.error("WorkManager is not available; the periodic cleanup is not scheduled")
        }

        override fun scheduleOneTime() {
            logger.error("WorkManager is not available; the one-time cleanup is not scheduled")
        }
    }
}

/** [AuraLogger] writing to Logcat with the tag `AuraLogger`. Never throws. */
internal object LogcatAuraLogger : AuraLogger {
    private const val TAG = "AuraLogger"

    override fun warn(message: String, throwable: Throwable?) {
        try {
            Log.w(TAG, message, throwable)
        } catch (_: Throwable) {
            // Logging must never fail the caller (for example, android.util.Log in JVM unit tests).
        }
    }

    override fun error(message: String, throwable: Throwable?) {
        try {
            Log.e(TAG, message, throwable)
        } catch (_: Throwable) {
            // Logging must never fail the caller.
        }
    }
}
