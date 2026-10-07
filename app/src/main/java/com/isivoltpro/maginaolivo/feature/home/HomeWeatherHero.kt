package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.domain.feed.FeedAge
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherMoods
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPhotoBrand
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoTint
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGoldText
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow

/**
 * #345: what Inicio says about rain. The source's probability when it publishes one (AEMET);
 * otherwise today's estimated millimetres if any (MET Norway); otherwise nothing. A missing
 * probability is never shown as 0 %.
 */
internal fun WeatherNow.rainLine(today: java.time.LocalDate?): String? =
    rainProbabilityPercent?.let { "Prob. lluvia ${it.coerceIn(0, 100)} %" }
        ?: today?.let { day -> daily.firstOrNull { it.date == day }?.rainMm }?.let { "Lluvia prevista ${rainFormat(it)} mm" }

internal fun WeatherNow.solarLine(now: Instant): String? {
    val zone = solarTimeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: SUN_ZONE
    if (solarDate != now.atZone(zone).toLocalDate()) return null
    val sunrise = sunriseAt ?: return null
    val sunset = sunsetAt ?: return null
    return "Salida ${SUN_TIME.format(sunrise.atZone(zone))} · Puesta ${SUN_TIME.format(sunset.atZone(zone))}"
}

/** The photograph remains visible; content grows naturally at enlarged font sizes. */
@Composable
internal fun HomeWeatherHero(
    state: HomeUiState,
    greeting: String,
    date: String?,
    now: Instant,
    weatherMotion: Boolean?,
    onForecast: () -> Unit,
) {
    val minHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).coerceIn(360f, 460f).dp
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))) {
        Image(
            painterResource(R.drawable.onboarding_welcome_olive_grove),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        val mood = WeatherMoods.of(state.weather)
        if (weatherMotion == null) WeatherMoodLayer(mood, Modifier.matchParentSize())
        else WeatherMoodLayer(mood, Modifier.matchParentSize(), animate = weatherMotion)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    // #360: a neutral shade instead of the olive one, so the photograph keeps its
                    // own colours. White stays at least 4.5:1 from HERO_TEXT_STOP down, even over a
                    // completely white photograph (HeroShadeTest). Codex #376: with large text on a
                    // small screen the greeting starts at about 18 % of the hero, so the readable
                    // shade begins earlier, right under the brand.
                    0f to HERO_SHADE.copy(alpha = HERO_SHADE_TOP),
                    HERO_TEXT_STOP to HERO_SHADE.copy(alpha = HERO_SHADE_TEXT),
                    1f to HERO_SHADE.copy(alpha = HERO_SHADE_BOTTOM),
                ),
            ),
        )
        Column(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(MoSpacing.screen),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MoPhotoBrand()
            Spacer(Modifier.height(MoSpacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = MoWarmWhite)
                    date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MoWarmWhite) }
                }
                Column(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("home-weather-summary"),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        Icon(MoIcons.Location, null, tint = MoWarmWhite, modifier = Modifier.size(18.dp))
                        Text(
                            state.weatherLocation?.label ?: if (state.weatherLocationAmbiguous) "Tiempo de tus fincas" else "Tiempo de tu zona",
                            style = MaterialTheme.typography.titleSmall,
                            color = MoWarmWhite,
                        )
                    }
                    when (val weather = state.weather) {
                        is FeedState.Value -> {
                            weather.value.solarLine(now)?.let { line ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                                    modifier = Modifier.testTag("home-weather-solar"),
                                ) {
                                    Icon(MoIcons.Sun, null, tint = MoSoftGold, modifier = Modifier.size(20.dp))
                                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MoWarmWhite)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WeatherTemperature(weather.value.temperatureC, Modifier.weight(1f), MoWarmWhite)
                                WeatherConditionIcon(weather.value.condition, Modifier.size(56.dp), onPhoto = true)
                            }
                            Text(weather.value.condition.label, style = MaterialTheme.typography.titleMedium, color = MoWarmWhite)
                            // #345: rain as the source gives it — never a 0 % made up from a missing value.
                            weather.value.rainLine(state.today)?.let { line ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                                    modifier = Modifier.testTag("home-weather-rain"),
                                ) {
                                    Icon(MoIcons.Drop, null, tint = MoWarmWhite, modifier = Modifier.size(20.dp))
                                    Text(line, style = MaterialTheme.typography.titleMedium, color = MoWarmWhite)
                                }
                            }
                            if (weather.stale) {
                                Text("Datos guardados · sin actualizar", style = MaterialTheme.typography.labelLarge, color = MoSoftGold)
                            }
                            Text(
                                "${weather.source} · ${FeedAge.label(weather.value.updatedAt ?: weather.fetchedAt, now)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoWarmWhite,
                            )
                        }
                        else -> {
                            Text(
                                if (state.isLoading) "Consultando el tiempo…" else "Previsión no disponible",
                                style = MaterialTheme.typography.titleLarge,
                                color = MoWarmWhite,
                            )
                            Text(
                                when (weather) {
                                    FeedState.NoLocation -> if (state.weatherLocationAmbiguous) "Varias ubicaciones · Elige tu municipio en Perfil" else "Añade el municipio en Mi Campo o en Perfil"
                                    FeedState.NotConfigured -> "Fuente del tiempo no configurada"
                                    else -> "Conéctate para consultar el tiempo de tu zona"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MoWarmWhite,
                            )
                        }
                    }
                }
                Surface(
                    onClick = onForecast,
                    modifier = Modifier.fillMaxWidth().testTag("home-weather-hero"),
                    shape = MoShape.field,
                    color = MoWarmWhite.copy(alpha = 0.96f),
                ) {
                    Row(
                        Modifier.heightIn(min = 56.dp).padding(horizontal = MoSpacing.sm, vertical = MoSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
                    ) {
                        Icon(MoIcons.Weather, null, tint = MoInfoText, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ver previsión", style = MaterialTheme.typography.labelLarge, color = MoOliveDark)
                            Text("Próximos días y radar", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
                        }
                        Icon(MoIcons.ChevronRight, null, tint = MoOliveDark, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeatherTemperature(temperatureC: Int, modifier: Modifier = Modifier, color: Color = MoOliveDark) {
    Text(
        "$temperatureC°",
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 64.sp,
            lineHeight = 72.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
        ),
        color = color,
        modifier = modifier,
    )
}

/** One sky symbol per condition, shared by Home, the current summary and every forecast day. */
@Composable
internal fun WeatherConditionIcon(condition: WeatherCondition?, modifier: Modifier = Modifier, onPhoto: Boolean = false) {
    val icon = when (condition) {
        WeatherCondition.CLEAR -> MoIcons.Sun
        WeatherCondition.PARTLY_CLOUDY -> MoIcons.Weather
        WeatherCondition.CLOUDY -> MoIcons.Cloud
        WeatherCondition.RAIN -> MoIcons.Rain
        WeatherCondition.STORM -> MoIcons.Storm
        WeatherCondition.SNOW -> MoIcons.Snow
        WeatherCondition.FOG, WeatherCondition.HAZE -> MoIcons.Fog
        null -> MoIcons.Calendar
    }
    val tint = when {
        condition == WeatherCondition.CLEAR -> if (onPhoto) MoSoftGold else MoSoftGoldText
        onPhoto -> if (condition == WeatherCondition.RAIN || condition == WeatherCondition.SNOW) MoInfoTint else MoWarmWhite
        else -> MoInfoText
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier)
}

/** #360: near-neutral, slightly warm shade over the hero photo (no olive cast). */
private val SUN_ZONE = ZoneId.of("Europe/Madrid")
private val SUN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal val HERO_SHADE = Color(0xFF1A1A16)
internal const val HERO_SHADE_TOP = 0.10f
/** Where the readable shade is reached: above the highest point any text below the brand can start. */
internal const val HERO_TEXT_STOP = 0.14f
internal const val HERO_SHADE_TEXT = 0.62f
internal const val HERO_SHADE_BOTTOM = 0.80f
