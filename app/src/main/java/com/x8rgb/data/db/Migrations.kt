package com.x8rgb.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v3 -> v4: lock mode gets an effect id. Keeps existing settings and rules. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN forceHoldEffectId INTEGER NOT NULL DEFAULT 10")
    }
}
