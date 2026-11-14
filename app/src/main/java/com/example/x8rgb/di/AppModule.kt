package com.x8rgb.di

import android.content.Context
import androidx.room.Room
import com.x8rgb.data.db.AppDatabase
import com.x8rgb.data.db.dao.AppSettingsDao
import com.x8rgb.data.db.dao.LedProfileDao
import com.x8rgb.data.db.dao.NotificationRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "x8rgb.db")
            .fallbackToDestructiveMigration()   // upgrade gracefully in prod; replace with Migration
            .build()

    @Provides fun provideRuleDao(db: AppDatabase):     NotificationRuleDao = db.notificationRuleDao()
    @Provides fun provideProfileDao(db: AppDatabase):  LedProfileDao       = db.ledProfileDao()
    @Provides fun provideSettingsDao(db: AppDatabase): AppSettingsDao      = db.appSettingsDao()
}
