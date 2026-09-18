package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoOcrConfidence
import com.isivoltpro.maginaolivo.ui.components.MoOcrReviewField
import com.isivoltpro.maginaolivo.ui.components.MoOcrReviewPanel
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
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
            MoOcrReviewPanel(
                title = "Vale detectado",
                sourceName = "vale_271226.jpg · imagen original guardada",
            ) {
                MoOcrReviewField(
                    label = "Cooperativa / almazara",
                    extractedValue = "Cooperativa de referencia",
                    confidence = MoOcrConfidence.High,
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
                MoOcrReviewField(
                    label = "Fecha",
                    extractedValue = "27/12/2026",
                    confidence = MoOcrConfidence.High,
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
                MoOcrReviewField(
                    label = "Número de vale",
                    extractedValue = "45872",
                    confidence = MoOcrConfidence.Medium,
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
                )
                MoOcrReviewField(
                    label = "Peso neto",
                    extractedValue = "3.800 kg",
                    confidence = MoOcrConfidence.High,
                )
            }
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
