package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSuccess
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * Pantalla de reserva para el Asistente IA (CR-UX-PERFIL-01 §1.5).
 * Ocupa la cuarta pestaña inferior liberada por el traslado de Perfil al avatar superior.
 *
 * Reglas estrictas:
 * - Sin chat ficticio ni respuestas conversacionales simuladas.
 * - Sin recomendaciones agrícolas inventadas.
 * - Explicación serena y transparente de las funciones planificadas para la versión 1.1.
 * - Total respeto por la autoridad técnica del agricultor y su asesor CUE.
 */
@Composable
fun AiAssistantPlaceholderScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai-assistant-placeholder-root")
            .background(MoSurfaceTokens.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen, vertical = MoSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        Spacer(Modifier.height(MoSpacing.sm))

        // Emblema visual de Mágina IA
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MoColors.current.actionTint)
                .border(2.dp, MoColors.current.actionText, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OliveMark(
                modifier = Modifier
                    .size(46.dp)
                    .testTag("ai-assistant-emblem"),
            )
        }

        // Títulos de presentación
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Mágina IA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MoColors.current.primaryText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Asistente inteligente del olivar",
                style = MaterialTheme.typography.titleMedium,
                color = MoColors.current.actionText,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MoColors.current.warningTint,
                border = BorderStroke(1.dp, MoColors.current.warningAccent.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "En preparación para la versión 1.1",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MoColors.current.warningText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        // Tarjeta principal de explicación
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.card,
            colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
            border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text(
                    text = "¿Qué podrás hacer con Mágina IA?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MoColors.current.primaryText,
                )

                AiCapabilityItem(
                    icon = MoIcons.Tractor,
                    title = "Consultas en lenguaje natural sobre tu campo",
                    description = "Pregunta cuándo se hizo el último tratamiento de cobre, qué día se regó una parcela o cuántos kilos llevas entregados.",
                )

                AiCapabilityItem(
                    icon = MoIcons.Checklist,
                    title = "Lectura asistida de albaranes y facturas",
                    description = "Fotografía albaranes de cooperativa o facturas de fitosanitarios para extraer automáticamente kilos, grados y gastos sin teclear.",
                )

                AiCapabilityItem(
                    icon = MoIcons.Parcels,
                    title = "Resumen de rendimientos y costes",
                    description = "Análisis comparativo de rendimiento graso y costes por kilo entre parcelas y campañas anteriores.",
                )

                AiCapabilityItem(
                    icon = MoIcons.Weather,
                    title = "Alertas agroclimáticas preventivas",
                    description = "Avisos personalizados según la previsión AEMET y los modelos fenológicos del olivo en Sierra Mágina.",
                )
            }
        }

        // Tarjeta de compromiso ético y agronómico
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.card,
            color = MoColors.current.actionTint,
            border = BorderStroke(1.dp, MoColors.current.actionText.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.padding(MoSpacing.md),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Icon(
                    imageVector = MoIcons.Checklist,
                    contentDescription = null,
                    tint = MoColors.current.actionText,
                    modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Compromiso de rigor agronómico",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MoColors.current.primaryText,
                    )
                    Text(
                        text = "Mágina IA se basará exclusivamente en los datos de tus cuadernos y fuentes oficiales. Nunca inventará datos agronómicos ni sustituirá el criterio de tu asesor técnico ni el de tu cooperativa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.bodyText,
                    )
                }
            }
        }

        Spacer(Modifier.height(MoSpacing.md))
    }
}

@Composable
private fun AiCapabilityItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MoColors.current.actionTint,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MoColors.current.actionText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MoColors.current.primaryText,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
}

// ======================================================================================
// PREVIEWS
// ======================================================================================

@Preview(name = "Asistente IA - Reserva v1.1 - Claro", showBackground = true)
@Composable
fun AiAssistantPlaceholderPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        AiAssistantPlaceholderScreen()
    }
}

@Preview(name = "Asistente IA - Reserva v1.1 - Oscuro", showBackground = true)
@Composable
fun AiAssistantPlaceholderPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        AiAssistantPlaceholderScreen()
    }
}
