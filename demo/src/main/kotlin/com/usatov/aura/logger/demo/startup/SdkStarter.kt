package com.usatov.aura.logger.demo.startup

import android.content.Context
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.DemoConfigRepository
import com.usatov.aura.logger.demo.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlin.time.Clock

/** Starts the SDK on every process start. Called once from `Application.onCreate`. */
interface SdkStarter {
    /**
     * Must not block the main thread and never throws:
     * 1. tracks [AppEvents.processStarted] immediately (the SDK buffers it until it is initialised);
     * 2. in the application scope, reads [DemoConfigRepository.current] and initialises the SDK
     *    with that config and the app clock through [SdkInitializer].
     *
     * If reading the config fails, the SDK is initialised with `EventTrackerConfig()` defaults.
     */
    fun start()
}

/** Seam for `EventTrackerSDK.init`, so that [SdkStarter] can be tested without the real SDK. */
fun interface SdkInitializer {
    fun init(context: Context, config: EventTrackerConfig, clock: Clock)
}

class DefaultSdkStarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configRepository: DemoConfigRepository,
    private val appEvents: AppEvents,
    private val initializer: SdkInitializer,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SdkStarter {

    override fun start(): Unit = TODO("D1")
}
