package com.x8rgb.data.db.dao

import androidx.room.*
import com.x8rgb.data.db.entity.LedProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedProfileDao {
    @Query("SELECT * FROM led_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LedProfileEntity>>

    @Query("SELECT * FROM led_profiles WHERE id = :id")
    suspend fun getById(id: Long): LedProfileEntity?

    @Upsert
    suspend fun upsert(profile: LedProfileEntity): Long

    @Query("DELETE FROM led_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
