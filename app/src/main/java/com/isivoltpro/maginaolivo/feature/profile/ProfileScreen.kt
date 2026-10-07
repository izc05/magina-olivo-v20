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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import kotlinx.coroutines.launch
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

/** Perfil (UI polish v2): real settings only; what belongs to a later phase says so. */
@Composable
fun ProfileRoute(
    appVersion: String,
    onMachinery: () -> Unit,
    developerGalleryEnabled: Boolean,
    onDeveloperGallery: () -> Unit,
    persistence: LocalPersistence? = null,
    /** Phase 21C: Perfil → Ayuda y privacidad. */
    onHelp: (HelpTopic) -> Unit = {},
    /** #399: DEV/QA demo farm; null outside the dev flavor, so nothing is shown there. */
    demoFarm: com.isivoltpro.maginaolivo.app.DemoFarmTools? = null,
    /** #671: nested CUE resource management, never a bottom-bar destination. */
    onAgronomicPeople: () -> Unit = {},
) {
    val context = LocalContext.current
    val profileRepository = persistence?.profileRepository
    val profileViewModel: MyProfileViewModel? = if (persistence != null && profileRepository != null) {
        viewModel(
            factory = viewModelFactory {
                initializer { MyProfileViewModel(profileRepository, persistence.organizationRepository, persistence.reminders) }
            },
        )
    } else {
        null
    }
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
        onAgronomicPeople = onAgronomicPeople,
        developerGalleryEnabled = developerGalleryEnabled,
        onDeveloperGallery = onDeveloperGallery,
        onHelp = onHelp,
        myProfile = if (profileViewModel != null) {
            {
                val state by profileViewModel.state.collectAsStateWithLifecycle()
                MyProfileSection(
                    state = state,
                    onSaveLocation = profileViewModel::saveLocation,
                    onChooseCooperative = profileViewModel::chooseCooperative,
                    onCreateCooperative = profileViewModel::createCooperative,
                    onClearError = profileViewModel::clearError,
                )
            }
        } else {
            null
        },
        devTools = demoFarm?.let { tools -> { DemoFarmSection(tools) } },
        reminderSettings = if (profileViewModel != null) {
            {
                val state by profileViewModel.state.collectAsStateWithLifecycle()
                ReminderSettings(state.settings.reminders, state.isSaving, profileViewModel::saveReminders)
            }
        } else {
            null
        },
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
    /** Phase 21C: Perfil → Ayuda y privacidad; rows are hidden where no navigation exists. */
    onHelp: ((HelpTopic) -> Unit)? = null,
    /** Phase 21A: «Mi perfil» (municipality + cooperative); absent where no storage exists. */
    myProfile: (@Composable () -> Unit)? = null,
    /** Phase 21B: Perfil → Avisos (switch + day-before hour); absent where no storage exists. */
    reminderSettings: (@Composable () -> Unit)? = null,
    /** #399: «Herramientas de desarrollo», only in the dev flavor. */
    devTools: (@Composable () -> Unit)? = null,
    /** #671: CUE resources live under Perfil, not in the root navigation. */
    onAgronomicPeople: () -> Unit = {},
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
        myProfile?.invoke()
        MoSectionHeader("Tu olivar")
        MoCompactListItem(
            title = "Mis máquinas",
            subtitle = "Tus máquinas, para anotarlas en los trabajos",
            icon = MoIcons.Tractor,
            onClick = onMachinery,
            modifier = Modifier.testTag("profile-machinery"),
            trailing = { Chevron() },
        )
        MoCompactListItem(
            title = "Aplicadores y asesores",
            subtitle = "Personas y credenciales para el cuaderno",
            icon = MoIcons.People,
            onClick = onAgronomicPeople,
            modifier = Modifier.testTag("profile-agronomic-people"),
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
        reminderSettings?.invoke()
        MoCompactListItem(
            title = "Modo sin conexión",
            subtitle = "Registra en el campo sin cobertura; nada depende de internet",
            icon = MoIcons.Checklist,
            modifier = Modifier.testTag("profile-offline"),
            trailing = { MoStatusChip("Siempre", tone = MoStatusTone.Success) },
        )
        // CR-011 §25: no «Pronto» rows in the public app; account/sync returns when it exists.
        if (onHelp != null) {
            MoSectionHeader("Ayuda y privacidad")
            HelpTopic.entries.forEach { topic ->
                MoCompactListItem(
                    title = topic.title,
                    subtitle = when (topic) {
                        HelpTopic.NEWS -> "Lo que ha cambiado en cada versión"
                        HelpTopic.PRIVACY -> "Qué se guarda en el teléfono y qué servicios se consultan"
                        HelpTopic.OFFLINE -> "Qué funciona en modo avión"
                    },
                    icon = when (topic) {
                        HelpTopic.NEWS -> MoIcons.Bell
                        HelpTopic.PRIVACY -> MoIcons.Checklist
                        HelpTopic.OFFLINE -> MoIcons.Map
                    },
                    onClick = { onHelp(topic) },
                    modifier = Modifier.testTag("profile-help-${topic.route}"),
                    trailing = { Chevron() },
                )
            }
        }
        MoSectionHeader("Acerca de")
        MoCompactListItem(
            title = "Mágina Olivo",
            subtitle = "Versión $appVersion",
            modifier = Modifier.testTag("profile-about"),
            trailing = { OliveMark(Modifier.size(32.dp)) },
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
        devTools?.invoke()
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun Chevron() {
    Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoTextSecondary, modifier = Modifier.size(18.dp))
}

/** "0.2.0-dev · compilación 531": the version plus the CI build, so each APK is identifiable. */
internal fun appVersionLabel(versionName: String, buildNumber: Int): String =
    if (buildNumber > 0) "$versionName · compilación $buildNumber" else "$versionName · compilación local"

/**
 * #399 — load or reset the DEV/QA «Finca Demo». Both actions write through the canonical
 * repositories; nothing runs by itself and the section never exists outside the dev flavor.
 */
@Composable
private fun DemoFarmSection(tools: com.isivoltpro.maginaolivo.app.DemoFarmTools) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val run: (suspend () -> com.isivoltpro.maginaolivo.core.common.AppResult<String>) -> Unit = { action ->
        busy = true
        scope.launch {
            status = action().fold({ it }, { "No se pudo completar: ${it}" })
            busy = false
        }
    }
    MoSectionHeader("Herramientas de desarrollo")
    Text("Finca Demo Mágina: datos ficticios para revisar la app (solo DEV, nunca en producción).", color = MoTextSecondary, style = MaterialTheme.typography.bodySmall)
    MoSecondaryButton("Cargar Finca Demo", { run(tools::load) }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-load"), enabled = !busy)
    if (confirmReset) {
        Text("Se cerrará y archivará la Finca Demo actual y se creará de nuevo. Tus fincas no se tocan.", style = MaterialTheme.typography.bodySmall)
        MoDestructiveButton("Restablecer ahora", { confirmReset = false; run(tools::reset) }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-reset-confirm"), enabled = !busy)
        MoTertiaryButton("Cancelar", { confirmReset = false }, modifier = Modifier.fillMaxWidth())
    } else {
        MoTertiaryButton("Restablecer Finca Demo", { confirmReset = true }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-reset"), enabled = !busy)
    }
    if (busy) Text("Trabajando…", color = MoTextSecondary, modifier = Modifier.testTag("profile-demo-busy"))
    status?.let { Text(it, modifier = Modifier.testTag("profile-demo-status")) }
}
