package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.WeatherCacheEntity
import com.isivoltpro.maginaolivo.data.remote.market.EdgeOilMarketResponse
import com.isivoltpro.maginaolivo.data.remote.market.EdgeOilMarketSource
import com.isivoltpro.maginaolivo.data.remote.market.OilMarketJsonSource
import com.isivoltpro.maginaolivo.domain.feed.FeedAge
import com.isivoltpro.maginaolivo.domain.feed.FeedKind
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilMarketFeed
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilTrends
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Phase 20D — the official Andalucía oil series for Inicio, cache first, in the same device cache
 * as the weather (no Room change). The stored text is the function's own answer, re-read on the
 * way out; a failed refresh keeps the last one. The value is "stale" when its latest week is
 * older than the next weekly publication (plus a margin), not merely when it was fetched long ago.
 */
class CachedOilMarketFeed(
    private val database: MaginaOlivoDatabase,
    private val source: OilMarketJsonSource?,
    private val workspaces: WorkspaceRepository,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val weeks: Int = 12,
    private val timeoutMillis: Long = 10_000,
) : OilMarketFeed {
    override fun observe(): Flow<FeedState<OilMarketSeries>> =
        if (source == null) flowOf(FeedState.NotConfigured) else database.weatherCacheDao().observe(KEY).map(::stateOf)

    override suspend fun refreshIfStale() {
        val market = source ?: return
        withContext(dispatchers.io) {
            val cached = database.weatherCacheDao().find(KEY)
            if (cached != null && decode(cached.payloadJson) != null &&
                !FeedAge.isStale(FeedKind.OIL_MARKET, cached.fetchedAt, clock.nowInstant())
            ) {
                return@withContext
            }
            val workspaceId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@withContext
            val raw = try {
                withTimeout(timeoutMillis) { market.andaluciaJson(weeks) }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) return@withContext else throw cancelled
            } catch (failure: Exception) {
                return@withContext
            }
            // Only an answer that reads back completely replaces what the phone already has.
            val series = decode(raw) ?: return@withContext
            val now = clock.nowInstant()
            database.weatherCacheDao().upsert(
                WeatherCacheEntity(KEY, workspaceId, series.sourceName, raw, now, now.plus(FeedKind.OIL_MARKET.freshFor)),
            )
        }
    }

    private fun stateOf(row: WeatherCacheEntity?): FeedState<OilMarketSeries> {
        val series = row?.let { decode(it.payloadJson) } ?: return FeedState.Unavailable
        val latest = series.observations.maxByOrNull { it.periodEnd } ?: return FeedState.Unavailable
        return FeedState.Value(series, series.sourceName, row.fetchedAt, OilTrends.isOutdated(latest, clock.today(zone())))
    }

    private fun decode(json: String): OilMarketSeries? =
        runCatching { EdgeOilMarketResponse.parse(json, EdgeOilMarketSource.JUNTA).series.firstOrNull { it.geographyCode == "ES-AN" } }
            .getOrNull()
            ?.takeIf { it.observations.isNotEmpty() }

    private companion object {
        const val KEY = "OIL_MARKET|ES-AN"
    }
}
