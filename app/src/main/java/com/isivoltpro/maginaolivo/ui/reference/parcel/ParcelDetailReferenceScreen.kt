package com.isivoltpro.maginaolivo.ui.reference.parcel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPhotoCover
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoMapBase
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun ParcelDetailReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("parcel-detail-reference-root"),
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

            MoPhotoCover(
                title = "Parcela Norte",
                subtitle = "La Solana · Huelma, Jaén",
                modifier = Modifier.aspectRatio(1.65f),
                badge = {
                    MoStatusChip(
                        text = "Campaña activa",
                        tone = MoStatusTone.Success,
                    )
                },
            )

            Spacer(Modifier.height(MoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Superficie",
                    value = "12,4 ha",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Olivos",
                    value = "1.640",
                    supportingText = "Maqueta visual",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Variedad",
                    value = "Picual",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Riego",
                    value = "Secano",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Ubicación")
            Spacer(Modifier.height(MoSpacing.sm))
            ParcelMapPreview()

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Campaña 2026/27")
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
                        text = "Próxima actuación",
                        style = MaterialTheme.typography.labelMedium,
                        color = MoTextSecondary,
                    )
                    Text(
                        text = "Tratamiento · 24 septiembre",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Última actuación: riego · 9 septiembre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Histórico")
            Spacer(Modifier.height(MoSpacing.sm))
            HistoryRow("2025/26", "4.120 kg", "17,9 %")
            Spacer(Modifier.height(MoSpacing.xs))
            HistoryRow("2024/25", "3.760 kg", "18,6 %")

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun ParcelMapPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.05f),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoMapBase),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            repeat(8) { row ->
                repeat(10) { col ->
                    drawCircle(
                        color = MoOlivePrimary.copy(alpha = 0.24f),
                        radius = 4.5f,
                        center = Offset(
                            w * (0.04f + col * 0.10f),
                            h * (0.07f + row * 0.12f),
                        ),
                    )
                }
            }

            val parcel = Path().apply {
                moveTo(w * 0.18f, h * 0.20f)
                lineTo(w * 0.72f, h * 0.13f)
                lineTo(w * 0.82f, h * 0.49f)
                lineTo(w * 0.66f, h * 0.78f)
                lineTo(w * 0.27f, h * 0.70f)
                lineTo(w * 0.13f, h * 0.39f)
                close()
            }
            drawPath(
                path = parcel,
                color = MoOlivePrimary.copy(alpha = 0.28f),
            )
            drawPath(
                path = parcel,
                color = MoOlivePrimary,
                style = Stroke(width = 4f),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    campaign: String,
    kg: String,
    yield: String,
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
                Text(campaign, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(kg, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            }
            Text(yield, style = MaterialTheme.typography.titleMedium, color = MoOlivePrimary)
        }
    }
}
