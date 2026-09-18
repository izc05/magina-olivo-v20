package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoListSkeleton
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoMetricStatus
import com.isivoltpro.maginaolivo.ui.components.MoOfflineBanner
import com.isivoltpro.maginaolivo.ui.components.MoSourceFreshness
import com.isivoltpro.maginaolivo.ui.components.MoFreshnessState
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceStatesScreen(
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
                    text = "Estados de interfaz",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Carga · vacío · parcial · offline · error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { MoOfflineBanner() }

        item {
            MoMetricCard(
                label = "Rendimiento medio",
                value = "22,43",
                unit = "%",
                supportText = "78 % de kg con análisis",
                status = MoMetricStatus.Partial,
            )
        }

        item {
            MoEmptyState(
                title = "Aún no hay entregas",
                body = "Añade la primera cuando lleves aceituna a la cooperativa o almazara.",
                actionText = "Añadir entrega",
                onAction = {},
            )
        }

        item {
            MoErrorState(
                title = "No se pudo actualizar el radar",
                body = "Tus datos del olivar siguen disponibles. Puedes volver a intentarlo cuando tengas conexión.",
                onAction = {},
            )
        }

        item {
            MoSourceFreshness(
                source = "Mercado de referencia",
                updatedText = "actualizado hace 6 h",
                state = MoFreshnessState.Stale,
            )
        }

        item { MoListSkeleton(rows = 2) }
    }
}
