package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.feature.activities.ActivityDetailScreen
import com.isivoltpro.maginaolivo.feature.activities.ActivityDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #416: money for a work is a Gasto of its own, opened from the work; the work holds no new cost. */
class ActivityRelatedExpenseTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun doneWorkOffersARelatedExpense() {
        var opened: Activity? = null
        val done = UiPolishFixtures.activity.copy(status = ActivityStatus.COMPLETED, activityDate = UiPolishFixtures.today)
        show(done) { opened = it }
        composeRule.onNodeWithTag("activity-add-expense").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(done.id, opened?.id) }
    }

    @Test fun plannedWorkOffersNoMoney() {
        show(UiPolishFixtures.activity) {}
        assertEquals(0, composeRule.onAllNodesWithTag("activity-add-expense").fetchSemanticsNodes().size)
    }

    @Test fun aHistoricCostIsShownForWhatItIs() {
        show(UiPolishFixtures.activity.copy(status = ActivityStatus.COMPLETED, costMinor = 6_500)) {}
        composeRule.onNodeWithTag("activity-cost-summary").performScrollTo()
        composeRule.onNodeWithText("Coste histórico vinculado").assertExists()
    }

    /** Owner #480: with a historic cost the CTA says «otro» and links to the cost's own Gasto. */
    @Test fun aHistoricCostLeadsToItsOwnExpenseAndTheCtaSaysOther() {
        val historicId = java.util.UUID.randomUUID()
        var opened: java.util.UUID? = null
        show(UiPolishFixtures.activity.copy(status = ActivityStatus.COMPLETED, costMinor = 6_500), historicCostExpenseId = historicId,
            onOpenExpense = { opened = it }) {}
        composeRule.onNodeWithText("Añadir otro gasto relacionado").performScrollTo().assertExists()
        composeRule.onNodeWithTag("activity-add-expense-note").assertExists()
        composeRule.onNodeWithTag("activity-historic-expense").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(historicId, opened) }
    }

    /** Owner #480: no new money on a work of a closed Campaign; it says how to proceed. */
    @Test fun aClosedCampaignDisablesTheCta() {
        show(UiPolishFixtures.activity.copy(status = ActivityStatus.COMPLETED), campaignClosed = true) {}
        composeRule.onNodeWithTag("activity-add-expense").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("activity-add-expense-closed").assertExists()
    }

    /** #416: «Añadir gasto relacionado» carries the work (and its single Parcel) into Gasto. */
    @Test fun aRelatedExpenseRouteCarriesTheWork() {
        assertEquals("expenses/farm/f?parcelId=p&activityId=a",
            com.isivoltpro.maginaolivo.navigation.AppDestination.farmExpenses("f", parcelId = "p", activityId = "a"))
        assertEquals("expenses/farm/f?activityId=a",
            com.isivoltpro.maginaolivo.navigation.AppDestination.farmExpenses("f", activityId = "a"))
        assertEquals("expenses/farm/f?campaignId=c",
            com.isivoltpro.maginaolivo.navigation.AppDestination.farmExpenses("f", campaignId = "c"))
        assertEquals("expenses/farm/f", com.isivoltpro.maginaolivo.navigation.AppDestination.farmExpenses("f"))
    }

    private fun show(
        activity: Activity,
        historicCostExpenseId: java.util.UUID? = null,
        onOpenExpense: ((java.util.UUID) -> Unit)? = null,
        campaignClosed: Boolean = false,
        onAddRelatedExpense: (Activity) -> Unit,
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ActivityDetailScreen(
                    ActivityDetailUiState(isLoading = false, activity = activity),
                    {}, {}, {}, {}, {}, {},
                    onAddRelatedExpense = onAddRelatedExpense,
                    historicCostExpenseId = historicCostExpenseId,
                    onOpenExpense = onOpenExpense,
                    campaignClosed = campaignClosed,
                )
            }
        }
    }
}
