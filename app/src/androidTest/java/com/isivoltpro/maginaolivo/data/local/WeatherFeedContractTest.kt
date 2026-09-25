package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.repository.CachedWeatherFeed
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 20A — Inicio's weather is read from the local cache only; the network is asked only
 * when the value is stale or missing, and a failure never erases what was known.
 */
@RunWith(AndroidJUnit4::class)
class WeatherFeedContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bedmar = FeedLocation("Bedmar", "Jaén")
    private val start = Instant.parse("2026-11-26T09:00:00Z")
    private val sunny = WeatherNow(22, WeatherCondition.CLEAR, 5, 11, Instant.parse("2026-11-26T10:00:00Z"))

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
    fun withoutASourceOrAPlaceTheFeedSaysSo() = runBlocking {
        assertEquals(FeedState.NotConfigured, feed(source = null).observe(bedmar).first())
        assertEquals(FeedState.NoLocation, feed().observe(null).first())
    }

    @Test
    fun aFailedFirstFetchIsUnavailableNeverAGuess() = runBlocking {
        source.failNext = true
        val weather = feed()
        weather.refreshIfStale(bedmar)
        assertEquals(FeedState.Unavailable, weather.observe(bedmar).first())
    }

    @Test
    fun aFreshValueIsNotFetchedAgainAndAStaleOneSurvivesAFailure() = runBlocking {
        val weather = feed()
        weather.refreshIfStale(bedmar)
        weather.refreshIfStale(bedmar)
        assertEquals(1, source.calls)
        val fresh = weather.observe(bedmar).first() as FeedState.Value
        assertEquals(sunny, fresh.value)
        assertEquals("AEMET", fresh.source)
        assertEquals(start, fresh.fetchedAt)
        assertEquals(false, fresh.stale)

        // Four hours later, offline: the last value stays, marked stale, with its old time.
        clock.value = start.plusSeconds(4 * 3600)
        source.failNext = true
        weather.refreshIfStale(bedmar)
        assertEquals(2, source.calls)
        val stale = weather.observe(bedmar).first() as FeedState.Value
        assertEquals(sunny, stale.value)
        assertEquals(start, stale.fetchedAt)
        assertTrue(stale.stale)
    }

    @Test
    fun theSamePlaceSpelledDifferentlySharesItsCache() = runBlocking {
        val weather = feed()
        weather.refreshIfStale(bedmar)
        weather.refreshIfStale(FeedLocation("BEDMAR", "Jaen"))
        assertEquals(1, source.calls)
    }

    private fun feed(source: WeatherSource? = this.source) = CachedWeatherFeed(
        db, source, Workspaces, clock, TestDispatchers,
    )

    private inner class ScriptedSource : WeatherSource {
        override val name = "AEMET"
        var calls = 0
        var failNext = false

        override suspend fun fetch(location: FeedLocation): WeatherNow {
            calls++
            if (failNext) {
                failNext = false
                throw IOException("offline")
            }
            return sunny
        }
    }

    private class MovableClock(var value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object Workspaces : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> =
            AppResult.Success(UUID.fromString("10000000-0000-0000-0000-000000000020"))
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "weather-feed-contract-test.db"
    }
}
