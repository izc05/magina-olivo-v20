package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoAttachmentState
import com.isivoltpro.maginaolivo.ui.components.MoAttachmentTile
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceDocumentsScreen(
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
                    text = "Documentos",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Campaña 2026/27 · archivos de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { MoSectionHeader(title = "Vales de entrega") }

        item {
            MoAttachmentTile(
                fileName = "vale_271226.jpg",
                typeLabel = "Vale de entrega · 27/12/2026",
                state = MoAttachmentState.Confirmed,
            )
        }

        item {
            MoAttachmentTile(
                fileName = "vale_110126.jpg",
                typeLabel = "Vale de entrega · rendimiento pendiente",
                state = MoAttachmentState.OcrPending,
            )
        }

        item { MoSectionHeader(title = "Facturas y tickets") }

        item {
            MoAttachmentTile(
                fileName = "factura_agroservicios.pdf",
                typeLabel = "Factura · fitosanitario",
                state = MoAttachmentState.OcrPending,
            )
        }

        item {
            MoAttachmentTile(
                fileName = "riego_septiembre.pdf",
                typeLabel = "Factura de riego",
                state = MoAttachmentState.UploadPending,
            )
        }

        item { MoSectionHeader(title = "Otros documentos") }

        item {
            MoAttachmentTile(
                fileName = "analisis_rendimiento.pdf",
                typeLabel = "Análisis de rendimiento",
                state = MoAttachmentState.Local,
            )
        }
    }
}
