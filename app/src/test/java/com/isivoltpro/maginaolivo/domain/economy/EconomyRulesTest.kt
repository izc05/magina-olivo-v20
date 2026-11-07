package com.isivoltpro.maginaolivo.domain.economy

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class EconomyRulesTest {
    private val ws = UUID.randomUUID()
    private val farmA = UUID.randomUUID()
    private val farmB = UUID.randomUUID()
    private val cycle = UUID.randomUUID()
    private val day = LocalDate.parse("2027-03-15")
    private val all = EconomyScope(ws)
    private val farm = EconomyScope(ws, farmA)
    private val inCycle = EconomyScope(ws, farmA, cycle)

    private fun doc(kind: SettlementKind, gross: Long, withholding: Long = 0, fees: Long = 0, farm: UUID? = farmA,
        cycle: UUID? = this.cycle, replaces: UUID? = null, currency: String = "EUR",
        status: SettlementStatus = SettlementStatus.ACTIVE) = Settlement(UUID.randomUUID(), ws, farm, cycle, kind,
        "Cooperativa Mágina", day, currency, gross, withholding, fees, gross - withholding - fees,
        replacesId = replaces, status = status)
    private fun apply(advance: Settlement, to: Settlement, amount: Long) = AdvanceApplication(UUID.randomUUID(), advance.id, to.id, amount)
    private fun pay(to: Settlement, amount: Long) = SettlementPayment(UUID.randomUUID(), to.id, day, amount)
    private fun one(scope: EconomyScope, ledger: EconomyLedger, costs: Map<String, Long> = emptyMap()) =
        EconomyRules.balances(scope, ledger, costs).single()

    @Test fun documentNeedsConsistentNetAndCooperative() {
        assertNull(EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000, 200, 300)))
        assertEquals("net_mismatch", EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000).copy(netMinor = 9_000)))
        assertEquals("cooperative_required", EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000).copy(cooperative = " ")))
        assertEquals("invalid_currency", EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000, currency = "eur")))
        assertEquals("cycle_requires_farm", EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000, farm = null)))
        assertEquals("invalid_amount", EconomyRules.documentError(doc(SettlementKind.FINAL, 10_000, withholding = 20_000)))
    }

    @Test fun advanceIsNeverIncomeAndFinalReplacesProvisionalWithoutDoubleCounting() {
        val advance = doc(SettlementKind.ADVANCE, 3_000)
        val provisional = doc(SettlementKind.PROVISIONAL, 10_000, 500, 200)
        val final = doc(SettlementKind.FINAL, 12_000, 600, 250, replaces = provisional.id)
        val before = one(inCycle, EconomyLedger(listOf(advance, provisional), emptyList(), emptyList()))
        assertEquals(10_000, before.recognisedGrossMinor)
        assertTrue(before.provisional)
        assertEquals(3_000, before.advancesUnappliedMinor)
        val after = one(inCycle, EconomyLedger(listOf(advance, provisional, final), emptyList(), emptyList()))
        assertEquals(12_000, after.recognisedGrossMinor)
        assertEquals(600, after.withholdingMinor)
        assertEquals(11_150, after.recognisedNetMinor)
        assertFalse(after.provisional)
    }

    @Test fun appliedAdvanceAndPaymentsFollowTheChainAndPendingNeverDoubles() {
        val advance = doc(SettlementKind.ADVANCE, 3_000)
        val provisional = doc(SettlementKind.PROVISIONAL, 10_000)
        val applied = apply(advance, provisional, 3_000)
        val paidAdvance = pay(advance, 3_000)
        val paidSettlement = pay(provisional, 4_000)
        val final = doc(SettlementKind.FINAL, 12_000, replaces = provisional.id)
        val ledger = EconomyLedger(listOf(advance, provisional, final), listOf(applied), listOf(paidAdvance, paidSettlement))
        val b = one(inCycle, ledger)
        // Due 12 000 − 3 000 advance − 4 000 paid = 5 000; the advance itself is fully collected.
        assertEquals(5_000, b.pendingMinor)
        assertEquals(7_000, b.collectedMinor)
        assertEquals(0, b.advancesUnappliedMinor)
        assertEquals(12_000, b.recognisedGrossMinor)
    }

    @Test fun overApplicationOverPaymentAndReplacedTargetsAreRejected() {
        val advance = doc(SettlementKind.ADVANCE, 3_000)
        val provisional = doc(SettlementKind.PROVISIONAL, 10_000)
        val base = EconomyLedger(listOf(advance, provisional), emptyList(), emptyList())
        assertEquals("advance_exceeded", EconomyRules.applicationError(apply(advance, provisional, 3_001), base))
        assertNull(EconomyRules.applicationError(apply(advance, provisional, 3_000), base))
        assertEquals("payment_exceeds_pending", EconomyRules.paymentError(pay(provisional, 10_001), base))
        val withPayment = base.copy(payments = listOf(pay(provisional, 9_000)))
        assertEquals("replacement_below_collected", EconomyRules.replacementError(
            doc(SettlementKind.FINAL, 8_000, replaces = provisional.id), withPayment))
        val final = doc(SettlementKind.FINAL, 11_000, replaces = provisional.id)
        val replaced = withPayment.copy(settlements = withPayment.settlements + final)
        assertEquals("settlement_replaced", EconomyRules.paymentError(pay(provisional, 1), replaced))
        assertEquals("already_replaced", EconomyRules.replacementError(doc(SettlementKind.FINAL, 11_000, replaces = provisional.id), replaced))
        assertEquals("only_provisional_replaced", EconomyRules.replacementError(doc(SettlementKind.FINAL, 11_000, replaces = final.id), replaced))
        val otherCurrency = doc(SettlementKind.PROVISIONAL, 10_000, currency = "USD")
        assertEquals("scope_mismatch", EconomyRules.applicationError(apply(advance, otherCurrency, 1),
            base.copy(settlements = base.settlements + otherCurrency)))
    }

    @Test fun cancelledDocumentsAndPaymentsNeverCount() {
        val cancelled = doc(SettlementKind.FINAL, 9_000, status = SettlementStatus.CANCELLED)
        val live = doc(SettlementKind.FINAL, 5_000)
        val payment = pay(live, 1_000).copy(active = false)
        val b = one(inCycle, EconomyLedger(listOf(cancelled, live), emptyList(), listOf(payment)))
        assertEquals(5_000, b.recognisedGrossMinor)
        assertEquals(0, b.collectedMinor)
        assertEquals(5_000, b.pendingMinor)
    }

    @Test fun workspaceDocumentsAreNeverProratedToFarmsAndCurrenciesNeverMix() {
        val shared = doc(SettlementKind.FINAL, 20_000, farm = null, cycle = null)
        val own = doc(SettlementKind.FINAL, 6_000)
        val otherFarm = doc(SettlementKind.FINAL, 4_000, farm = farmB, cycle = null)
        val dollars = doc(SettlementKind.FINAL, 1_000, currency = "USD")
        val ledger = EconomyLedger(listOf(shared, own, otherFarm, dollars), emptyList(), emptyList())
        assertEquals(6_000, EconomyRules.balances(farm, ledger, emptyMap()).single { it.currency == "EUR" }.recognisedGrossMinor)
        assertEquals(30_000, EconomyRules.balances(all, ledger, emptyMap()).single { it.currency == "EUR" }.recognisedGrossMinor)
        assertEquals(listOf("EUR", "USD"), EconomyRules.balances(inCycle, ledger, emptyMap()).map { it.currency })
    }

    @Test fun marginSubtractsFeesAndCostsButNotWithholding() {
        val final = doc(SettlementKind.FINAL, 50_000, withholding = 1_000, fees = 2_000)
        val b = one(inCycle, EconomyLedger(listOf(final), emptyList(), emptyList()), mapOf("EUR" to 30_000))
        assertEquals(18_000, b.marginMinor)
        assertEquals(1_000, b.withholdingMinor)
        val costsOnly = one(inCycle, EconomyLedger(emptyList(), emptyList(), emptyList()), mapOf("EUR" to 30_000))
        assertEquals(-30_000, costsOnly.marginMinor)
        assertTrue(costsOnly.provisional)
        assertFalse(costsOnly.hasSettlements)
    }
}
