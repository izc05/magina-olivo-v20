package com.isivoltpro.maginaolivo.ui.reference.components

import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

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
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoDateField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIconButton
import com.isivoltpro.maginaolivo.ui.components.MoListSkeleton
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoOfflineBanner
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoSourceFreshness
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoSyncState
import com.isivoltpro.maginaolivo.ui.components.MoSyncStatus
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.components.MoTopAppBar
import com.isivoltpro.maginaolivo.domain.analytics.ExpenseSlice
import com.isivoltpro.maginaolivo.ui.charts.MoChartPalette
import com.isivoltpro.maginaolivo.ui.charts.MoDonut
import com.isivoltpro.maginaolivo.ui.charts.MoDonutSlice
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

@Composable
fun ComponentCatalogueReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("component-catalogue-root"),
        containerColor = MoSurfaceTokens.appBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))

            MoTopAppBar(
                title = "Componentes",
                subtitle = "Mágina Olivo · Phase 3",
                navigationSymbol = "‹",
                onNavigationClick = {},
                action = {
                    MoIconButton(
                        symbol = "⋮",
                        contentDescription = "Más opciones",
                        onClick = {},
                    )
                },
            )

            Text(
                text = "Botones",
                style = MaterialTheme.typography.titleLarge,
                color = MoColors.current.primaryText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoPrimaryButton("Primario", onClick = {}, modifier = Modifier.weight(1f))
                MoSecondaryButton("Secundario", onClick = {}, modifier = Modifier.weight(1f))
            }

            Text(
                text = "Campos",
                style = MaterialTheme.typography.titleLarge,
                color = MoColors.current.primaryText,
            )
            MoTextField(
                value = "La Solana",
                onValueChange = {},
                label = "Nombre",
                modifier = Modifier.fillMaxWidth(),
            )
            MoSelectField(
                label = "Parcela",
                value = "Parcela Norte",
                onClick = {},
            )
            MoDateField(
                label = "Fecha",
                value = "18 septiembre 2026",
                onClick = {},
            )

            Text(
                text = "Métricas y estados",
                style = MaterialTheme.typography.titleLarge,
                color = MoColors.current.primaryText,
            )
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
                    label = "Producción",
                    value = "4.210 kg",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoStatusChip("Activa", tone = MoStatusTone.Success)
                MoStatusChip("Por revisar", tone = MoStatusTone.Warning)
                MoSyncStatus(MoSyncState.Pending)
            }

            MoSourceFreshness(
                source = "Fuente externa",
                freshness = "Actualizado hace 12 min",
                modifier = Modifier.fillMaxWidth(),
            )
            MoOfflineBanner()

            Text(
                text = "Estados",
                style = MaterialTheme.typography.titleLarge,
                color = MoColors.current.primaryText,
            )
            MoEmptyState(
                title = "Aún no hay datos",
                body = "Cuando añadas tu primera finca aparecerá aquí.",
                actionText = "Añadir finca",
                onAction = {},
            )
            MoErrorState(
                title = "No hemos podido cargar esta información",
                body = "Tus datos guardados siguen disponibles.",
                onRetry = {},
            )
            MoListSkeleton(rows = 2)

            Text(
                text = "Sheets",
                style = MaterialTheme.typography.titleLarge,
                color = MoColors.current.primaryText,
            )
            MoConfirmationSheet(
                title = "Confirmar entrega",
                body = "Revisa los kilos y el destino antes de guardar.",
                confirmText = "Confirmar",
                onConfirm = {},
            )
            MoBottomActionSheet(
                title = "Añadir documento",
                body = "Elige cómo quieres incorporarlo.",
            ) {
                Text(
                    text = "Cámara · Archivo · Galería",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoColors.current.secondaryText,
                )
            }

            // #355 Fase 2, bloque B: los dos donuts con las cifras reales de la finca DEMO, para
            // revisar color, contraste y leyenda en claro y oscuro antes de llevarlos a Campaña.
            Text(
                text = "Gráficas · reparto (#355)",
                style = MaterialTheme.typography.titleMedium,
                color = MoColors.current.primaryText,
            )
            Text(
                text = "Kilos por parcela",
                style = MaterialTheme.typography.labelLarge,
                color = MoColors.current.secondaryText,
            )
            MoDonut(
                slices = listOf(
                    MoDonutSlice("El Barranco", 2_710_000, "2.710 kg", MoChartPalette.parcel(0)),
                    MoDonutSlice("Los Llanos", 1_850_000, "1.850 kg", MoChartPalette.parcel(1)),
                    MoDonutSlice("Pesadas mezcladas", 2_120_000, "2.120 kg", MoChartPalette.unassigned(), muted = true),
                ),
                centreValue = "6.680 kg",
                centreLabel = "pesados",
                tag = "catalogue-donut-parcels",
            )
            Spacer(Modifier.height(MoSpacing.md))
            Text(
                text = "Gastos por categoría",
                style = MaterialTheme.typography.labelLarge,
                color = MoColors.current.secondaryText,
            )
            MoDonut(
                slices = listOf(
                    MoDonutSlice("Jornales", 73_250, "732,50 €", MoChartPalette.cost(ExpenseSlice.LABOUR)),
                    MoDonutSlice("Maquinaria", 54_000, "540,00 €", MoChartPalette.cost(ExpenseSlice.EQUIPMENT)),
                    MoDonutSlice("Combustible", 12_000, "120,00 €", MoChartPalette.cost(ExpenseSlice.FUEL)),
                    MoDonutSlice("Otros", 13_000, "130,00 €", MoChartPalette.cost(ExpenseSlice.OTHER)),
                ),
                centreValue = "1.522,50 €",
                centreLabel = "recogida",
                tag = "catalogue-donut-costs",
            )

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}
