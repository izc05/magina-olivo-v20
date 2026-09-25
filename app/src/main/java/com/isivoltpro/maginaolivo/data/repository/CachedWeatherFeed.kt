package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.WeatherCacheEntity
import com.isivoltpro.maginaolivo.domain.feed.FeedAge
import com.isivoltpro.maginaolivo.domain.feed.FeedKind
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCodec
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Phase 20A — weather for Inicio, cache first. The UI reads only the local cache; a network
 * call happens only when the cached value is stale or missing, and any failure leaves the
 * last value (with its time) in place. With no [source] the feed is simply not configured.
 */
class CachedWeatherFeed(
    private val database: MaginaOlivoDatabase,
    private val source: WeatherSource?,
    private val workspaces: WorkspaceRepository,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    private val timeoutMillis: Long = 10_000,
) : WeatherFeed {
    override fun observe(location: FeedLocation?): Flow<FeedState<WeatherNow>> = when {
        source == null -> flowOf(FeedState.NotConfigured)
        location == null -> flowOf(FeedState.NoLocation)
        else -> database.weatherCacheDao().observe(keyOf(location)).map { row -> stateOf(row) }
    }

    override suspend fun refreshIfStale(location: FeedLocation) {
        val provider = source ?: return
        withContext(dispatchers.io) {
            val key = keyOf(location)
            val cached = database.weatherCacheDao().find(key)
            if (cached != null && WeatherCodec.decode(cached.payloadJson) != null &&
                !FeedAge.isStale(FeedKind.WEATHER, cached.fetchedAt, clock.nowInstant())
            ) {
                return@withContext
            }
            val workspaceId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@withContext
            val reading = try {
                withTimeout(timeoutMillis) { provider.fetch(location) }
            } catch (cancelled: CancellationException) {
                // A timeout is a failed fetch; a cancelled caller is not.
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) return@withContext else throw cancelled
            } catch (failure: Exception) {
                return@withContext
            }
            val now = clock.nowInstant()
            database.weatherCacheDao().upsert(
                WeatherCacheEntity(
                    cacheKey = key,
                    workspaceId = workspaceId,
                    // The provider that actually answered (AEMET or MET Norway), never assumed.
                    source = reading.provider,
                    payloadJson = WeatherCodec.encode(reading.weather),
                    fetchedAt = now,
                    expiresAt = now.plus(FeedKind.WEATHER.freshFor),
                ),
            )
        }
    }

    private fun stateOf(row: WeatherCacheEntity?): FeedState<WeatherNow> {
        val weather = row?.let { WeatherCodec.decode(it.payloadJson) } ?: return FeedState.Unavailable
        return FeedState.Value(weather, row.source, row.fetchedAt, FeedAge.isStale(FeedKind.WEATHER, row.fetchedAt, clock.nowInstant()))
    }

    private fun keyOf(location: FeedLocation): String = "${FeedKind.WEATHER.name}|${location.key}"
}
