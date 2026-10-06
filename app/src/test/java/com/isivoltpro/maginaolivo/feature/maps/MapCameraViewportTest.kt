package com.isivoltpro.maginaolivo.feature.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraViewportTest {
    @Test fun frameMarginIsAddedWithoutLosingMeasuredInsets() {
        assertEquals(
            MapCameraInsets(leftPx = 24, topPx = 144, rightPx = 24, bottomPx = 224),
            MapCameraInsets(topPx = 120, bottomPx = 200).withMargin(24),
        )
    }

    @Test fun labelsUnderFloatingPanelsAreNotConsideredVisible() {
        val insets = MapCameraInsets(topPx = 120, bottomPx = 180)
        assertFalse(insideSafeViewport(200f, 80f, 400, 800, insets))
        assertFalse(insideSafeViewport(200f, 700f, 400, 800, insets))
        assertTrue(insideSafeViewport(200f, 300f, 400, 800, insets))
    }

    @Test fun leftAndRightInsetsAreRespectedForFuturePanels() {
        val insets = MapCameraInsets(leftPx = 30, rightPx = 40)
        assertFalse(insideSafeViewport(20f, 200f, 400, 800, insets))
        assertFalse(insideSafeViewport(370f, 200f, 400, 800, insets))
        assertTrue(insideSafeViewport(200f, 200f, 400, 800, insets))
    }
}
