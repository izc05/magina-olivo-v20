package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoActivityRow
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoParcelRow
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceFarmScreen(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = PaddingValues(OlivarDimens.ScreenPadding),
    ) {
        item {
            Text(
                text = "Referencia visual · datos de demostración",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            MoFarmCard(
                name = "Finca Foralico",
                campaign = "Bedmar · Campaña 2026/27",
                parcelCount = 12,
                areaHa = "24,38",
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard(
                    label = "Parcelas",
                    value = "12",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Olivos",
                    value = "2.480",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            MoSectionHeader(
                title = "Parcelas",
                actionText = "Añadir",
                onAction = {},
            )
        }

        item {
            MoParcelRow(
                name = "Los Llanos",
                area = "4,82",
                oliveTrees = 510,
                variety = "Picual",
            )
        }

        item {
            MoParcelRow(
                name = "La Hoya",
                area = "3,15",
                oliveTrees = 326,
                variety = "Picual",
            )
        }

        item {
            MoParcelRow(
                name = "Parcela 3",
                area = null,
                oliveTrees = null,
                variety = null,
            )
        }

        item {
            MoSectionHeader(title = "Campaña actual")
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "2026/27",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "5.300 kg entregados · rendimiento parcial 22,43 %",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoSectionHeader(title = "Actividad reciente")
        }

        item {
            MoActivityRow(
                title = "Riego",
                dateText = "18 sep",
                contextText = "Los Llanos · Sector 4 · 2 h",
                statusText = "Completado",
                statusTone = com.isivoltpro.maginaolivo.ui.components.MoStatusTone.Confirmed,
            )
        }
    }
}
