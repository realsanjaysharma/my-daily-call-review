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

@Database(entities = [TaggedContactEntity::class], version = 1, exportSchema = false)
abstract class TaggedContactTestDatabase : RoomDatabase() {
    abstract fun taggedContactDao(): TaggedContactDao
}

@RunWith(AndroidJUnit4::class)
class TaggedContactDaoTest {
    private lateinit var db: TaggedContactTestDatabase
    private lateinit var dao: TaggedContactDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaggedContactTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.taggedContactDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndObserveTaggedContacts() = runBlocking {
        dao.insertAll(
            listOf(
                TaggedContactEntity("9876543210", 1L, "Asha"),
                TaggedContactEntity("9123456780", 1L, "Asha")
            )
        )
        val result = dao.observeAll().first()
        assertEquals(2, result.size)
        assertEquals("Asha", result[0].displayName)
    }

    @Test
    fun deleteByContactIdRemovesAllNumbersForThatContact() = runBlocking {
        dao.insertAll(
            listOf(
                TaggedContactEntity("9876543210", 1L, "Asha"),
                TaggedContactEntity("9123456780", 1L, "Asha"),
                TaggedContactEntity("9000000000", 2L, "Ravi")
            )
        )
        dao.deleteByContactId(1L)
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Ravi", result[0].displayName)
    }
}
