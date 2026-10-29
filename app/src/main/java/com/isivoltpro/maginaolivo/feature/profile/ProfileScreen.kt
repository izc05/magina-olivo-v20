package com.isivoltpro.maginaolivo.feature.profile

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.app.AppearanceStore
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.data.reminder.notificationSettingsIntent
import com.isivoltpro.maginaolivo.data.reminder.notificationsAllowed
import com.isivoltpro.maginaolivo.feature.notifications.LocalNotificationPermissionAccess
import com.isivoltpro.maginaolivo.feature.notifications.NotificationPermissionTrigger
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import kotlinx.coroutines.launch
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize

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
    appearanceStore: AppearanceStore? = null,
    onAccount: (() -> Unit)? = null,
    accountRepository: com.isivoltpro.maginaolivo.feature.account.AccountRepository? = null,
) {
    val context = LocalContext.current
    val avatarStore = remember(context) { ProfileAvatarStore.fromContext(context) }
    var avatarVersion by remember { mutableStateOf(0) }
    val accountState by (accountRepository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val personalProfile = remember(accountState, avatarVersion) {
        val uid = accountState?.userId
        val avatarFile = uid?.let { avatarStore.getAvatarFile(it) }
        accountState?.let { PersonalProfile.fromAccountState(it, avatarFile) }
    }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val uid = accountState?.userId
        if (uri != null && uid != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val result = avatarStore.saveAvatar(uid, stream)
                if (result is com.isivoltpro.maginaolivo.core.common.AppResult.Success) {
                    avatarVersion++
                }
            }
        }
    }
    val onPickAvatar = if (personalProfile != null) ({
        photoPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }) else null
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
    val notificationAccess = LocalNotificationPermissionAccess.current
    var standaloneNotificationsOn by remember { mutableStateOf(notificationsAllowed(context, ReminderNotifier.CHANNEL_ID)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { standaloneNotificationsOn = notificationsAllowed(context, ReminderNotifier.CHANNEL_ID) }
    val notificationsOn = notificationAccess?.allowed?.value ?: standaloneNotificationsOn
    val openNotifications = {
        if (notificationAccess != null) notificationAccess.request(NotificationPermissionTrigger.SETTINGS)
        else context.startActivity(notificationSettingsIntent(context, ReminderNotifier.CHANNEL_ID))
    }
    ProfileScreen(
        onAccount = onAccount,
        personalProfile = personalProfile,
        onPickAvatar = onPickAvatar,
        appVersion = appVersion,
        notificationsOn = notificationsOn,
        onNotifications = openNotifications,
        onMachinery = onMachinery,
        onAgronomicPeople = onAgronomicPeople,
        developerGalleryEnabled = developerGalleryEnabled,
        onDeveloperGallery = onDeveloperGallery,
        onHelp = onHelp,
        appearanceSettings = appearanceStore?.let { store -> { AppearanceSettings(store) } },
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
                ReminderSettings(
                    preferences = state.settings.reminders,
                    isSaving = state.isSaving,
                    notificationsOn = notificationsOn,
                    onNotifications = openNotifications,
                    onChange = { preferences -> profileViewModel.saveReminders(preferences) {
                        notificationAccess?.request(NotificationPermissionTrigger.ENABLE_REMINDERS)
                    } },
                )
            }
        } else {
            null
        },
    )
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
    appearanceSettings: (@Composable () -> Unit)? = null,
    onAccount: (() -> Unit)? = null,
    personalProfile: PersonalProfile? = null,
    onPickAvatar: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag("profile-root")
            .background(MoSurfaceTokens.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Spacer(Modifier.height(MoSpacing.sm))
        Text("Perfil", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.primaryText)
        Spacer(Modifier.height(MoSpacing.xs))
        PersonalProfileHeader(
            profile = personalProfile,
            onAccount = onAccount,
            onPickAvatar = onPickAvatar,
        )
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
        MoSectionHeader("Datos del cuaderno")
        MoCompactListItem(
            title = "Aplicadores y asesores",
            subtitle = "Personas y credenciales para el cuaderno",
            icon = MoIcons.People,
            onClick = onAgronomicPeople,
            modifier = Modifier.testTag("profile-agronomic-people"),
            trailing = { Chevron() },
        )
        MoSectionHeader("Ajustes")
        appearanceSettings?.invoke()
        ProfileStatusRow(
            title = "Notificaciones",
            subtitle = "Avisos de los trabajos planificados",
            icon = MoIcons.Bell,
            onClick = onNotifications,
            modifier = Modifier.testTag("profile-notifications"),
            status = if (notificationsOn) "Activadas" else "Desactivadas",
            tone = if (notificationsOn) MoStatusTone.Success else MoStatusTone.Warning,
        )
        reminderSettings?.invoke()
        ProfileStatusRow(
            title = "Modo sin conexión",
            subtitle = "Los registros se guardan en este teléfono. Para actualizar mapas, tiempo y otros datos necesitas conexión.",
            icon = MoIcons.Checklist,
            modifier = Modifier.testTag("profile-offline"),
            status = "Siempre",
            tone = MoStatusTone.Success,
        )
        if (onAccount != null) {
            MoSectionHeader("Cuenta")
            MoCompactListItem(title="Cuenta",subtitle="Acceso y recuperación. Tus fincas siguen guardadas en este teléfono.",
                onClick=onAccount,modifier=Modifier.testTag("profile-account"),trailing={Chevron()})
        }
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

/** Profile-only: the status never competes with the title/explanation for horizontal space. */
@Composable
private fun ProfileStatusRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    status: String,
    tone: MoStatusTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = MoSize.minTouchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            Modifier.padding(horizontal = MoSpacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoIconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, color = MoColors.current.bodyText)
                Text(subtitle, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
                MoStatusChip(status, tone = tone)
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp))
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
    var confirmRemove by remember { mutableStateOf(false) }
    val run: (suspend () -> com.isivoltpro.maginaolivo.core.common.AppResult<String>) -> Unit = { action ->
        busy = true
        scope.launch {
            status = action().fold({ it }, { "No se pudo completar: ${it}" })
            busy = false
        }
    }
    MoSectionHeader("Herramientas de desarrollo")
    Text("Finca Demo Mágina: datos ficticios para revisar la app (solo DEV, nunca en producción).", color = MoSurfaceTokens.secondaryText, style = MaterialTheme.typography.bodySmall)
    MoSecondaryButton("Cargar Finca Demo", { run(tools::load) }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-load"), enabled = !busy)
    if (confirmReset) {
        Text("Se cerrará y archivará la Finca Demo actual y se creará de nuevo. Tus fincas no se tocan.", style = MaterialTheme.typography.bodySmall)
        MoDestructiveButton("Restablecer ahora", { confirmReset = false; run(tools::reset) }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-reset-confirm"), enabled = !busy)
        MoTertiaryButton("Cancelar", { confirmReset = false }, modifier = Modifier.fillMaxWidth())
    } else {
        MoTertiaryButton("Restablecer Finca Demo", { confirmReset = true }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-reset"), enabled = !busy)
    }
    if (confirmRemove) {
        Text("Se retirará y archivará la Finca Demo. Tus fincas y datos reales no se tocan.", style = MaterialTheme.typography.bodySmall)
        MoDestructiveButton("Retirar ahora", { confirmRemove = false; run(tools::remove) }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-remove-confirm"), enabled = !busy)
        MoTertiaryButton("Cancelar", { confirmRemove = false }, modifier = Modifier.fillMaxWidth())
    } else {
        MoTertiaryButton("Retirar datos de demostración", { confirmRemove = true }, modifier = Modifier.fillMaxWidth().testTag("profile-demo-remove"), enabled = !busy)
    }
    if (busy) Text("Trabajando…", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("profile-demo-busy"))
    status?.let { Text(it, modifier = Modifier.testTag("profile-demo-status")) }
}
