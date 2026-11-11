package com.usatov.aura.logger.demo.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.usatov.aura.logger.EventTrackerSDK
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.startup.SdkInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.ZoneId
import javax.inject.Singleton
import kotlin.random.Random

/** Platform objects and the SDK. Feature bindings are in [BindingsModule]; the clock is per build type. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun eventTracker(): EventTracker = EventTrackerSDK

    @Provides
    fun sdkInitializer(): SdkInitializer = SdkInitializer(EventTrackerSDK::init)

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun random(): Random = Random.Default

    @Provides
    fun zoneProvider(): ZoneProvider = ZoneProvider { ZoneId.systemDefault() }

    @Provides
    @Singleton
    @DemoPreferences
    fun demoPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("demo_settings") },
        )
}
