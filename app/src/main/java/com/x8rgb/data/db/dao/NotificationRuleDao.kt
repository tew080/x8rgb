package com.x8rgb.data.db.dao

import androidx.room.*
import com.x8rgb.data.db.entity.NotificationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rules ORDER BY type ASC")
    fun observeAll(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules WHERE isEnabled = 1 ORDER BY type ASC")
    suspend fun getEnabledRules(): List<NotificationRuleEntity>

    @Query("SELECT * FROM notification_rules WHERE type = :type AND key = :key LIMIT 1")
    suspend fun findByTypeAndKey(type: String, key: String): NotificationRuleEntity?

    @Upsert
    suspend fun upsert(rule: NotificationRuleEntity): Long

    @Delete
    suspend fun delete(rule: NotificationRuleEntity)

    @Query("DELETE FROM notification_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
