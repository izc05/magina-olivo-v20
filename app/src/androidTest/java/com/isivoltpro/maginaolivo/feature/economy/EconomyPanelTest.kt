package com.isivoltpro.maginaolivo.feature.economy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.economy.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EconomyPanelTest {
    @get:Rule val compose = createComposeRule()
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val today = LocalDate.parse("2027-03-15")

    /** In-memory ledger with the production rules; costs are fixed at 300,00 €. */
    private inner class Fake : EconomyRepository {
        val ledger = MutableStateFlow(EconomyLedger(emptyList(), emptyList(), emptyList()))
        override fun observe(farmId: UUID?, cycleId: UUID?) = ledger.map { l ->
            val scope = EconomyScope(workspace, farmId, cycleId)
            AppResult.Success(EconomySnapshot(scope, l, EconomyRules.balances(scope, l, mapOf("EUR" to 30_000L)))) as AppResult<EconomySnapshot>
        }
        private fun fail(code: String) = AppResult.Failure(AppError.Validation("x", code))
        override suspend fun record(input: Settlement): AppResult<Unit> {
            (EconomyRules.documentError(input) ?: EconomyRules.replacementError(input, ledger.value))?.let { return fail(it) }
            ledger.value = ledger.value.copy(settlements = ledger.value.settlements + input); return AppResult.Success(Unit)
        }
        override suspend fun cancel(settlementId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun applyAdvance(application: AdvanceApplication): AppResult<Unit> {
            EconomyRules.applicationError(application, ledger.value)?.let { return fail(it) }
            ledger.value = ledger.value.copy(applications = ledger.value.applications + application); return AppResult.Success(Unit)
        }
        override suspend fun recordPayment(payment: SettlementPayment): AppResult<Unit> {
            EconomyRules.paymentError(payment, ledger.value)?.let { return fail(it) }
            ledger.value = ledger.value.copy(payments = ledger.value.payments + payment); return AppResult.Success(Unit)
        }
        override suspend fun cancelPayment(paymentId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun show(repo: Fake) = compose.setContent { MaginaOlivoTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) { EconomyPanel(repo, farm, null, today) } } }
    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun costsWithoutSettlementsAreAnIncompleteBalanceWithNegativeMargin() {
        show(Fake())
        compose.onNodeWithTag("campaign-balance-incomplete").assertTextContains("balance incompleto", substring = true)
        compose.onNodeWithText("-300,00", substring = true).assertExists()
    }

    @Test fun advanceThenFinalSettlementThenPaymentReachesZeroPending() {
        val repo = Fake()
        show(repo)
        click("economy-new"); click("economy-kind-ADVANCE")
        type("economy-cooperative", "Cooperativa Mágina"); type("economy-gross", "200")
        click("economy-save"); compose.waitForIdle()
        compose.onNodeWithText("Anticipos sin aplicar (no son ingreso)").assertExists()
        click("economy-new"); click("economy-kind-FINAL")
        type("economy-cooperative", ""); type("economy-gross", "1000"); type("economy-withholding", "20"); type("economy-fees", "50")
        compose.onNodeWithTag("economy-net").assertTextContains("930,00", substring = true)
        click("economy-save"); compose.waitForIdle()
        val advance = repo.ledger.value.settlements.first { it.kind == SettlementKind.ADVANCE }
        val final = repo.ledger.value.settlements.first { it.kind == SettlementKind.FINAL }
        click("economy-apply-${advance.id}"); type("economy-apply-amount", "200"); click("economy-apply-to-${final.id}")
        compose.waitForIdle()
        click("economy-pay-${final.id}"); click("economy-pay-save"); compose.waitForIdle()
        assertEquals(0L, EconomyRules.pending(final, repo.ledger.value))
        // Margin 1 000 − 50 fees − 300 costs; withholding is shown apart.
        compose.onNodeWithText("650,00", substring = true).assertExists()
        compose.onNodeWithText("Balance con liquidaciones definitivas").assertExists()
    }

    @Test fun overPaymentShowsTheRuleMessageAndSavesNothing() {
        val repo = Fake()
        show(repo)
        click("economy-new"); type("economy-cooperative", "Coop"); type("economy-gross", "100"); click("economy-save")
        compose.waitForIdle()
        val doc = repo.ledger.value.settlements.single()
        click("economy-pay-${doc.id}")
        compose.onNodeWithTag("economy-pay-amount").performTextClearance(); type("economy-pay-amount", "150")
        click("economy-pay-save"); compose.waitForIdle()
        compose.onNodeWithTag("economy-error").assertTextContains("supera lo pendiente", substring = true)
        assertEquals(0, repo.ledger.value.payments.size)
    }
}
