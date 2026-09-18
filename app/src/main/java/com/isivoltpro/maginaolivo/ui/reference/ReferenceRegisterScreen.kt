package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoDateField
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceRegisterScreen(
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
                    text = "Registrar",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Referencia visual · datos de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                text = "¿Qué quieres registrar?",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceXs),
            ) {
                MoStatusChip("Riego", MoStatusTone.Active)
                MoStatusChip("Tratamiento", MoStatusTone.Planned)
                MoStatusChip("Poda", MoStatusTone.Planned)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceXs),
            ) {
                MoStatusChip("Abonado", MoStatusTone.Planned)
                MoStatusChip("Suelo", MoStatusTone.Planned)
                MoStatusChip("Otro", MoStatusTone.Planned)
            }
        }

        item {
            MoSelectField(
                value = "Finca Foralico",
                label = "Finca",
                onClick = {},
            )
        }

        item {
            MoSelectField(
                value = "Los Llanos · Sector 4",
                label = "Parcela / sector",
                onClick = {},
            )
        }

        item {
            MoDateField(
                value = "19/09/2026 · 08:00",
                label = "Fecha y hora",
                onClick = {},
            )
        }

        item {
            MoTextField(
                value = "2 h",
                onValueChange = {},
                label = "Duración",
            )
        }

        item {
            MoTextField(
                value = "Comunidad de Regantes X",
                onValueChange = {},
                label = "Comunidad / empresa",
            )
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Recordatorio",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Avisar el día anterior a las 19:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoPrimaryButton(
                text = "Guardar como planificado",
                onClick = {},
            )
        }
    }
}
