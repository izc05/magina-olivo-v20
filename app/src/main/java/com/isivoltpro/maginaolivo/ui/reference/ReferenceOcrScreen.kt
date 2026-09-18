package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceOcrScreen(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = PaddingValues(OlivarDimens.ScreenPadding),
    ) {
        item {
            Column {
                Text(
                    text = "Entrega · revisar vale",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "OCR de demostración · ningún dato se confirma automáticamente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("vale_271226.jpg", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Imagen original guardada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoStatusChip(
                        text = "Por revisar",
                        tone = MoStatusTone.Pending,
                    )
                }
            }
        }

        item {
            OcrFieldReference("Cooperativa / almazara", "Cooperativa de referencia", "Alta")
        }
        item {
            OcrFieldReference("Fecha", "27/12/2026", "Alta")
        }
        item {
            OcrFieldReference("Número de vale", "45872", "Media")
        }
        item {
            OcrFieldReference("Peso neto", "3.800 kg", "Alta")
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Rendimiento",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Pendiente. Se añadirá más adelante como análisis separado sin modificar la entrega original.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
            }
        }

        item {
            MoPrimaryButton(
                text = "Confirmar entrega",
                onClick = {},
            )
        }
    }
}

@Composable
private fun OcrFieldReference(
    label: String,
    value: String,
    confidence: String,
) {
    SimpleReferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = OlivarDimens.Space2Xs),
                )
            }
            MoStatusChip(
                text = "Conf. $confidence",
                tone = if (confidence == "Alta") MoStatusTone.Confirmed else MoStatusTone.Pending,
            )
        }
    }
}
