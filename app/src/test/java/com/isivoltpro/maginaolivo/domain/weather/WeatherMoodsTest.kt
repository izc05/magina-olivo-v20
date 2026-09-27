package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 20C: the header's look comes only from a current weather value. */
class WeatherMoodsTest {
    private val now = Instant.parse("2026-09-27T09:00:00Z")

    private fun current(condition: WeatherCondition, windKmh: Int? = 10, stale: Boolean = false) =
        FeedState.Value(WeatherNow(20, condition, null, windKmh, now), "AEMET", now, stale)

    @Test fun eachSkyHasItsLook() {
        assertEquals(WeatherMood.CLEAR, WeatherMoods.of(current(WeatherCondition.CLEAR)))
        assertEquals(WeatherMood.CLOUDY, WeatherMoods.of(current(WeatherCondition.PARTLY_CLOUDY)))
        assertEquals(WeatherMood.CLOUDY, WeatherMoods.of(current(WeatherCondition.CLOUDY)))
        assertEquals(WeatherMood.RAIN, WeatherMoods.of(current(WeatherCondition.RAIN)))
        assertEquals(WeatherMood.STORM, WeatherMoods.of(current(WeatherCondition.STORM)))
        assertEquals(WeatherMood.FOG, WeatherMoods.of(current(WeatherCondition.FOG)))
        assertEquals(WeatherMood.FOG, WeatherMoods.of(current(WeatherCondition.HAZE)))
    }

    @Test fun strongWindTurnsAClearOrCloudySkyWindyButNeverRainOrStorm() {
        assertEquals(WeatherMood.WIND, WeatherMoods.of(current(WeatherCondition.CLEAR, windKmh = WeatherMoods.WINDY_KMH)))
        assertEquals(WeatherMood.WIND, WeatherMoods.of(current(WeatherCondition.CLOUDY, windKmh = 50)))
        assertEquals(WeatherMood.CLEAR, WeatherMoods.of(current(WeatherCondition.CLEAR, windKmh = null)))
        assertEquals(WeatherMood.RAIN, WeatherMoods.of(current(WeatherCondition.RAIN, windKmh = 60)))
        assertEquals(WeatherMood.STORM, WeatherMoods.of(current(WeatherCondition.STORM, windKmh = 60)))
    }

    @Test fun unknownOrOutOfDateWeatherGivesNoLook() {
        assertNull(WeatherMoods.of(FeedState.NotConfigured))
        assertNull(WeatherMoods.of(FeedState.NoLocation))
        assertNull(WeatherMoods.of(FeedState.Unavailable))
        assertNull(WeatherMoods.of(current(WeatherCondition.RAIN, stale = true)))
        // Snow has no look yet: none rather than a wrong one.
        assertNull(WeatherMoods.of(current(WeatherCondition.SNOW)))
    }
}
