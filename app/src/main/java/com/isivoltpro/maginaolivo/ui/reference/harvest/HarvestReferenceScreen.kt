package com.isivoltpro.maginaolivo.ui.reference.harvest

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun HarvestReferenceScreen(
    modifier: Modifier = Modifier,
    onDeliverySelected: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("harvest-reference-root"),
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
                text = "Cosecha",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Campaña 2026/27 · La Solana",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Kilos recogidos",
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

            MoMetricCard(
                label = "Entregas confirmadas",
                value = "5",
                supportingText = "Los kilos sin análisis no inventan rendimiento",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoPrimaryButton(
                text = "Registrar entrega",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))
            MoSectionHeader(title = "Entregas")
            Spacer(Modifier.height(MoSpacing.sm))

            HarvestDelivery(
                date = "18 nov",
                kg = "2.850 kg",
                destination = "Cooperativa de referencia",
                status = "Confirmada",
                tone = MoStatusTone.Success,
                onClick = onDeliverySelected,
            )
            Spacer(Modifier.height(MoSpacing.xs))
            HarvestDelivery(
                date = "24 nov",
                kg = "3.150 kg",
                destination = "Cooperativa de referencia",
                status = "Rendimiento pendiente",
                tone = MoStatusTone.Warning,
                onClick = onDeliverySelected,
            )
            Spacer(Modifier.height(MoSpacing.xs))
            HarvestDelivery(
                date = "2 dic",
                kg = "2.420 kg",
                destination = "Cooperativa de referencia",
                status = "Confirmada",
                tone = MoStatusTone.Success,
                onClick = onDeliverySelected,
            )

            Spacer(Modifier.height(MoSpacing.lg))
            MoSectionHeader(title = "Por parcela")
            Spacer(Modifier.height(MoSpacing.sm))
            HarvestParcel("Parcela Norte", "4.210 kg")
            Spacer(Modifier.height(MoSpacing.xs))
            HarvestParcel("Parcela Central", "5.640 kg")
            Spacer(Modifier.height(MoSpacing.xs))
            HarvestParcel("Parcela Sur", "2.600 kg")

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun HarvestDelivery(
    date: String,
    kg: String,
    destination: String,
    status: String,
    tone: MoStatusTone,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(date, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
                MoStatusChip(text = status, tone = tone)
            }
            Text(kg, style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
            Text(destination, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        }
    }
}

@Composable
private fun HarvestParcel(
    name: String,
    kg: String,
) {
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
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MoOliveDark)
            Text(kg, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
        }
    }
}
