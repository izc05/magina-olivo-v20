package com.isivoltpro.maginaolivo.ui.reference.campaign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoChartContainer
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun CampaignReferenceScreen(
    modifier: Modifier = Modifier,
    onAnalyticsSelected: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("campaign-reference-root"),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Campaña",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "2026/27 · La Solana",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MoTextSecondary,
                    )
                }
                MoStatusChip(
                    text = "Activa",
                    tone = MoStatusTone.Success,
                )
            }

            Spacer(Modifier.height(MoSpacing.md))

            CampaignTabs()

            Spacer(Modifier.height(MoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Cosechado",
                    value = "12.450 kg",
                    supportingText = "Maqueta visual",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Rendimiento",
                    value = "18,3 %",
                    supportingText = "Con análisis",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Entregas",
                    value = "5",
                    supportingText = "Confirmadas",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Gastos",
                    value = "3.120 €",
                    supportingText = "Libro de gastos",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoChartContainer(
                title = "Evolución de producción",
                description = "Comparativa de ejemplo; los datos reales se cargarán desde el histórico.",
                modifier = Modifier.clickable(onClick = onAnalyticsSelected),
            ) {
                Spacer(Modifier.height(MoSpacing.md))
                CampaignBars(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Fechas clave")
            Spacer(Modifier.height(MoSpacing.sm))
            CampaignDateRow("Inicio de campaña", "1 oct 2026")
            Spacer(Modifier.height(MoSpacing.xs))
            CampaignDateRow("Primera entrega", "18 nov 2026")
            Spacer(Modifier.height(MoSpacing.xs))
            CampaignDateRow("Última actualización", "18 sep 2026")

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Actividad reciente")
            Spacer(Modifier.height(MoSpacing.sm))
            ActivitySummary("Tratamiento foliar", "14 sep", "Parcela Norte")
            Spacer(Modifier.height(MoSpacing.xs))
            ActivitySummary("Riego", "9 sep", "Parcela Central")
            Spacer(Modifier.height(MoSpacing.xs))
            ActivitySummary("Poda", "28 ago", "Parcela Sur")

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun CampaignTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoWarmWhite, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MoOlivePrimary,
        ) {
            Text(
                text = "Campaña actual",
                modifier = Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MoWarmWhite,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MoWarmWhite,
        ) {
            Text(
                text = "Histórico",
                modifier = Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MoTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CampaignBars(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val values = listOf(0.55f, 0.72f, 0.61f, 0.88f)
        val labels = values.size
        val gap = size.width * 0.05f
        val barWidth = (size.width - gap * (labels + 1)) / labels

        drawLine(
            color = MoOutline,
            start = Offset(0f, size.height - 2f),
            end = Offset(size.width, size.height - 2f),
            strokeWidth = 2f,
        )

        values.forEachIndexed { index, value ->
            val left = gap + index * (barWidth + gap)
            val top = size.height * (1f - value)
            drawRoundRect(
                color = if (index == values.lastIndex) MoOlivePrimary else MoSoftGold.copy(alpha = 0.72f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height - top - 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            )
        }
    }
}

@Composable
private fun CampaignDateRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, MoOutline),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MoOliveDark, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActivitySummary(title: String, date: String, parcel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(parcel, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            }
            Text(date, style = MaterialTheme.typography.labelLarge, color = MoOlivePrimary)
        }
    }
}
