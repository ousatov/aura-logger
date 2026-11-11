package com.usatov.aura.logger.demo.core.di

import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.catalog.DefaultAppEvents
import com.usatov.aura.logger.demo.config.DataStoreDemoConfigRepository
import com.usatov.aura.logger.demo.config.DemoConfigRepository
import com.usatov.aura.logger.demo.startup.DefaultSdkStarter
import com.usatov.aura.logger.demo.startup.SdkStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Interface bindings of the features. Owned by the app shell; features only implement the classes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun appEvents(impl: DefaultAppEvents): AppEvents

    @Binds
    @Singleton
    abstract fun demoConfigRepository(impl: DataStoreDemoConfigRepository): DemoConfigRepository

    @Binds
    @Singleton
    abstract fun sdkStarter(impl: DefaultSdkStarter): SdkStarter
}
