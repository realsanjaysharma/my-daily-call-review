package com.dailycallsreview.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tagged_contacts")
data class TaggedContactEntity(
    @PrimaryKey val phoneNumberLast10: String,
    val contactId: Long,
    val displayName: String
)

@Dao
interface TaggedContactDao {
    @Query("SELECT * FROM tagged_contacts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<TaggedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<TaggedContactEntity>)

    @Query("DELETE FROM tagged_contacts WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: Long)

    @Query("SELECT * FROM tagged_contacts")
    suspend fun getAllOnce(): List<TaggedContactEntity>
}
