package com.isivoltpro.maginaolivo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.feature.harvests.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class LabourCurrencyAndOverflowUiTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 12)
    private val worker = Worker(UUID.randomUUID(), "Juan García López")
    private val harvest = Harvest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), date, 0, emptyList(), null, null, null, null, 1)
    private val legacy = LabourEntry(UUID.randomUUID(), harvest.id, worker.id, worker.name, 1, LabourUnit.FULL_DAY, null, 1)
    private val rates = RecollectionRates(fullDayMinor = 6000, hourlyMinor = 1000, currency = "EUR")
    private fun ledger(currency: String) = Expense(UUID.randomUUID(), harvest.workspaceId, date, "Jornales", ExpenseCategory.LABOR, 1000, currency, ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = harvest.campaignId, harvestId = harvest.id)
    private fun showDay(entries: List<LabourEntry>, costs: List<Expense>, actions: LabourActions = LabourActions()) {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = harvest, labour = entries, workers = listOf(worker), costs = costs, rates = rates), {}, {}, labourActions = actions) } }
    }

    @Test fun legacyConfirmationKeepsJpyLedgerInsteadOfCurrentEurRates() {
        var change: LabourChange? = null
        showDay(listOf(legacy), listOf(ledger("JPY")), LabourActions(onUpdate = { _, value -> change = value }))
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-labour-edit").performScrollTo().performClick()
        rule.onNodeWithText("Precio del jornal (JPY)").assertExists()
        assertEquals("", rule.onNodeWithTag("labour-edit-rate").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("1000")
        assertEquals("1000", rule.onNodeWithTag("labour-edit-rate").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        // Verify historical-currency confirmation through the enabled form action;
        // physical post-input modal reachability remains a separate device check.
        rule.onNodeWithTag("labour-edit-save").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        rule.runOnIdle {
            assertNotNull("The enabled legacy confirmation must emit its draft", change)
            assertEquals("JPY", change?.appliedRate?.currency)
            assertEquals(1000L, change?.appliedRate?.unitPriceMinor)
            assertEquals(date, change?.appliedRate?.priceDate)
            assertEquals(LabourRateBasis.DAY, change?.appliedRate?.basis)
        }
    }

    @Test fun pendingCostReadBlocksLegacyConfirmationAndNewAttendance() {
        unavailableCostReadBlocksLabourForms(loaded = false, failed = false, expected = "Cargando")
    }

    @Test fun failedCostReadBlocksLegacyConfirmationAndNewAttendance() {
        unavailableCostReadBlocksLabourForms(loaded = true, failed = true, expected = "No pudimos leer los gastos")
    }

    private fun unavailableCostReadBlocksLabourForms(loaded: Boolean, failed: Boolean, expected: String) {
        var changes = 0
        var creates = 0
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false,
            harvest = harvest, labour = listOf(legacy), workers = listOf(worker), costs = emptyList(),
            costsLoaded = loaded, costsReadFailed = failed, rates = rates), {}, {},
            labourActions = LabourActions(onUpdate = { _, _ -> changes++ }, onSaveCrew = { creates++ })) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-labour-edit").performScrollTo().performClick()
        rule.onNodeWithTag("labour-currency-error").assertTextContains(expected, substring = true)
        rule.onNodeWithTag("labour-edit-rate").assertIsNotEnabled()
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-register-labour").performScrollTo().performClick()
        rule.onNodeWithTag("labour-currency-error").assertTextContains(expected, substring = true)
        rule.onNodeWithTag("labour-save").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, changes); assertEquals(0, creates) }
    }

    @Test fun successfulEmptyCostReadAllowsUsualCurrencyConfirmation() {
        var change: LabourChange? = null
        showDay(listOf(legacy), emptyList(), LabourActions(onUpdate = { _, value -> change = value }))
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-labour-edit").performScrollTo().performClick()
        rule.onNodeWithText("Precio del jornal (EUR)").assertExists()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("60")
        rule.onNodeWithTag("labour-edit-save").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        rule.runOnIdle { assertEquals("EUR", change?.appliedRate?.currency); assertEquals(6_000L, change?.appliedRate?.unitPriceMinor) }
    }

    @Test fun partlyConfirmedDayKeepsTheOtherPersonsJpySnapshotWithoutLedger() {
        val confirmed = legacy.copy(id = UUID.randomUUID(), workerId = UUID.randomUUID(), workerName = "Ana López", appliedRate = LabourRateSnapshot(1000, "JPY", date, LabourRateBasis.DAY))
        showDay(listOf(legacy, confirmed), emptyList())
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onAllNodesWithTag("jornada-labour-edit")[0].performScrollTo().performClick()
        rule.onNodeWithText("Precio del jornal (JPY)").assertExists()
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
    }

    @Test fun ambiguousHistoricalCurrenciesExplainWhyConfirmationCannotSave() {
        showDay(listOf(legacy), listOf(ledger("JPY"), ledger("EUR")))
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-labour-edit").performScrollTo().performClick()
        rule.onNodeWithTag("labour-currency-error").assertIsDisplayed()
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
    }

    @Test fun newAttendanceOnJpyDayDoesNotPrefillOrSaveCurrentEurRate() {
        var draft: CrewDraft? = null
        showDay(emptyList(), listOf(ledger("JPY")), LabourActions(onSaveCrew = { draft = it }))
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-register-labour").performScrollTo().performClick()
        rule.onNodeWithText("Precio del jornal (JPY)").assertExists()
        rule.onNodeWithTag("labour-rate").assertTextContains("Confirma un precio válido", substring = true)
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-selected-count").assertTextContains("1 seleccionada")
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextInput("1000")
        assertEquals("1000", rule.onNodeWithTag("labour-rate").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        // Verify the enabled form action directly; touch routing is covered by the other save tests.
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        rule.runOnIdle { assertEquals("JPY", draft?.appliedRate?.currency); assertEquals(1000L, draft?.appliedRate?.unitPriceMinor) }
    }

    @Test fun overflowingHourlyCostExplainsReducingPriceOrDurationAndDisablesSave() {
        rule.setContent { MaginaOlivoTheme { LabourSheet(listOf(worker), emptySet(), false, null, harvest.id, harvest.campaignId!!, date, rates, {}, {}, {}) } }
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-unit-HOURS").performClick()
        rule.onNodeWithTag("labour-hours").performTextInput("24")
        rule.onNodeWithTag("labour-rate").performTextReplacement("92233720368547758,07")
        rule.onNodeWithTag("labour-calculation-error").performScrollTo().assertIsDisplayed().assertTextContains("Reduce el precio o la duración", substring = true)
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
    }
}
