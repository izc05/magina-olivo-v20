package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.material3.MaterialTheme

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

@Composable
internal fun LabourPaymentsRoute(
    campaignId: UUID,
    persistence: LocalPersistence,
    personId: UUID? = null,
    /** #365: from Campaña → Jornales, a running campaign adds a jornal through today's day. */
    onAddLabour: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val model: LabourPaymentsViewModel = viewModel(key = "labour-payments-$campaignId", factory = viewModelFactory {
        initializer { LabourPaymentsViewModel(campaignId, persistence.campaignRepository, persistence.labourRepository, persistence.harvestRepository, persistence.expenseRepository) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    LabourPaymentsScreen(state, personId, onPay = model::record, onRemove = model::remove, onClearError = model::clearError, onClose = onClose, onAddLabour = onAddLabour)
}

/** Same campaign data from both entry points. Payments are displayed separately from work. */
@Composable
internal fun LabourPaymentsScreen(state: LabourPaymentsUiState, initialPerson: UUID? = null, today: LocalDate = LocalDate.now(), onPay: (LabourPayment) -> Unit = {}, onRemove: (UUID) -> Unit = {}, onClearError: () -> Unit = {}, onClose: () -> Unit = {}, onAddLabour: (() -> Unit)? = null) {
    var person by rememberSaveable { mutableStateOf(initialPerson?.toString()) }
    var paymentCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedAtOpen by rememberSaveable { mutableStateOf(state.saved) }
    LaunchedEffect(state.saved) { if (state.saved > savedAtOpen) { paymentCurrency = null; removeId = null; savedAtOpen = state.saved } }
    BackHandler(enabled = person != null && !state.isSaving) { if (paymentCurrency != null) paymentCurrency = null else person = null; onClearError() }
    val account = state.accounts.firstOrNull { it.person.workerId.toString() == person }
    val balance = account?.balances?.firstOrNull { it.currency == paymentCurrency }
    if (balance != null && state.readError == null) {
        LabourPaymentSheet(account.person.name, balance, today, state.isSaving, state.error, onPay) { paymentCurrency = null; onClearError() }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen).testTag("labour-payments-root"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        MoTertiaryButton(if (person == null) "Cerrar" else "Volver a jornales", { if (person == null) onClose() else person = null; onClearError() }, enabled = !state.isSaving)
        Text(if (account == null) "Jornales de la campaña" else account.person.name, style = MaterialTheme.typography.headlineSmall, color = MoColors.current.labourText)
        state.campaign?.let { Text(it.name, color = MoSurfaceTokens.secondaryText) }
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.testTag("labour-payments-loading"))
            state.readError != null -> MoErrorState("No pudimos abrir los jornales", state.readError)
            state.campaign == null -> MoErrorState("Campaña no disponible", "Vuelve a abrir la campaña.")
            account != null -> {
                if (state.campaign.status == CampaignStatus.CLOSED) Text("Campaña cerrada: puedes liquidar pagos pendientes. El coste histórico se conserva.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("labour-payments-closed"))
                LabourPersonCard(account, null, { currency -> savedAtOpen = state.saved; paymentCurrency = currency; onClearError() }, state.isSaving)
                MoSectionHeader("Trabajo", Modifier.testTag("labour-person-work"))
                state.entries.filter { it.workerId == account.person.workerId }.sortedByDescending { row -> state.days.firstOrNull { it.id == row.harvestId }?.harvestDate }.forEach { row ->
                    val date = state.days.firstOrNull { it.id == row.harvestId }?.harvestDate
                    val price = row.appliedRate
                    val cost = account.costs.firstOrNull { it.labourEntryId == row.id }
                    MoCompactListItem(title = date?.format(WORK_DATE) ?: "Fecha no disponible", subtitle = "${row.label()} · " + (cost?.let { Money.format(it.amountMinor, it.currency) } ?: if (price == null) "Precio sin confirmar" else "Coste sin atribuir"), icon = MoIcons.People, iconTint = MoColors.current.labourText, iconContainer = MoColors.current.labourTint, modifier = Modifier.testTag("labour-person-work-row"))
                }
                MoSectionHeader("Pagos", Modifier.testTag("labour-person-payments"))
                val ownPayments = state.payments.filter { it.workerId == account.person.workerId }.sortedByDescending { it.paymentDate }
                if (ownPayments.isEmpty()) Text("Sin pagos registrados", color = MoSurfaceTokens.secondaryText)
                ownPayments.forEach { payment ->
                    MoCompactListItem(title = "${payment.paymentDate.format(WORK_DATE)} · ${Money.format(payment.amountMinor, payment.currency)}", subtitle = payment.note, icon = MoIcons.Euro, iconTint = MoColors.current.infoText, iconContainer = MoColors.current.infoTint, modifier = Modifier.testTag("labour-person-payment-row"), trailing = { MoTertiaryButton("Corregir", { removeId = payment.id.toString(); onClearError() }, enabled = !state.isSaving, modifier = Modifier.testTag("payment-correct")) })
                }
            }
            else -> {
                val named = state.accounts.size
                if (named > 0) Text(if (named == 1) "1 persona con nombre" else "$named personas con nombre", color = MoSurfaceTokens.secondaryText)
                if (state.entries.isNotEmpty()) Text(LabourSummary.of(state.entries).label(), color = MoColors.current.labourText)
                val status = state.campaign.status
                // #365: the campaign already knows its Farm, so the entry is right here.
                // Codex #401: a day before the campaign starts cannot be opened; say when it can.
                val startsLater = state.campaign.startDate.isAfter(today)
                if (onAddLabour != null && status.isRunning && !startsLater) {
                    MoPrimaryButton("+ Añadir jornal", onAddLabour, modifier = Modifier.fillMaxWidth().testTag("labour-add"), enabled = !state.isSaving)
                }
                if (onAddLabour != null && status.isRunning && startsLater) {
                    Text("La campaña empieza el ${state.campaign.startDate.format(WORK_DATE)}: podrás añadir jornales desde ese día.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("labour-add-later"))
                }
                if (onAddLabour != null && status == CampaignStatus.CLOSED && state.entries.isNotEmpty()) {
                    Text("Campaña cerrada. Reábrela para añadir nuevos jornales.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("labour-add-closed"))
                }
                if (state.entries.isEmpty()) MoEmptyState(
                    "Sin jornales anotados",
                    when {
                        onAddLabour == null -> "Registra un jornal desde el Cuaderno o un día de recolección."
                        status.isRunning && startsLater -> "Todavía no hay jornales: la campaña aún no ha empezado."
                        status.isRunning -> "Todavía no has registrado jornales en esta campaña."
                        status == CampaignStatus.CLOSED -> "Esta campaña está cerrada."
                        else -> "Activa la campaña para añadir jornales."
                    },
                    icon = MoIcons.People,
                )
                state.accounts.forEach { current ->
                    LabourPersonCard(current, { person = current.person.workerId.toString() }, { currency -> person = current.person.workerId.toString(); paymentCurrency = currency; savedAtOpen = state.saved; onClearError() }, state.isSaving)
                }
                val unnamed = LabourByWorker.unnamed(state.entries)
                if (!unnamed.isEmpty) MoCompactListItem("Sin identificar", subtitle = unnamed.label() + " · Histórico sin pagos por persona", icon = MoIcons.People, iconTint = MoColors.current.labourText, iconContainer = MoColors.current.labourTint, modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("notebook-worker-labour-unnamed"))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("labour-payments-error")) }
        val correction = state.payments.firstOrNull { it.id.toString() == removeId }
        if (correction != null) {
            Surface(color = MoSurfaceTokens.cardSurface, shape = MoShape.card) {
                Column(Modifier.padding(MoSpacing.md).testTag("payment-correction-confirmation"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                    Text("Corregir pago", style = MaterialTheme.typography.titleLarge)
                    Text("Se retirará el pago de ${Money.format(correction.amountMinor, correction.currency)} del ${correction.paymentDate.format(WORK_DATE)}. El saldo pendiente se recalculará y el movimiento se conservará en el histórico.")
                    MoPrimaryButton("Retirar pago", { onRemove(correction.id) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth())
                    MoTertiaryButton("Cancelar", { removeId = null }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun LabourPersonCard(account: LabourAccount, onDetail: (() -> Unit)?, onPay: (String) -> Unit, saving: Boolean) {
    Surface(color = MoSurfaceTokens.cardSurface, shape = MoShape.card, border = BorderStroke(1.dp, MoColors.current.labourTint), modifier = Modifier.fillMaxWidth().testTag("notebook-worker-labour")) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoIconBadge(MoIcons.People, tint = MoColors.current.labourText, container = MoColors.current.labourTint)
                Column {
                    Text(account.person.name, style = MaterialTheme.typography.titleMedium)
                    Text("${account.person.jornadas} ${if (account.person.jornadas == 1) "día" else "días"} · ${account.person.summary.label()}", color = MoSurfaceTokens.secondaryText)
                }
            }
            if (account.unconfirmed) Text("Precio sin confirmar o coste sin atribuir. El saldo de esos jornales no está disponible.", color = MoColors.current.warningText, modifier = Modifier.testTag("labour-unconfirmed"))
            account.balances.forEach { balance ->
                Text("${Money.format(balance.generatedMinor, balance.currency)} generados", color = MoColors.current.labourText, modifier = Modifier.testTag("person-generated"))
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = MoColors.current.successText)) { append("${Money.format(balance.paidMinor, balance.currency)} pagados") }
                    append(" · ")
                    withStyle(SpanStyle(color = MoColors.current.warningText)) { append("${Money.format(balance.pendingMinor, balance.currency)} pendientes") }
                }, color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("person-paid-pending"))
                LabourPaymentStatus(balance.state)
                if (balance.pendingMinor > 0) MoSecondaryButton("Registrar pago${if (account.balances.size > 1) " (${balance.currency})" else ""}", { onPay(balance.currency) }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("person-register-payment"))
            }
            if (onDetail != null) MoTertiaryButton("Ver detalle", onDetail, modifier = Modifier.fillMaxWidth().testTag("person-detail"), enabled = !saving)
        }
    }
}

/** Text + outlined icon + semantic token, with a light surface for every payment state. */
@Composable
internal fun LabourPaymentStatus(state: LabourPaymentState) {
    val text = when (state) { LabourPaymentState.PENDING -> "Pendiente"; LabourPaymentState.PARTIAL -> "Pago parcial"; LabourPaymentState.PAID -> "Pagado" }
    val foreground = when (state) { LabourPaymentState.PENDING -> MoColors.current.warningText; LabourPaymentState.PARTIAL -> MoColors.current.infoText; LabourPaymentState.PAID -> MoColors.current.successText }
    val background = when (state) { LabourPaymentState.PENDING -> MoColors.current.warningTint; LabourPaymentState.PARTIAL -> MoColors.current.infoTint; LabourPaymentState.PAID -> MoColors.current.successTint }
    val icon = when (state) { LabourPaymentState.PENDING -> MoIcons.Clock; LabourPaymentState.PARTIAL -> MoIcons.Euro; LabourPaymentState.PAID -> MoIcons.Check }
    Surface(color = background, contentColor = foreground, shape = MoShape.pill, modifier = Modifier.testTag("payment-state-${state.name}")) {
        Row(Modifier.padding(MoSpacing.xs), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) { Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp)); Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}

private val WORK_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.forLanguageTag("es-ES"))
