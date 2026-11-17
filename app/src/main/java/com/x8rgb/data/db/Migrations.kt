package com.x8rgb.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v3 -> v4: lock mode gets an effect id. Keeps existing settings and rules. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN forceHoldEffectId INTEGER NOT NULL DEFAULT 10")
    }
}

/** v4 -> v5: music sync, motion reactive and quiet hours are removed. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings DROP COLUMN musicSyncEnabled")
        db.execSQL("ALTER TABLE app_settings DROP COLUMN motionReactiveEnabled")
        db.execSQL("ALTER TABLE app_settings DROP COLUMN quietHoursEnabled")
        db.execSQL("ALTER TABLE app_settings DROP COLUMN quietHoursStart")
        db.execSQL("ALTER TABLE app_settings DROP COLUMN quietHoursEnd")
    }
}
