package com.isivoltpro.maginaolivo.feature.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLabelCollisionTest {
    @Test fun selectedParcelWinsAgainstAnOverlappingOrdinaryLabel() {
        val ordinary = MapLabelCandidate("a", "1", 100, 100, selected = false)
        val selected = MapLabelCandidate("b", "2", 105, 104, selected = true)

        val result = resolveMapLabelCollisions(
            listOf(ordinary, selected),
            centerX = 100,
            centerY = 100,
            horizontalSpacingPx = 36,
            verticalSpacingPx = 24,
            maxLabels = 60,
        )

        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun selectedParcelSurvivesEvenWhenLabelLimitIsReached() {
        val selected = MapLabelCandidate("selected", "S", 500, 500, selected = true)
        val ordinary = (0 until 60).map { index ->
            MapLabelCandidate("p$index", index.toString(), index * 100, 0, selected = false)
        }

        val result = resolveMapLabelCollisions(
            ordinary + selected,
            centerX = 0,
            centerY = 0,
            horizontalSpacingPx = 20,
            verticalSpacingPx = 20,
            maxLabels = 3,
        )

        assertTrue(result.any { it.id == "selected" })
        assertEquals(3, result.size)
    }

    @Test fun overlappingOrdinaryLabelsCollapseToOne() {
        val result = resolveMapLabelCollisions(
            listOf(
                MapLabelCandidate("near", "1", 100, 100, selected = false),
                MapLabelCandidate("overlap", "2", 110, 105, selected = false),
                MapLabelCandidate("far", "3", 200, 200, selected = false),
            ),
            centerX = 100,
            centerY = 100,
            horizontalSpacingPx = 36,
            verticalSpacingPx = 24,
            maxLabels = 60,
        )

        assertEquals(listOf("near", "far"), result.map { it.id })
    }

    @Test fun inputOrderDoesNotChangeOrdinaryPriority() {
        val a = MapLabelCandidate("a", "1", 90, 100, selected = false)
        val b = MapLabelCandidate("b", "2", 110, 100, selected = false)

        val first = resolveMapLabelCollisions(
            listOf(b, a),
            centerX = 100,
            centerY = 100,
            horizontalSpacingPx = 36,
            verticalSpacingPx = 24,
            maxLabels = 60,
        )
        val second = resolveMapLabelCollisions(
            listOf(a, b),
            centerX = 100,
            centerY = 100,
            horizontalSpacingPx = 36,
            verticalSpacingPx = 24,
            maxLabels = 60,
        )

        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
