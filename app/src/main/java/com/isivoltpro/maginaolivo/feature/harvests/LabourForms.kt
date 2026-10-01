package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One canonical form for day and Cuaderno. Every entered amount retains its contextual currency. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabourSheet(
    workers: List<Worker>, alreadyRecorded: Set<UUID>, isSaving: Boolean, error: String?,
    harvestId: UUID, campaignId: UUID, date: LocalDate, rates: RecollectionRates?,
    onSaveCrew: (CrewDraft) -> Unit, onAddWorker: (String) -> Unit, onCancel: () -> Unit,
    currency: String = rates?.currency ?: "EUR",
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var newPerson by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    var unit by rememberSaveable { mutableStateOf(LabourUnit.FULL_DAY) }
    var hours by rememberSaveable { mutableStateOf("") }
    val usualRates = rates?.takeIf { it.currency == currency }
    var price by rememberSaveable { mutableStateOf(Money.editable(usualRates?.fullDayMinor, currency)) }
    var touchedPrice by rememberSaveable { mutableStateOf(false) }
    var initialPayment by rememberSaveable { mutableStateOf("NONE") }
    var paymentAmount by rememberSaveable { mutableStateOf("") }
    var paymentId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    LaunchedEffect(workers, pendingName) {
        val name = pendingName ?: return@LaunchedEffect
        workers.firstOrNull { it.name.equals(name, true) }?.let {
            if (it.id !in alreadyRecorded) selected = it.id.toString()
            pendingName = null; newPerson = false; newName = ""
        }
    }
    LaunchedEffect(rates, unit) {
        if (!touchedPrice) price = Money.editable(if (unit == LabourUnit.HOURS) usualRates?.hourlyMinor else usualRates?.fullDayMinor, currency)
    }
    val minutes = if (unit == LabourUnit.HOURS) parseHours(hours)?.takeIf { it <= LabourRules.MAX_MINUTES } else null
    val minor = Money.parseMinor(price, currency)
    val rate = minor?.let { LabourRateSnapshot(it, currency, date, if (unit == LabourUnit.HOURS) LabourRateBasis.HOUR else LabourRateBasis.DAY) }
    val workerId = selected?.let(UUID::fromString)?.takeIf { it !in alreadyRecorded && workers.any { worker -> worker.id == it } }
    val total = if (rate != null && (unit != LabourUnit.HOURS || minutes != null)) runCatching {
        LabourPricing.amountMinor(LabourEntry(UUID.randomUUID(), harvestId, workerId, null, 1, unit, minutes, 1, rate))
    }.getOrNull() else null
    val partial = Money.parseMinor(paymentAmount, currency)
    val paymentError = when {
        initialPayment == "PARTIAL" && (partial == null || partial <= 0) -> "Escribe un importe válido mayor que cero"
        initialPayment == "PARTIAL" && total != null && partial!! > total -> "El importe supera los ${Money.format(total, currency)} del jornal"
        initialPayment == "FULL" && total == 0L -> "Un jornal sin coste no necesita pago"
        else -> null
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("labour-sheet"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Text("Registrar jornal", style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text("Persona", style = MaterialTheme.typography.titleMedium, color = MoLabourText)
        if (workers.isEmpty()) Text("Añade una persona para guardar su jornal.", color = MoTextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            workers.forEach { worker ->
                FilterChip(selected == worker.id.toString(), { selected = worker.id.toString() }, { Text(worker.name) }, enabled = !isSaving && worker.id !in alreadyRecorded, modifier = Modifier.testTag("labour-worker"))
            }
        }
        Text(if (workerId == null) "Elige una persona" else "1 seleccionada", color = MoTextSecondary, modifier = Modifier.testTag("labour-selected-count"))
        MoTertiaryButton("+ Nueva persona", { newPerson = !newPerson }, enabled = !isSaving)
        if (newPerson) {
            MoTextField(newName, { newName = it }, "Nombre y apellidos", enabled = !isSaving, modifier = Modifier.fillMaxWidth().testTag("labour-new-name"))
            MoSecondaryButton("Añadir persona", { pendingName = newName.trim(); onAddWorker(newName) }, enabled = newName.isNotBlank() && !isSaving, modifier = Modifier.fillMaxWidth().testTag("labour-add-worker"))
        }
        Text("Duración", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            listOf(LabourUnit.FULL_DAY, LabourUnit.HOURS).forEach { option ->
                FilterChip(unit == option, { unit = option; touchedPrice = false }, { Text(option.label()) }, enabled = !isSaving, modifier = Modifier.testTag("labour-unit-${option.name}"))
            }
        }
        if (unit == LabourUnit.HOURS) MoTextField(hours, { hours = it }, "Horas", enabled = !isSaving, supportingText = if (minutes == null) "Entre 0 y 24 horas; por ejemplo 3 o 3,5" else null, modifier = Modifier.fillMaxWidth().testTag("labour-hours"))
        MoTextField(price, { price = it; touchedPrice = true }, if (unit == LabourUnit.HOURS) "Tarifa por hora ($currency)" else "Precio del jornal ($currency)", enabled = !isSaving, isError = minor == null, supportingText = if (minor == null) "Confirma un precio válido para guardar" else null, modifier = Modifier.fillMaxWidth().testTag("labour-rate"))
        total?.let { Text("Coste del jornal: ${Money.format(it, currency)}", color = MoLabourText, modifier = Modifier.testTag("labour-generated")) }
        Text("Pago inicial (opcional)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            listOf("NONE" to "Sin pagar", "PARTIAL" to "Pago parcial", "FULL" to "Pagado completo").forEach { (value, label) ->
                FilterChip(initialPayment == value, { initialPayment = value }, { Text(label) }, enabled = !isSaving, modifier = Modifier.testTag("labour-payment-$value"))
            }
        }
        if (initialPayment == "PARTIAL") MoTextField(paymentAmount, { paymentAmount = it }, "Importe entregado ($currency)", enabled = !isSaving, isError = paymentError != null, supportingText = paymentError, modifier = Modifier.fillMaxWidth().testTag("labour-payment-amount"))
        if (initialPayment != "PARTIAL" && paymentError != null) Text(paymentError, color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("labour-form-error")) }
        MoPrimaryButton(if (isSaving) "Guardando…" else "Guardar 1 jornal", {
            val paid = when (initialPayment) { "FULL" -> total; "PARTIAL" -> partial; else -> null }
            onSaveCrew(CrewDraft(harvestId, listOf(workerId!!), unit, minutes, rate!!, paid?.let { listOf(LabourPayment(UUID.fromString(paymentId), workerId, campaignId, date, it, currency)) }.orEmpty()))
        }, enabled = workerId != null && total != null && rate != null && paymentError == null && !isSaving, modifier = Modifier.fillMaxWidth().testTag("labour-save"))
        MoTertiaryButton("Cancelar", onCancel, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

/** Legacy snapshots remain missing until the farmer explicitly confirms duration and price. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabourPriceSheet(entry: LabourEntry, date: LocalDate, currency: String, isSaving: Boolean, error: String?, onSave: (LabourChange) -> Unit, onCancel: () -> Unit) {
    var unit by rememberSaveable(entry.id.toString()) { mutableStateOf(entry.unit) }
    var hours by rememberSaveable(entry.id.toString()) { mutableStateOf(entry.minutes?.let { java.math.BigDecimal(it).divide(java.math.BigDecimal(60), 2, java.math.RoundingMode.HALF_UP).toPlainString() }.orEmpty()) }
    var price by rememberSaveable(entry.id.toString()) { mutableStateOf(Money.editable(entry.appliedRate?.unitPriceMinor, currency)) }
    val minutes = if (unit == LabourUnit.HOURS) parseHours(hours)?.takeIf { it <= LabourRules.MAX_MINUTES } else null
    val minor = Money.parseMinor(price, currency)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        Text("Confirmar jornal · ${entry.workerName ?: "Sin identificar"}", style = MaterialTheme.typography.titleLarge)
        if (entry.appliedRate == null) Text("Precio sin confirmar. Introduce el precio acordado para este día.", color = MoWarningText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            (listOf(LabourUnit.FULL_DAY, LabourUnit.HOURS) + listOfNotNull(entry.unit.takeIf { it == LabourUnit.HALF_DAY })).forEach { option ->
                FilterChip(unit == option, { unit = option }, { Text(option.label()) }, enabled = !isSaving)
            }
        }
        if (unit == LabourUnit.HOURS) MoTextField(hours, { hours = it }, "Horas", enabled = !isSaving, modifier = Modifier.fillMaxWidth().testTag("labour-edit-hours"))
        MoTextField(price, { price = it }, "Tarifa aplicada ($currency)", enabled = !isSaving, isError = minor == null, supportingText = if (minor == null) "Confirma un precio válido" else null, modifier = Modifier.fillMaxWidth().testTag("labour-edit-rate"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MoPrimaryButton("Guardar cambios", { onSave(LabourChange(entry.quantity, unit, minutes, LabourRateSnapshot(minor!!, currency, entry.appliedRate?.priceDate ?: date, if (unit == LabourUnit.HOURS) LabourRateBasis.HOUR else LabourRateBasis.DAY))) }, enabled = minor != null && (unit != LabourUnit.HOURS || minutes != null) && !isSaving, modifier = Modifier.fillMaxWidth().testTag("labour-edit-save"))
        MoTertiaryButton("Cancelar", onCancel, enabled = !isSaving)
    }
}

@Composable
internal fun LabourPaymentSheet(name: String, balance: LabourSettlement, today: LocalDate, isSaving: Boolean, error: String?, onSave: (LabourPayment) -> Unit, onCancel: () -> Unit) {
    var id by rememberSaveable(balance.workerId.toString(), balance.currency) { mutableStateOf(UUID.randomUUID().toString()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today.format(PAYMENT_DATE)) }
    var note by rememberSaveable { mutableStateOf("") }
    val minor = Money.parseMinor(amount, balance.currency)
    val paymentDate = runCatching { LocalDate.parse(date, PAYMENT_DATE) }.getOrNull()
    val amountError = when {
        amount.isBlank() -> null
        minor == null || minor <= 0 -> "Escribe un importe válido mayor que cero"
        minor > balance.pendingMinor -> "El importe supera los ${Money.format(balance.pendingMinor, balance.currency)} pendientes"
        else -> null
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen).testTag("labour-payment-sheet"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        Text("Registrar pago", style = MaterialTheme.typography.headlineSmall)
        Text(name, style = MaterialTheme.typography.titleMedium, color = MoLabourText, modifier = Modifier.testTag("payment-person"))
        Text("Pendiente actual: ${Money.format(balance.pendingMinor, balance.currency)}", color = MoWarningText, modifier = Modifier.testTag("payment-pending"))
        MoTextField(amount, { amount = it }, "Importe a pagar (${balance.currency})", enabled = !isSaving, isError = amountError != null, supportingText = amountError, modifier = Modifier.fillMaxWidth().testTag("payment-amount"))
        MoSecondaryButton("Pagar todo (${Money.format(balance.pendingMinor, balance.currency)})", { amount = Money.editable(balance.pendingMinor, balance.currency) }, enabled = !isSaving && balance.pendingMinor > 0, modifier = Modifier.fillMaxWidth().testTag("payment-all"))
        MoTextField(date, { date = it }, "Fecha (dd/mm/aaaa)", enabled = !isSaving, isError = paymentDate == null, supportingText = if (paymentDate == null) "Escribe una fecha válida" else null, modifier = Modifier.fillMaxWidth().testTag("payment-date"))
        MoTextField(note, { note = it }, "Nota (opcional)", enabled = !isSaving, modifier = Modifier.fillMaxWidth().testTag("payment-note"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("payment-error")) }
        MoPrimaryButton(if (isSaving) "Guardando…" else "Guardar pago", { onSave(LabourPayment(UUID.fromString(id), balance.workerId, balance.campaignId, paymentDate!!, minor!!, balance.currency, note.trim().takeIf { it.isNotEmpty() })) }, enabled = !isSaving && minor != null && minor > 0 && minor <= balance.pendingMinor && paymentDate != null, modifier = Modifier.fillMaxWidth().testTag("payment-save"))
        MoTertiaryButton("Cancelar", onCancel, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
    }
}

private val PAYMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
