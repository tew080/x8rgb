package com.x8rgb.data.repository

import com.x8rgb.data.db.dao.LedProfileDao
import com.x8rgb.data.db.entity.LedProfileEntity
import com.x8rgb.domain.model.LedProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val dao: LedProfileDao) {

    fun observeAll(): Flow<List<LedProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): LedProfile? = dao.getById(id)?.toDomain()

    suspend fun upsert(profile: LedProfile): Long =
        dao.upsert(LedProfileEntity.fromDomain(profile))

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Export profile as JSON string for offline sharing. */
    fun exportJson(profile: LedProfile): String = JSONObject().apply {
        put("name", profile.name)
        put("effectId", profile.effectId)
        put("speedMs", profile.speedMs)
        put("colors", JSONArray(profile.colorSequence.map { it.packed }))
    }.toString()

    /** Parse exported JSON string. Returns null on parse error. */
    fun importJson(json: String): LedProfile? = try {
        val obj = JSONObject(json)
        val colorsArr = obj.getJSONArray("colors")
        LedProfile(
            name          = obj.getString("name"),
            effectId      = obj.optInt("effectId", 0),
            speedMs       = obj.optInt("speedMs", 500),
            colorSequence = (0 until colorsArr.length()).map {
                com.x8rgb.domain.model.RgbColor(colorsArr.getLong(it))
            }
        )
    } catch (_: Exception) { null }
}
