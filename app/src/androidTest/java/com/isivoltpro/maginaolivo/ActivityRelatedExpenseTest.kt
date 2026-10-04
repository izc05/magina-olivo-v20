package com.isivoltpro.maginaolivo

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

    private fun show(activity: Activity, onAddRelatedExpense: (Activity) -> Unit) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ActivityDetailScreen(
                    ActivityDetailUiState(isLoading = false, activity = activity),
                    {}, {}, {}, {}, {}, {},
                    onAddRelatedExpense = onAddRelatedExpense,
                )
            }
        }
    }
}
