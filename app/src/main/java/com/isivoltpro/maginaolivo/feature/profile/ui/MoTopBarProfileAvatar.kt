package com.isivoltpro.maginaolivo.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoIconButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * Botón de Avatar para la barra superior (CR-UX-PERFIL-01).
 * NOTA: Componente de prototipo visual.
 * Se sitúa en la parte superior derecha, junto a la campana de Avisos.
 * Da acceso directo al panel de Perfil, Cuenta y Explotaciones.
 */
@Composable
fun ProfileAvatarIconButton(
    initials: String?,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    syncState: AccountSyncState = if (isConnected) AccountSyncState.SIGNED_IN_SYNCED else AccountSyncState.LOCAL_ONLY,
    hasUnreadAlerts: Boolean = false,
) {
    val palette = MoColors.current
    val dotColor = when (syncState) {
        AccountSyncState.SIGNED_IN_OFFLINE,
        AccountSyncState.LOCAL_ONLY -> palette.warningText
        AccountSyncState.SIGNED_IN_SYNCED -> Color(0xFF4E7A45)
        AccountSyncState.SIGNED_IN_SYNCING -> Color(0xFF4C6876)
    }

    // Zona táctil mínima accesible de 48dp x 48dp conforme a WCAG y directrices Android
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Mi perfil, cuenta y explotaciones" }
            .testTag("topbar-profile-avatar"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = if (isConnected) MoOlivePrimary else MoSurfaceTokens.cardElevated,
            border = BorderStroke(1.5.dp, MoSurfaceTokens.cardStroke),
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isConnected && !initials.isNullOrBlank()) {
                    Text(
                        text = initials.take(2).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.testTag("topbar-avatar-initials"),
                    )
                } else {
                    OliveMark(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("topbar-avatar-local"),
                    )
                }
            }
        }

        // Indicador visual de estado de sincronización / conexión fuera del recorte circular
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 2.dp)
                .size(11.dp)
                .background(dotColor, CircleShape)
                .border(1.5.dp, MoSurfaceTokens.cardSurface, CircleShape),
        )
    }
}

/**
 * Barra superior unificada de Mágina Olivo 1.0 con acceso a Avisos y Avatar de Perfil.
 * Prototipo visual para revisión; no altera AppNavigation ni MoBottomBar reales.
 */
@Composable
fun MoTopAppBarWithProfile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationSymbol: String? = null,
    navigationDescription: String = "Volver",
    onNavigationClick: (() -> Unit)? = null,
    onAlertsClick: (() -> Unit)? = null,
    unreadAlertsCount: Int = 0,
    userProfile: MockUserProfile? = null,
    onProfileClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MoSurfaceTokens.cardSurface,
        contentColor = MoColors.current.primaryText,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = MoSpacing.md, vertical = MoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            // Acción de navegación izquierda (ej: Volver)
            if (navigationSymbol != null && onNavigationClick != null) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = navigationDescription }
                        .clickable(role = Role.Button, onClick = onNavigationClick)
                        .testTag("topbar-back-button"),
                    shape = CircleShape,
                    color = MoSurfaceTokens.cardElevated,
                    contentColor = MoColors.current.bodyText,
                    border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MoIcons.ChevronLeft,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MoColors.current.bodyText,
                        )
                    }
                }
            }

            // Título y subtítulo central
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MoColors.current.primaryText,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }

            // Grupo de acciones derechas: Campana de avisos + Avatar de perfil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                // Campana de avisos y calendario
                if (onAlertsClick != null) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = "Avisos y calendario" }
                                .clickable(role = Role.Button, onClick = onAlertsClick)
                                .testTag("topbar-alerts-button"),
                            shape = CircleShape,
                            color = MoSurfaceTokens.cardElevated,
                            contentColor = MoColors.current.bodyText,
                            border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MoIcons.Bell,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MoColors.current.bodyText,
                                )
                            }
                        }

                        // Badge de avisos pendientes si existen
                        if (unreadAlertsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MoColors.current.warningText, CircleShape)
                                    .border(1.5.dp, MoSurfaceTokens.cardSurface, CircleShape),
                            )
                        }
                    }
                }

                // Avatar de Perfil y Cuenta
                ProfileAvatarIconButton(
                    initials = userProfile?.initials ?: "PL",
                    isConnected = userProfile?.isConnected ?: false,
                    syncState = userProfile?.syncState ?: AccountSyncState.LOCAL_ONLY,
                    onClick = onProfileClick,
                )
            }
        }
    }
}

// ======================================================================================
// PREVIEWS
// ======================================================================================

@Preview(name = "TopBar con Perfil Conectado - Claro", showBackground = true)
@Composable
fun TopAppBarWithProfilePreview_Light() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        MoTopAppBarWithProfile(
            title = "Mi Campo",
            subtitle = "4 parcelas · 14,8 ha",
            onAlertsClick = {},
            unreadAlertsCount = 2,
            userProfile = ProfilePreviewMocks.signedInUser,
            onProfileClick = {},
        )
    }
}

@Preview(name = "TopBar con Perfil Local - Claro", showBackground = true)
@Composable
fun TopAppBarWithProfile_GuestPreview() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        MoTopAppBarWithProfile(
            title = "Inicio",
            subtitle = "Martes, 3 de noviembre",
            onAlertsClick = {},
            unreadAlertsCount = 0,
            userProfile = ProfilePreviewMocks.localGuestUser,
            onProfileClick = {},
        )
    }
}

@Preview(name = "TopBar con Navegación Atrás - Claro", showBackground = true)
@Composable
fun TopAppBarWithProfile_NestedPreview() {
    MaginaOlivoTheme(mode = AppearanceMode.LIGHT) {
        MoTopAppBarWithProfile(
            title = "Finca La Esperanza",
            subtitle = "Campaña 2026/2027",
            navigationSymbol = "←",
            onNavigationClick = {},
            onAlertsClick = {},
            unreadAlertsCount = 0,
            userProfile = ProfilePreviewMocks.signedInUser,
            onProfileClick = {},
        )
    }
}

@Preview(name = "TopBar con Perfil Conectado - Oscuro", showBackground = true)
@Composable
fun TopAppBarWithProfilePreview_Dark() {
    MaginaOlivoTheme(mode = AppearanceMode.DARK) {
        MoTopAppBarWithProfile(
            title = "Cuaderno",
            subtitle = "Labores y tratamientos",
            onAlertsClick = {},
            unreadAlertsCount = 1,
            userProfile = ProfilePreviewMocks.signedInUser,
            onProfileClick = {},
        )
    }
}
