package com.isivoltpro.maginaolivo.ui.reference.components

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
        containerColor = MoCream,
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
                color = MoOliveDark,
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
                color = MoOliveDark,
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
                color = MoOliveDark,
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
                color = MoOliveDark,
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
                color = MoOliveDark,
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
                    color = MoTextSecondary,
                )
            }

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}
