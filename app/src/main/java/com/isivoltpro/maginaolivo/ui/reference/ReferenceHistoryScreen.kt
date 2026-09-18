package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoActivityRow
import com.isivoltpro.maginaolivo.ui.components.MoDeliveryRow
import com.isivoltpro.maginaolivo.ui.components.MoExpenseRow
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceHistoryScreen(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = PaddingValues(OlivarDimens.ScreenPadding),
    ) {
        item {
            Column {
                Text(
                    text = "Histórico",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Campaña 2026/27 · datos de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoSectionHeader(title = "Comparación con 2025/26")
        }

        item {
            MoMetricCard(
                label = "Producción",
                value = "+8",
                unit = "%",
                supportText = "5.300 kg vs 4.907 kg",
            )
        }

        item {
            MoMetricCard(
                label = "Coste por kg",
                value = "−4",
                unit = "%",
                supportText = "0,21 €/kg vs 0,22 €/kg",
            )
        }

        item { MoSectionHeader(title = "Cronología") }

        item {
            MoActivityRow(
                title = "Riego",
                dateText = "18 sep",
                contextText = "Los Llanos · Sector 4 · 2 h",
                statusText = "Completado",
            )
        }

        item {
            MoExpenseRow(
                title = "Agroservicios Sierra",
                dateText = "18 sep",
                amountText = "148,20 €",
                detailText = "Fitosanitario",
                ocrPending = true,
            )
        }

        item {
            MoDeliveryRow(
                dateText = "27 dic",
                kgText = "3.800 kg",
                destination = "Cooperativa de referencia",
                yieldText = "23,0 %",
                ocrText = "Confirmado",
            )
        }
    }
}
