package com.isivoltpro.maginaolivo.feature.agenda

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.agenda.AgendaBucket
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.feature.activities.planningLine
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoOfflineBanner
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun AgendaRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onActivitySelected: (UUID) -> Unit,
    onPlanWork: () -> Unit,
) {
    val viewModel: AgendaViewModel = viewModel(
        key = "agenda",
        factory = viewModelFactory { initializer { AgendaViewModel(persistence.activityRepository, clock) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = notificationsAllowed(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted && notificationsAllowed(context)
    }
    AgendaScreen(
        state = state,
        notificationsOn = notificationsOn,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted(context)) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onActivitySelected = onActivitySelected,
        onPlanWork = onPlanWork,
        onComplete = viewModel::complete,
        onCancel = viewModel::cancel,
    )
}

private fun permissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    permissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Calendario — the planned work of every Farm, soonest first, grouped by when it falls.
 * It reads only this phone's data, so it works the same in the field with no signal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    state: AgendaUiState,
    notificationsOn: Boolean,
    onEnableNotifications: () -> Unit,
    onActivitySelected: (UUID) -> Unit,
    onPlanWork: () -> Unit,
    onComplete: (UUID) -> Unit,
    onCancel: (UUID) -> Unit,
) {
    var pending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(Modifier.fillMaxSize().testTag("calendar-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Calendario", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Los trabajos que has planificado, con sus avisos en este teléfono.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            MoPrimaryButton("Planificar trabajo", onPlanWork, Modifier.fillMaxWidth().testTag("agenda-plan-work"))
            if (state.hasReminders && !notificationsOn) {
                MoOfflineBanner(
                    modifier = Modifier.testTag("agenda-notifications-off"),
                    message = "Los avisos están desactivados: los trabajos siguen aquí, pero el teléfono no te avisará.",
                )
                TextButton(onClick = onEnableNotifications, modifier = Modifier.testTag("agenda-enable-notifications")) {
                    Text("Activar avisos")
                }
            }
            state.message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("agenda-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("agenda-error")) }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.sections.isEmpty() -> MoEmptyState(
                    "No hay trabajos planificados",
                    "Planifica una poda, un riego, un tratamiento o la cosecha y aparecerá aquí con su aviso.",
                    modifier = Modifier.testTag("agenda-empty"),
                )
                else -> state.sections.forEach { section ->
                    MoSectionHeader(section.bucket.title(), Modifier.testTag("agenda-section-${section.bucket.name}"))
                    section.items.forEach { entry ->
                        AgendaRow(
                            entry = entry,
                            today = state.today,
                            overdue = section.bucket == AgendaBucket.OVERDUE,
                            enabled = !state.isSaving,
                            onSelected = { onActivitySelected(entry.activityId) },
                            onComplete = { pending = "complete" to entry.activityId.toString() },
                            onCancel = { pending = "cancel" to entry.activityId.toString() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    pending?.let { (action, id) ->
        ModalBottomSheet(onDismissRequest = { pending = null }) {
            MoConfirmationSheet(
                title = if (action == "complete") "¿Trabajo hecho?" else "¿Cancelar este trabajo?",
                body = if (action == "complete") {
                    "Se marcará como completado en la misma actuación y dejará de avisar."
                } else {
                    "Quedará como cancelado en el histórico y dejará de avisar."
                },
                confirmText = if (action == "complete") "Marcar hecho" else "Cancelar trabajo",
                cancelText = "Volver",
                onConfirm = {
                    val activityId = UUID.fromString(id)
                    if (action == "complete") onComplete(activityId) else onCancel(activityId)
                    pending = null
                },
                onCancel = { pending = null },
                modifier = Modifier.testTag("agenda-confirm"),
            )
        }
    }
}

@Composable
private fun AgendaRow(
    entry: AgendaEntry,
    today: LocalDate?,
    overdue: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("agenda-row").clickable(role = Role.Button, onClick = onSelected),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.description, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MoStatusChip(entry.type.label(), tone = if (overdue) MoStatusTone.Warning else MoStatusTone.Info)
            }
            Text(dayLabel(entry.activityDate, today), color = MoTextSecondary)
            listOfNotNull(entry.farmName, entry.parcelNames.takeIf { it.isNotEmpty() }?.joinToString(", "))
                .joinToString(" · ").takeIf { it.isNotEmpty() }
                ?.let { Text(it, color = MoTextSecondary) }
            planningLine(entry.planning)?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("agenda-planning")) }
            if (entry.reminders.isNotEmpty()) {
                val waiting = entry.reminders.count { it.firedAt == null }
                Text(
                    if (waiting == 0) "Avisos ya enviados" else if (waiting == 1) "1 aviso programado" else "$waiting avisos programados",
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("agenda-reminders"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                TextButton(onClick = onComplete, enabled = enabled, modifier = Modifier.testTag("agenda-complete")) { Text("Hecho") }
                TextButton(onClick = onCancel, enabled = enabled, modifier = Modifier.testTag("agenda-cancel")) { Text("Cancelar") }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

private fun dayLabel(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Hoy"
    today?.plusDays(1) -> "Mañana"
    else -> date.format(DAY).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-ES")) }
}

private fun AgendaBucket.title() = when (this) {
    AgendaBucket.OVERDUE -> "Pendientes de días pasados"
    AgendaBucket.TODAY -> "Hoy"
    AgendaBucket.TOMORROW -> "Mañana"
    AgendaBucket.NEXT_7_DAYS -> "Próximos 7 días"
    AgendaBucket.LATER -> "Más adelante"
}
