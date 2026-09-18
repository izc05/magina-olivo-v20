package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceOliveGroveScreen(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = PaddingValues(
            start = OlivarDimens.ScreenPadding,
            end = OlivarDimens.ScreenPadding,
            top = OlivarDimens.SpaceMd,
            bottom = OlivarDimens.SpaceXl,
        ),
    ) {
        item {
            Column {
                Text(
                    text = "Mi Olivar",
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
            Column(
                verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
                ) {
                    MoMetricCard(
                        label = "Fincas",
                        value = "2",
                        modifier = Modifier.weight(1f),
                    )
                    MoMetricCard(
                        label = "Parcelas",
                        value = "18",
                        modifier = Modifier.weight(1f),
                    )
                }
                MoMetricCard(
                    label = "Superficie total",
                    value = "31,62",
                    unit = "ha",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            MoSectionHeader(
                title = "Tus fincas",
                actionText = "Añadir finca",
                onAction = {},
            )
        }

        item {
            MoFarmCard(
                name = "Finca Foralico",
                campaign = "Campaña 2026/27 · Activa",
                parcelCount = 12,
                areaHa = "24,38",
            )
        }

        item {
            MoFarmCard(
                name = "Finca La Hoya",
                campaign = "Campaña 2026/27 · Activa",
                parcelCount = 6,
                areaHa = "7,24",
            )
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Consejo",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Cada finca puede contener todas las parcelas agrícolas que necesites. El nombre visible de la parcela es independiente de Catastro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
            }
        }
    }
}
