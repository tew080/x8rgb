package com.x8rgb.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.x8rgb.data.db.dao.AppSettingsDao
import com.x8rgb.data.db.dao.LedProfileDao
import com.x8rgb.data.db.dao.NotificationRuleDao
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.db.entity.LedProfileEntity
import com.x8rgb.data.db.entity.NotificationRuleEntity

@Database(
    entities = [
        NotificationRuleEntity::class,
        LedProfileEntity::class,
        AppSettingsEntity::class,
    ],
    version  = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationRuleDao(): NotificationRuleDao
    abstract fun ledProfileDao(): LedProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
}
