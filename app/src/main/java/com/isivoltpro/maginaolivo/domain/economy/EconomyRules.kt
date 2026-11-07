package com.isivoltpro.maginaolivo.domain.economy

import java.util.UUID

/**
 * Deterministic P5 rules (docs/07-plans/CR015-P5-LOCAL-ECONOMY.md). Pure functions: the repository calls
 * [validate…] inside its transaction and the screens call [balances]. No conversion, no prorating.
 */
object EconomyRules {
    const val MAX_MINOR = 1_000_000_000_000L

    /** Returns an error code, or null when the document is consistent on its own. */
    fun documentError(s: Settlement): String? = when {
        s.cooperative.isBlank() || s.cooperative.length > 120 -> "cooperative_required"
        !Regex("^[A-Z]{3}$").matches(s.currency) -> "invalid_currency"
        listOf(s.grossMinor, s.withholdingMinor, s.feesMinor, s.netMinor).any { it < 0 || it > MAX_MINOR } -> "invalid_amount"
        s.grossMinor == 0L -> "invalid_amount"
        s.netMinor != s.grossMinor - s.withholdingMinor - s.feesMinor -> "net_mismatch"
        s.kilograms != null && s.kilograms <= 0 -> "invalid_kilograms"
        s.cycleId != null && s.farmId == null -> "cycle_requires_farm"
        s.kind == SettlementKind.ADVANCE && s.replacesId != null -> "advance_cannot_replace"
        s.replacesId == s.id -> "invalid_replacement"
        else -> null
    }

    /** A replacement keeps scope and currency; only an active, not yet replaced PROVISIONAL can be replaced. */
    fun replacementError(new: Settlement, ledger: EconomyLedger): String? {
        val replaces = new.replacesId ?: return null
        val target = ledger.settlements.firstOrNull { it.id == replaces } ?: return "replaced_not_found"
        return when {
            target.kind != SettlementKind.PROVISIONAL -> "only_provisional_replaced"
            !target.active -> "replaced_cancelled"
            ledger.settlements.any { it.active && it.replacesId == target.id && it.id != new.id } -> "already_replaced"
            !sameScope(target, new) -> "scope_mismatch"
            // What was already applied or collected on the chain must still fit in the new net.
            new.netMinor < target.netMinor - pending(target, ledger) -> "replacement_below_collected"
            else -> null
        }
    }

    fun applicationError(app: AdvanceApplication, ledger: EconomyLedger): String? {
        val advance = ledger.settlements.firstOrNull { it.id == app.advanceId } ?: return "advance_not_found"
        val target = ledger.settlements.firstOrNull { it.id == app.settlementId } ?: return "settlement_not_found"
        if (app.amountMinor <= 0) return "invalid_amount"
        if (advance.kind != SettlementKind.ADVANCE || !advance.active) return "not_an_active_advance"
        if (target.kind == SettlementKind.ADVANCE || !target.active) return "not_an_active_settlement"
        if (!sameScope(advance, target)) return "scope_mismatch"
        if (head(target.id, ledger).id != target.id) return "settlement_replaced"
        val others = ledger.applications.filter { it.active && it.id != app.id }
        if (others.filter { it.advanceId == advance.id }.sumOf { it.amountMinor } + app.amountMinor > advance.netMinor)
            return "advance_exceeded"
        val head = head(target.id, ledger)
        val chainIds = chain(head, ledger).map { it.id }.toSet()
        val applied = others.filter { it.settlementId in chainIds }.sumOf { it.amountMinor } + app.amountMinor
        val paid = ledger.payments.filter { it.active && it.settlementId in chainIds }.sumOf { it.amountMinor }
        return if (applied + paid > head.netMinor) "settlement_exceeded" else null
    }

    fun paymentError(payment: SettlementPayment, ledger: EconomyLedger): String? {
        val doc = ledger.settlements.firstOrNull { it.id == payment.settlementId } ?: return "settlement_not_found"
        if (payment.amountMinor <= 0) return "invalid_amount"
        if (!doc.active) return "settlement_cancelled"
        if (doc.kind != SettlementKind.ADVANCE && head(doc.id, ledger).id != doc.id) return "settlement_replaced"
        val pending = pending(doc, ledger, excludePayment = payment.id)
        return if (payment.amountMinor > pending) "payment_exceeds_pending" else null
    }

    /** Balances per currency for a scope. Workspace-level documents never reach a farm or cycle scope. */
    fun balances(scope: EconomyScope, ledger: EconomyLedger, costsMinor: Map<String, Long>): List<EconomyBalance> {
        val inScope = ledger.settlements.filter { it.active && inScope(it, scope) }
        val effective = inScope.filter { it.kind != SettlementKind.ADVANCE && isHead(it, ledger) }
        val advances = inScope.filter { it.kind == SettlementKind.ADVANCE }
        val currencies = (inScope.map { it.currency } + costsMinor.filterValues { it != 0L }.keys).toSortedSet()
        return currencies.map { currency ->
            val heads = effective.filter { it.currency == currency }
            val adv = advances.filter { it.currency == currency }
            val gross = heads.sumOf { it.grossMinor }
            val fees = heads.sumOf { it.feesMinor }
            val costs = costsMinor[currency] ?: 0L
            val unapplied = adv.sumOf { a -> a.netMinor - ledger.applications.filter { it.active && it.advanceId == a.id }.sumOf { it.amountMinor } }
            val collected = (heads.flatMap { chain(it, ledger) } + adv).sumOf { doc ->
                ledger.payments.filter { it.active && it.settlementId == doc.id }.sumOf { it.amountMinor } }
            EconomyBalance(currency, gross, heads.sumOf { it.withholdingMinor }, fees, heads.sumOf { it.netMinor },
                unapplied, collected, (heads + adv).sumOf { pending(it, ledger) }, costs,
                Math.subtractExact(Math.subtractExact(gross, fees), costs),
                provisional = heads.isEmpty() || heads.any { it.kind == SettlementKind.PROVISIONAL },
                hasSettlements = heads.isNotEmpty())
        }
    }

    /** Net still to be received for a chain head or an advance. */
    fun pending(doc: Settlement, ledger: EconomyLedger, excludePayment: UUID? = null): Long {
        val ids = if (doc.kind == SettlementKind.ADVANCE) setOf(doc.id) else chain(doc, ledger).map { it.id }.toSet()
        val paid = ledger.payments.filter { it.active && it.id != excludePayment && it.settlementId in ids }.sumOf { it.amountMinor }
        val applied = if (doc.kind == SettlementKind.ADVANCE) 0L
            else ledger.applications.filter { it.active && it.settlementId in ids }.sumOf { it.amountMinor }
        return doc.netMinor - applied - paid
    }

    private fun inScope(s: Settlement, scope: EconomyScope) = s.workspaceId == scope.workspaceId && when {
        scope.cycleId != null -> s.farmId == scope.farmId && s.cycleId == scope.cycleId
        scope.farmId != null -> s.farmId == scope.farmId
        else -> true
    }
    private fun sameScope(a: Settlement, b: Settlement) =
        a.workspaceId == b.workspaceId && a.farmId == b.farmId && a.cycleId == b.cycleId && a.currency == b.currency
    private fun isHead(s: Settlement, ledger: EconomyLedger) = ledger.settlements.none { it.active && it.replacesId == s.id }
    /** The current active document of a replacement chain. */
    fun head(id: UUID, ledger: EconomyLedger): Settlement {
        var current = ledger.settlements.first { it.id == id }
        val seen = mutableSetOf(current.id)
        while (true) {
            val next = ledger.settlements.firstOrNull { it.active && it.replacesId == current.id } ?: return current
            check(seen.add(next.id)) { "replacement cycle" }
            current = next
        }
    }
    /** Head first, then every document it replaced (applications and payments follow the chain). */
    private fun chain(head: Settlement, ledger: EconomyLedger): List<Settlement> = buildList {
        var current: Settlement? = head
        while (current != null && size < 100) {
            add(current)
            current = current.replacesId?.let { id -> ledger.settlements.firstOrNull { it.id == id } }
        }
    }
}
