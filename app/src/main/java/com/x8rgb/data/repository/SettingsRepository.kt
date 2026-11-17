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

    /** read-modify-write serialised to prevent lost updates from several coroutines */
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
