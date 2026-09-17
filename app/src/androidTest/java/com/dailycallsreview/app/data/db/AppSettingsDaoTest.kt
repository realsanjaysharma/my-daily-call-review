package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(entities = [AppSettingsEntity::class], version = 1, exportSchema = false)
abstract class AppSettingsTestDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
}

@RunWith(AndroidJUnit4::class)
class AppSettingsDaoTest {
    private lateinit var db: AppSettingsTestDatabase
    private lateinit var dao: AppSettingsDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppSettingsTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.appSettingsDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun observeReturnsNullBeforeAnySettingsAreSaved() = runBlocking {
        assertNull(dao.observe().first())
    }

    @Test
    fun upsertReplacesThePreviousSingleRow() = runBlocking {
        dao.upsert(AppSettingsEntity(workStartMinutes = 540, workEndMinutes = 1080, workingDaysMask = 63))
        dao.upsert(AppSettingsEntity(workStartMinutes = 480, workEndMinutes = 1020, workingDaysMask = 31))
        val result = dao.observe().first()
        assertEquals(480, result?.workStartMinutes)
        assertEquals(31, result?.workingDaysMask)
    }
}
