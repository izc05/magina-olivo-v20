package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness
import com.isivoltpro.maginaolivo.feature.harvests.RecollectionTotalCards
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #449: an incomplete cost says so beside the number; a complete one reads as final. */
class CostCompletenessCardsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theSameTotalIsMarkedIncompleteUntilTheLastPriceIsConfirmed() {
        var completeness by mutableStateOf<RecollectionCostCompleteness?>(null)
        rule.setContent { MaginaOlivoTheme { RecollectionTotalCards(emptyList(), false, "5.700 kg", completeness) } }
        // Codex #605: while any source is unread, nothing reads as final.
        rule.onNodeWithTag("dashboard-cost-per-kg").assertTextContains("Coste contabilizado/kg", substring = true)
            .assertTextContains("Comprobando si faltan costes", substring = true)
        rule.runOnIdle { completeness = RecollectionCostCompleteness(setOf(RecollectionCostCompleteness.Reason.LABOUR_UNPRICED)) }
        rule.onNodeWithTag("dashboard-cost-per-kg").assertTextContains("Coste contabilizado/kg", substring = true)
            .assertTextContains("Incompleto · jornales sin precio", substring = true)

        rule.runOnIdle { completeness = RecollectionCostCompleteness(emptySet()) }
        rule.onNodeWithTag("dashboard-cost-per-kg").assertTextContains("Coste/kg", substring = true)
        assertEquals(0, rule.onAllNodesWithText("Incompleto", substring = true).fetchSemanticsNodes().size)
    }
}
