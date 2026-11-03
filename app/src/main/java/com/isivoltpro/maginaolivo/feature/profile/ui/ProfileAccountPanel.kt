package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSuccess
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText

/**
 * Panel completo de Perfil, Cuenta y Explotaciones (CR-UX-PERFIL-01 §2).
 * NOTA: Prototipo visual para revisión UX y pruebas de Compose Preview.
 * No altera la navegación real de AppNavigation ni la base de datos Room.
 *
 * Reúne de forma organizada y sin duplicar:
 * 1. Cabecera personal (iniciales/foto, nombre, correo, estado de conexión).
 * 2. Mis explotaciones y roles (fincas asignadas, invitaciones recibidas).
 * 3. Datos del agricultor y campo (municipio, provincia, cooperativa).
 * 4. Herramientas del olivar (maquinaria, aplicadores y asesores CUE).
 * 5. Cuenta y seguridad (gestión de acceso, sincronización).
 * 6. Ajustes, ayuda y soporte (apariencia, notificaciones, ayuda, versión).
 * 7. Pie con cierre de sesión o registro.
 */
@Composable
fun ProfileAccountPanel(
    userProfile: MockUserProfile,
    farmMemberships: List<MockFarmMembership>,
    receivedInvitationsCount: Int = 1,
    notificationsEnabled: Boolean = true,
    appVersion: String = "1.0.0 (Prototipo)",
    onDismiss: () -> Unit = {},
    onEditLocation: () -> Unit = {},
    onChooseCooperative: () -> Unit = {},
    onMachinery: () -> Unit = {},
    onAgronomicPeople: () -> Unit = {},
    onInvitations: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onToggleNotifications: () -> Unit = {},
    onToggleAppearance: () -> Unit = {},
    onHelp: (String) -> Unit = {},
    onLogoutOrLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile-panel-root")
            .background(MoSurfaceTokens.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen, vertical = MoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        // Cabecera de navegación del panel (Cerrar / Volver)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Mi Perfil y Cuenta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MoColors.current.primaryText,
            )
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .testTag("profile-panel-close"),
                shape = CircleShape,
                color = MoSurfaceTokens.cardElevated,
                border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoColors.current.bodyText,
                    )
                }
            }
        }

        // ======================================================================================
        // 1. CABECERA PERSONAL Y ESTADO DE SESIÓN
        // ======================================================================================
        Card(
            modifier = Modifier.fillMaxWidth().testTag("profile-header-card"),
            shape = MoShape.card,
            colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
            border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                // Avatar circular grande
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .testTag("profile-panel-avatar"),
                    shape = CircleShape,
                    color = if (userProfile.isConnected) MoOlivePrimary else MoOliveTint,
                    border = BorderStroke(2.dp, MoOliveMid),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (userProfile.isConnected && userProfile.initials.isNotBlank()) {
                            Text(
                                text = userProfile.initials,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        } else {
                            OliveMark(modifier = Modifier.size(32.dp))
                        }
                    }
                }

                // Identidad y correo
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = userProfile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoColors.current.primaryText,
                    )
                    Text(
                        text = userProfile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                    MoStatusChip(
                        text = userProfile.accountStatusLabel,
                        tone = userProfile.syncState.tone,
                    )
                }
            }
        }

        // ======================================================================================
        // 2. MIS EXPLOTACIONES Y COLABORACIÓN
        // ======================================================================================
        MoSectionHeader("Mis explotaciones y roles (Prototipo)")

        farmMemberships.forEach { membership ->
            FarmMembershipCard(membership = membership)
        }

        // Acceso a invitaciones recibidas
        MoCompactListItem(
            title = "Mis invitaciones y accesos",
            subtitle = if (receivedInvitationsCount > 0) {
                "$receivedInvitationsCount invitación pendiente de revisar"
            } else {
                "Gestionar permisos y colaboraciones recibidas"
            },
            icon = MoIcons.People,
            onClick = onInvitations,
            modifier = Modifier.testTag("profile-invitations-item"),
            trailing = {
                if (receivedInvitationsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFDF6E2),
                        border = BorderStroke(1.dp, Color(0xFFE5DFC9)),
                    ) {
                        Text(
                            text = "$receivedInvitationsCount pendiente",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MoColors.current.warningText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
                }
            },
        )

        // ======================================================================================
        // 3. DATOS DEL AGRICULTOR Y CAMPO
        // ======================================================================================
        MoSectionHeader("Datos del agricultor y campo")

        MoCompactListItem(
            title = "Municipio preferido",
            subtitle = "${userProfile.preferredMunicipality} (${userProfile.preferredProvince}) · Previsión y Catastro",
            icon = MoIcons.Map,
            onClick = onEditLocation,
            modifier = Modifier.testTag("profile-municipality-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        MoCompactListItem(
            title = "Cooperativa vinculada",
            subtitle = userProfile.preferredCooperative ?: "Sin cooperativa asignada",
            icon = MoIcons.Tractor,
            onClick = onChooseCooperative,
            modifier = Modifier.testTag("profile-cooperative-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        // ======================================================================================
        // 4. HERRAMIENTAS DEL OLIVAR
        // ======================================================================================
        MoSectionHeader("Herramientas del olivar")

        MoCompactListItem(
            title = "Mis máquinas y aperos",
            subtitle = "Tractores, atomizadores y equipos para anotar en los trabajos",
            icon = MoIcons.Tractor,
            onClick = onMachinery,
            modifier = Modifier.testTag("profile-machinery-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        MoCompactListItem(
            title = "Aplicadores y asesores",
            subtitle = "Carné ROPO y credenciales técnicas para el cuaderno CUE",
            icon = MoIcons.People,
            onClick = onAgronomicPeople,
            modifier = Modifier.testTag("profile-agronomic-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        // ======================================================================================
        // 5. CUENTA Y SEGURIDAD
        // ======================================================================================
        MoSectionHeader("Cuenta y sincronización")

        MoCompactListItem(
            title = "Gestión de cuenta",
            subtitle = if (userProfile.isConnected) {
                "Sesión activa con ${userProfile.email} · Cambio de contraseña"
            } else {
                "Iniciar sesión o crear cuenta para sincronizar entre dispositivos"
            },
            icon = MoIcons.Checklist,
            onClick = onAccountSettings,
            modifier = Modifier.testTag("profile-account-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        // ======================================================================================
        // 6. AJUSTES, AYUDA Y SOPORTE
        // ======================================================================================
        MoSectionHeader("Ajustes y soporte")

        MoCompactListItem(
            title = "Notificaciones y avisos",
            subtitle = if (notificationsEnabled) "Activadas · Avisos de labores planificadas" else "Desactivadas",
            icon = MoIcons.Bell,
            onClick = onToggleNotifications,
            modifier = Modifier.testTag("profile-notifications-item"),
            trailing = {
                MoStatusChip(
                    text = if (notificationsEnabled) "Activas" else "Inactivas",
                    tone = if (notificationsEnabled) MoStatusTone.Success else MoStatusTone.Warning,
                )
            },
        )

        MoCompactListItem(
            title = "Apariencia",
            subtitle = "Tema claro / oscuro adaptado al olivar",
            icon = MoIcons.Weather,
            onClick = onToggleAppearance,
            modifier = Modifier.testTag("profile-appearance-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        MoCompactListItem(
            title = "Modo sin conexión",
            subtitle = "Tus registros se guardan siempre en este teléfono.",
            icon = MoIcons.Checklist,
            trailing = {
                MoStatusChip(text = "Siempre", tone = MoStatusTone.Success)
            },
        )

        MoCompactListItem(
            title = "Ayuda y privacidad",
            subtitle = "Qué se guarda en el teléfono, novedades y política de datos",
            icon = MoIcons.Checklist,
            onClick = { onHelp("privacy") },
            modifier = Modifier.testTag("profile-help-item"),
            trailing = {
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
            },
        )

        MoCompactListItem(
            title = "Mágina Olivo",
            subtitle = "Versión $appVersion · Conforme a INSPIRE Catastro",
            trailing = {
                OliveMark(modifier = Modifier.size(28.dp))
            },
        )

        // ======================================================================================
        // 7. CIERRE DE SESIÓN / INICIO DE SESIÓN
        // ======================================================================================
        Spacer(Modifier.height(MoSpacing.xs))

        if (userProfile.isConnected) {
            MoDestructiveButton(
                text = "Cerrar sesión",
                onClick = onLogoutOrLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-logout-btn"),
            )
        } else {
            MoPrimaryButton(
                text = "Iniciar sesión o Registrarme",
                onClick = onLogoutOrLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-login-btn"),
            )
        }

        Spacer(Modifier.height(MoSpacing.lg))
    }
}

// ======================================================================================
// PREVIEWS
// ======================================================================================

@Preview(name = "Panel Perfil y Cuenta - Conectado Sincronizado - Claro", showBackground = true)
@Composable
fun ProfileAccountPanelPreview_SignedIn() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        ProfileAccountPanel(
            userProfile = ProfilePreviewMocks.signedInUser,
            farmMemberships = ProfilePreviewMocks.farmMemberships,
            receivedInvitationsCount = 1,
            notificationsEnabled = true,
        )
    }
}

@Preview(name = "Panel Perfil y Cuenta - Sesión Offline - Claro", showBackground = true)
@Composable
fun ProfileAccountPanelPreview_Offline() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        ProfileAccountPanel(
            userProfile = ProfilePreviewMocks.signedInOfflineUser,
            farmMemberships = ProfilePreviewMocks.farmMemberships,
            receivedInvitationsCount = 1,
            notificationsEnabled = true,
        )
    }
}

@Preview(name = "Panel Perfil y Cuenta - Local Sin Sesión - Claro", showBackground = true)
@Composable
fun ProfileAccountPanelPreview_Guest() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        ProfileAccountPanel(
            userProfile = ProfilePreviewMocks.localGuestUser,
            farmMemberships = listOf(ProfilePreviewMocks.farmMemberships.first()),
            receivedInvitationsCount = 0,
            notificationsEnabled = false,
        )
    }
}

@Preview(name = "Panel Perfil y Cuenta - Conectado Sincronizado - Oscuro", showBackground = true)
@Composable
fun ProfileAccountPanelPreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        ProfileAccountPanel(
            userProfile = ProfilePreviewMocks.signedInUser,
            farmMemberships = ProfilePreviewMocks.farmMemberships,
            receivedInvitationsCount = 1,
            notificationsEnabled = true,
        )
    }
}

@Preview(name = "Panel Perfil y Cuenta - Local Sin Sesión - Oscuro", showBackground = true)
@Composable
fun ProfileAccountPanelPreview_Guest_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        ProfileAccountPanel(
            userProfile = ProfilePreviewMocks.localGuestUser,
            farmMemberships = listOf(ProfilePreviewMocks.farmMemberships.first()),
            receivedInvitationsCount = 0,
            notificationsEnabled = false,
        )
    }
}
