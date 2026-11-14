package com.x8rgb.data.repository

import com.x8rgb.data.db.dao.NotificationRuleDao
import com.x8rgb.data.db.entity.NotificationRuleEntity
import com.x8rgb.domain.model.NotificationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(private val dao: NotificationRuleDao) {

    fun observeAll(): Flow<List<NotificationRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun getEnabledRules(): Flow<List<NotificationRule>> =
        dao.observeAll().map { list -> list.filter { it.isEnabled }.map { it.toDomain() } }

    suspend fun upsert(rule: NotificationRule): Long =
        dao.upsert(NotificationRuleEntity.fromDomain(rule))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
