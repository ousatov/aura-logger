package com.usatov.aura.logger.demo.debug

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

/** Debug builds: the app clock is the shiftable [TimeShiftClock]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun clock(impl: TimeShiftClock): Clock
}
