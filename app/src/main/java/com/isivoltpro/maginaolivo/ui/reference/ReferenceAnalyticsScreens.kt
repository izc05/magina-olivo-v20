package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoBarPoint
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoMetricStatus
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSimpleBarChart
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceCampaignScreen(
    modifier: Modifier = Modifier,
) {
    AnalyticsList(
        modifier = modifier,
        title = "Campaña 2026/27",
        subtitle = "Finca Foralico · datos de demostración",
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Entregado", "5.300", "kg", modifier = Modifier.weight(1f))
                MoMetricCard(
                    "Rendimiento",
                    "22,43",
                    "%",
                    supportText = "78 % de kg analizados",
                    modifier = Modifier.weight(1f),
                    status = MoMetricStatus.Partial,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Costes", "1.102,94", "€", modifier = Modifier.weight(1f))
                MoMetricCard("Resultado", "3.501,15", "€", modifier = Modifier.weight(1f))
            }
        }

        item {
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Datos de campaña", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "12 parcelas · 24,38 ha · 100 olivos registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoStatusChip("Activa", MoStatusTone.Active)
                }
            }
        }

        item {
            MoSectionHeader(title = "Próximos trabajos")
        }

        item {
            SimpleReferenceCard {
                Text("Mañana · Riego", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Los Llanos · Sector 4 · 08:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ReferenceProductionScreen(
    modifier: Modifier = Modifier,
) {
    AnalyticsList(
        modifier = modifier,
        title = "Producción",
        subtitle = "Campaña 2026/27 · datos de demostración",
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Aceituna", "5.300", "kg", modifier = Modifier.weight(1f))
                MoMetricCard(
                    "Rendimiento",
                    "22,43",
                    "%",
                    supportText = "78 % analizado",
                    modifier = Modifier.weight(1f),
                    status = MoMetricStatus.Partial,
                )
            }
        }

        item {
            MoMetricCard(
                label = "Aceite estimado",
                value = "1.189",
                unit = "L",
                supportText = "Estimación basada en entregas con análisis",
                status = MoMetricStatus.Estimate,
            )
        }

        item {
            MoSimpleBarChart(
                title = "Kilos por entrega",
                points = listOf(
                    MoBarPoint("11 dic", 0.40f),
                    MoBarPoint("18 dic", 0.62f),
                    MoBarPoint("27 dic", 1.00f),
                    MoBarPoint("3 ene", 0.48f),
                ),
            )
        }

        item {
            MoSectionHeader(
                title = "Historial de entregas",
                actionText = "Añadir",
                onAction = {},
            )
        }

        item {
            DeliveryReferenceRow(
                date = "27/12/26",
                kg = "3.800 kg",
                destination = "Cooperativa de referencia",
                yield = "23,0 %",
                ocr = "Confirmado",
            )
        }

        item {
            DeliveryReferenceRow(
                date = "11/12/26",
                kg = "1.500 kg",
                destination = "Cooperativa de referencia",
                yield = "Pendiente",
                ocr = "Por revisar",
            )
        }
    }
}

@Composable
fun ReferenceCostsScreen(
    modifier: Modifier = Modifier,
) {
    AnalyticsList(
        modifier = modifier,
        title = "Costes",
        subtitle = "Campaña 2026/27 · datos de demostración",
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Total", "1.102,94", "€", modifier = Modifier.weight(1f))
                MoMetricCard("Coste/kg", "0,21", "€", modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Coste/ha", "45,24", "€", modifier = Modifier.weight(1f))
                MoMetricCard("Coste/olivo", "11,03", "€", modifier = Modifier.weight(1f))
            }
        }

        item {
            MoSectionHeader(title = "Desglose por actividad")
        }

        item {
            SimpleReferenceCard {
                CostBar("Recolección", "515,20 €", 0.47f)
                CostBar("Suelo / desbroce", "400,05 €", 0.36f)
                CostBar("Poda", "92,29 €", 0.08f)
                CostBar("Riego", "54,89 €", 0.05f)
                CostBar("Otros", "40,50 €", 0.04f)
            }
        }

        item {
            MoSectionHeader(
                title = "Últimos gastos",
                actionText = "Escanear factura",
                onAction = {},
            )
        }

        item {
            SimpleReferenceCard {
                Text("Agroservicios Sierra · 148,20 €", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Factura · Fitosanitario · OCR por revisar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ReferenceProfitabilityScreen(
    modifier: Modifier = Modifier,
) {
    AnalyticsList(
        modifier = modifier,
        title = "Rentabilidad",
        subtitle = "Campaña 2026/27 · datos de demostración",
    ) {
        item {
            SimpleReferenceCard {
                Text(
                    text = "Resultado neto",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "3.501,15 €",
                    style = MaterialTheme.typography.displayLarge,
                    color = OlivarColors.Olive900,
                )
                Text(
                    text = "Margen 76,04 %",
                    style = MaterialTheme.typography.titleMedium,
                    color = OlivarColors.Success,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Ingresos", "4.604,08", "€", modifier = Modifier.weight(1f))
                MoMetricCard("Costes", "1.102,94", "€", modifier = Modifier.weight(1f))
            }
        }

        item {
            MoSectionHeader(title = "Ratios")
        }

        item {
            SimpleReferenceCard {
                RatioRow("Ingresos/kg", "0,87 €")
                RatioRow("Costes/kg", "0,21 €")
                RatioRow("Resultado/kg", "0,66 €")
                RatioRow("Ingresos/olivo", "46,04 €")
                RatioRow("Costes/olivo", "11,03 €")
                RatioRow("Resultado/olivo", "35,01 €")
            }
        }

        item {
            MoSectionHeader(title = "Comparación")
        }

        item {
            SimpleReferenceCard {
                RatioRow("Kg vs 2025/26", "+8 %")
                RatioRow("Coste/kg", "−4 %")
                RatioRow("Rendimiento", "+1,2 pp")
                RatioRow("Resultado", "+12 %")
            }
        }
    }
}

@Composable
private fun AnalyticsList(
    modifier: Modifier,
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
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
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun DeliveryReferenceRow(
    date: String,
    kg: String,
    destination: String,
    yield: String,
    ocr: String,
) {
    SimpleReferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$date · $kg", style = MaterialTheme.typography.titleMedium)
                Text(
                    destination,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Rendimiento: $yield",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MoStatusChip(
                text = ocr,
                tone = if (ocr == "Confirmado") MoStatusTone.Confirmed else MoStatusTone.Pending,
            )
        }
    }
}

@Composable
private fun CostBar(
    label: String,
    amount: String,
    fraction: Float,
) {
    Column(
        modifier = Modifier.padding(vertical = OlivarDimens.SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(amount, style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = OlivarColors.Cream200,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(
                        color = OlivarColors.Earth600,
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}

@Composable
private fun RatioRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OlivarDimens.SpaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
