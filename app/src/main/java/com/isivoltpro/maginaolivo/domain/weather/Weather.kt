package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import java.time.Instant
import java.time.LocalDate
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

/** Daily values supplied by one provider. Null means the source did not publish that measure. */
data class WeatherDayForecast(
    val date: LocalDate,
    val minTemperatureC: Int?,
    val maxTemperatureC: Int?,
    val condition: WeatherCondition?,
    val rainProbabilityPercent: Int?,
    val rainMm: Double?,
    val windKmh: Int?,
)

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
    /** Optional additive field: empty for pre-week responses and legacy cache rows. */
    val daily: List<WeatherDayForecast> = emptyList(),
    /** Local calendar date the solar calculation belongs to. */
    val solarDate: LocalDate? = null,
    /** IANA zone used for the solar civil date/times; null for old cached responses. */
    val solarTimeZone: String? = null,
    /** Civil sunrise/sunset as UTC instants; null when coordinates were unavailable. */
    val sunriseAt: Instant? = null,
    val sunsetAt: Instant? = null,
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

    /**
     * #315: the farmer's «Actualizar» — fetches now whatever the age. False when the source
     * failed; the cached value and its time stay as they were.
     */
    suspend fun refresh(location: FeedLocation): Boolean
}

/** Stored form of [WeatherNow] in the local cache: plain `key=value` lines, no JSON library. */
object WeatherCodec {
    fun encode(weather: WeatherNow): String = (listOfNotNull(
        "t=${weather.temperatureC}",
        "c=${weather.condition.name}",
        weather.rainProbabilityPercent?.let { "p=$it" },
        weather.windKmh?.let { "w=$it" },
        "at=${weather.validAt.epochSecond}",
        weather.updatedAt?.let { "u=${it.epochSecond}" },
        weather.attribution?.let { "a=${it.replace('\n', ' ')}" },
        weather.solarDate?.let { "sd=$it" },
        weather.solarTimeZone?.let { "sz=$it" },
        weather.sunriseAt?.let { "sr=${it.epochSecond}" },
        weather.sunsetAt?.let { "ss=${it.epochSecond}" },
    ) + weather.daily.map { day ->
            "d=${listOf(
                day.date.toString(),
                day.minTemperatureC?.toString().orEmpty(),
                day.maxTemperatureC?.toString().orEmpty(),
                day.condition?.name.orEmpty(),
                day.rainProbabilityPercent?.toString().orEmpty(),
                day.rainMm?.toString().orEmpty(),
                day.windKmh?.toString().orEmpty(),
            ).joinToString("|")}"
        }).joinToString("\n")

    /** Null when the stored text is not a complete weather value (never a partial guess). */
    fun decode(text: String): WeatherNow? {
        val fields = text.lines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val temperature = fields["t"]?.toIntOrNull() ?: return null
        val condition = fields["c"]?.let { name -> WeatherCondition.entries.firstOrNull { it.name == name } } ?: return null
        val validAt = fields["at"]?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        val daily = text.lines().mapNotNull { line ->
            if (!line.startsWith("d=")) return@mapNotNull null
            val columns = line.removePrefix("d=").split('|')
            if (columns.size != 7) return@mapNotNull null
            val date = runCatching { LocalDate.parse(columns[0]) }.getOrNull() ?: return@mapNotNull null
            WeatherDayForecast(
                date = date,
                minTemperatureC = columns[1].toIntOrNull(),
                maxTemperatureC = columns[2].toIntOrNull(),
                condition = WeatherCondition.entries.firstOrNull { it.name == columns[3] },
                rainProbabilityPercent = columns[4].toIntOrNull(),
                rainMm = columns[5].toDoubleOrNull(),
                windKmh = columns[6].toIntOrNull(),
            )
        }
        return WeatherNow(
            temperatureC = temperature,
            condition = condition,
            rainProbabilityPercent = fields["p"]?.toIntOrNull(),
            windKmh = fields["w"]?.toIntOrNull(),
            validAt = validAt,
            updatedAt = fields["u"]?.toLongOrNull()?.let(Instant::ofEpochSecond),
            attribution = fields["a"]?.takeIf { it.isNotBlank() },
            daily = daily,
            solarDate = fields["sd"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            solarTimeZone = fields["sz"]?.takeIf { it.isNotBlank() },
            sunriseAt = fields["sr"]?.toLongOrNull()?.let(Instant::ofEpochSecond),
            sunsetAt = fields["ss"]?.toLongOrNull()?.let(Instant::ofEpochSecond),
        )
    }
}
