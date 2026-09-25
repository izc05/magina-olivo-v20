package com.isivoltpro.maginaolivo.data.remote.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherReading
import com.isivoltpro.maginaolivo.domain.weather.WeatherSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
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
        val connection = URL("$functionsUrl/weather-forecast").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer $anonKey")
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("weather-forecast answered $status")
            EdgeWeatherResponse.parse(connection.inputStream.use { readCapped(it) })
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw IOException("weather-forecast response too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024
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
            ),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)
}
