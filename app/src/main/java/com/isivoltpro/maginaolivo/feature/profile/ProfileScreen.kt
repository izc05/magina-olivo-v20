package com.isivoltpro.maginaolivo.feature.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

/** Perfil (UI polish v2): real settings only; what belongs to a later phase says so. */
@Composable
fun ProfileRoute(
    appVersion: String,
    onMachinery: () -> Unit,
    developerGalleryEnabled: Boolean,
    onDeveloperGallery: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = notificationsAllowed(context) }
    ProfileScreen(
        appVersion = appVersion,
        notificationsOn = notificationsOn,
        onNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onMachinery = onMachinery,
        developerGalleryEnabled = developerGalleryEnabled,
        onDeveloperGallery = onDeveloperGallery,
    )
}

private fun notificationsAllowed(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@Composable
fun ProfileScreen(
    appVersion: String,
    notificationsOn: Boolean,
    onNotifications: () -> Unit,
    onMachinery: () -> Unit,
    developerGalleryEnabled: Boolean = false,
    onDeveloperGallery: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag("profile-root")
            .background(MoCream)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Spacer(Modifier.height(MoSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
            OliveMark(Modifier.size(48.dp))
            Column {
                Text("Perfil", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
                Text("Tus datos se guardan primero en este teléfono.", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
            }
        }
        Spacer(Modifier.height(MoSpacing.xs))
        MoSectionHeader("Tu olivar")
        MoCompactListItem(
            title = "Maquinaria",
            subtitle = "Tus máquinas, para anotarlas en las actuaciones",
            icon = MoIcons.Tractor,
            onClick = onMachinery,
            modifier = Modifier.testTag("profile-machinery"),
            trailing = { Chevron() },
        )
        MoSectionHeader("Ajustes")
        MoCompactListItem(
            title = "Notificaciones",
            subtitle = "Avisos de los trabajos planificados",
            icon = MoIcons.Bell,
            onClick = onNotifications,
            modifier = Modifier.testTag("profile-notifications"),
            trailing = {
                MoStatusChip(
                    if (notificationsOn) "Activadas" else "Desactivadas",
                    tone = if (notificationsOn) MoStatusTone.Success else MoStatusTone.Warning,
                )
            },
        )
        MoCompactListItem(
            title = "Modo sin conexión",
            subtitle = "Registra en el campo sin cobertura; nada depende de internet",
            icon = MoIcons.Checklist,
            modifier = Modifier.testTag("profile-offline"),
            trailing = { MoStatusChip("Siempre", tone = MoStatusTone.Success) },
        )
        MoCompactListItem(
            title = "Cuenta y sincronización",
            subtitle = "Llegará en su fase; por ahora todo vive en este teléfono",
            icon = MoIcons.Person,
            iconTint = MoTextSecondary,
            iconContainer = MoSurfaceSoft,
            modifier = Modifier.testTag("profile-account-later"),
            trailing = { MoStatusChip("Pronto", tone = MoStatusTone.Neutral) },
        )
        MoSectionHeader("Acerca de")
        MoCompactListItem(
            title = "Mágina Olivo",
            subtitle = "Versión $appVersion",
            icon = MoIcons.Leaf,
            modifier = Modifier.testTag("profile-about"),
        )
        MoCompactListItem(
            title = "Datos de parcelas",
            subtitle = "Consulta al servicio INSPIRE de la Dirección General del Catastro. La copia guardada no sustituye una certificación oficial.",
            icon = MoIcons.Map,
            modifier = Modifier.testTag("profile-catastro-source"),
        )
        if (developerGalleryEnabled) {
            MoCompactListItem(
                title = "Catálogo de diseño (DEV)",
                subtitle = "Solo en la versión de desarrollo",
                icon = MoIcons.Parcels,
                onClick = onDeveloperGallery,
                trailing = { Chevron() },
            )
        }
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun Chevron() {
    Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoTextSecondary, modifier = Modifier.size(18.dp))
}
