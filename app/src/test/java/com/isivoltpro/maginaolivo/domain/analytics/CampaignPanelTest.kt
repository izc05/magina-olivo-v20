package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #355 Fase 2, bloque A — the panel's figures, checked against the DEMO campaign. */
class CampaignPanelTest {
    private val campaign = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val workspace = UUID.randomUUID()
    private val barranco = UUID.randomUUID()
    private val llanos = UUID.randomUUID()
    private val day = LocalDate.of(2026, 10, 3)

    private fun expense(
        category: ExpenseCategory,
        minor: Long,
        concept: String = "Gasto",
        origin: ExpenseOrigin = ExpenseOrigin.MANUAL,
        status: ExpenseStatus = ExpenseStatus.POSTED,
        currency: String = "EUR",
        inCampaign: UUID? = campaign,
    ) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = day, concept = concept,
        category = category, amountMinor = minor, currency = currency, status = status,
        origin = origin, farmId = farm, campaignId = inCampaign,
    )

    // -------------------------------------------------------------------- kilos

    @Test fun theDemoCampaignSplitsIntoTwoParcelsAndTheMixedPesadas() {
        val split = ParcelKiloSplit.of(
            deliveredGrams = 6_680_000,
            attributed = listOf(
                ParcelKiloSlice(barranco, "El Barranco", 2_710_000),
                ParcelKiloSlice(llanos, "Los Llanos", 1_850_000),
            ),
        )
        assertEquals(3, split.slices.size)
        assertEquals(4_560_000L, split.attributedGrams)
        assertEquals(2_120_000L, split.mixedGrams)
        assertEquals(6_680_000L, split.deliveredGrams)
        // The three slices add up to exactly what the campaign weighed: nothing is lost or invented.
        assertEquals(6_680_000L, split.slices.map { it.grams }.checkedSum())
        val mixed = split.slices.single { it.isMixed }
        assertEquals(ParcelKiloSplit.MIXED_LABEL, mixed.label)
        assertNull(mixed.parcelId)
    }

    @Test fun theMixedPesadasAreNeverSharedOutAmongTheParcels() {
        val split = ParcelKiloSplit.of(
            deliveredGrams = 6_680_000,
            attributed = listOf(
                ParcelKiloSlice(barranco, "El Barranco", 2_710_000),
                ParcelKiloSlice(llanos, "Los Llanos", 1_850_000),
            ),
        )
        // Each Parcel keeps exactly the kilos it already had by name, to the gram.
        assertEquals(2_710_000L, split.slices.single { it.parcelId == barranco }.grams)
        assertEquals(1_850_000L, split.slices.single { it.parcelId == llanos }.grams)
    }

    @Test fun withEveryKiloAttributedThereIsNoMixedSlice() {
        val split = ParcelKiloSplit.of(4_560_000, listOf(ParcelKiloSlice(barranco, "El Barranco", 4_560_000)))
        assertEquals(1, split.slices.size)
        assertNull(split.mixedGrams)
    }

    @Test fun anUnknownOrImpossibleTotalDrawsNothingRatherThanAZero() {
        // The campaign's kilos are unknown (#497).
        assertTrue(ParcelKiloSplit.of(null, listOf(ParcelKiloSlice(barranco, "El Barranco", 10))).isEmpty)
        // The Parcels hold more than the campaign weighed: the records disagree.
        val impossible = ParcelKiloSplit.of(1_000, listOf(ParcelKiloSlice(barranco, "El Barranco", 2_000)))
        assertTrue(impossible.isEmpty)
        assertNull(impossible.mixedGrams)
        // A sum that would wrap around is unknown, never a negative slice.
        val overflow = ParcelKiloSplit.of(
            Long.MAX_VALUE,
            listOf(ParcelKiloSlice(barranco, "A", Long.MAX_VALUE - 10), ParcelKiloSlice(llanos, "B", 1_000)),
        )
        assertTrue(overflow.isEmpty)
    }

    @Test fun aParcelWithNegativeKilosRejectsTheSplitInsteadOfSwellingTheMixedSlice() {
        // Regression (#355): a negative figure used to be dropped silently, and the kilos it should
        // have held reappeared inside «Pesadas mezcladas» — a Parcel's bad record turning into
        // mixed kilos the farmer never weighed that way.
        val split = ParcelKiloSplit.of(
            deliveredGrams = 6_680_000,
            attributed = listOf(
                ParcelKiloSlice(barranco, "El Barranco", 2_710_000),
                ParcelKiloSlice(llanos, "Los Llanos", -1_850_000),
            ),
        )
        assertTrue(split.isEmpty)
        assertNull(split.mixedGrams)
        assertNull(split.attributedGrams)
    }

    // -------------------------------------------------------------------- costs

    @Test fun fuelIsItsOwnSliceOnlyWhenTheCostSaysSo() {
        val splits = CampaignCostSplit.of(
            campaign,
            listOf(
                expense(ExpenseCategory.LABOR, 73_250, concept = "Cuadrilla"),
                expense(ExpenseCategory.MACHINERY, 54_000, concept = "Tractor", origin = ExpenseOrigin.DAY_EQUIPMENT),
                expense(ExpenseCategory.FUEL, 12_000, concept = "Gasoil"),
                expense(ExpenseCategory.TRANSPORT, 7_500, concept = "Transporte a la cooperativa"),
                // An old cost whose description mentions fuel but whose category is OTHER stays «Otros».
                expense(ExpenseCategory.OTHER, 3_000, concept = "Gasoil del remolque"),
            ),
            deliveries = emptyList(),
        )
        val eur = splits.single { it.currency == "EUR" }
        val amounts = eur.slices.toMap()
        assertEquals(12_000L, amounts[ExpenseSlice.FUEL])
        // Transport and the badly described old cost are «Otros»: 7.500 + 3.000.
        assertEquals(10_500L, amounts[ExpenseSlice.OTHER])
        assertEquals(54_000L, amounts[ExpenseSlice.EQUIPMENT])
        assertEquals(73_250L, amounts[ExpenseSlice.LABOUR])
    }

    @Test fun everyPostedCostIsCountedExactlyOnce() {
        val rows = listOf(
            expense(ExpenseCategory.LABOR, 10_000),
            expense(ExpenseCategory.MACHINERY, 20_000),
            expense(ExpenseCategory.FUEL, 30_000),
            expense(ExpenseCategory.REPAIR, 40_000),
        )
        val eur = CampaignCostSplit.of(campaign, rows, emptyList()).single()
        assertEquals(100_000L, eur.totalMinor)
        // Four costs, four slices, and the slices add up to the posted total: nothing is counted
        // twice and nothing is dropped.
        assertEquals(4, eur.slices.size)
        assertEquals(4, eur.slices.map { it.first }.distinct().size)
        assertEquals(eur.slices.sumOf { it.second }, eur.totalMinor)
        assertEquals(listOf(10_000L, 20_000L, 30_000L, 40_000L), eur.slices.map { it.second })
    }

    @Test fun draftsAndCostsOfAnotherCampaignNeverEnterTheDonut() {
        val splits = CampaignCostSplit.of(
            campaign,
            listOf(
                expense(ExpenseCategory.FUEL, 12_000),
                expense(ExpenseCategory.FUEL, 99_000, status = ExpenseStatus.DRAFT),
                expense(ExpenseCategory.FUEL, 88_000, inCampaign = UUID.randomUUID()),
                expense(ExpenseCategory.FUEL, 77_000, inCampaign = null),
            ),
            deliveries = emptyList(),
        )
        assertEquals(12_000L, splits.single().totalMinor)
    }

    @Test fun currenciesAreKeptApartAndNeverAddedTogether() {
        val splits = CampaignCostSplit.of(
            campaign,
            listOf(
                expense(ExpenseCategory.FUEL, 12_000, currency = "EUR"),
                expense(ExpenseCategory.LABOR, 5_000, currency = "GBP"),
            ),
            deliveries = emptyList(),
        )
        assertEquals(2, splits.size)
        assertEquals(12_000L, splits.single { it.currency == "EUR" }.totalMinor)
        assertEquals(5_000L, splits.single { it.currency == "GBP" }.totalMinor)
    }

    @Test fun aCategoryWithNothingPostedIsAbsent() {
        val eur = CampaignCostSplit.of(campaign, listOf(expense(ExpenseCategory.FUEL, 12_000)), emptyList()).single()
        // No empty «Jornales 0 €» slice: a category with nothing in it simply is not drawn.
        assertEquals(listOf(ExpenseSlice.FUEL), eur.slices.map { it.first })
    }

    @Test fun aCurrencyWithAnInvalidAmountDrawsNoDonutAtAll() {
        // Regression (#355): one unusable category used to leave the others drawn, so the donut
        // showed parts that did not add up to the total. A partial donut is a wrong donut.
        val overflowing = CampaignCostSplit.of(
            campaign,
            listOf(
                expense(ExpenseCategory.LABOR, 73_250),
                expense(ExpenseCategory.FUEL, Long.MAX_VALUE - 10),
                expense(ExpenseCategory.FUEL, 1_000),
            ),
            deliveries = emptyList(),
        )
        assertTrue(overflowing.isEmpty())

        val negative = CampaignCostSplit.of(
            campaign,
            listOf(expense(ExpenseCategory.LABOR, 73_250), expense(ExpenseCategory.FUEL, -5_000)),
            deliveries = emptyList(),
        )
        assertTrue(negative.isEmpty())

        // A second currency that is sound is still drawn: one bad currency does not hide the rest.
        val mixed = CampaignCostSplit.of(
            campaign,
            listOf(
                expense(ExpenseCategory.FUEL, -5_000, currency = "EUR"),
                expense(ExpenseCategory.LABOR, 5_000, currency = "GBP"),
            ),
            deliveries = emptyList(),
        )
        assertEquals(listOf("GBP"), mixed.map { it.currency })
    }

    @Test fun aCampaignWithNoPostedCostsDrawsNothing() {
        assertTrue(CampaignCostSplit.of(campaign, emptyList(), emptyList()).isEmpty())
        assertTrue(
            CampaignCostSplit.of(
                campaign,
                listOf(expense(ExpenseCategory.FUEL, 12_000, status = ExpenseStatus.DRAFT)),
                emptyList(),
            ).isEmpty(),
        )
    }
}
