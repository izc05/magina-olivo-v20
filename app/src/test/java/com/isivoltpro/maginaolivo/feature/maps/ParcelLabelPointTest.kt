package com.isivoltpro.maginaolivo.feature.maps

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelLabelPointTest {
    @Test fun rectangleUsesAnInteriorPoint() {
        val point = parcelLabelPoint(
            """{"type":"Polygon","coordinates":[[[0,0],[4,0],[4,2],[0,2],[0,0]]]}""",
        )
        assertNotNull(point)
        point!!
        assertTrue(point.longitude > 0 && point.longitude < 4)
        assertTrue(point.latitude > 0 && point.latitude < 2)
    }

    @Test fun concaveLShapeNeverPlacesTheLabelInTheMissingCorner() {
        val point = parcelLabelPoint(
            """{"type":"Polygon","coordinates":[[[0,0],[4,0],[4,1],[1,1],[1,4],[0,4],[0,0]]]}""",
        )
        assertNotNull(point)
        point!!
        // The empty corner is x > 1 AND y > 1.
        assertTrue(point.longitude <= 1.0 || point.latitude <= 1.0)
    }

    @Test fun aHoleNeverReceivesTheLabel() {
        val point = parcelLabelPoint(
            """{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]]}""",
        )
        assertNotNull(point)
        point!!
        val insideHole = point.longitude > 4 && point.longitude < 6 && point.latitude > 4 && point.latitude < 6
        assertTrue(!insideHole)
    }

    @Test fun multiPolygonUsesTheLargestComponentNotTheFirstOne() {
        val point = parcelLabelPoint(
            """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,1],[0,0]]],[[[10,10],[20,10],[20,20],[10,20],[10,10]]]]}""",
        )
        assertNotNull(point)
        point!!
        assertTrue(point.longitude > 9)
        assertTrue(point.latitude > 9)
    }

    @Test fun malformedGeometryHasNoLabelPoint() {
        assertNull(parcelLabelPoint("not-json"))
        assertNull(parcelLabelPoint("""{"type":"Point","coordinates":[0,0]}"""))
    }
}
