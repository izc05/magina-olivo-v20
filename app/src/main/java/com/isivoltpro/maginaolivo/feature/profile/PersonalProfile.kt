package com.isivoltpro.maginaolivo.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.feature.account.AccountState
import com.isivoltpro.maginaolivo.feature.account.AccountStatus
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.io.File
import java.util.UUID

/**
 * Representa la identidad personal de la cuenta autenticada para la cabecera de perfil.
 * Desacoplado de la configuracin territorial de workspace.
 */
data class PersonalProfile(
    val userId: UUID,
    val email: String,
    val displayName: String? = null,
    val avatarFile: File? = null,
) {
    val initials: String get() = computeInitials(displayName, email)

    companion object {
        fun computeInitials(displayName: String?, email: String?): String {
            val trimmedName = displayName?.trim().orEmpty()
            if (trimmedName.isNotEmpty()) {
                val parts = trimmedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                return when {
                    parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                    parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
                    parts.size == 1 -> parts[0].take(1).uppercase()
                    else -> ""
                }
            }
            val trimmedEmail = email?.trim().orEmpty()
            val namePart = trimmedEmail.substringBefore('@')
            return if (namePart.isNotEmpty()) {
                namePart.take(1).uppercase()
            } else {
                "?"
            }
        }

        fun fromAccountState(state: AccountState, avatarFile: File? = null): PersonalProfile? {
            val uid = state.userId
            val em = state.email
            return if (state.status == AccountStatus.SIGNED_IN && uid != null && !em.isNullOrBlank()) {
                PersonalProfile(userId = uid, email = em, avatarFile = avatarFile)
            } else {
                null
            }
        }
    }
}

/**
 * Cabecera de Perfil Personal con avatar circular (foto o iniciales), nombre/correo
 * y accesos a Cuenta y cambio de fotografa.
 */
@Composable
fun PersonalProfileHeader(
    profile: PersonalProfile?,
    onAccount: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onPickAvatar: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile-personal-header"),
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            // Avatar
            if (profile != null) {
                val avatarModifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .then(
                        if (onPickAvatar != null) {
                            Modifier.clickable(role = Role.Button, onClick = onPickAvatar)
                        } else Modifier
                    )

                if (profile.avatarFile != null && profile.avatarFile.exists()) {
                    AsyncImage(
                        model = profile.avatarFile,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = avatarModifier
                            .border(1.5.dp, MoSurfaceTokens.cardStroke, CircleShape)
                            .testTag("profile-avatar-image"),
                    )
                } else {
                    Box(
                        modifier = avatarModifier
                            .background(MoColors.current.primaryButton)
                            .border(1.5.dp, MoSurfaceTokens.cardStroke, CircleShape)
                            .testTag("profile-avatar-initials"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = profile.initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MoColors.current.onPrimaryButton,
                            modifier = Modifier.testTag("profile-avatar-text"),
                        )
                    }
                }
            } else {
                OliveMark(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("profile-avatar-guest"),
                )
            }

            // Datos de identidad
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (profile != null) {
                    val title = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email.substringBefore('@')
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MoColors.current.primaryText,
                        modifier = Modifier.testTag("profile-display-name"),
                    )
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("profile-email"),
                    )
                } else {
                    Text(
                        text = "Perfil local",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MoColors.current.primaryText,
                        modifier = Modifier.testTag("profile-display-name"),
                    )
                    Text(
                        text = "Tus fincas se guardan en este dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }

            // Accin a Cuenta
            if (onAccount != null) {
                MoTertiaryButton(
                    text = if (profile != null) "Cuenta" else "Iniciar sesión",
                    onClick = onAccount,
                    modifier = Modifier.testTag("profile-header-account-button"),
                )
            }
        }
    }
}
