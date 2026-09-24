package com.isivoltpro.maginaolivo.feature.catastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadastreQueryTest {
    @Test fun discoveryBoxIsCenteredAndRemainsFarBelowOfficialLimit() {
        val box = boundedBbox(latitude = 37.73, longitude = -3.45, radiusMeters = 120.0)

        assertEquals(37.73, (box.south + box.north) / 2, 0.0000001)
        assertEquals(-3.45, (box.west + box.east) / 2, 0.0000001)
        assertTrue(box.approximateAreaM2 in 56_000.0..59_000.0)
        assertEquals("${box.south},${box.west},${box.north},${box.east},urn:ogc:def:crs:EPSG::4326", box.parameter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun discoveryBoxRejectsRadiusThatCouldExceedOneSquareKilometre() {
        boundedBbox(latitude = 37.73, longitude = -3.45, radiusMeters = 501.0)
    }
}
