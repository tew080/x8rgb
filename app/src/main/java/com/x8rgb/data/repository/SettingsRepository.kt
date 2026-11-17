package com.x8rgb.data.repository

import com.x8rgb.data.db.dao.AppSettingsDao
import com.x8rgb.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: AppSettingsDao
) {
    private val mutex = Mutex()

    /** read-modify-write แบบ serialize ป้องกัน lost update จากหลาย coroutine */
    private suspend fun mutate(block: (AppSettingsEntity) -> AppSettingsEntity) {
        mutex.withLock {
            val current = dao.get() ?: AppSettingsEntity()
            dao.upsert(block(current))
        }
    }

    fun observe(): Flow<AppSettingsEntity?> = dao.observe()

    suspend fun get(): AppSettingsEntity = dao.get() ?: AppSettingsEntity()

    suspend fun updateMasterEnabled(enabled: Boolean) {
        mutate { it.copy(masterEnabled = enabled) }
    }

    suspend fun updateBrightness(brightness: Int) {
        mutate { it.copy(brightness = brightness) }
    }

    suspend fun updateQuietHours(enabled: Boolean, start: Int, end: Int) {
        mutate { it.copy(quietHoursEnabled = enabled, quietHoursStart = start, quietHoursEnd = end) }
    }

    suspend fun updateMusicSync(enabled: Boolean) {
        mutate { it.copy(musicSyncEnabled = enabled) }
    }

    suspend fun updateMotionReactive(enabled: Boolean) {
        mutate { it.copy(motionReactiveEnabled = enabled) }
    }

    suspend fun updateBaseColor(packed: Long) {
        mutate { it.copy(baseColorPacked = packed) }
    }

    suspend fun updateAmbientEffect(id: Int) {
        mutate { it.copy(ambientEffectId = id) }
    }

    suspend fun updateForceHold(enabled: Boolean) {
        mutate { it.copy(forceHoldEnabled = enabled) }
    }

    suspend fun updateForceHoldColor(packed: Long) {
        mutate { it.copy(forceHoldColorPacked = packed) }
    }

    suspend fun updateForceHoldEffect(id: Int) {
        mutate { it.copy(forceHoldEffectId = id) }
    }
}