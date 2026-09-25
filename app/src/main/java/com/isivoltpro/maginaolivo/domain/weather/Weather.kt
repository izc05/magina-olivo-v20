package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Sky as the source reports it; the visual layer (20C) derives only from this. */
enum class WeatherCondition(val label: String) {
    CLEAR("Despejado"),
    PARTLY_CLOUDY("Parcialmente nublado"),
    CLOUDY("Nublado"),
    RAIN("Lluvia"),
    STORM("Tormenta"),
    SNOW("Nieve"),
    FOG("Niebla"),
}

/** The weather for the coming hours; every optional figure is null when the source omits it. */
data class WeatherNow(
    val temperatureC: Int,
    val condition: WeatherCondition,
    val rainProbabilityPercent: Int?,
    val windKmh: Int?,
    /** The hour the figures are for, as the source gives it. */
    val validAt: Instant,
)

/** A weather provider (20B: AEMET OpenData). Throws on any failure; callers keep the cache. */
interface WeatherSource {
    /** Shown next to every value, e.g. "AEMET". */
    val name: String

    suspend fun fetch(location: FeedLocation): WeatherNow
}

/** Inicio's weather: cached value first, a refresh only when it is stale or missing. */
interface WeatherFeed {
    fun observe(location: FeedLocation?): Flow<FeedState<WeatherNow>>

    /** Fetches when stale or missing; a failure leaves the cache as it was. */
    suspend fun refreshIfStale(location: FeedLocation)
}

/** Stored form of [WeatherNow] in the local cache: plain `key=value` lines, no JSON library. */
object WeatherCodec {
    fun encode(weather: WeatherNow): String = listOfNotNull(
        "t=${weather.temperatureC}",
        "c=${weather.condition.name}",
        weather.rainProbabilityPercent?.let { "p=$it" },
        weather.windKmh?.let { "w=$it" },
        "at=${weather.validAt.epochSecond}",
    ).joinToString("\n")

    /** Null when the stored text is not a complete weather value (never a partial guess). */
    fun decode(text: String): WeatherNow? {
        val fields = text.lines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val temperature = fields["t"]?.toIntOrNull() ?: return null
        val condition = fields["c"]?.let { name -> WeatherCondition.entries.firstOrNull { it.name == name } } ?: return null
        val validAt = fields["at"]?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        return WeatherNow(temperature, condition, fields["p"]?.toIntOrNull(), fields["w"]?.toIntOrNull(), validAt)
    }
}
