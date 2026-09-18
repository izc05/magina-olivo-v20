package com.isivoltpro.maginaolivo.ui.reference.ocr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun DeliveryOcrReviewReferenceScreen(
    modifier: Modifier = Modifier,
) {
    var ticket by remember { mutableStateOf("A-18472") }
    var kg by remember { mutableStateOf("2.850") }
    var date by remember { mutableStateOf("18/11/2026") }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ocr-review-reference-root"),
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
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Revisar entrega",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Comprueba los datos extraídos antes de confirmar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MoTextSecondary,
                    )
                }
                MoStatusChip(
                    text = "Por revisar",
                    tone = MoStatusTone.Warning,
                )
            }

            Spacer(Modifier.height(MoSpacing.lg))

            TicketPreview()

            Spacer(Modifier.height(MoSpacing.lg))

            Text(
                text = "Datos extraídos",
                style = MaterialTheme.typography.titleLarge,
                color = MoOliveDark,
            )
            Text(
                text = "El OCR propone estos valores; ninguno se considera confirmado hasta tu revisión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.md))

            MoSelectField(
                label = "Destino",
                value = "Cooperativa de referencia",
                onClick = {},
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoTextField(
                value = ticket,
                onValueChange = { ticket = it },
                label = "Nº de albarán",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoTextField(
                value = date,
                onValueChange = { date = it },
                label = "Fecha",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoTextField(
                value = kg,
                onValueChange = { kg = it },
                label = "Kilos entregados",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.md))

            ConfidenceCard()

            Spacer(Modifier.height(MoSpacing.md))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MoSurfaceSoft),
                border = BorderStroke(1.dp, MoOutline),
            ) {
                Column(
                    modifier = Modifier.padding(MoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    Text(
                        text = "Rendimiento",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Pendiente de análisis",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MoOlivePrimary,
                    )
                    Text(
                        text = "La entrega puede confirmarse sin inventar un rendimiento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoPrimaryButton(
                text = "Confirmar entrega",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoSecondaryButton(
                text = "Volver a revisar imagen",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun TicketPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Text(
                text = "ALBARÁN · IMAGEN DE MAQUETA",
                style = MaterialTheme.typography.labelMedium,
                color = MoTextSecondary,
            )
            Text(
                text = "Cooperativa de referencia",
                style = MaterialTheme.typography.titleLarge,
                color = MoOliveDark,
            )
            Text("Nº A-18472", style = MaterialTheme.typography.bodyLarge)
            Text("18/11/2026", style = MaterialTheme.typography.bodyLarge)
            Text("2.850 kg", style = MaterialTheme.typography.headlineMedium, color = MoOlivePrimary)
        }
    }
}

@Composable
private fun ConfidenceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Text(
                text = "Confianza OCR",
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
            ConfidenceRow("Destino", "Alta")
            ConfidenceRow("Fecha", "Alta")
            ConfidenceRow("Kilos", "Media · revisar")
            ConfidenceRow("Nº albarán", "Alta")
        }
    }
}

@Composable
private fun ConfidenceRow(
    field: String,
    confidence: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(field, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        Text(confidence, style = MaterialTheme.typography.labelLarge, color = MoOliveDark)
    }
}
