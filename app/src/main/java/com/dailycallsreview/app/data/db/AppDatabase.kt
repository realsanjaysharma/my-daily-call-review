package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaggedContactEntity::class, HolidayEntity::class, AppSettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taggedContactDao(): TaggedContactDao
    abstract fun holidayDao(): HolidayDao
    abstract fun appSettingsDao(): AppSettingsDao
}
