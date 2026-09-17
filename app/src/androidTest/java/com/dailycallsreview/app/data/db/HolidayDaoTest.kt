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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(entities = [HolidayEntity::class], version = 1, exportSchema = false)
abstract class HolidayTestDatabase : RoomDatabase() {
    abstract fun holidayDao(): HolidayDao
}

@RunWith(AndroidJUnit4::class)
class HolidayDaoTest {
    private lateinit var db: HolidayTestDatabase
    private lateinit var dao: HolidayDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HolidayTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.holidayDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndObserveHolidaysOrderedByDate() = runBlocking {
        dao.insert(HolidayEntity("2026-12-25", "Christmas"))
        dao.insert(HolidayEntity("2026-01-26", "Republic Day"))
        val result = dao.observeAll().first()
        assertEquals(2, result.size)
        assertEquals("2026-01-26", result[0].date)
    }

    @Test
    fun deleteByDateRemovesOnlyThatHoliday() = runBlocking {
        dao.insert(HolidayEntity("2026-12-25", "Christmas"))
        dao.insert(HolidayEntity("2026-01-26", "Republic Day"))
        dao.deleteByDate("2026-12-25")
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Republic Day", result[0].label)
    }
}
