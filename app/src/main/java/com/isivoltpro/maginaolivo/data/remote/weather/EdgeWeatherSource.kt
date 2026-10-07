package com.isivoltpro.maginaolivo.data.remote.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.weather.WeatherReading
import com.isivoltpro.maginaolivo.domain.weather.WeatherSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Phase 20B (CR-006) — the only remote call the app makes to the project's backend before
 * Phase 22: an HTTPS POST to the `weather-forecast` Supabase Edge Function. That function asks
 * AEMET (its key stays in the function's secrets) and falls back to MET Norway, and says which
 * one answered. No Supabase SDK, no Auth, no sync; only the project's public anon key.
 */
class EdgeWeatherSource(
    private val functionsUrl: String,
    private val anonKey: String,
    private val timeoutMillis: Int = 10_000,
) : WeatherSource {
    override suspend fun fetch(location: FeedLocation): WeatherReading = withContext(Dispatchers.IO) {
        val request = JSONObject().apply {
            put("municipality", location.municipality)
            location.province?.let { put("province", it) }
        }
        EdgeWeatherResponse.parse(
            EdgeFunctionHttp.post(functionsUrl, "weather-forecast", anonKey, request.toString(), timeoutMillis),
        )
    }
}

/** The function's response contract (supabase/functions/weather-forecast/contract.ts). */
object EdgeWeatherResponse {
    private val PROVIDER_NAMES = mapOf("AEMET" to "AEMET", "MET_NORWAY" to "MET Norway")

    /** Throws on anything incomplete or unknown: a bad answer is a failed fetch, never a guess. */
    fun parse(json: String): WeatherReading {
        val body = JSONObject(json)
        val providerId = body.getString("provider")
        val provider = body.optString("providerName").ifBlank { PROVIDER_NAMES[providerId].orEmpty() }
        require(provider.isNotBlank()) { "unknown provider $providerId" }
        val current = body.getJSONObject("current")
        val condition = WeatherCondition.entries.firstOrNull { it.name == current.getString("condition") }
            ?: throw IllegalArgumentException("unknown condition")
        val dailyArray = body.optJSONArray("daily")
        val daily = (0 until (dailyArray?.length() ?: 0)).mapNotNull { index ->
            val day = dailyArray?.optJSONObject(index) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(day.optString("date")) }.getOrNull() ?: return@mapNotNull null
            val dayCondition = day.optString("condition").takeIf(String::isNotBlank)
                ?.let { name -> WeatherCondition.entries.firstOrNull { it.name == name } }
            WeatherDayForecast(
                date = date,
                minTemperatureC = day.optIntOrNull("minTemperatureC"),
                maxTemperatureC = day.optIntOrNull("maxTemperatureC"),
                condition = dayCondition,
                rainProbabilityPercent = day.optIntOrNull("rainProbabilityPercent"),
                rainMm = day.optDoubleOrNull("rainMm"),
                windKmh = day.optIntOrNull("windKmh"),
            )
        }.take(7)
        val solar = body.optJSONObject("solar")
        return WeatherReading(
            provider = provider,
            weather = WeatherNow(
                temperatureC = current.getInt("temperatureC"),
                condition = condition,
                rainProbabilityPercent = current.optIntOrNull("rainProbabilityPercent"),
                windKmh = current.optIntOrNull("windKmh"),
                validAt = Instant.parse(current.getString("validAt")),
                updatedAt = Instant.parse(body.getString("updatedAt")),
                attribution = body.optString("attribution").ifBlank { null },
                daily = daily,
                solarDate = solar?.optString("date")?.takeIf(String::isNotBlank)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                sunriseAt = solar?.optString("sunriseAt")?.takeIf(String::isNotBlank)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                sunsetAt = solar?.optString("sunsetAt")?.takeIf(String::isNotBlank)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            ),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key)
}
