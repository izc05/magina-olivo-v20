package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import com.isivoltpro.maginaolivo.feature.expenses.DocumentReviewScreen
import com.isivoltpro.maginaolivo.feature.expenses.DocumentReviewUiState
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseEditor
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseFormErrors
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesScreen
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesUiState
import com.isivoltpro.maginaolivo.feature.expenses.RelationOptions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Owner decision 2026-10-03 (CR-012 Slice 4) and P1: «Gasto de recogida · Campaña …» is
 * preselected and visible from Cuaderno → Gasto, «Gasto general de finca/parcela» is an
 * explicit alternative, and a ticket keeps its Farm/Campaign through OCR review.
 */
class RecollectionChoiceUiTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 11, 20)
    private val workspace = UUID.randomUUID()
    private val farm = farm("La Solana")
    private val otherFarm = farm("El Chaparral")
    private val running = campaign(farm.id, "2026/27", CampaignStatus.HARVEST)
    private val options = RelationOptions(farms = listOf(farm, otherFarm), campaigns = listOf(running))

    @Test fun cuadernoGastoStartsOnTheRunningRecolection() {
        val saved = cuadernoExpense(options) {}
        rule.runOnIdle { assertEquals(running.id, saved()?.campaignId); assertEquals(farm.id, saved()?.farmId) }
    }

    @Test fun farmerCanSwitchToAGeneralFarmExpense() {
        val saved = cuadernoExpense(options) {
            rule.onNodeWithTag("expense-kind-general").performClick().assertIsSelected()
            rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
        }
        rule.runOnIdle { assertNotNull(saved()); assertNull(saved()?.campaignId); assertEquals(farm.id, saved()?.farmId) }
    }

    @Test fun withoutARunningRecolectionNoChoiceIsShownAndTheExpenseIsGeneral() {
        val closed = campaign(farm.id, "2025/26", CampaignStatus.CLOSED)
        val saved = cuadernoExpense(options.copy(campaigns = listOf(closed)), expectChoice = false) {}
        rule.runOnIdle { assertNotNull(saved()); assertNull(saved()?.campaignId) }
    }

    @Test fun editingAGeneralExpenseNeverReassignsIt() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "25", "Poda", farmId = farm.id)) { saved = it }
        rule.onNodeWithTag("expense-kind-general").assertIsSelected()
        rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertNotNull(saved); assertNull(saved?.campaignId) }
    }

    @Test fun changingTheFarmClearsTheCampaign() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "40", "Gasoil", farmId = farm.id, campaignId = running.id), preselect = true) { saved = it }
        rule.onNodeWithTag("expense-kind-recollection").assertIsSelected()
        rule.onNodeWithTag("expense-farm").performScrollTo().performClick()
        rule.onNodeWithTag("choice-${otherFarm.id}").performClick()
        rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(otherFarm.id, saved?.farmId); assertNull(saved?.campaignId) }
    }

    @Test fun ticketFromRecollectionExpensesKeepsFarmAndCampaignThroughReview() {
        val reviewed = review(contextCampaignId = running.id, preselect = false)
        rule.runOnIdle { assertEquals(farm.id, reviewed()?.farmId); assertEquals(running.id, reviewed()?.campaignId) }
    }

    @Test fun ticketFromCuadernoGastoStartsOnTheRunningRecolection() {
        val reviewed = review(contextCampaignId = null, preselect = true)
        rule.runOnIdle { assertEquals(farm.id, reviewed()?.farmId); assertEquals(running.id, reviewed()?.campaignId) }
    }

    private fun cuadernoExpense(options: RelationOptions, expectChoice: Boolean = true, choose: () -> Unit): () -> ExpenseForm? {
        var saved: ExpenseForm? = null
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options), date, { saved = it }, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id,
                )
            }
        }
        rule.onNodeWithTag("add-expense").performClick()
        if (expectChoice) {
            rule.onNodeWithText("Gasto de recogida · Campaña 2026/27").assertExists()
            rule.onNodeWithTag("expense-kind-recollection").assertIsSelected()
            rule.onNodeWithTag("expense-kind-general").assertIsNotSelected()
        } else {
            rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        }
        choose()
        rule.onNodeWithTag("expense-concept").performTextInput("Gasoil")
        rule.onNodeWithTag("expense-amount").performTextInput("40")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        return { saved }
    }

    /** #375: opened on a Farm already chosen (Cuaderno, campaign), the Farm is context only. */
    @Test fun aFarmFromContextIsShownNotAsked() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "12", "Gasoil", farmId = farm.id), farmLocked = true) { saved = it }
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
        rule.onNodeWithTag("expense-farm-context").performScrollTo().assertTextContains(farm.name)
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(farm.id, saved?.farmId) }
    }

    /** Codex #405: before the Farms load, a locked Farm is still not a selector. */
    @Test fun aLockedFarmIsNotSelectableWhileLoading() {
        editor(ExpenseForm(date.toString(), "12", "Gasoil", farmId = farm.id), farmLocked = true, loaded = options.copy(farms = emptyList())) {}
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
        rule.onNodeWithTag("expense-farm-context").performScrollTo().assertTextContains("Cargando la finca…")
    }

    private fun editor(initial: ExpenseForm, preselect: Boolean = false, farmLocked: Boolean = false, loaded: RelationOptions = options, onSave: (ExpenseForm) -> Unit) {
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Editar gasto", initial = initial, options = loaded, errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar cambios", onFarmSelected = {}, onSave = onSave, onCancel = {},
                    preselectRecollection = preselect, farmLocked = farmLocked,
                )
            }
        }
    }

    private fun review(contextCampaignId: UUID?, preselect: Boolean): () -> ExpenseForm? {
        var reviewed: ExpenseForm? = null
        val extraction = DocumentExtraction(
            id = UUID.randomUUID(), attachmentId = UUID.randomUUID(), documentType = DocumentType.PURCHASE_INVOICE,
            status = OcrStatus.EXTRACTED, engine = "test", rawText = "…",
            proposal = PurchaseProposal(supplierName = "Gasóleos Mágina", invoiceDate = date, totalMinor = 7_260),
            expenseId = null, createdAt = Instant.EPOCH, reviewedAt = null,
        )
        rule.setContent {
            MaginaOlivoTheme {
                DocumentReviewScreen(
                    state = DocumentReviewUiState(isLoading = false, extraction = extraction, options = options),
                    onRead = {}, onCreateDraft = { reviewed = it }, onKeep = {}, onDiscard = {}, onFarmSelected = {},
                    contextFarmId = farm.id, contextCampaignId = contextCampaignId, preselectRecollection = preselect,
                )
            }
        }
        rule.onNodeWithTag("expense-kind-recollection").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        return { reviewed }
    }

    private fun farm(name: String) = Farm(
        UUID.randomUUID(), workspace, name, null, null, null, null, null, 0L, null, null, null, 1L,
    )

    private fun campaign(farmId: UUID, name: String, status: CampaignStatus) =
        Campaign(UUID.randomUUID(), workspace, farmId, name, date.minusMonths(1), null, status, null, emptyList(), 1)
}
