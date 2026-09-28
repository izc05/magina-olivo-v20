package com.isivoltpro.maginaolivo

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import java.time.Instant
import java.time.LocalDate

/** Synthetic UI-only values. Never packaged in the APK or presented as live weather evidence. */
internal object WeatherVisualFixtures {
    val now: Instant = Instant.parse("2026-09-28T10:00:00Z")
    val today: LocalDate = LocalDate.of(2026, 9, 28)
    val location = FeedLocation("Bedmar y Garcíez", "Jaén")
    val fresh = FeedState.Value(
        value = WeatherNow(
            temperatureC = 22,
            condition = WeatherCondition.CLEAR,
            rainProbabilityPercent = 10,
            windKmh = 14,
            validAt = now,
            updatedAt = now.minusSeconds(1800),
            attribution = "Datos ficticios para comprobar el diseño",
            daily = listOf(
                WeatherDayForecast(today, 14, 26, WeatherCondition.CLEAR, 10, 0.0, 14),
                WeatherDayForecast(today.plusDays(1), 15, 25, WeatherCondition.PARTLY_CLOUDY, 20, null, 12),
                WeatherDayForecast(today.plusDays(2), 13, 21, WeatherCondition.RAIN, 80, 4.5, 18),
                WeatherDayForecast(today.plusDays(3), 12, 20, WeatherCondition.STORM, 90, 8.0, 26),
                WeatherDayForecast(today.plusDays(4), 14, 23, WeatherCondition.CLOUDY, 30, 0.4, 10),
                WeatherDayForecast(today.plusDays(5), 13, 24, WeatherCondition.FOG, null, null, 8),
                WeatherDayForecast(today.plusDays(6), null, null, null, null, null, null),
            ),
        ),
        source = "AEMET · prueba visual",
        fetchedAt = now,
        stale = false,
    )
}
