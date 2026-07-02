package com.p2pmessenger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.p2pmessenger.data.signalstore.KyberPreKeyDao
import com.p2pmessenger.data.signalstore.KyberPreKeyEntity
import com.p2pmessenger.data.signalstore.OwnIdentityDao
import com.p2pmessenger.data.signalstore.OwnIdentityEntity
import com.p2pmessenger.data.signalstore.PreKeyDao
import com.p2pmessenger.data.signalstore.PreKeyEntity
import com.p2pmessenger.data.signalstore.RemoteIdentityDao
import com.p2pmessenger.data.signalstore.RemoteIdentityEntity
import com.p2pmessenger.data.signalstore.SessionDao
import com.p2pmessenger.data.signalstore.SessionEntity
import com.p2pmessenger.data.signalstore.SignedPreKeyDao
import com.p2pmessenger.data.signalstore.SignedPreKeyEntity

@Database(
    entities = [
        ContactEntity::class,
        MessageEntity::class,
        OwnIdentityEntity::class,
        RemoteIdentityEntity::class,
        PreKeyEntity::class,
        SignedPreKeyEntity::class,
        KyberPreKeyEntity::class,
        SessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun ownIdentityDao(): OwnIdentityDao
    abstract fun remoteIdentityDao(): RemoteIdentityDao
    abstract fun preKeyDao(): PreKeyDao
    abstract fun signedPreKeyDao(): SignedPreKeyDao
    abstract fun kyberPreKeyDao(): KyberPreKeyDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "p2p_messenger.db"
    }
}
