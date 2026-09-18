package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceProfileScreen(
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
                    text = "Perfil",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Preferencias de la aplicación",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Cuenta",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Usuario de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoStatusChip(
                    text = "Datos locales disponibles",
                    tone = MoStatusTone.Confirmed,
                )
            }
        }

        item {
            MoSelectField(
                value = "España",
                label = "País",
                onClick = {},
            )
        }

        item {
            MoSelectField(
                value = "Bedmar · Jaén",
                label = "Localidad de referencia",
                onClick = {},
            )
        }

        item {
            MoSelectField(
                value = "EUR (€)",
                label = "Moneda",
                onClick = {},
            )
        }

        item {
            MoSelectField(
                value = "ha · kg · L",
                label = "Unidades",
                onClick = {},
            )
        }

        item {
            MoSelectField(
                value = "Cooperativa de referencia",
                label = "Cooperativa preferida",
                onClick = {},
            )
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Notificaciones",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Recordatorios agrícolas · avisos el día anterior y el mismo día",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "Datos y ayuda",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Exportar datos · Ayuda · Privacidad · Información de la aplicación",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
