package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.remote.market.OilMarketJsonSource
import com.isivoltpro.maginaolivo.data.repository.CachedOilMarketFeed
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilTrends
import com.isivoltpro.maginaolivo.domain.market.TrendDirection
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 20D — the official weekly oil series lives in the device cache: fetched at most once a
 * day, kept through failures and offline, and marked old once a newer week should exist.
 */
@RunWith(AndroidJUnit4::class)
class OilMarketFeedContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-09-27T09:00:00Z")
    private lateinit var db: MaginaOlivoDatabase
    private val clock = MovableClock(start)
    private val source = ScriptedSource()

    @Before
    fun before() {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun withoutASourceItIsNotConfiguredAndAFailedFirstFetchIsUnavailable() = runBlocking {
        assertEquals(FeedState.NotConfigured, feed(null).observe().first())
        source.failNext = true
        val market = feed()
        market.refreshIfStale()
        assertEquals(FeedState.Unavailable, market.observe().first())
    }

    @Test
    fun theCachedWeeksGiveTheTrendAndSurviveAFailureUntilTheyAreOld() = runBlocking {
        val market = feed()
        market.refreshIfStale()
        market.refreshIfStale() // fresh within a day: not asked again
        assertEquals(1, source.calls)
        val value = market.observe().first() as FeedState.Value
        assertEquals("Observatorio de Precios y Mercados - Junta de Andalucía", value.source)
        assertFalse(value.stale)
        assertEquals(TrendDirection.DOWN, OilTrends.of(value.value, OilCategory.AOVE)!!.direction)

        // Two weeks later and offline: same weeks, same time, now marked as old.
        clock.value = start.plusSeconds(14L * 24 * 3600)
        source.failNext = true
        market.refreshIfStale()
        assertEquals(2, source.calls)
        val old = market.observe().first() as FeedState.Value
        assertEquals(start, old.fetchedAt)
        assertTrue(old.stale)
        assertEquals(6, old.value.observations.size)
    }

    @Test
    fun aBrokenAnswerNeverReplacesGoodWeeks() = runBlocking {
        val market = feed()
        market.refreshIfStale()
        clock.value = start.plusSeconds(2L * 24 * 3600)
        source.answer = """{"provider":"oil-market","fetchedAt":"2026-09-29T09:00:00Z","series":[{"sourceId":"poolred"}]}"""
        market.refreshIfStale()
        val value = market.observe().first() as FeedState.Value
        assertEquals(start, value.fetchedAt)
        assertEquals(6, value.value.observations.size)
    }

    private fun feed(source: OilMarketJsonSource? = this.source) =
        CachedOilMarketFeed(db, source, Workspaces, clock, TestDispatchers, zone = { ZoneOffset.UTC })

    private inner class ScriptedSource : OilMarketJsonSource {
        var calls = 0
        var failNext = false
        var answer = JUNTA_37_38

        override suspend fun andaluciaJson(weeks: Int): String {
            calls++
            if (failNext) {
                failNext = false
                throw IOException("offline")
            }
            return answer
        }
    }

    private class MovableClock(var value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object Workspaces : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> =
            AppResult.Success(UUID.fromString("10000000-0000-0000-0000-00000000020d"))
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "oil-market-feed-contract-test.db"

        // The owner's verified Junta de Andalucía weeks 37 and 38 (free-market-sources.json).
        val JUNTA_37_38 = """
            {"provider":"oil-market","fetchedAt":"2026-09-27T09:00:00Z","series":[
             {"sourceId":"junta-andalucia-observatorio","sourceName":"Observatorio de Precios y Mercados - Junta de Andalucía",
              "geography":{"code":"ES-AN","name":"Andalucía"},"marketStage":"ALMAZARA_OR_BODEGA","category":"AOVE","observations":[
               {"periodStart":"2026-09-07","periodEnd":"2026-09-13","valueEurPerKg":3.67,"originalValue":3.67,"originalUnit":"EUR_PER_KG"},
               {"periodStart":"2026-09-14","periodEnd":"2026-09-20","valueEurPerKg":3.46,"originalValue":3.46,"originalUnit":"EUR_PER_KG"}]},
             {"sourceId":"junta-andalucia-observatorio","sourceName":"Observatorio de Precios y Mercados - Junta de Andalucía",
              "geography":{"code":"ES-AN","name":"Andalucía"},"marketStage":"ALMAZARA_OR_BODEGA","category":"AOV","observations":[
               {"periodStart":"2026-09-07","periodEnd":"2026-09-13","valueEurPerKg":3.30,"originalValue":3.30,"originalUnit":"EUR_PER_KG"},
               {"periodStart":"2026-09-14","periodEnd":"2026-09-20","valueEurPerKg":3.31,"originalValue":3.31,"originalUnit":"EUR_PER_KG"}]},
             {"sourceId":"junta-andalucia-observatorio","sourceName":"Observatorio de Precios y Mercados - Junta de Andalucía",
              "geography":{"code":"ES-AN","name":"Andalucía"},"marketStage":"ALMAZARA_OR_BODEGA","category":"AOL","observations":[
               {"periodStart":"2026-09-07","periodEnd":"2026-09-13","valueEurPerKg":3.19,"originalValue":3.19,"originalUnit":"EUR_PER_KG"},
               {"periodStart":"2026-09-14","periodEnd":"2026-09-20","valueEurPerKg":3.15,"originalValue":3.15,"originalUnit":"EUR_PER_KG"}]}]}
        """.trimIndent()
    }
}
