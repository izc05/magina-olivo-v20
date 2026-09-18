package com.isivoltpro.maginaolivo.ui.reference.farm

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoParcelRow
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSage
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

@Composable
fun FarmDetailReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("farm-detail-reference-root"),
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.55f),
                shape = MoShape.hero,
                colors = CardDefaults.cardColors(containerColor = MoSage.copy(alpha = 0.36f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MoSpacing.lg),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    OliveMark(modifier = Modifier.height(MoSpacing.xxl))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        MoStatusChip(
                            text = "Campaña activa",
                            tone = MoStatusTone.Success,
                        )
                        Text(
                            text = "La Solana",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MoOliveDark,
                        )
                        Text(
                            text = "Huelma, Jaén",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MoTextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Superficie",
                    value = "42,6 ha",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Parcelas",
                    value = "3",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Parcelas")
            Spacer(Modifier.height(MoSpacing.sm))

            MoParcelRow(
                name = "Parcela Norte",
                area = "12,4 ha",
                variety = "Picual",
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoParcelRow(
                name = "Parcela Central",
                area = "18,6 ha",
                variety = "Picual",
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoParcelRow(
                name = "Parcela Sur",
                area = "11,6 ha",
                variety = "Hojiblanca",
            )

            Spacer(Modifier.height(MoSpacing.md))

            MoPrimaryButton(
                text = "Añadir parcela",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Campaña 2026/27")
            Spacer(Modifier.height(MoSpacing.sm))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MoSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Próxima actuación",
                            style = MaterialTheme.typography.labelMedium,
                            color = MoTextSecondary,
                        )
                        Text(
                            text = "Tratamiento · 24 sep",
                            style = MaterialTheme.typography.titleMedium,
                            color = MoOliveDark,
                        )
                    }
                    Text(
                        text = "Ver campaña",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}
