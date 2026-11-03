package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
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
 * Tarjeta de Invitación Recibida (CR-COLLAB-02).
 * NOTA: Prototipo visual para revisión UX; no conectado a backend ni RLS real.
 */
@Composable
fun ReceivedInvitationCard(
    invitation: MockInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("received-invitation-card-${invitation.id}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.5.dp, MoOlivePrimary.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            // Cabecera de la tarjeta: Título de aviso y fecha
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MoColors.current.warningText, CircleShape),
                    )
                    Text(
                        text = "Invitación de colaboración (Diseño)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoColors.current.primaryText,
                    )
                }
                Text(
                    text = invitation.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MoSurfaceTokens.secondaryText,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }

            // Información de quién invita y finca
            Text(
                text = "${invitation.inviterName} te ha invitado a colaborar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MoColors.current.primaryText,
            )
            Text(
                text = "${invitation.farmName} (${invitation.municipality})",
                style = MaterialTheme.typography.bodyMedium,
                color = MoColors.current.actionText,
                fontWeight = FontWeight.SemiBold,
            )

            // Rol propuesto
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Rol propuesto:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoSurfaceTokens.secondaryText,
                )
                RoleBadge(role = invitation.role)
            }

            // Alcance y transparencia de permisos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MoSurfaceTokens.cardElevated,
                border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Alcance: ${invitation.scope}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MoColors.current.primaryText,
                    )
                    Text(
                        text = invitation.role.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.bodyText,
                    )
                    Text(
                        text = "Nota: No tendrás acceso a los datos bancarios ni a otras explotaciones del titular.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Botones de acción: Aceptar o Rechazar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoPrimaryButton(
                    text = "Aceptar invitación",
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("invitation-accept-btn"),
                )
                MoSecondaryButton(
                    text = "Rechazar",
                    onClick = onDecline,
                    modifier = Modifier.testTag("invitation-decline-btn"),
                )
            }
        }
    }
}

/**
 * Formulario visual de Invitación de Colaborador (CR-COLLAB-02).
 * NOTA: Prototipo visual; no realiza envíos reales por red.
 */
@Composable
fun InviteCollaboratorSheetContent(
    farmName: String,
    onSendInvitation: (CollaboratorRole, InvitationChannel, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRole by remember { mutableStateOf(CollaboratorRole.GESTOR) }
    var selectedChannel by remember { mutableStateOf(InvitationChannel.WHATSAPP) }
    var scopeDetail by remember { mutableStateOf("Toda la finca · Campaña vigente 2026/2027 (Prototipo)") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoSurfaceTokens.cardSurface)
            .padding(MoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(
            text = "Invitar colaborador a $farmName",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MoColors.current.primaryText,
        )
        Text(
            text = "El colaborador accederá solo a los datos de esta finca con los permisos del rol elegido (Diseño).",
            style = MaterialTheme.typography.bodySmall,
            color = MoSurfaceTokens.secondaryText,
        )

        MoSectionHeader("1. Elige el rol del colaborador")

        CollaboratorRole.entries.forEach { role ->
            val isSelected = selectedRole == role
            val palette = MoColors.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { selectedRole = role }
                    .testTag("invite-role-${role.name.lowercase()}"),
                shape = MoShape.card,
                color = if (isSelected) palette.actionTint else MoSurfaceTokens.cardElevated,
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) palette.actionText else MoSurfaceTokens.cardStroke,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedRole = role },
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RoleBadge(role = role)
                        Text(
                            text = role.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText,
                        )
                        Text(
                            text = role.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MoSurfaceTokens.secondaryText,
                        )
                    }
                }
            }
        }

        MoSectionHeader("2. Vía de envío de la invitación")

        InvitationChannel.entries.forEach { channel ->
            val isSelected = selectedChannel == channel
            val palette = MoColors.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { selectedChannel = channel }
                    .testTag("invite-channel-${channel.name.lowercase()}"),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) palette.actionTint else MoSurfaceTokens.cardElevated,
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) palette.actionText else MoSurfaceTokens.cardStroke,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedChannel = channel },
                    )
                    Column {
                        Text(
                            text = channel.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText,
                        )
                        Text(
                            text = channel.iconDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MoSurfaceTokens.secondaryText,
                        )
                    }
                }
            }
        }

        // Aviso de seguridad y privacidad
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MoColors.current.actionTint,
            border = BorderStroke(1.dp, MoColors.current.actionText.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = MoIcons.Checklist,
                    contentDescription = null,
                    tint = MoColors.current.actionText,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Garantía de privacidad: Mágina Olivo nunca comparte tus datos financieros, contraseñas ni otras fincas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MoColors.current.bodyText,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoPrimaryButton(
                text = "Generar invitación",
                onClick = { onSendInvitation(selectedRole, selectedChannel, scopeDetail) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("invite-submit-btn"),
            )
            MoTertiaryButton(
                text = "Cancelar",
                onClick = onCancel,
                modifier = Modifier.testTag("invite-cancel-btn"),
            )
        }
    }
}

/**
 * Ficha de Invitación Enviada (Prototipo UX).
 */
@Composable
fun SentInvitationCard(
    invitation: MockInvitation,
    onResend: () -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sent-invitation-card-${invitation.id}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoleBadge(role = invitation.role)

            Text(
                text = invitation.recipientEmail ?: "Invitación abierta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MoColors.current.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${invitation.scope} · Vía ${invitation.channel.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
            )

            if (invitation.oneTimeCode != null) {
                Text(
                    text = "Código de un solo uso (Demo): ${invitation.oneTimeCode}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MoColors.current.actionText,
                )
            }

            Text(
                text = "${invitation.status.label} · ${invitation.dateLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = when (invitation.status) {
                    InvitationStatus.ACEPTADA -> MoColors.current.successText
                    InvitationStatus.PENDIENTE -> MoColors.current.warningText
                    else -> MoSurfaceTokens.secondaryText
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                if (invitation.status == InvitationStatus.PENDIENTE) {
                    MoSecondaryButton(
                        text = "Reenviar",
                        onClick = onResend,
                        modifier = Modifier.testTag("sent-invitation-resend"),
                    )
                }
                MoTertiaryButton(
                    text = if (invitation.status == InvitationStatus.ACEPTADA) "Revocar acceso" else "Cancelar",
                    onClick = onRevoke,
                    modifier = Modifier.testTag("sent-invitation-revoke"),
                )
            }
        }
    }
}

// ======================================================================================
// PREVIEWS
// ======================================================================================

@Preview(name = "Invitación Recibida - Claro", showBackground = true)
@Composable
fun ReceivedInvitationCardPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
        ) {
            ReceivedInvitationCard(
                invitation = ProfilePreviewMocks.receivedInvitations.first(),
                onAccept = {},
                onDecline = {},
            )
        }
    }
}

@Preview(name = "Formulario Invitar Colaborador - Claro", showBackground = true)
@Composable
fun InviteCollaboratorSheetPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        InviteCollaboratorSheetContent(
            farmName = "Finca La Esperanza (Demo)",
            onSendInvitation = { _, _, _ -> },
            onCancel = {},
        )
    }
}

@Preview(name = "Lista de Invitaciones Enviadas - Claro", showBackground = true)
@Composable
fun SentInvitationListPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoSectionHeader("Colaboradores e invitaciones (Muestra)")
            ProfilePreviewMocks.sentInvitations.forEach { invitation ->
                SentInvitationCard(
                    invitation = invitation,
                    onResend = {},
                    onRevoke = {},
                )
            }
        }
    }
}

@Preview(name = "Invitación Recibida - Oscuro", showBackground = true)
@Composable
fun ReceivedInvitationCardPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoSurfaceTokens.appBackground)
                .padding(MoSpacing.screen),
        ) {
            ReceivedInvitationCard(
                invitation = ProfilePreviewMocks.receivedInvitations.first(),
                onAccept = {},
                onDecline = {},
            )
        }
    }
}
