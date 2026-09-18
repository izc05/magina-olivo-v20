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
import com.isivoltpro.maginaolivo.ui.components.MoActivityRow
import com.isivoltpro.maginaolivo.ui.components.MoReminderRow
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceCalendarScreen(
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
                    text = "Calendario",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Septiembre 2026 · datos de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Semana del 14 al 20 sep",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "3 trabajos planificados · 1 recordatorio hoy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { MoSectionHeader(title = "Próximos") }

        item {
            MoActivityRow(
                title = "Riego",
                dateText = "19 SEP",
                contextText = "Los Llanos · Sector 4 · 08:00 · 2 h",
                statusText = "Planificado",
                statusTone = MoStatusTone.Planned,
            )
        }

        item {
            MoActivityRow(
                title = "Tratamiento",
                dateText = "22 SEP",
                contextText = "La Hoya · Cobre · 07:30 · 2 personas",
                statusText = "Planificado",
                statusTone = MoStatusTone.Planned,
            )
        }

        item {
            MoActivityRow(
                title = "Inicio de recolección",
                dateText = "04 OCT",
                contextText = "Finca Foralico · 08:00 · 6 personas",
                statusText = "Planificado",
                statusTone = MoStatusTone.Active,
            )
        }

        item { MoSectionHeader(title = "Recordatorios") }

        item {
            MoReminderRow(
                title = "Riego mañana",
                dateTimeText = "Hoy · 19:00",
                contextText = "Los Llanos · Sector 4",
                active = true,
            )
        }
    }
}

