package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoInfoTint
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSuccess
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoWarning
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText

/**
 * Chip distintivo de Rol de Colaboración (CR-COLLAB-01 / CR-COLLAB-02).
 * NOTA: Componente de prototipo visual.
 * Colores deterministas según la jerarquía de responsabilidades:
 * - Propietario: Verde Olivo Principal (control total de la finca).
 * - Gestor / Arrendatario: Ocre / Ámbar (gestión agronómica y de campaña).
 * - Empresa Recolectora: Azul pizarra suave (encargo técnico acotado).
 */
@Composable
fun RoleBadge(
    role: CollaboratorRole,
    modifier: Modifier = Modifier,
) {
    val palette = MoColors.current
    val (backgroundColor, textColor, borderColor) = when (role) {
        CollaboratorRole.PROPIETARIO -> Triple(palette.actionTint, palette.actionText, palette.actionText)
        CollaboratorRole.GESTOR -> Triple(palette.warningTint, palette.warningText, palette.warningAccent)
        CollaboratorRole.RECOLECTOR -> Triple(palette.infoTint, palette.infoText, palette.infoAccent)
    }

    Surface(
        modifier = modifier.testTag("role-badge-${role.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(textColor, CircleShape)
            )
            Text(
                text = role.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Tarjeta explicativa de un Rol con sus capacidades permitidas y restricciones claras.
 * Diseñado en lenguaje transparente y comprensible para agricultores.
 */
@Composable
fun RoleDetailCard(
    role: CollaboratorRole,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("role-card-${role.name.lowercase()}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            RoleBadge(role = role)
            Text(
                text = role.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MoColors.current.primaryText,
            )
            Text(
                text = role.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = role.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MoColors.current.bodyText,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Capacidades autorizadas (Diseño):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MoColors.current.primaryText,
            )

            role.allowedActions.forEach { action ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = MoIcons.Checklist,
                        contentDescription = null,
                        tint = MoSuccess,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.bodyText,
                    )
                }
            }

            if (role.restrictedActions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Límites y privacidad (Diseño):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MoColors.current.warningText,
                )

                role.restrictedActions.forEach { restriction ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MoColors.current.warningText,
                        )
                        Text(
                            text = restriction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MoSurfaceTokens.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ficha de Explotación vinculada con el Rol que desempeña el agricultor en ella.
 * Muestra que una misma persona puede ser Titular de una finca y Gestor/Recolector de otra.
 */
@Composable
fun FarmMembershipCard(
    membership: MockFarmMembership,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = MoColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("farm-membership-${membership.farmName.lowercase().replace(" ", "-")}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(
            if (membership.isPrimary) 1.5.dp else 1.dp,
            if (membership.isPrimary) palette.actionText.copy(alpha = 0.6f) else MoSurfaceTokens.cardStroke,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoleBadge(role = membership.role)
                if (membership.isPrimary) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.actionTint,
                        border = BorderStroke(1.dp, palette.actionText.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = "Principal",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.actionText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                text = membership.farmName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText,
            )
            Text(
                text = "${membership.municipality} · ${membership.parcelCount}\u00A0parcelas · ${membership.areaHectares}\u00A0ha",
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
}

// ======================================================================================
// PREVIEWS
// ======================================================================================

@Preview(name = "Comparativa de 3 Roles - Claro", showBackground = true)
@Composable
fun CollaborationRolesComparisonPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoSectionHeader("Roles de colaboración en Mágina Olivo (Prototipo)")
            CollaboratorRole.entries.forEach { role ->
                RoleDetailCard(role = role)
            }
        }
    }
}

@Preview(name = "Lista de Fincas y Roles de la Cuenta - Claro", showBackground = true)
@Composable
fun FarmMembershipCardListPreview() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoSectionHeader("Mis explotaciones y encargos (Muestra)")
            ProfilePreviewMocks.farmMemberships.forEach { membership ->
                FarmMembershipCard(membership = membership)
            }
        }
    }
}

@Preview(name = "Comparativa de 3 Roles - Oscuro", showBackground = true)
@Composable
fun CollaborationRolesComparisonPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoSectionHeader("Roles de colaboración en Mágina Olivo (Prototipo)")
            CollaboratorRole.entries.forEach { role ->
                RoleDetailCard(role = role)
            }
        }
    }
}
