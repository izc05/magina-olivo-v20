package com.isivoltpro.maginaolivo.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/** One account entry point on the main screens. The saved photo stays local to this device. */
@Composable
fun ProfileAvatarAction(repository: AccountRepository, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val account by repository.state.collectAsStateWithLifecycle()
    val avatarRevision by ProfileAvatarStore.changes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember(context) { ProfileAvatarStore.fromContext(context) }
    val profile = remember(account, avatarRevision) {
        val state = account
        val file = state.userId?.let(store::getAvatarFile)
        PersonalProfile.fromAccountState(state, file)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp).testTag("top-profile-avatar")
            .semantics { contentDescription = "Abrir Perfil" },
        shape = CircleShape,
        color = MoSurfaceTokens.cardSurface,
        contentColor = MoColors.current.primaryText,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (profile?.avatarFile?.exists() == true) {
                AsyncImage(avatarImageRequest(context, profile.avatarFile, avatarRevision), "Foto de perfil", Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else if (profile != null) {
                Text(profile.initials, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(MoIcons.Person, null, Modifier.size(24.dp))
            }
        }
    }
}
