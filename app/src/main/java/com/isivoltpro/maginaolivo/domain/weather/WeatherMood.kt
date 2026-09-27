package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedState

/** Phase 20C — the six looks Inicio's header can take; none when the weather is not known. */
enum class WeatherMood { CLEAR, CLOUDY, RAIN, WIND, FOG, STORM }

object WeatherMoods {
    /** From this wind on, a clear or cloudy sky reads as windy. */
    const val WINDY_KMH = 35

    /**
     * Only a current value decides the look. Unknown, unavailable or out-of-date weather gives
     * no mood, so the header never suggests a sky the farmer does not have. Snow has no look
     * of its own yet and gives none rather than a wrong one.
     */
    fun of(state: FeedState<WeatherNow>): WeatherMood? {
        if (state !is FeedState.Value || state.stale) return null
        val value = state.value
        val windy = (value.windKmh ?: 0) >= WINDY_KMH
        return when (value.condition) {
            WeatherCondition.STORM -> WeatherMood.STORM
            WeatherCondition.RAIN -> WeatherMood.RAIN
            WeatherCondition.FOG, WeatherCondition.HAZE -> WeatherMood.FOG
            WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY -> if (windy) WeatherMood.WIND else WeatherMood.CLOUDY
            WeatherCondition.CLEAR -> if (windy) WeatherMood.WIND else WeatherMood.CLEAR
            WeatherCondition.SNOW -> null
        }
    }
}
