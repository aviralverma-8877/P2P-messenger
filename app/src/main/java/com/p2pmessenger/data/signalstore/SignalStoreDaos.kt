package com.p2pmessenger.data.signalstore

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OwnIdentityDao {
    @Query("SELECT * FROM own_identity WHERE id = 0")
    suspend fun get(): OwnIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnIdentityEntity)
}

@Dao
interface RemoteIdentityDao {
    @Query("SELECT * FROM remote_identities WHERE address = :address")
    suspend fun get(address: String): RemoteIdentityEntity?

    @Query("SELECT * FROM remote_identities")
    suspend fun getAll(): List<RemoteIdentityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemoteIdentityEntity)
}

@Dao
interface PreKeyDao {
    @Query("SELECT * FROM prekeys WHERE id = :id")
    suspend fun get(id: Int): PreKeyEntity?

    @Query("SELECT * FROM prekeys")
    suspend fun getAll(): List<PreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PreKeyEntity)

    @Query("DELETE FROM prekeys WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM prekeys WHERE id = :id)")
    suspend fun contains(id: Int): Boolean
}

@Dao
interface SignedPreKeyDao {
    @Query("SELECT * FROM signed_prekeys WHERE id = :id")
    suspend fun get(id: Int): SignedPreKeyEntity?

    @Query("SELECT * FROM signed_prekeys")
    suspend fun getAll(): List<SignedPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SignedPreKeyEntity)

    @Query("DELETE FROM signed_prekeys WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM signed_prekeys WHERE id = :id)")
    suspend fun contains(id: Int): Boolean
}

@Dao
interface KyberPreKeyDao {
    @Query("SELECT * FROM kyber_prekeys WHERE id = :id")
    suspend fun get(id: Int): KyberPreKeyEntity?

    @Query("SELECT * FROM kyber_prekeys")
    suspend fun getAll(): List<KyberPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KyberPreKeyEntity)

    @Query("DELETE FROM kyber_prekeys WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM kyber_prekeys WHERE id = :id)")
    suspend fun contains(id: Int): Boolean
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE address = :address")
    suspend fun get(address: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE address LIKE :namePrefix || '.%'")
    suspend fun getAllForName(namePrefix: String): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query("DELETE FROM sessions WHERE address = :address")
    suspend fun delete(address: String)

    @Query("DELETE FROM sessions WHERE address LIKE :namePrefix || '.%'")
    suspend fun deleteAllForName(namePrefix: String)

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE address = :address)")
    suspend fun contains(address: String): Boolean
}
