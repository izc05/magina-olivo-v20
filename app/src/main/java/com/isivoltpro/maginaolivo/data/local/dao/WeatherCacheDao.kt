package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.WeatherCacheEntity
import kotlinx.coroutines.flow.Flow

/** Phase 20A: the device cache of external feeds (table shipped in v1, unused until now). */
@Dao
interface WeatherCacheDao {
    @Upsert suspend fun upsert(row: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE cache_key = :key")
    suspend fun find(key: String): WeatherCacheEntity?

    @Query("SELECT * FROM weather_cache WHERE cache_key = :key")
    fun observe(key: String): Flow<WeatherCacheEntity?>
}
