package com.x8rgb.data.repository

import com.x8rgb.data.db.dao.AppSettingsDao
import com.x8rgb.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dao: AppSettingsDao) {

    fun observe(): Flow<AppSettingsEntity?> = dao.observe()

    suspend fun get(): AppSettingsEntity = dao.get() ?: AppSettingsEntity()

    suspend fun save(settings: AppSettingsEntity) = dao.upsert(settings)

    suspend fun updateBrightness(brightness: Int) {
        val current = get()
        dao.upsert(current.copy(brightness = brightness.coerceIn(80, 255)))
    }

    suspend fun updateMasterEnabled(enabled: Boolean) {
        dao.upsert(get().copy(masterEnabled = enabled))
    }

    suspend fun updateQuietHours(enabled: Boolean, start: Int, end: Int) {
        dao.upsert(get().copy(
            quietHoursEnabled = enabled,
            quietHoursStart   = start.coerceIn(0, 23),
            quietHoursEnd     = end.coerceIn(0, 23),
        ))
    }

    suspend fun updateMusicSync(enabled: Boolean) {
        dao.upsert(get().copy(musicSyncEnabled = enabled))
    }

    suspend fun updateMotionReactive(enabled: Boolean) {
        dao.upsert(get().copy(motionReactiveEnabled = enabled))
    }

    suspend fun updateBaseColor(packed: Long) {
        dao.upsert(get().copy(baseColorPacked = packed))
    }

    suspend fun updateForceHold(enabled: Boolean, colorPacked: Long? = null) {
        val cur = get()
        dao.upsert(
            cur.copy(
                forceHoldEnabled = enabled,
                forceHoldColorPacked = colorPacked ?: cur.forceHoldColorPacked,
            )
        )
    }

    suspend fun updateForceHoldColor(packed: Long) {
        dao.upsert(get().copy(forceHoldColorPacked = packed))
    }
}
