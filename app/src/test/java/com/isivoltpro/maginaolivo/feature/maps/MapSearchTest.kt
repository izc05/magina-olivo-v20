package com.isivoltpro.maginaolivo.feature.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapSearchTest {

    @Test fun readsGoogleMapsDecimalPair() {
        val point = parseCoordinates("37.63601, -3.48012")!!
        assertEquals(37.63601, point.latitude, 1e-9)
        assertEquals(-3.48012, point.longitude, 1e-9)
    }

    @Test fun readsSpanishDecimalCommas() {
        val point = parseCoordinates("37,636 -3,480")!!
        assertEquals(37.636, point.latitude, 1e-9)
        assertEquals(-3.48, point.longitude, 1e-9)
    }

    @Test fun readsDegreesMinutesSecondsWithWestOrOeste() {
        val west = parseCoordinates("37°38'09.6\"N 3°28'48.4\"W")!!
        assertEquals(37.636, west.latitude, 1e-4)
        assertEquals(-3.48011, west.longitude, 1e-4)
        val oeste = parseCoordinates("37°38'09.6\"N 3°28'48.4\"O")!!
        assertEquals(west.longitude, oeste.longitude, 1e-12)
    }

    @Test fun refusesAnythingThatIsNotAPairOfCoordinates() {
        assertNull(parseCoordinates(""))
        assertNull(parseCoordinates("Huelma"))
        assertNull(parseCoordinates("37.6"))
        assertNull(parseCoordinates("37.6, -3.4, 12"))
        assertNull(parseCoordinates("137.6, -3.4"))
        assertNull(parseCoordinates("37°75'00\"N 3°28'48\"W"))
    }
}
