package com.isivoltpro.maginaolivo.feature.economy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.economy.*
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** CR-015 P5: income, advances, payments and margin of a farm or of one agricultural cycle. Local only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EconomyPanel(repository: EconomyRepository, farmId: UUID, cycleId: UUID?, today: LocalDate = LocalDate.now()) {
    val result by remember(repository, farmId, cycleId) { repository.observe(farmId, cycleId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    fun run(action: suspend () -> AppResult<Unit>, after: () -> Unit = {}) = scope.launch {
        error = when (val r = action()) { is AppResult.Success -> { after(); null }; is AppResult.Failure -> economyMessage(r.error) }
    }
    Column(Modifier.fillMaxWidth().testTag("economy-panel"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        when (val current = result) {
            null -> Text("Calculando ingresos y cobros…", style = MaterialTheme.typography.bodySmall)
            is AppResult.Failure -> Text(economyMessage(current.error), color = MaterialTheme.colorScheme.error)
            is AppResult.Success -> {
                val snapshot = current.value
                if (snapshot.balances.isEmpty()) Text("Aún no hay liquidaciones, anticipos ni gastos en este ámbito.",
                    modifier = Modifier.testTag("campaign-balance-incomplete"))
                snapshot.balances.forEach { BalanceCard(it) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("economy-error")) }
                if (editing) SettlementForm(snapshot, farmId, cycleId, today, onCancel = { editing = false }) { s ->
                    run({ repository.record(s) }) { editing = false }
                } else MoSecondaryButton("Registrar liquidación o anticipo", { editing = true; error = null },
                    Modifier.fillMaxWidth().testTag("economy-new"))
                val docs = snapshot.ledger.settlements.filter { EconomyRules.inScope(it, snapshot.scope) &&
                    (it.kind == SettlementKind.ADVANCE || EconomyRules.isHead(it, snapshot.ledger)) }
                    .sortedWith(compareByDescending<Settlement> { it.documentDate }.thenBy { it.id.toString() })
                if (docs.isNotEmpty()) Text("Documentos", style = MaterialTheme.typography.titleSmall)
                docs.forEach { doc -> DocumentRow(doc, snapshot, today, { a, b -> run(a, b) }, repository) }
            }
        }
    }
}

@Composable
private fun BalanceCard(b: EconomyBalance) {
    fun m(v: Long) = Money.format(v, b.currency)
    Surface(color = MoSurfaceTokens.cardSurface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("economy-balance-${b.currency}")) {
        Column(Modifier.padding(MoSpacing.sm), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Text(if (!b.hasSettlements) "Ingresos pendientes de registrar · balance incompleto"
                else if (b.provisional) "Balance provisional: hay liquidaciones provisionales"
                else "Balance con liquidaciones definitivas",
                style = MaterialTheme.typography.labelLarge,
                modifier = if (b.provisional) Modifier.testTag("campaign-balance-incomplete") else Modifier)
            MoLabeledValue("Ingresos reconocidos (bruto)", m(b.recognisedGrossMinor))
            MoLabeledValue("Cuotas y gastos de cooperativa", m(b.feesMinor))
            MoLabeledValue("Costes contabilizados", m(b.costsMinor))
            MoLabeledValue("Margen", m(b.marginMinor))
            MoLabeledValue("Retenciones (aparte, no son coste)", m(b.withholdingMinor))
            MoLabeledValue("Cobrado", m(b.collectedMinor))
            MoLabeledValue("Pendiente de cobro", m(b.pendingMinor))
            if (b.advancesUnappliedMinor > 0) MoLabeledValue("Anticipos sin aplicar (no son ingreso)", m(b.advancesUnappliedMinor))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentRow(doc: Settlement, snapshot: EconomySnapshot, today: LocalDate,
    run: (suspend () -> AppResult<Unit>, () -> Unit) -> Unit, repository: EconomyRepository) {
    var action by rememberSaveable(doc.id) { mutableStateOf<String?>(null) }
    var amount by rememberSaveable(doc.id) { mutableStateOf("") }
    val pending = if (doc.active) EconomyRules.pending(doc, snapshot.ledger) else 0L
    val label = when (doc.kind) { SettlementKind.ADVANCE -> "Anticipo"; SettlementKind.PROVISIONAL -> "Liquidación provisional"; SettlementKind.FINAL -> "Liquidación definitiva" }
    Column(Modifier.fillMaxWidth().testTag("economy-doc-${doc.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label · ${doc.cooperative}", style = MaterialTheme.typography.bodyMedium)
        Text("${doc.documentDate} · neto ${Money.format(doc.netMinor, doc.currency)}" +
            if (doc.active) " · pendiente ${Money.format(pending, doc.currency)}" else " · anulado",
            style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
        if (doc.active) FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            if (pending > 0) MoTertiaryButton("Registrar cobro", { action = "pay"; amount = Money.editable(pending, doc.currency) },
                Modifier.testTag("economy-pay-${doc.id}"))
            if (doc.kind == SettlementKind.ADVANCE) MoTertiaryButton("Aplicar a una liquidación", { action = "apply"; amount = "" },
                Modifier.testTag("economy-apply-${doc.id}"))
            MoTertiaryButton("Anular", { run({ repository.cancel(doc.id) }, {}) }, Modifier.testTag("economy-cancel-${doc.id}"))
        }
        when (action) {
            "pay" -> {
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe cobrado (${doc.currency})") },
                    modifier = Modifier.fillMaxWidth().testTag("economy-pay-amount"))
                MoPrimaryButton("Guardar cobro", {
                    val minor = Money.parseMinor(amount, doc.currency)
                    if (minor != null) run({ repository.recordPayment(SettlementPayment(UUID.randomUUID(), doc.id, today, minor)) }, { action = null })
                }, Modifier.fillMaxWidth().testTag("economy-pay-save"))
            }
            "apply" -> {
                val targets = snapshot.ledger.settlements.filter { it.active && it.kind != SettlementKind.ADVANCE &&
                    it.currency == doc.currency && it.farmId == doc.farmId && it.cycleId == doc.cycleId &&
                    EconomyRules.isHead(it, snapshot.ledger) }
                if (targets.isEmpty()) Text("Registra antes la liquidación a la que se aplica este anticipo.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe a aplicar (${doc.currency})") },
                    modifier = Modifier.fillMaxWidth().testTag("economy-apply-amount"))
                targets.forEach { t ->
                    MoSecondaryButton("Aplicar a ${t.cooperative} · ${t.documentDate}", {
                        val minor = Money.parseMinor(amount, doc.currency)
                        if (minor != null) run({ repository.applyAdvance(AdvanceApplication(UUID.randomUUID(), doc.id, t.id, minor)) }, { action = null })
                    }, Modifier.fillMaxWidth().testTag("economy-apply-to-${t.id}"))
                }
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettlementForm(snapshot: EconomySnapshot, farmId: UUID, cycleId: UUID?, today: LocalDate,
    onCancel: () -> Unit, onSave: (Settlement) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(SettlementKind.FINAL.name) }
    var cooperative by rememberSaveable { mutableStateOf(snapshot.ledger.settlements.lastOrNull()?.cooperative.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var gross by rememberSaveable { mutableStateOf("") }
    var withholding by rememberSaveable { mutableStateOf("") }
    var fees by rememberSaveable { mutableStateOf("") }
    var replaces by rememberSaveable { mutableStateOf<String?>(null) }
    val g = Money.parseMinor(gross, currency); val w = Money.parseMinor(withholding.ifBlank { "0" }, currency)
    val f = Money.parseMinor(fees.ifBlank { "0" }, currency)
    val net = if (g != null && w != null && f != null) g - w - f else null
    val provisionals = snapshot.ledger.settlements.filter { it.active && it.kind == SettlementKind.PROVISIONAL &&
        it.farmId == farmId && it.cycleId == cycleId && it.currency == currency && EconomyRules.isHead(it, snapshot.ledger) }
    Column(Modifier.fillMaxWidth().testTag("economy-form"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Text("Nuevo documento de la cooperativa", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            listOf(SettlementKind.ADVANCE to "Anticipo", SettlementKind.PROVISIONAL to "Provisional", SettlementKind.FINAL to "Definitiva").forEach { (k, text) ->
                FilterChip(kind == k.name, { kind = k.name; if (k == SettlementKind.ADVANCE) replaces = null }, { Text(text) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("economy-kind-${k.name}"))
            }
        }
        if (kind == SettlementKind.ADVANCE.name) Text("Un anticipo es dinero a cuenta: no cuenta como ingreso hasta aplicarlo a una liquidación.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(cooperative, { cooperative = it }, label = { Text("Cooperativa o almazara") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("economy-cooperative"))
        MoDateInputField(date, { date = it }, "Fecha del documento", modifier = Modifier.testTag("economy-date"))
        OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Moneda") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("economy-currency"))
        OutlinedTextField(gross, { gross = it }, label = { Text("Importe bruto") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("economy-gross"))
        if (kind != SettlementKind.ADVANCE.name) {
            OutlinedTextField(withholding, { withholding = it }, label = { Text("Retenciones (opcional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("economy-withholding"))
            OutlinedTextField(fees, { fees = it }, label = { Text("Cuotas y gastos de cooperativa (opcional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("economy-fees"))
            if (provisionals.isNotEmpty()) {
                Text("Sustituye a (opcional)", style = MaterialTheme.typography.labelMedium)
                provisionals.forEach { p ->
                    FilterChip(replaces == p.id.toString(), { replaces = if (replaces == p.id.toString()) null else p.id.toString() },
                        { Text("Provisional ${p.documentDate} · ${Money.format(p.netMinor, p.currency)}") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("economy-replaces-${p.id}"))
                }
            }
        }
        Text("Neto: ${net?.let { Money.format(it, currency) } ?: "revisa los importes"}", modifier = Modifier.testTag("economy-net"))
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        MoPrimaryButton("Guardar documento", {
            if (g != null && w != null && f != null && net != null && parsedDate != null) onSave(Settlement(UUID.randomUUID(),
                snapshot.scope.workspaceId, farmId, cycleId, SettlementKind.valueOf(kind), cooperative, parsedDate, currency,
                g, w, f, net, replacesId = replaces?.let(UUID::fromString)))
        }, Modifier.fillMaxWidth().testTag("economy-save"), enabled = net != null && parsedDate != null && cooperative.isNotBlank())
        MoTertiaryButton("Cancelar", onCancel)
    }
}

internal fun economyMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when (error.code) {
        "net_mismatch" -> "El neto no cuadra con bruto, retenciones y cuotas."
        "invalid_amount" -> "Revisa los importes: deben ser positivos y el neto no puede ser negativo."
        "cooperative_required" -> "Indica la cooperativa o almazara."
        "invalid_currency" -> "La moneda debe ser un código de tres letras, por ejemplo EUR."
        "advance_exceeded" -> "Ese importe supera lo que queda del anticipo."
        "settlement_exceeded" -> "Ese importe supera lo pendiente de la liquidación."
        "payment_exceeds_pending" -> "El cobro supera lo pendiente."
        "replacement_below_collected" -> "La nueva liquidación es menor que lo ya cobrado o aplicado."
        "already_replaced" -> "Esa provisional ya tiene sustituta."
        "settlement_replaced" -> "Esa liquidación ya fue sustituida; usa la vigente."
        "has_movements" -> "Tiene cobros o anticipos aplicados: no se puede anular."
        "scope_mismatch" -> "Debe ser de la misma finca, campaña agrícola y moneda."
        "archived_farm" -> "La finca está archivada: consulta su histórico sin modificarlo."
        else -> "No se pudo guardar (${error.code})."
    }
    is AppError.Permission -> "Solo el propietario del espacio puede consultar y registrar su economía."
    is AppError.Conflict -> "Los datos han cambiado. Revísalos de nuevo."
    else -> "No se pudo completar la operación. Los datos anteriores se conservan."
}
