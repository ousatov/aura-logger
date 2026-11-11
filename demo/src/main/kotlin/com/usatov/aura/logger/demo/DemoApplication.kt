package com.usatov.aura.logger.demo

import android.app.Application
import com.usatov.aura.logger.demo.startup.SdkStarter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Starts the SDK on every process start. */
@HiltAndroidApp
class DemoApplication : Application() {

    @Inject
    lateinit var sdkStarter: SdkStarter

    override fun onCreate() {
        super.onCreate()
        sdkStarter.start()
    }
}
