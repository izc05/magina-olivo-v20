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
            CalendarItem(
                day = "19 SEP",
                title = "Riego",
                detail = "Los Llanos · Sector 4 · 08:00 · 2 h",
                people = null,
                tone = MoStatusTone.Planned,
            )
        }

        item {
            CalendarItem(
                day = "22 SEP",
                title = "Tratamiento",
                detail = "La Hoya · Cobre · 07:30",
                people = "2 personas",
                tone = MoStatusTone.Planned,
            )
        }

        item {
            CalendarItem(
                day = "04 OCT",
                title = "Inicio de recolección",
                detail = "Finca Foralico · 08:00",
                people = "6 personas",
                tone = MoStatusTone.Active,
            )
        }

        item { MoSectionHeader(title = "Recordatorios") }

        item {
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Riego mañana",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Aviso programado hoy a las 19:00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoStatusChip("Activo", MoStatusTone.Confirmed)
                }
            }
        }
    }
}

@Composable
private fun CalendarItem(
    day: String,
    title: String,
    detail: String,
    people: String?,
    tone: MoStatusTone,
) {
    SimpleReferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!people.isNullOrBlank()) {
                    Text(
                        text = people,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            MoStatusChip("Planificado", tone)
        }
    }
}
