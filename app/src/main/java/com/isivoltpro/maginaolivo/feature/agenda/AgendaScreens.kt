package com.isivoltpro.maginaolivo.feature.agenda

import androidx.compose.foundation.layout.WindowInsets
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
import com.isivoltpro.maginaolivo.feature.activities.icon
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.feature.activities.planningLine
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.isivoltpro.maginaolivo.domain.agenda.Agenda
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMonthCalendar
import com.isivoltpro.maginaolivo.ui.theme.MoErrorText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import java.time.YearMonth
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
    /** UX-B (Issue #246): the same agenda is the Avisos root. */
    title: String = "Calendario",
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
        title = title,
    )
}

private fun permissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    permissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Calendario — the planned work of every Farm (UI polish v2: two views).
 *
 * - Agenda: pendientes de días pasados, hoy, esta semana, más adelante.
 * - Mes: a real month grid with a dot on each day that has work; tapping a day lists it.
 *
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
    title: String = "Calendario",
) {
    var pending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var view by rememberSaveable { mutableStateOf(AgendaView.AGENDA) }
    val today = state.today ?: LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    val entries = remember(state.sections) { state.sections.flatMap { it.items } }
    val row: @Composable (AgendaEntry, Boolean) -> Unit = { entry, overdue ->
        AgendaRow(
            entry = entry,
            today = state.today,
            overdue = overdue,
            enabled = !state.isSaving,
            onSelected = { onActivitySelected(entry.activityId) },
            onComplete = { pending = "complete" to entry.activityId.toString() },
            onCancel = { pending = "cancel" to entry.activityId.toString() },
        )
    }

    Scaffold(Modifier.fillMaxSize().testTag("calendar-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = MoOliveDark, modifier = Modifier.weight(1f))
                MoPrimaryButton("Planificar trabajo", onPlanWork, Modifier.testTag("agenda-plan-work"))
            }
            AgendaViewSwitch(view) { view = it }
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
                view == AgendaView.MONTH -> {
                    val shown = YearMonth.parse(month)
                    val day = LocalDate.parse(selectedDay)
                    Surface(shape = MoShape.card, color = MoWarmWhite, border = BorderStroke(1.dp, MoOutline)) {
                        MoMonthCalendar(
                            month = shown,
                            selected = day,
                            today = today,
                            onMonthChange = { month = it.toString() },
                            onDaySelected = { selectedDay = it.toString() },
                            markers = entries.groupingBy { it.activityDate }.eachCount(),
                            modifier = Modifier.padding(MoSpacing.xs).testTag("agenda-month"),
                        )
                    }
                    MoSectionHeader(dayLabel(day, state.today), Modifier.testTag("agenda-day-title"))
                    val ofDay = entries.filter { it.activityDate == day }
                        .sortedWith(compareBy(nullsFirst()) { it.planning?.startTime })
                    if (ofDay.isEmpty()) {
                        MoEmptyState(
                            "Nada planificado este día",
                            "Elige otro día o planifica un trabajo para él.",
                            icon = MoIcons.Calendar,
                            modifier = Modifier.testTag("agenda-day-empty"),
                        )
                    } else {
                        ofDay.forEach { row(it, it.activityDate.isBefore(today)) }
                    }
                }
                entries.isEmpty() -> MoEmptyState(
                    "No hay trabajos planificados",
                    "Planifica una poda, un riego, un tratamiento o la cosecha y aparecerá aquí con su aviso.",
                    icon = MoIcons.Calendar,
                    modifier = Modifier.testTag("agenda-empty"),
                )
                else -> AgendaGroup.of(entries, today).forEach { (group, items) ->
                    MoSectionHeader(group.title, Modifier.testTag("agenda-section-${group.name}"))
                    items.forEach { row(it, group == AgendaGroup.OVERDUE) }
                }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }

    pending?.let { (action, id) ->
        ModalBottomSheet(onDismissRequest = { pending = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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

private enum class AgendaView { AGENDA, MONTH }

@Composable
private fun AgendaViewSwitch(view: AgendaView, onChange: (AgendaView) -> Unit) {
    Surface(shape = MoShape.pill, color = MoSurfaceSoft, border = BorderStroke(1.dp, MoOutline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            listOf(AgendaView.AGENDA to "Agenda", AgendaView.MONTH to "Mes").forEach { (option, label) ->
                val active = option == view
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .selectable(selected = active, role = Role.Tab, onClick = { onChange(option) })
                        .testTag("agenda-view-${option.name.lowercase()}"),
                    shape = MoShape.pill,
                    color = if (active) MoOlivePrimary else Color.Transparent,
                    contentColor = if (active) MoWarmWhite else MoInk,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** How the Agenda view reads the domain buckets: tomorrow and the next days are "this week". */
private enum class AgendaGroup(val title: String) {
    OVERDUE("Pendientes de días pasados"),
    TODAY("Hoy"),
    WEEK("Esta semana"),
    LATER("Más adelante"),
    ;

    companion object {
        fun of(entries: List<AgendaEntry>, today: LocalDate): List<Pair<AgendaGroup, List<AgendaEntry>>> =
            Agenda.group(entries, today, { it.activityDate }, { it.planning?.startTime })
                .groupBy(
                    { section ->
                        when (section.bucket) {
                            AgendaBucket.OVERDUE -> OVERDUE
                            AgendaBucket.TODAY -> TODAY
                            AgendaBucket.TOMORROW, AgendaBucket.NEXT_7_DAYS -> WEEK
                            AgendaBucket.LATER -> LATER
                        }
                    },
                    { it.items },
                )
                .map { (group, lists) -> group to lists.flatten() }
                .sortedBy { it.first.ordinal }
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
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("agenda-row").clickable(role = Role.Button, onClick = onSelected),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(start = MoSpacing.sm, end = MoSpacing.xs, top = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoIconBadge(entry.type.icon(), size = 36)
                Text(entry.description, style = MaterialTheme.typography.titleSmall, color = MoInk, modifier = Modifier.weight(1f))
                if (overdue) MoStatusChip("Atrasado", tone = MoStatusTone.Warning)
            }
            Text(
                listOfNotNull(
                    entry.type.label(),
                    dayLabel(entry.activityDate, today) + (entry.planning?.startTime?.let { " · ${it.format(HOUR)}" } ?: ""),
                    entry.farmName,
                    entry.parcelNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
            )
            planningLine(entry.planning?.copy(startTime = null))?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("agenda-planning"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (entry.reminders.isNotEmpty()) {
                    val waiting = entry.reminders.count { it.firedAt == null }
                    Icon(MoIcons.Bell, contentDescription = null, tint = MoInfoText, modifier = Modifier.size(16.dp))
                    Text(
                        if (waiting == 0) " Avisos enviados" else if (waiting == 1) " 1 aviso" else " $waiting avisos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MoInfoText,
                        modifier = Modifier.testTag("agenda-reminders"),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel, enabled = enabled, modifier = Modifier.testTag("agenda-cancel")) {
                    Text("Cancelar", color = MoErrorText)
                }
                TextButton(onClick = onComplete, enabled = enabled, modifier = Modifier.testTag("agenda-complete")) { Text("Hecho") }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Hoy"
    today?.plusDays(1) -> "Mañana"
    else -> date.format(DAY).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-ES")) }
}
