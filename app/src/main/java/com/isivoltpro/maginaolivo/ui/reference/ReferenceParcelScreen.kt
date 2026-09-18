package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceParcelScreen(
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
                    text = "Los Llanos",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Finca Foralico · Parcela de demostración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
            ) {
                MoMetricCard("Superficie", "4,82", "ha", modifier = Modifier.weight(1f))
                MoMetricCard("Olivos", "510", modifier = Modifier.weight(1f))
            }
        }

        item {
            SimpleReferenceCard {
                Text("Datos agrícolas", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Variedad: Picual",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Riego: Comunidad de Regantes X · Sector 4",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Marco/otros datos: Sin registrar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MoSectionHeader(title = "Ubicación")
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        color = OlivarColors.Sage200,
                        shape = MaterialTheme.shapes.large,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mapa / geometría de parcela",
                        style = MaterialTheme.typography.titleMedium,
                        color = OlivarColors.Olive900,
                    )
                    Text(
                        text = "Referencia visual · Catastro llegará en su fase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OlivarColors.Charcoal700,
                    )
                }
            }
        }

        item {
            MoSectionHeader(title = "Campaña 2026/27")
        }

        item {
            SimpleReferenceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trabajo y cosecha",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "3 trabajos registrados · 1 entrega vinculada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoStatusChip(
                        text = "Activa",
                        tone = MoStatusTone.Active,
                    )
                }
            }
        }

        item {
            MoSectionHeader(title = "Próximos trabajos")
        }

        item {
            SimpleReferenceCard {
                Text(
                    text = "19 sep · Riego",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "08:00 · 2 h · aviso el día anterior",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
