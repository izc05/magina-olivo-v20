package com.isivoltpro.maginaolivo.ui.reference.expenses

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
import com.isivoltpro.maginaolivo.ui.components.MoSyncState
import com.isivoltpro.maginaolivo.ui.components.MoSyncStatus
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun ExpensesDocumentsReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("expenses-reference-root"),
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
                text = "Gastos y documentos",
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
                    label = "Gastos campaña",
                    value = "3.120 €",
                    supportingText = "Maqueta visual",
                    modifier = Modifier.weight(1f),
                )
                MoMetricCard(
                    label = "Este mes",
                    value = "680 €",
                    supportingText = "Maqueta visual",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoPrimaryButton(
                    text = "Añadir gasto",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                MoPrimaryButton(
                    text = "Subir documento",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Categorías")
            Spacer(Modifier.height(MoSpacing.sm))
            ExpenseCategory("Tratamientos", "1.240 €", "40 %")
            Spacer(Modifier.height(MoSpacing.xs))
            ExpenseCategory("Abonado", "860 €", "28 %")
            Spacer(Modifier.height(MoSpacing.xs))
            ExpenseCategory("Maquinaria", "620 €", "20 %")
            Spacer(Modifier.height(MoSpacing.xs))
            ExpenseCategory("Otros", "400 €", "12 %")

            Spacer(Modifier.height(MoSpacing.lg))

            MoSectionHeader(title = "Documentos recientes")
            Spacer(Modifier.height(MoSpacing.sm))
            DocumentItem(
                name = "Factura tratamiento.pdf",
                meta = "18 sep · 320 €",
                sync = MoSyncState.Synced,
            )
            Spacer(Modifier.height(MoSpacing.xs))
            DocumentItem(
                name = "Ticket gasóleo.jpg",
                meta = "14 sep · 95 €",
                sync = MoSyncState.Pending,
            )
            Spacer(Modifier.height(MoSpacing.xs))
            DocumentItem(
                name = "Albarán fertilizante.pdf",
                meta = "7 sep · 460 €",
                sync = MoSyncState.Synced,
            )

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun ExpenseCategory(
    label: String,
    value: String,
    share: String,
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
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(share, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = MoOlivePrimary)
        }
    }
}

@Composable
private fun DocumentItem(
    name: String,
    meta: String,
    sync: MoSyncState,
) {
    Card(
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
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                }
                Text("⋮", style = MaterialTheme.typography.titleMedium, color = MoTextSecondary)
            }
            MoSyncStatus(state = sync)
        }
    }
}
