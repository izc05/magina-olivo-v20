package com.isivoltpro.maginaolivo.ui.reference.olivar

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

@Composable
fun OlivarReferenceScreen(
    modifier: Modifier = Modifier,
    onFarmSelected: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("olivar-reference-root"),
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
            Spacer(Modifier.height(MoSpacing.lg))

            Text(
                text = "Mis fincas",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Todo tu olivar organizado por fincas y parcelas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Fincas",
                    value = "2",
                    supportingText = "Datos de maqueta",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Parcelas",
                    value = "5",
                    supportingText = "Datos de maqueta",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.sm))

            MoMetricCard(
                label = "Superficie total",
                value = "63,8 ha",
                supportingText = "Solo referencia visual",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoPrimaryButton(
                text = "Añadir finca",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoFarmCard(
                name = "La Solana",
                municipality = "Huelma, Jaén",
                area = "42,6 ha",
                parcels = "3",
                campaignStatus = "Campaña activa",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("farm-La Solana"),
                onClick = { onFarmSelected("la-solana") },
            )

            Spacer(Modifier.height(MoSpacing.md))

            MoFarmCard(
                name = "El Portillo",
                municipality = "Cambil, Jaén",
                area = "21,2 ha",
                parcels = "2",
                campaignStatus = "Sin campaña",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("farm-El Portillo"),
                onClick = { onFarmSelected("el-portillo") },
            )

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}
