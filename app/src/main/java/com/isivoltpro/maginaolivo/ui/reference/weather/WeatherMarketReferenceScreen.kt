package com.isivoltpro.maginaolivo.ui.reference.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoOfflineBanner
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSourceFreshness
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun WeatherMarketReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("weather-market-reference-root"),
        containerColor = MoCream,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text(
                text = "Tiempo y mercado",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Información de apoyo para tus decisiones en campo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.md))

            WeatherCard()

            Spacer(Modifier.height(MoSpacing.sm))

            MoSourceFreshness(
                source = "Fuente meteorológica",
                freshness = "Actualizado hace 12 min · maqueta visual",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))

            RadarCard()

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Mercado del aceite")
            Spacer(Modifier.height(MoSpacing.sm))

            OilTypeCard("AOVE", "4,32 €/kg", "+2,1 %")
            Spacer(Modifier.height(MoSpacing.xs))
            OilTypeCard("Virgen", "3,89 €/kg", "+1,3 %")
            Spacer(Modifier.height(MoSpacing.xs))
            OilTypeCard("Lampante", "3,12 €/kg", "+0,6 %")

            Spacer(Modifier.height(MoSpacing.sm))

            MoSourceFreshness(
                source = "Fuente de mercado",
                freshness = "Precios de ejemplo · no usar como cotización real",
                modifier = Modifier.fillMaxWidth(),
                stale = true,
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Alertas")
            Spacer(Modifier.height(MoSpacing.sm))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
            ) {
                Column(
                    modifier = Modifier.padding(MoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    Text(
                        text = "Lluvia prevista",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Posible precipitación durante las próximas jornadas. Valora la planificación de trabajos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(MoSpacing.md))

            MoOfflineBanner()

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun WeatherCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Text("Huelma, Jaén", style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("☀ 18°C", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
                    Text("Cielo despejado", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                }
                Column {
                    Text("Máx. 24°", style = MaterialTheme.typography.bodyLarge, color = MoOliveDark)
                    Text("Mín. 12°", style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ForecastDay("Sáb", "20°", "☀")
                ForecastDay("Dom", "19°", "☁")
                ForecastDay("Lun", "16°", "☂")
                ForecastDay("Mar", "17°", "☁")
            }
        }
    }
}

@Composable
private fun ForecastDay(day: String, temp: String, symbol: String) {
    Column {
        Text(day, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = MoSoftGold)
        Text(temp, style = MaterialTheme.typography.bodyMedium, color = MoOliveDark)
    }
}

@Composable
private fun RadarCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
        ) {
            Text("Radar de lluvia", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
            Text("Vista de referencia", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            Spacer(Modifier.height(MoSpacing.sm))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp),
            ) {
                drawCircle(
                    color = MoInfo.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.30f,
                    center = Offset(size.width * 0.52f, size.height * 0.50f),
                )
                drawCircle(
                    color = MoInfo.copy(alpha = 0.32f),
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.48f, size.height * 0.46f),
                )
                drawCircle(
                    color = MoOlivePrimary,
                    radius = 7f,
                    center = Offset(size.width * 0.50f, size.height * 0.52f),
                )
                drawCircle(
                    color = MoTextSecondary.copy(alpha = 0.25f),
                    radius = size.minDimension * 0.38f,
                    center = Offset(size.width * 0.50f, size.height * 0.52f),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

@Composable
private fun OilTypeCard(
    type: String,
    value: String,
    trend: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(type, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text("Precio de referencia", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text("↑ $trend", style = MaterialTheme.typography.labelMedium, color = MoOlivePrimary)
            }
        }
    }
}
