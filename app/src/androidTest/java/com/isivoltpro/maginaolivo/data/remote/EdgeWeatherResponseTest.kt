package com.isivoltpro.maginaolivo.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.remote.weather.EdgeWeatherResponse
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import java.time.LocalDate
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 20B — the app reads the `weather-forecast` contract exactly
 * (supabase/functions/weather-forecast/contract.ts); fixtures, never a live call.
 */
@RunWith(AndroidJUnit4::class)
class EdgeWeatherResponseTest {
    private val aemet = """
        {"provider":"AEMET","providerName":"AEMET",
         "attribution":"© AEMET. Información elaborada por la Agencia Estatal de Meteorología.",
         "updatedAt":"2026-09-25T05:40:00.000Z","fetchedAt":"2026-09-25T06:30:00.000Z",
         "location":{"code":"23000","name":"Bedmar y Garcíez","province":"Jaén"},
         "current":{"validAt":"2026-09-25T06:00:00.000Z","temperatureC":16,"condition":"PARTLY_CLOUDY",
                    "rainProbabilityPercent":15,"windKmh":11}}
    """.trimIndent()

    @Test
    fun anAemetAnswerKeepsItsProviderTimeAndCredit() {
        val reading = EdgeWeatherResponse.parse(aemet)
        assertEquals("AEMET", reading.provider)
        assertEquals(16, reading.weather.temperatureC)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, reading.weather.condition)
        assertEquals(15, reading.weather.rainProbabilityPercent)
        assertEquals(11, reading.weather.windKmh)
        assertEquals(Instant.parse("2026-09-25T06:00:00Z"), reading.weather.validAt)
        assertEquals(Instant.parse("2026-09-25T05:40:00Z"), reading.weather.updatedAt)
        assertEquals("© AEMET. Información elaborada por la Agencia Estatal de Meteorología.", reading.weather.attribution)
    }

    @Test
    fun solarTimesAreOptionalAndParsedWhenPresent() {
        val withSolar = aemet.replace(
            "\"current\":{",
            "\"solar\":{\"date\":\"2026-09-25\",\"timeZone\":\"Europe/Madrid\",\"sunriseAt\":\"2026-09-25T06:04:57Z\",\"sunsetAt\":\"2026-09-25T18:08:28Z\"},\"current\":{",
        )
        val weather = EdgeWeatherResponse.parse(withSolar).weather
        assertEquals(LocalDate.parse("2026-09-25"), weather.solarDate)
        assertEquals("Europe/Madrid", weather.solarTimeZone)
        assertEquals(Instant.parse("2026-09-25T06:04:57Z"), weather.sunriseAt)
        assertEquals(Instant.parse("2026-09-25T18:08:28Z"), weather.sunsetAt)

        val legacy = EdgeWeatherResponse.parse(aemet).weather
        assertNull(legacy.solarDate)
        assertNull(legacy.solarTimeZone)
        assertNull(legacy.sunriseAt)
        assertNull(legacy.sunsetAt)
    }

    @Test
    fun dailyForecastIsParsedAndOldCurrentOnlyResponsesStayValid() {
        val withDaily = aemet.replace(
            "\"current\":{",
            "\"daily\":[{\"date\":\"2026-09-25\",\"minTemperatureC\":13,\"maxTemperatureC\":25,\"condition\":\"RAIN\",\"rainProbabilityPercent\":65,\"rainMm\":1.2,\"windKmh\":18}],\"current\":{",
        )
        val day = EdgeWeatherResponse.parse(withDaily).weather.daily.single()
        assertEquals(LocalDate.parse("2026-09-25"), day.date)
        assertEquals(13, day.minTemperatureC)
        assertEquals(25, day.maxTemperatureC)
        assertEquals(WeatherCondition.RAIN, day.condition)
        assertEquals(65, day.rainProbabilityPercent)
        assertEquals(1.2, day.rainMm!!, 0.001)
        assertEquals(18, day.windKmh)
        assertEquals(emptyList<Any>(), EdgeWeatherResponse.parse(aemet).weather.daily)
    }

    @Test
    fun aMetNorwayFallbackHasNoInventedRainProbability() {
        val reading = EdgeWeatherResponse.parse(
            aemet.replace("\"provider\":\"AEMET\",\"providerName\":\"AEMET\"", "\"provider\":\"MET_NORWAY\",\"providerName\":\"MET Norway\"")
                .replace("\"rainProbabilityPercent\":15", "\"rainProbabilityPercent\":null"),
        )
        assertEquals("MET Norway", reading.provider)
        assertNull(reading.weather.rainProbabilityPercent)
    }

    @Test
    fun anIncompleteOrUnknownAnswerIsAFailureNotAGuess() {
        assertThrows(Exception::class.java) { EdgeWeatherResponse.parse(aemet.replace("PARTLY_CLOUDY", "SUNNY_ISH")) }
        assertThrows(Exception::class.java) { EdgeWeatherResponse.parse("""{"error":"providers_unavailable"}""") }
        assertThrows(Exception::class.java) { EdgeWeatherResponse.parse(aemet.replace("\"temperatureC\":16,", "")) }
    }
}
