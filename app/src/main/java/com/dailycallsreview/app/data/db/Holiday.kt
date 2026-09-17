package com.dailycallsreview.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val date: String, // ISO-8601 yyyy-MM-dd
    val label: String
)

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayEntity)

    @Query("DELETE FROM holidays WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
