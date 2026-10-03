package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * #362 — the week reads at a glance. Only what the source published is said: a probability
 * appears only when the source gives one (never an invented 0 %), and «Sin lluvia» only when it
 * published 0 mm or 0 %.
 */

/** «Lluvia 30 % · 1,2 mm», «Sin lluvia», or null when the source said nothing about rain. */
internal fun dayRainLine(day: WeatherDayForecast): String? {
    val parts = listOfNotNull(
        day.rainProbabilityPercent?.takeIf { it > 0 }?.let { "Lluvia $it %" },
        day.rainMm?.takeIf { it > 0.0 }?.let { "${rainFormat(it)} mm" },
    )
    if (parts.isNotEmpty()) return parts.joinToString(" · ")
    return if (day.rainMm == 0.0 || day.rainProbabilityPercent == 0) "Sin lluvia" else null
}

/** True only when the day has published rain (mm or a probability above zero). */
internal fun dayHasRain(day: WeatherDayForecast): Boolean =
    (day.rainMm ?: 0.0) > 0.0 || (day.rainProbabilityPercent ?: 0) > 0

/**
 * «5 días con lluvia · Máx. 25° · Mayor lluvia: mañana, 7,9 mm» from the published values only;
 * null when there is nothing to summarise.
 */
internal fun weekSummary(days: List<WeatherDayForecast>, today: LocalDate): String? {
    if (days.isEmpty()) return null
    val knowsRain = days.any { it.rainMm != null || it.rainProbabilityPercent != null }
    // Codex #374: a dry week is only claimed when every day published its rain.
    val knowsEveryDay = days.all { it.rainMm != null || it.rainProbabilityPercent != null }
    val rainyDays = days.count(::dayHasRain)
    val rain = when {
        !knowsRain -> null
        rainyDays == 0 -> "Sin lluvia prevista".takeIf { knowsEveryDay }
        rainyDays == 1 -> "1 día con lluvia"
        else -> "$rainyDays días con lluvia"
    }
    val max = days.mapNotNull { it.maxTemperatureC }.maxOrNull()?.let { "Máx. $it°" }
    val wettest = days.filter { (it.rainMm ?: 0.0) > 0.0 }.maxByOrNull { it.rainMm!! }
        ?.let { "Mayor lluvia: ${shortDay(it.date, today)}, ${rainFormat(it.rainMm!!)} mm" }
    return listOfNotNull(rain, max, wettest).joinToString(" · ").ifEmpty { null }
}

/** The colour family of a day; the label and icon always say the same in words. */
internal enum class WeatherTone { RAIN, CLOUD, PARTLY, SUN, NEUTRAL }

internal fun WeatherCondition?.tone(): WeatherTone = when (this) {
    WeatherCondition.RAIN, WeatherCondition.STORM -> WeatherTone.RAIN
    WeatherCondition.CLOUDY, WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.SNOW -> WeatherTone.CLOUD
    WeatherCondition.PARTLY_CLOUDY -> WeatherTone.PARTLY
    WeatherCondition.CLEAR -> WeatherTone.SUN
    null -> WeatherTone.NEUTRAL
}

private fun shortDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "hoy"
    today.plusDays(1) -> "mañana"
    else -> date.format(WEEKDAY)
}

private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-ES"))
