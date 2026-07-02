package com.p2pmessenger.di

import android.content.Context
import androidx.room.Room
import com.p2pmessenger.data.AppDatabase
import com.p2pmessenger.data.ContactDao
import com.p2pmessenger.data.MessageDao
import com.p2pmessenger.data.signalstore.KyberPreKeyDao
import com.p2pmessenger.data.signalstore.OwnIdentityDao
import com.p2pmessenger.data.signalstore.PreKeyDao
import com.p2pmessenger.data.signalstore.RemoteIdentityDao
import com.p2pmessenger.data.signalstore.SessionDao
import com.p2pmessenger.data.signalstore.SignedPreKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideOwnIdentityDao(db: AppDatabase): OwnIdentityDao = db.ownIdentityDao()

    @Provides
    fun provideRemoteIdentityDao(db: AppDatabase): RemoteIdentityDao = db.remoteIdentityDao()

    @Provides
    fun providePreKeyDao(db: AppDatabase): PreKeyDao = db.preKeyDao()

    @Provides
    fun provideSignedPreKeyDao(db: AppDatabase): SignedPreKeyDao = db.signedPreKeyDao()

    @Provides
    fun provideKyberPreKeyDao(db: AppDatabase): KyberPreKeyDao = db.kyberPreKeyDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
}
