package com.isivoltpro.maginaolivo

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.feature.home.MarketPulse
import com.isivoltpro.maginaolivo.feature.home.PulseBlock
import com.isivoltpro.maginaolivo.feature.home.PulseOutcome
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * #271: «Mercado» must never show a tall white rectangle. The three states of the daily pulse are
 * driven here without a WebView, which is exactly what the defect needed: the widget's own failure
 * modes are states of this block, not of the page.
 */
class MarketPulseBlockTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun whileItLoadsTheBlockIsASmallStripThatSaysSo() {
        show(PulseOutcome.LOADING)
        composeRule.onNodeWithTag("home-market-pulse-loading").assertExists()
        // The widget is laid out — a WebView with no height never paints — but only this much.
        composeRule.onNodeWithTag("home-market-pulse-web")
            .assertHeightIsEqualTo(MarketPulse.LOADING_HEIGHT_DP.dp)
        composeRule.onNodeWithTag("home-market-pulse-unavailable").assertDoesNotExist()
    }

    @Test fun whenItFailsThereIsNoWidgetAreaAtAllAndTwoWaysOut() {
        var retried = 0
        var opened = 0
        show(PulseOutcome.UNAVAILABLE, onRetry = { retried++ }, onOpenSource = { opened++ })
        composeRule.onNodeWithTag("home-market-pulse-unavailable").assertExists()
        // The space the white block used to keep is simply not there.
        composeRule.onNodeWithTag("home-market-pulse-web").assertDoesNotExist()
        composeRule.onNodeWithTag("home-market-pulse-retry").performClick()
        composeRule.onNodeWithTag("home-market-pulse-detail").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retried)
            assertEquals(1, opened)
        }
    }

    @Test fun realContentKeepsItsOwnMeasuredHeightAndItsSourceLine() {
        show(PulseOutcome.SHOWN, heightDp = 180)
        composeRule.onNodeWithTag("home-market-pulse-web").assertHeightIsEqualTo(180.dp)
        composeRule.onNodeWithTag("home-market-pulse-loading").assertDoesNotExist()
        // The licence line stays: it is an indicative daily reference, not an official quotation.
        composeRule.onNodeWithTag("home-market-pulse").assertHeightIsAtLeast(180.dp)
    }

    private fun show(
        outcome: PulseOutcome,
        heightDp: Int = MarketPulse.blockHeightDp(outcome, null),
        onRetry: () -> Unit = {},
        onOpenSource: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                PulseBlock(
                    outcome = outcome,
                    heightDp = heightDp,
                    onRetry = onRetry,
                    onOpenSource = onOpenSource,
                    // Stands in for the publisher's widget: the block's job is its size and its states.
                    web = if (outcome == PulseOutcome.UNAVAILABLE) {
                        null
                    } else {
                        { modifier ->
                            androidx.compose.foundation.layout.Box(
                                modifier.testTag("home-market-pulse-web").background(Color.White),
                            )
                        }
                    },
                )
            }
        }
    }
}
