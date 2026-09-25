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
    HAZE("Calima"),
}

/** The weather for the coming hours; every optional figure is null when the source omits it. */
data class WeatherNow(
    val temperatureC: Int,
    val condition: WeatherCondition,
    val rainProbabilityPercent: Int?,
    val windKmh: Int?,
    /** The hour the figures are for, as the source gives it. */
    val validAt: Instant,
    /** When the provider produced this forecast (20B); "Actualizado hace …" reads this. */
    val updatedAt: Instant? = null,
    /** The provider's required credit, shown with the value (20B). */
    val attribution: String? = null,
)

/** What a source answered: the provider that actually produced it, and the value. */
data class WeatherReading(val provider: String, val weather: WeatherNow)

/**
 * A weather source. 20B: the `weather-forecast` Edge Function, which asks AEMET and falls
 * back to MET Norway and says which one answered. Throws on any failure; callers keep the cache.
 */
interface WeatherSource {
    suspend fun fetch(location: FeedLocation): WeatherReading
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
        weather.updatedAt?.let { "u=${it.epochSecond}" },
        weather.attribution?.let { "a=${it.replace('\n', ' ')}" },
    ).joinToString("\n")

    /** Null when the stored text is not a complete weather value (never a partial guess). */
    fun decode(text: String): WeatherNow? {
        val fields = text.lines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val temperature = fields["t"]?.toIntOrNull() ?: return null
        val condition = fields["c"]?.let { name -> WeatherCondition.entries.firstOrNull { it.name == name } } ?: return null
        val validAt = fields["at"]?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        return WeatherNow(
            temperature,
            condition,
            fields["p"]?.toIntOrNull(),
            fields["w"]?.toIntOrNull(),
            validAt,
            fields["u"]?.toLongOrNull()?.let(Instant::ofEpochSecond),
            fields["a"]?.takeIf { it.isNotBlank() },
        )
    }
}
