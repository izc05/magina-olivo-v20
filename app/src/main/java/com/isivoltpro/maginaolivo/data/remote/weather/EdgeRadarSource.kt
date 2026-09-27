package com.isivoltpro.maginaolivo.data.remote.weather

import com.isivoltpro.maginaolivo.domain.weather.RadarFrame
import com.isivoltpro.maginaolivo.domain.weather.RadarFrames
import com.isivoltpro.maginaolivo.domain.weather.RadarSource
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Phase 20B-radar (CR-006) — the recent radar pictures from the `weather-radar` Edge Function
 * (RainViewer behind it), over the same plain HTTPS POST and public anon key as the forecast.
 */
class EdgeRadarSource(
    private val functionsUrl: String,
    private val anonKey: String,
    private val timeoutMillis: Int = 10_000,
) : RadarSource {
    override suspend fun frames(): RadarFrames = withContext(Dispatchers.IO) {
        EdgeRadarResponse.parse(
            EdgeFunctionHttp.post(functionsUrl, "weather-radar", anonKey, """{"operation":"frames"}""", timeoutMillis),
        )
    }
}

/** The function's response contract (supabase/functions/weather-radar/handler.ts). */
object EdgeRadarResponse {
    /** Throws on anything incomplete: a bad answer is "no radar", never a guessed picture. */
    fun parse(json: String): RadarFrames {
        val body = JSONObject(json)
        val provider = body.optString("providerName").ifBlank { body.getString("provider") }
        val attribution = body.getString("attribution")
        require(attribution.isNotBlank()) { "radar without attribution" }
        val list = body.getJSONArray("frames")
        val frames = (0 until list.length()).map { index ->
            val frame = list.getJSONObject(index)
            val template = frame.getString("tileUrlTemplate")
            require(template.startsWith("https://") && listOf("{z}", "{x}", "{y}").all { it in template }) {
                "unusable radar tile template"
            }
            RadarFrame(Instant.parse(frame.getString("time")), template)
        }.sortedBy { it.time }
        return RadarFrames(
            provider = provider,
            attribution = attribution,
            updatedAt = body.optString("updatedAt").takeIf { it.isNotBlank() }?.let(Instant::parse),
            frames = frames,
        )
    }
}
