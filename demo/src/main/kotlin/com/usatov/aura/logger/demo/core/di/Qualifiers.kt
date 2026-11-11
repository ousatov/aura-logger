package com.usatov.aura.logger.demo.core.di

import javax.inject.Qualifier

/** Process-wide scope for work that must outlive any screen (SDK start, config writes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Preferences DataStore of the demo app's own settings (not the SDK's). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DemoPreferences
