package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.labour.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecollectionCostSummaryTest {
    private val campaign = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val workspace = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)

    @Test fun confirmed530EurosOver3200WeighedKgPreservesExactPrecision() {
        val expenses = listOf(
            expense(30_000, ExpenseCategory.LABOR), expense(18_000, ExpenseCategory.MACHINERY), expense(5_000, ExpenseCategory.FUEL),
        )
        val summary = RecollectionCostSummary.of(campaign, expenses, listOf(delivery(3_200_000)), "EUR")
        assertEquals(53_000L, summary.totalMinor)
        assertEquals(0, BigDecimal("0.165625").compareTo(summary.costPerKg))
    }

    @Test fun draftsOutsideCampaignAndOtherCurrenciesDoNotEnterCampaignCost() {
        val expenses = listOf(
            expense(53_000),
            expense(90_000).copy(status = ExpenseStatus.DRAFT),
            expense(10_000).copy(campaignId = null, concept = "Poda fuera de recogida"),
            expense(20_000).copy(campaignId = UUID.randomUUID()),
            expense(30_000).copy(currency = "USD"),
        )
        val summary = RecollectionCostSummary.of(campaign, expenses, listOf(
            delivery(3_200_000), delivery(1_000_000).copy(campaignId = UUID.randomUUID()),
        ), "EUR")
        assertEquals(53_000L, summary.totalMinor)
        assertEquals(3_200_000L, summary.weighedGrams)
        assertEquals(0, BigDecimal("0.165625").compareTo(summary.costPerKg))
    }

    @Test fun noWeighingsOrNoConfirmedCostsMeansUnknownCostPerKg() {
        assertNull(RecollectionCostSummary.of(campaign, listOf(expense(100)), emptyList(), "EUR").costPerKg)
        assertNull(RecollectionCostSummary.of(campaign, listOf(expense(100)), listOf(delivery(0)), "EUR").costPerKg)
        assertNull(RecollectionCostSummary.of(campaign, emptyList(), listOf(delivery(100_000)), "EUR").costPerKg)
        assertNull(RecollectionCostSummary.of(campaign, listOf(expense(100).copy(status = ExpenseStatus.DRAFT)), listOf(delivery(100_000)), "EUR").costPerKg)
        assertEquals(0, BigDecimal.ZERO.compareTo(
            RecollectionCostSummary.of(campaign, listOf(expense(0)), listOf(delivery(100_000)), "EUR").costPerKg,
        ))
    }

    @Test fun legacyAnonymousExpenseCountsOnceWithoutInventingIndividualDebt() {
        val anonymousCost = expense(30_000, ExpenseCategory.LABOR)
        val summary = RecollectionCostSummary.of(campaign, listOf(anonymousCost), listOf(delivery(1_000_000)), "EUR")
        assertEquals(30_000L, summary.totalMinor)
        assertEquals(0, BigDecimal("0.3").compareTo(summary.costPerKg))
    }

    @Test fun repeatedCanonicalExpenseOrWeighingIdsCannotDoubleCount() {
        val expense = expense(100)
        val weighing = delivery(100_000)
        assertThrows(IllegalArgumentException::class.java) {
            RecollectionCostSummary.of(campaign, listOf(expense, expense), listOf(weighing), "EUR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecollectionCostSummary.of(campaign, listOf(expense), listOf(weighing, weighing), "EUR")
        }
    }

    @Test fun negativeCostsOrKgAreRefusedInsteadOfCreatingMisleadingRatios() {
        assertThrows(IllegalArgumentException::class.java) {
            RecollectionCostSummary.of(campaign, listOf(expense(-1)), listOf(delivery(100_000)), "EUR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecollectionCostSummary.of(campaign, listOf(expense(100)), listOf(delivery(-1)), "EUR")
        }
    }

    @Test fun payingAfterClosureLeavesExpenseAndCostPerKgUnchanged() {
        val worker = UUID.randomUUID()
        val day = UUID.randomUUID()
        val line = LabourEntry(
            UUID.randomUUID(), day, worker, "Juan", 1, LabourUnit.FULL_DAY, null, 1,
            LabourRateSnapshot(24_000, "EUR", date, LabourRateBasis.DAY),
        )
        val ledger = expense(24_000, ExpenseCategory.LABOR).copy(origin = ExpenseOrigin.DAY_LABOUR, harvestId = day)
        val costs = LabourLedgerAllocation.of(ledger, listOf(line))!!
        val balance = LabourSettlement.of(worker, campaign, "EUR", costs, emptyList())
        val payment = LabourPayment(UUID.randomUUID(), worker, campaign, date.plusMonths(1), 18_000, "EUR")
        assertNull(LabourPaymentRules.validate(payment, balance, CampaignStatus.CLOSED))
        val after = LabourSettlement.of(worker, campaign, "EUR", costs, listOf(payment))
        assertEquals(24_000L, after.generatedMinor)
        assertEquals(18_000L, after.paidMinor)
        assertEquals(6_000L, after.pendingMinor)
        val summary = RecollectionCostSummary.of(campaign, listOf(ledger), listOf(delivery(1_000_000)), "EUR")
        assertEquals(24_000L, summary.totalMinor)
        assertEquals(0, BigDecimal("0.24").compareTo(summary.costPerKg))
        assertEquals(24_000L, ledger.amountMinor)
        assertEquals(ExpenseStatus.POSTED, ledger.status)
    }

    @Test fun ratioDoesNotOverflowOnLargeTotalsAndRespectsCurrencyUnits() {
        val summary = RecollectionCostSummary.of(campaign, listOf(expense(Long.MAX_VALUE)), listOf(delivery(1_000)), "EUR")
        assertEquals(0, BigDecimal("92233720368547758.07").compareTo(summary.costPerKg))
        val yen = RecollectionCostSummary.of(campaign, listOf(expense(530).copy(currency = "JPY")), listOf(delivery(3_200_000)), "JPY")
        assertEquals(0, BigDecimal("0.165625").compareTo(yen.costPerKg))
        assertThrows(ArithmeticException::class.java) {
            RecollectionCostSummary.of(campaign, listOf(expense(Long.MAX_VALUE), expense(1)), listOf(delivery(1_000)), "EUR")
        }
    }

    @Test fun aRepeatingRatioKeepsMoreThanDisplayPrecision() {
        val ratio = RecollectionCostSummary.of(campaign, listOf(expense(100)), listOf(delivery(3_000)), "EUR").costPerKg!!
        assertTrue(ratio.precision() >= 30)
        assertEquals(0, BigDecimal("0.33").compareTo(ratio.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    private fun expense(amount: Long, category: ExpenseCategory = ExpenseCategory.OTHER) = Expense(
        UUID.randomUUID(), workspace, date, "Recogida", category, amount, "EUR",
        ExpenseStatus.POSTED, ExpenseOrigin.MANUAL, farmId = farm, campaignId = campaign,
    )

    private fun delivery(grams: Long) = Delivery(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign,
        deliveryDate = date, destinationOrganizationId = null, destinationName = "Cooperativa",
        netGrams = grams, grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = null,
        source = DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
    )
}
