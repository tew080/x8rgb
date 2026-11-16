package com.x8rgb.data.repository

import com.x8rgb.data.db.dao.AppSettingsDao
import com.x8rgb.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: AppSettingsDao
) {
    fun observe(): Flow<AppSettingsEntity?> = dao.observe()

    suspend fun get(): AppSettingsEntity = dao.get() ?: AppSettingsEntity()

    suspend fun updateMasterEnabled(enabled: Boolean) {
        val s = get()
        dao.upsert(s.copy(masterEnabled = enabled))
    }

    suspend fun updateBrightness(brightness: Int) {
        val s = get()
        dao.upsert(s.copy(brightness = brightness))
    }

    suspend fun updateQuietHours(enabled: Boolean, start: Int, end: Int) {
        val s = get()
        dao.upsert(s.copy(quietHoursEnabled = enabled, quietHoursStart = start, quietHoursEnd = end))
    }

    suspend fun updateMusicSync(enabled: Boolean) {
        val s = get()
        dao.upsert(s.copy(musicSyncEnabled = enabled))
    }

    suspend fun updateMotionReactive(enabled: Boolean) {
        val s = get()
        dao.upsert(s.copy(motionReactiveEnabled = enabled))
    }

    suspend fun updateBaseColor(packed: Long) {
        val s = get()
        dao.upsert(s.copy(baseColorPacked = packed))
    }

    suspend fun updateForceHold(enabled: Boolean) {
        val s = get()
        dao.upsert(s.copy(forceHoldEnabled = enabled))
    }

    suspend fun updateForceHoldColor(packed: Long) {
        val s = get()
        dao.upsert(s.copy(forceHoldColorPacked = packed))
    }

    suspend fun updateAmbientEffect(id: Int) {
        val s = get()
        dao.upsert(s.copy(ambientEffectId = id))
    }
}
