package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.feature.harvests.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** CR-012 synthetic fixtures, rendered on the API 35 emulator; excluded from production. */
class LabourPaymentsUiTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 12)
    private val campaign = UiPolishFixtures.campaign
    private val worker = Worker(UUID.randomUUID(), "Juan García López")
    private val day = UUID.randomUUID()
    private val rates = RecollectionRates(fullDayMinor = 6000, hourlyMinor = 1000)
    private val entry = LabourEntry(UUID.randomUUID(), day, worker.id, worker.name, 1, LabourUnit.FULL_DAY, null, 1, LabourRateSnapshot(24000, "EUR", date, LabourRateBasis.DAY))
    private val ledger = Expense(UUID.randomUUID(), campaign.workspaceId, date, "Jornales", ExpenseCategory.LABOR, 24000, "EUR", ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = campaign.id, harvestId = day)
    private fun payment(amount: Long) = LabourPayment(UUID.randomUUID(), worker.id, campaign.id, date, amount, "EUR")
    private fun state(): LabourPaymentsUiState {
        val days = (0..3).map { n -> com.isivoltpro.maginaolivo.domain.harvest.Harvest(UUID.randomUUID(), campaign.workspaceId, campaign.farmId, campaign.id, date.minusDays((12 - n).toLong()), 0, emptyList(), null, null, null, null, 1) }
        val entries = days.map { day -> entry.copy(id = UUID.randomUUID(), harvestId = day.id, appliedRate = entry.appliedRate!!.copy(unitPriceMinor = 6000, priceDate = day.harvestDate)) }
        val ledgers = days.map { day -> ledger.copy(id = UUID.randomUUID(), harvestId = day.id, expenseDate = day.harvestDate, amountMinor = 6000) }
        val payments = listOf(payment(10000).copy(paymentDate = LocalDate.of(2026, 10, 5)), payment(8000))
        return LabourPaymentsUiState(isLoading = false, campaign = campaign.copy(status = CampaignStatus.CLOSED), entries = entries, days = days, payments = payments, accounts = labourAccounts(campaign.id, entries, ledgers, payments))
    }

    /** #442: a name already in use is never merged silently; a namesake is a new person, chosen by id. */
    @Test fun aNamesakeIsAskedAboutAndCanBeAnotherPerson() {
        val people = androidx.compose.runtime.mutableStateOf(listOf(worker))
        val added = mutableListOf<String>()
        var saved: CrewDraft? = null
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) {
            LabourSheet(people.value, emptySet(), false, null, day, campaign.id, date, null, { saved = it }, { added += it }, {})
        } } }
        rule.onNodeWithTag("labour-new-person").performScrollTo().performClick()
        rule.onNodeWithTag("labour-new-name").performScrollTo().performTextInput("juan garcía lópez")
        rule.onNodeWithTag("labour-add-worker").performScrollTo().performClick()
        rule.onNodeWithTag("labour-same-name").assertExists()
        rule.runOnIdle { assertTrue(added.isEmpty()) }
        rule.onNodeWithTag("labour-create-namesake").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("juan garcía lópez"), added) }
        val namesake = Worker(UUID.randomUUID(), "Juan García López")
        people.value = listOf(worker, namesake)
        rule.onNodeWithText("Juan García López · 1").assertExists()
        rule.onNodeWithText("Juan García López · 2").assertExists()
        rule.onNodeWithTag("labour-selected-count").assertTextContains("1 seleccionada")
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextInput("60")
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(namesake.id), saved!!.workerIds) }
    }

    @Test fun usingTheExistingPersonAddsNobody() {
        val added = mutableListOf<String>()
        var saved: CrewDraft? = null
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) {
            LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, null, { saved = it }, { added += it }, {})
        } } }
        rule.onNodeWithTag("labour-new-person").performScrollTo().performClick()
        rule.onNodeWithTag("labour-new-name").performScrollTo().performTextInput("Juan García López")
        rule.onNodeWithTag("labour-add-worker").performScrollTo().performClick()
        rule.onNodeWithTag("labour-use-existing").performScrollTo().performClick()
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextInput("60")
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(added.isEmpty()); assertEquals(listOf(worker.id), saved!!.workerIds) }
    }

    @Test fun completeHoursPriceAndSinglePersonAreRequiredWithNoAnonymousOrHalfDay() {
        var saved: CrewDraft? = null
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, null, { saved = it }, {}, {}) } } }
        rule.onNodeWithTag("labour-save").assertIsNotEnabled()
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-save").assertIsNotEnabled()
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextInput("60")
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("labour-mode-count").assertDoesNotExist()
        rule.onNodeWithTag("labour-unit-HALF_DAY").assertDoesNotExist()
        rule.onNodeWithTag("labour-unit-HOURS").performScrollTo().performClick()
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("labour-hours").performScrollTo().performTextInput("3")
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextInput("10")
        rule.onNodeWithTag("labour-generated").assertTextContains("30,00", substring = true)
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(worker.id), saved!!.workerIds); assertEquals(180, saved!!.minutes); assertEquals(1000L, saved!!.appliedRate!!.unitPriceMinor); assertTrue(saved!!.initialPayments.isEmpty()) }
    }

    /** #449: a person whose price is not known yet is saved without one, never 0 € and never paid. */
    @Test fun aPersonCanBeSavedWithThePriceNotKnownYet() {
        var saved: CrewDraft? = null
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, null, { saved = it }, {}, {}) } } }
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("labour-price-unknown").performScrollTo().performClick()
        rule.onNodeWithTag("labour-price-unknown-note").assertExists()
        rule.onNodeWithTag("labour-rate").assertDoesNotExist()
        rule.onNodeWithTag("labour-payment-FULL").assertDoesNotExist()
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertTrue(saved!!.priceUnknown)
            assertNull(saved!!.appliedRate)
            assertTrue(saved!!.initialPayments.isEmpty())
        }
    }

    @Test fun partialInitialPaymentSurvivesRotationWithStableIdAndInvalidAmountCannotSave() {
        val restored = StateRestorationTester(rule)
        val saved = mutableListOf<CrewDraft>()
        restored.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, rates, { saved += it }, {}, {}) } } }
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-payment-PARTIAL").performScrollTo().performClick()
        rule.onNodeWithTag("labour-payment-amount").performScrollTo().performTextInput("80")
        rule.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("labour-payment-amount").performScrollTo().performTextReplacement("20")
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        restored.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("labour-payment-amount").assertTextContains("20")
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(2000L, saved[0].initialPayments.single().amountMinor); assertEquals(saved[0].initialPayments.single(), saved[1].initialPayments.single()) }
    }

    @Test fun fullInitialPaymentUsesAppliedRate() {
        var saved: CrewDraft? = null
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, rates, { saved = it }, {}, {}) } } }
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        rule.onNodeWithTag("labour-payment-FULL").performScrollTo().performClick()
        rule.onNodeWithTag("labour-rate").performScrollTo().performTextReplacement("65")
        rule.onNodeWithTag("labour-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(6500L, saved!!.initialPayments.single().amountMinor) }
    }

    @Test fun personShows24018060WithSeparateWorkPaymentsAndClosedSettlement() {
        val state = state()
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPaymentsScreen(state, worker.id, date) } } }
        rule.onNodeWithTag("person-generated").assertTextContains("240,00", substring = true)
        rule.onNodeWithTag("person-paid-pending").assertTextContains("180,00", substring = true).assertTextContains("60,00", substring = true)
        rule.onNodeWithTag("payment-state-PARTIAL").assertExists()
        rule.onNodeWithContentDescription("Pago parcial", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("labour-person-work").assertExists()
        rule.onNodeWithTag("labour-person-payments").performScrollTo().assertExists()
        rule.onAllNodesWithTag("labour-person-payment-row").assertCountEquals(2)
        rule.onNodeWithTag("person-register-payment").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("payment-person").assertTextContains(worker.name)
        rule.onNodeWithTag("payment-amount").performTextInput("80")
        rule.onNodeWithTag("payment-save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("El importe supera los 60,00 € pendientes").assertExists()
        rule.onNodeWithTag("payment-all").performScrollTo().performClick()
        rule.onNodeWithTag("payment-save").performScrollTo().assertIsEnabled()
    }

    @Test fun paymentDateNoteAndUuidSurviveRotationAndStatesHaveIconText() {
        val restored = StateRestorationTester(rule)
        val state = state()
        val payments = mutableListOf<LabourPayment>()
        restored.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPaymentSheet(worker.name, state.accounts.single().balances.single(), date, false, null, { payments += it }, {}) } } }
        rule.onNodeWithTag("payment-all").performClick()
        rule.onNodeWithTag("payment-date").performTextReplacement("12/10/2026")
        rule.onNodeWithTag("payment-note").performTextInput("En efectivo")
        rule.onNodeWithTag("payment-save").performScrollTo().performClick()
        restored.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("payment-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(payments[0], payments[1]); assertEquals(LocalDate.of(2026, 10, 12), payments[0].paymentDate); assertEquals("En efectivo", payments[0].note) }
    }

    @Test fun futurePaymentDateShowsInlineErrorAndCannotSave() {
        val state = state()
        rule.setContent { MaginaOlivoTheme { LabourPaymentSheet(worker.name, state.accounts.single().balances.single(), date, false, null, {}, {}) } }
        rule.onNodeWithTag("payment-all").performClick()
        rule.onNodeWithTag("payment-date").performTextReplacement("13/10/2026")
        rule.onNodeWithText("La fecha del pago no puede ser futura").assertExists()
        rule.onNodeWithTag("payment-save").performScrollTo().assertIsNotEnabled()
    }

    @Test fun correctingPaymentRequiresAnExplicitMovementAndConfirmation() {
        var removed: UUID? = null
        val state = state()
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPaymentsScreen(state, worker.id, date, onRemove = { removed = it }) } } }
        rule.onAllNodesWithTag("payment-correct")[0].performScrollTo().performClick()
        rule.runOnIdle { assertNull(removed) }
        rule.onNodeWithTag("payment-correction-confirmation").performScrollTo().assertExists()
        rule.onNodeWithText("Retirar pago").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(state.payments[1].id, removed) }
    }

    @Test fun legacyPriceRequiresExplicitConfirmationAndNoCurrentRateBackfill() {
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPriceSheet(entry.copy(appliedRate = null), date, "EUR", false, null, {}, {}) } } }
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("60")
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled()
    }

    @Test fun pricedDayChangedToHoursRequiresNewHourlyPriceBeforeSaving() {
        val restored = StateRestorationTester(rule)
        var saved: LabourChange? = null
        val pricedDay = entry.copy(appliedRate = entry.appliedRate!!.copy(unitPriceMinor = 6000))
        restored.setContent { MaginaOlivoTheme { LabourPriceSheet(pricedDay, date, "EUR", false, null, { saved = it }, {}) } }
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled()
        rule.onNodeWithText("Horas").performClick()
        assertEquals("", rule.onNodeWithTag("labour-edit-rate").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("labour-edit-hours").performTextInput("3")
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        restored.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        assertEquals("", rule.onNodeWithTag("labour-edit-rate").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithText("Tarifa por hora (EUR)", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("10")
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(LabourUnit.HOURS, saved!!.unit)
            assertEquals(180, saved!!.minutes)
            assertEquals(LabourRateBasis.HOUR, saved!!.appliedRate!!.basis)
            assertEquals(1000L, saved!!.appliedRate!!.unitPriceMinor)
            assertEquals(3000L, LabourPricing.amountMinor(pricedDay.copy(unit = saved!!.unit, minutes = saved!!.minutes, appliedRate = saved!!.appliedRate)))
        }
    }

    @Test fun pricedHoursChangedToDayAlsoRequiresNewDayPrice() {
        var saved: LabourChange? = null
        val pricedHours = entry.copy(unit = LabourUnit.HOURS, minutes = 180, appliedRate = entry.appliedRate!!.copy(unitPriceMinor = 1000, basis = LabourRateBasis.HOUR))
        rule.setContent { MaginaOlivoTheme { LabourPriceSheet(pricedHours, date, "EUR", false, null, { saved = it }, {}) } }
        rule.onNodeWithText("Jornada completa").performClick()
        rule.onNodeWithTag("labour-edit-save").assertIsNotEnabled()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("60")
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(LabourRateBasis.DAY, saved!!.appliedRate!!.basis)
            assertEquals(6000L, saved!!.appliedRate!!.unitPriceMinor)
        }
    }

    @Test fun legacyHalfDayAndFullDayRetainAgreedDayPriceAcrossRestore() {
        val restored = StateRestorationTester(rule)
        val halfDay = entry.copy(unit = LabourUnit.HALF_DAY)
        restored.setContent { MaginaOlivoTheme { LabourPriceSheet(halfDay, date, "EUR", false, null, {}, {}) } }
        rule.onNodeWithText("Jornada completa").performClick()
        rule.onNodeWithTag("labour-edit-rate").assertTextContains("240")
        rule.onNodeWithText("Precio por jornada (EUR)", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled()
        restored.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("labour-edit-rate").assertTextContains("240")
        rule.onNodeWithTag("labour-edit-save").assertIsEnabled()
    }

    @Test fun screenshotForm() {
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourSheet(listOf(worker), emptySet(), false, null, day, campaign.id, date, rates, {}, {}, {}) } } }
        rule.onAllNodesWithTag("labour-worker")[0].performClick()
        capture("cr012-form")
    }
    @Test fun pendingAndPaidStatesHaveIconsAndSavingDisablesThePaymentButton() {
        rule.setContent { MaginaOlivoTheme { androidx.compose.foundation.layout.Column { LabourPaymentStatus(LabourPaymentState.PENDING); LabourPaymentStatus(LabourPaymentState.PAID); LabourPaymentSheet(worker.name, state().accounts.single().balances.single(), date, true, null, {}, {}) } } }
        rule.onNodeWithTag("payment-state-PENDING").assertExists()
        rule.onNodeWithTag("payment-state-PAID").assertExists()
        rule.onNodeWithContentDescription("Pendiente", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Pagado", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("payment-save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("payment-all").performScrollTo().assertIsNotEnabled()
    }
    @Test fun screenshotPerson() {
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPaymentsScreen(state(), worker.id, date) } } }
        rule.onNodeWithTag("person-generated").assertIsDisplayed()
        capture("cr012-person")
        rule.onAllNodesWithTag("labour-person-payment-row")[1].performScrollTo().assertIsDisplayed()
        capture("cr012-person-history")
    }
    @Test fun screenshotPayment() {
        rule.setContent { MaginaOlivoTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding(), color = com.isivoltpro.maginaolivo.ui.theme.MoCream) { LabourPaymentSheet(worker.name, state().accounts.single().balances.single(), date, false, null, {}, {}) } } }
        rule.onNodeWithTag("payment-all").performClick()
        capture("cr012-payment")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        var screenshot: Bitmap? = null
        repeat(5) {
            if (screenshot == null) {
                android.os.SystemClock.sleep(750)
                val candidate = instrumentation.uiAutomation.takeScreenshot()
                val contentColors = (2..8).flatMap { y -> (1..9).map { x -> candidate.getPixel(candidate.width * x / 10, candidate.height * y / 10) } }.toSet()
                if (contentColors.size > 1) screenshot = candidate else candidate.recycle()
            }
        }
        checkNotNull(screenshot) { "The activity has not drawn its content" }
        file.outputStream().use { screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot!!.recycle()
    }
}
