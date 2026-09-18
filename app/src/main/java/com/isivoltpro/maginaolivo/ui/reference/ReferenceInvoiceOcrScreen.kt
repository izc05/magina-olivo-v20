package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
fun ReferenceInvoiceOcrScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceMd),
        contentPadding = PaddingValues(OlivarDimens.ScreenPadding),
    ) {
        item {
            Column {
                Text("Factura · revisar OCR", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Fitosanitarios / abono / riego · demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoOcrReviewPanel(
                title = "Factura detectada",
                sourceName = "factura_agroservicios.pdf",
            ) {
                MoOcrReviewField("Proveedor", "Agroservicios Sierra", MoOcrConfidence.High)
                Spacer(modifier = Modifier.padding(top = OlivarDimens.SpaceXs))
                MoOcrReviewField("Fecha", "18/09/2026", MoOcrConfidence.High)
                Spacer(modifier = Modifier.padding(top = OlivarDimens.SpaceXs))
                MoOcrReviewField("Número de factura", "A-2058", MoOcrConfidence.Medium)
                Spacer(modifier = Modifier.padding(top = OlivarDimens.SpaceXs))
                MoOcrReviewField("Total", "148,20 €", MoOcrConfidence.High)
            }
        }

        item {
            SimpleReferenceCard {
                Text("Líneas detectadas", style = MaterialTheme.typography.titleMedium)
                Text("1 × Producto fitosanitario · 96,00 €", style = MaterialTheme.typography.bodyMedium)
                Text("1 × Corrector / auxiliar · 26,48 €", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Impuestos / total: revisar antes de guardar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SimpleReferenceCard {
                Text("Destino del gasto", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Campaña 2026/27 · Finca Foralico",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "El documento original quedará enlazado al gasto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoPrimaryButton(
                text = "Confirmar borrador de gasto",
                onClick = {},
            )
        }
    }
}
