package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoFreshnessState
import com.isivoltpro.maginaolivo.ui.components.MoSourceFreshness
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoWeatherHero
import com.isivoltpro.maginaolivo.ui.components.WeatherVisualState
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceHomeScreen(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = OlivarDimens.ScreenPadding,
            end = OlivarDimens.ScreenPadding,
            top = OlivarDimens.SpaceMd,
            bottom = OlivarDimens.SpaceXl,
        ),
    ) {
        item {
            Column {
                Text(
                    text = "Buenos días",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Referencia visual · datos de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column {
                MoWeatherHero(
                    location = "Finca Foralico · Bedmar",
                    temperature = "22 °C",
                    condition = "Nubes y claros",
                    rainText = "Lluvia 15 %",
                    windText = "Viento 11 km/h",
                    state = WeatherVisualState.Cloudy,
                )
                MoSourceFreshness(
                    source = "Tiempo de referencia",
                    updatedText = "actualizado hace 12 min",
                    state = MoFreshnessState.Fresh,
                )
            }
        }

        item {
            MoSectionHeader(
                title = "Mi olivar",
                actionText = "Ver todo",
                onAction = {},
            )
        }

        item {
            MoFarmCard(
                name = "Finca Foralico",
                campaign = "Campaña 2026/27",
                parcelCount = 12,
                areaHa = "24,38",
            )
        }

        item {
            MoSectionHeader(title = "Próximamente")
        }

        items(
            listOf(
                Triple("Mañana · 08:00", "Riego · Los Llanos", "Sector 4"),
                Triple("22 sep · 07:30", "Tratamiento · La Hoya", "Cobre"),
                Triple("4 oct · 08:00", "Recolección", "6 personas"),
            ),
        ) { (date, title, detail) ->
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(date, style = MaterialTheme.typography.labelMedium)
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoStatusChip(
                        text = "Planificado",
                        tone = MoStatusTone.Planned,
                    )
                }
            }
        }

        item {
            MoSectionHeader(
                title = "Mercado del aceite",
                actionText = "Ver evolución",
                onAction = {},
            )
        }

        item {
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MarketValue("AOVE", "3,63", "€/kg")
                    MarketValue("Virgen", "3,30", "€/kg")
                    MarketValue("Lampante", "3,19", "€/kg")
                }
                MoSourceFreshness(
                    source = "Mercado de referencia",
                    updatedText = "actualizado hace 2 h",
                    state = MoFreshnessState.Fresh,
                )
            }
        }

        item {
            MoSectionHeader(title = "Mi cooperativa")
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Cooperativa de referencia",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Aviso: próxima apertura de recepción de aceituna.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
            }
        }
    }
}

@Composable
private fun MarketValue(
    label: String,
    value: String,
    unit: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OlivarColors.Charcoal700,
        )
        Row {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = OlivarColors.Olive900,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = OlivarColors.Charcoal700,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun SimpleReferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(OlivarDimens.SpaceMd),
            content = content,
        )
    }
}
