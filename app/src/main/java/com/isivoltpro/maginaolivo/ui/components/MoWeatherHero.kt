package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

enum class WeatherVisualState {
    Clear,
    Cloudy,
    Rain,
    Fog,
    Wind,
    Storm,
}

@Composable
fun MoWeatherHero(
    location: String,
    temperature: String,
    condition: String,
    rainText: String,
    windText: String,
    state: WeatherVisualState,
    modifier: Modifier = Modifier,
) {
    val colors = when (state) {
        WeatherVisualState.Clear -> listOf(ColorHex(0xFFFFE7B5), OlivarColors.Cream100)
        WeatherVisualState.Cloudy -> listOf(ColorHex(0xFFD9E1DC), OlivarColors.Cream100)
        WeatherVisualState.Rain -> listOf(ColorHex(0xFFC9D8DE), ColorHex(0xFFE9EEF0))
        WeatherVisualState.Fog -> listOf(ColorHex(0xFFE5E9E5), OlivarColors.Cream100)
        WeatherVisualState.Wind -> listOf(ColorHex(0xFFDDE8DA), OlivarColors.Cream100)
        WeatherVisualState.Storm -> listOf(ColorHex(0xFF9EABB0), ColorHex(0xFFD9E0E2))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(colors),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(OlivarDimens.SpaceLg),
    ) {
        Text(
            text = location,
            style = MaterialTheme.typography.labelLarge,
            color = OlivarColors.Charcoal700,
        )
        Text(
            text = temperature,
            style = MaterialTheme.typography.displayLarge,
            color = OlivarColors.Charcoal900,
        )
        Text(
            text = condition,
            style = MaterialTheme.typography.bodyLarge,
            color = OlivarColors.Charcoal700,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OlivarDimens.SpaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = rainText,
                style = MaterialTheme.typography.bodyMedium,
                color = OlivarColors.Charcoal700,
            )
            Text(
                text = windText,
                style = MaterialTheme.typography.bodyMedium,
                color = OlivarColors.Charcoal700,
            )
        }
    }
}

private fun ColorHex(value: Long) = androidx.compose.ui.graphics.Color(value)
