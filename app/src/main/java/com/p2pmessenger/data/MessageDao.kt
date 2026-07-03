package com.p2pmessenger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestampEpochMs")
    fun observeForContact(contactId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: MessageStatus)

    @Query("UPDATE messages SET mediaUri = :mediaUri, status = :status WHERE id = :id")
    suspend fun updateMedia(id: String, mediaUri: String, status: MessageStatus)

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestampEpochMs DESC LIMIT 1")
    suspend fun latestForContact(contactId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteForContact(contactId: String)
}
