package com.usatov.aura.logger.internal.store

import androidx.room3.Database
import androidx.room3.RoomDatabase

/** Room database of the SDK. */
@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
internal abstract class AuraLoggerDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
