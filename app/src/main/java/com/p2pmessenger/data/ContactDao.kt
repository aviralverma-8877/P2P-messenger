package com.p2pmessenger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE signalName = :signalName")
    suspend fun getBySignalName(signalName: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET lastKnownIpv6 = :ipv6, lastKnownPort = :port, lastSeenAtEpochMs = :seenAt WHERE id = :id")
    suspend fun updateLastKnownAddress(id: String, ipv6: String, port: Int, seenAt: Long)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}
