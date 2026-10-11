package com.isivoltpro.maginaolivo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #271: the daily pulse never holds empty space open. These are the rules the white block broke.
 */
class MarketPulseTest {

    @Test fun whileItLoadsTheBlockIsASmallStripAndNeverTheOldTallBox() {
        val outcome = MarketPulse.outcome(online = true, failed = false, measuredCssPx = null, elapsedMs = 2_000)
        assertEquals(PulseOutcome.LOADING, outcome)
        // The widget still needs a real height to paint into, but a small one.
        assertEquals(MarketPulse.LOADING_HEIGHT_DP, MarketPulse.blockHeightDp(outcome, null))
        assertEquals(112, MarketPulse.LOADING_HEIGHT_DP)
    }

    @Test fun onlyAMeasurementBigEnoughToBeTheThreePricesBecomesHeight() {
        assertNull(MarketPulse.contentHeightDp(0))
        assertNull(MarketPulse.contentHeightDp(MarketPulse.MIN_CONTENT_HEIGHT_DP - 1))
        assertEquals(MarketPulse.MIN_CONTENT_HEIGHT_DP, MarketPulse.contentHeightDp(MarketPulse.MIN_CONTENT_HEIGHT_DP))
        assertEquals(180, MarketPulse.contentHeightDp(180))
        assertEquals(606, MarketPulse.contentHeightDp(606))   // the publisher's widget, whole
        // The block follows the widget's own height so no price card is cut, and a page that
        // measures itself huge is stopped rather than followed.
        assertEquals(MarketPulse.MAX_CONTENT_HEIGHT_DP, MarketPulse.contentHeightDp(2_000))
        assertEquals(640, MarketPulse.MAX_CONTENT_HEIGHT_DP)
    }

    /** #355 — on a short phone the block never eats the official weekly prices above it. */
    @Test fun onASmallScreenTheBlockKeepsItsShareAndNoMore() {
        assertEquals(288, MarketPulse.maxContentHeightDp(640))     // a short phone
        assertEquals(288, MarketPulse.contentHeightDp(606, screenHeightDp = 640))
        assertEquals(MarketPulse.MAX_CONTENT_HEIGHT_DP, MarketPulse.maxContentHeightDp(2_000))
        // Never below what the widget needs to paint into while it loads.
        assertEquals(MarketPulse.LOADING_HEIGHT_DP, MarketPulse.maxContentHeightDp(100))
        assertEquals(
            288,
            MarketPulse.blockHeightDp(PulseOutcome.SHOWN, measuredCssPx = 606, screenHeightDp = 640),
        )
    }

    @Test fun anEmptyPageIsNotContent() {
        val outcome = MarketPulse.outcome(online = true, failed = false, measuredCssPx = 40, elapsedMs = 1_000)
        assertEquals(PulseOutcome.LOADING, outcome)   // still waiting, not shown
        val tooLate = MarketPulse.outcome(online = true, failed = false, measuredCssPx = 40, elapsedMs = MarketPulse.TIMEOUT_MS)
        assertEquals(PulseOutcome.UNAVAILABLE, tooLate)
        assertEquals(0, MarketPulse.blockHeightDp(tooLate, 40))
    }

    @Test fun realContentIsShownAtItsOwnHeight() {
        val outcome = MarketPulse.outcome(online = true, failed = false, measuredCssPx = 150, elapsedMs = 3_000)
        assertEquals(PulseOutcome.SHOWN, outcome)
        assertEquals(150, MarketPulse.blockHeightDp(outcome, 150))
    }

    @Test fun offlineOrFailedTakesNoSpaceAtAll() {
        listOf(
            MarketPulse.outcome(online = false, failed = false, measuredCssPx = null, elapsedMs = 0),
            MarketPulse.outcome(online = true, failed = true, measuredCssPx = null, elapsedMs = 0),
            // A failure after the page had drawn still wins: a dead render process leaves a blank view.
            MarketPulse.outcome(online = true, failed = true, measuredCssPx = 150, elapsedMs = 5_000),
        ).forEach { outcome ->
            assertEquals(PulseOutcome.UNAVAILABLE, outcome)
            assertEquals(0, MarketPulse.blockHeightDp(outcome, 150))
        }
    }

    @Test fun waitingHasAnEnd() {
        assertEquals(
            PulseOutcome.LOADING,
            MarketPulse.outcome(online = true, failed = false, measuredCssPx = null, elapsedMs = MarketPulse.TIMEOUT_MS - 1),
        )
        assertEquals(
            PulseOutcome.UNAVAILABLE,
            MarketPulse.outcome(online = true, failed = false, measuredCssPx = null, elapsedMs = MarketPulse.TIMEOUT_MS),
        )
        assertEquals(12_000L, MarketPulse.TIMEOUT_MS)
    }
}
