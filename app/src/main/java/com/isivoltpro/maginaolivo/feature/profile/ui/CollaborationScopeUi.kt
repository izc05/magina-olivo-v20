package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/*
 * Vistas 3 y 4 (CR-UX-PERFIL-01 / CR-COLLAB-02): alcance de roles y ciclo de vida de invitaciones.
 *
 * PROTOTIPO UX EXCLUSIVAMENTE. Nada de este archivo:
 *  - concede, comprueba ni persiste permisos;
 *  - llama a Supabase, Auth o Room;
 *  - está enlazado desde AppNavigation.
 * Los datos son ficticios. Un permiso solo se muestra como «Activo» cuando el estado simulado es
 * ACTIVE_CONFIRMED, que en la integración real exigirá confirmación del servidor.
 */

/** Alcance de un rol: finca, parcelas y campaña (Prototipo UX). */
data class MockRoleScope(
    val farmName: String,
    val parcels: List<String>?, // null = toda la finca
    val campaign: String?, // null = sin límite de campaña
    val expiresLabel: String?, // null = sin caducidad
)

/**
 * Ciclo de vida de una invitación tal como se mostrará al usuario (Prototipo UX).
 * Separa claramente lo local (aún no enviado), lo pendiente de servidor y lo confirmado.
 */
enum class InvitationLifecycle(
    val label: String,
    val explanation: String,
    val tone: MoStatusTone,
    val grantsAccess: Boolean,
) {
    QUEUED_OFFLINE(
        "Sin conexión · En cola",
        "Guardada en este teléfono. Se enviará al recuperar conexión. Aún no da acceso.",
        MoStatusTone.Warning, false,
    ),
    AWAITING_SERVER(
        "Enviando…",
        "Esperando confirmación del servidor. Aún no da acceso.",
        MoStatusTone.Info, false,
    ),
    PENDING_RECIPIENT(
        "Pendiente de respuesta",
        "El destinatario todavía no la ha aceptado. Aún no da acceso.",
        MoStatusTone.Warning, false,
    ),
    ACCEPTED_AWAITING_SERVER(
        "Aceptada · Confirmando",
        "Aceptada en el teléfono; el acceso se activará cuando el servidor lo confirme.",
        MoStatusTone.Info, false,
    ),
    ACTIVE_CONFIRMED(
        "Acceso activo",
        "Confirmado por el servidor.",
        MoStatusTone.Success, true,
    ),
    REJECTED("Rechazada", "El destinatario rechazó la invitación.", MoStatusTone.Neutral, false),
    EXPIRED("Caducada", "Superó su fecha de validez sin aceptarse.", MoStatusTone.Neutral, false),
    REVOKED("Acceso revocado", "El titular retiró el acceso.", MoStatusTone.Neutral, false),
    ERROR(
        "No se pudo enviar",
        "El servidor rechazó la operación. Revisa los datos y reintenta. No se ha concedido ningún acceso.",
        MoStatusTone.Error, false,
    ),
}

data class MockInvitationItem(
    val counterpart: String,
    val role: CollaboratorRole,
    val scope: MockRoleScope,
    val lifecycle: InvitationLifecycle,
    val incoming: Boolean,
)

object CollaborationScopeMocks {
    private val esperanza = "Finca La Esperanza (Demo)"

    val gestorScope = MockRoleScope(esperanza, parcels = null, campaign = "Campaña 2026/27", expiresLabel = "Hasta 30/06/2027")
    val recolectorScope = MockRoleScope(esperanza, parcels = listOf("Parcela 1", "Parcela 2"), campaign = "Campaña 2026/27", expiresLabel = "Hasta fin de campaña")
    val titularScope = MockRoleScope(esperanza, parcels = null, campaign = null, expiresLabel = null)

    val sent = listOf(
        MockInvitationItem("gestor.demo@example.test", CollaboratorRole.GESTOR, gestorScope, InvitationLifecycle.PENDING_RECIPIENT, incoming = false),
        MockInvitationItem("cuadrilla.demo@example.test", CollaboratorRole.RECOLECTOR, recolectorScope, InvitationLifecycle.ACTIVE_CONFIRMED, incoming = false),
        MockInvitationItem("nuevo.demo@example.test", CollaboratorRole.RECOLECTOR, recolectorScope, InvitationLifecycle.QUEUED_OFFLINE, incoming = false),
        MockInvitationItem("error.demo@example.test", CollaboratorRole.GESTOR, gestorScope, InvitationLifecycle.ERROR, incoming = false),
        MockInvitationItem("antiguo.demo@example.test", CollaboratorRole.RECOLECTOR, recolectorScope, InvitationLifecycle.EXPIRED, incoming = false),
    )

    val received = listOf(
        MockInvitationItem("Colaborador Demo", CollaboratorRole.GESTOR, MockRoleScope("Finca El Manantial (Demo)", null, "Campaña 2026/27", "Hasta 30/06/2027"), InvitationLifecycle.PENDING_RECIPIENT, incoming = true),
        MockInvitationItem("Titular Muestra", CollaboratorRole.RECOLECTOR, MockRoleScope("Olivar Los Cerros (Muestra)", listOf("Parcela 3"), "Campaña 2026/27", null), InvitationLifecycle.ACCEPTED_AWAITING_SERVER, incoming = true),
        MockInvitationItem("Titular Ficticio", CollaboratorRole.GESTOR, MockRoleScope("Pago El Romeral (Ficticio)", null, "Campaña 2025/26", null), InvitationLifecycle.REVOKED, incoming = true),
    )
}

private fun MockRoleScope.parcelsLabel() = parcels?.joinToString(", ") ?: "Toda la finca"
private fun MockRoleScope.campaignLabel() = campaign ?: "Todas las campañas"
private fun MockRoleScope.expiryLabel() = expiresLabel ?: "Sin caducidad"

// ======================================================================================
// VISTA 3 · ROLES CON ALCANCE
// ======================================================================================

/** Aviso permanente: lo que se ve es diseño, no permisos efectivos. */
@Composable
fun PrototypeNotice(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("collab-prototype-notice"),
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoStatusChip(text = "Prototipo", tone = MoStatusTone.Info)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
        }
    }
}

@Composable
private fun ScopeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoColors.current.primaryText)
    }
}

/** Tarjeta de rol con su alcance (finca · parcelas · campaña · caducidad) y permisos previstos. */
@Composable
fun RoleScopeCard(role: CollaboratorRole, scope: MockRoleScope, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("role-scope-${role.name.lowercase()}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(role.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
                RoleBadge(role = role)
            }
            Text(role.description, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)

            Text("Alcance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
            ScopeRow("Finca", scope.farmName)
            ScopeRow("Parcelas", scope.parcelsLabel())
            ScopeRow("Campaña", scope.campaignLabel())
            ScopeRow("Validez", scope.expiryLabel())

            Text("Podrá (permiso futuro, no activo)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
            role.allowedActions.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = MoColors.current.bodyText) }
            if (role.restrictedActions.isNotEmpty()) {
                Text("No podrá", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
                role.restrictedActions.forEach { Text("✕ $it", style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText) }
            }
        }
    }
}

@Composable
fun RolesScopeScreenPrototype(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoSurfaceTokens.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Roles y accesos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
        PrototypeNotice("Diseño de roles. Los permisos todavía no se aplican ni se comprueban en el servidor.")
        RoleScopeCard(CollaboratorRole.PROPIETARIO, CollaborationScopeMocks.titularScope)
        RoleScopeCard(CollaboratorRole.GESTOR, CollaborationScopeMocks.gestorScope)
        RoleScopeCard(CollaboratorRole.RECOLECTOR, CollaborationScopeMocks.recolectorScope)
    }
}

// ======================================================================================
// VISTA 4 · INVITACIONES
// ======================================================================================

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MoOliveTint else MoSurfaceTokens.cardSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MoOlivePrimary else MoSurfaceTokens.cardStroke),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MoOlivePrimary else MoColors.current.bodyText,
        )
    }
}

@Composable
private fun ChoiceGroup(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    MoSectionHeader(title)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { i, option ->
                    val index = rowIndex * 2 + i
                    ChoiceChip(option, selected == index) { onSelect(index) }
                }
            }
        }
    }
}

/** Crear invitación: finca → rol → alcance → duración → vía de envío. Solo estado visual local. */
@Composable
fun CreateInvitationPrototype(modifier: Modifier = Modifier) {
    var farm by remember { mutableStateOf(0) }
    var role by remember { mutableStateOf(1) }
    var scope by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var channel by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoSurfaceTokens.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Invitar colaborador", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
        PrototypeNotice("No se envía nada. Al integrarse, el acceso solo existirá tras confirmarlo el servidor y aceptarlo el destinatario.")
        ChoiceGroup("1 · Finca", listOf("La Esperanza (Demo)", "Los Cerros (Muestra)"), farm) { farm = it }
        ChoiceGroup("2 · Rol", listOf("Titular", "Gestor", "Recolectora"), role) { role = it }
        ChoiceGroup("3 · Alcance", listOf("Toda la finca", "Parcelas concretas"), scope) { scope = it }
        ChoiceGroup("4 · Duración", listOf("Campaña actual", "Hasta una fecha", "Sin caducidad"), duration) { duration = it }
        ChoiceGroup("5 · Compartir por", listOf("Enlace", "WhatsApp", "Código QR"), channel) { channel = it }
        MoPrimaryButton(text = "Crear invitación (prototipo)", onClick = {}, modifier = Modifier.fillMaxWidth().testTag("invite-create-btn"))
    }
}

@Composable
fun InvitationLifecycleCard(item: MockInvitationItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("invite-${item.lifecycle.name.lowercase()}"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.counterpart,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MoColors.current.primaryText,
                )
                RoleBadge(role = item.role)
            }
            Text(
                "${item.scope.farmName} · ${item.scope.parcelsLabel()} · ${item.scope.campaignLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
            )
            MoStatusChip(text = item.lifecycle.label, tone = item.lifecycle.tone)
            Text(item.lifecycle.explanation, style = MaterialTheme.typography.bodySmall, color = MoColors.current.bodyText)

            when {
                item.incoming && item.lifecycle == InvitationLifecycle.PENDING_RECIPIENT -> Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                    MoPrimaryButton(text = "Aceptar", onClick = {}, modifier = Modifier.weight(1f))
                    MoSecondaryButton(text = "Rechazar", onClick = {}, modifier = Modifier.weight(1f))
                }
                !item.incoming && item.lifecycle == InvitationLifecycle.PENDING_RECIPIENT ->
                    MoTertiaryButton(text = "Cancelar invitación", onClick = {}, modifier = Modifier.fillMaxWidth())
                !item.incoming && item.lifecycle == InvitationLifecycle.ACTIVE_CONFIRMED ->
                    MoTertiaryButton(text = "Revocar acceso", onClick = {}, modifier = Modifier.fillMaxWidth())
                item.lifecycle == InvitationLifecycle.ERROR ->
                    MoSecondaryButton(text = "Reintentar", onClick = {}, modifier = Modifier.fillMaxWidth())
                item.lifecycle == InvitationLifecycle.EXPIRED && !item.incoming ->
                    MoTertiaryButton(text = "Volver a invitar", onClick = {}, modifier = Modifier.fillMaxWidth())
                else -> Unit
            }
        }
    }
}

/** Bandeja de invitaciones con pestañas Enviadas / Recibidas. */
@Composable
fun InvitationsInboxPrototype(modifier: Modifier = Modifier, startOnReceived: Boolean = false) {
    var showReceived by remember { mutableStateOf(startOnReceived) }
    val items = if (showReceived) CollaborationScopeMocks.received else CollaborationScopeMocks.sent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoSurfaceTokens.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Invitaciones y accesos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MoColors.current.primaryText)
        PrototypeNotice("Estados simulados. «Acceso activo» solo aparecerá tras confirmación real del servidor.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("Enviadas (${CollaborationScopeMocks.sent.size})", !showReceived) { showReceived = false }
            ChoiceChip("Recibidas (${CollaborationScopeMocks.received.size})", showReceived) { showReceived = true }
        }
        if (!showReceived) {
            MoCompactListItem(
                title = "Invitar colaborador",
                subtitle = "Finca, rol, alcance y duración",
                icon = MoIcons.Plus,
                onClick = {},
                modifier = Modifier.testTag("invite-new-item"),
            )
        }
        items.forEach { InvitationLifecycleCard(it) }
    }
}

// ======================================================================================
// PREVIEWS (393 dp, claro y oscuro)
// ======================================================================================

@Preview(name = "V3 Roles y alcance - Claro", widthDp = 393, heightDp = 1600, showBackground = true)
@Composable
fun RolesScopePreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) { RolesScopeScreenPrototype() }
}

@Preview(name = "V3 Roles y alcance - Oscuro", widthDp = 393, heightDp = 1600, showBackground = true)
@Composable
fun RolesScopePreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) { RolesScopeScreenPrototype() }
}

@Preview(name = "V4 Crear invitación - Claro", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
fun CreateInvitationPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) { CreateInvitationPrototype() }
}

@Preview(name = "V4 Crear invitación - Oscuro", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
fun CreateInvitationPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) { CreateInvitationPrototype() }
}

@Preview(name = "V4 Invitaciones enviadas - Claro", widthDp = 393, heightDp = 1500, showBackground = true)
@Composable
fun InvitationsSentPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) { InvitationsInboxPrototype() }
}

@Preview(name = "V4 Invitaciones recibidas - Claro", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
fun InvitationsReceivedPreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) { InvitationsInboxPrototype(startOnReceived = true) }
}

@Preview(name = "V4 Invitaciones enviadas - Oscuro", widthDp = 393, heightDp = 1500, showBackground = true)
@Composable
fun InvitationsSentPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) { InvitationsInboxPrototype() }
}
