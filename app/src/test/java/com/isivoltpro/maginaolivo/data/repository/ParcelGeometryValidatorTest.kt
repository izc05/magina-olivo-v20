package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParcelGeometryValidatorTest {
    @Test fun blankGeometryIsOptional() {
        assertNull(validateParcelGeometryGeoJson(null))
        assertNull(validateParcelGeometryGeoJson("   "))
    }

    @Test fun validPolygonIsAccepted() {
        assertNull(
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[[-3.50,37.70],[-3.40,37.70],[-3.40,37.80],[-3.50,37.70]]]}""",
            ),
        )
    }

    @Test fun validMultiPolygonIsAccepted() {
        assertNull(
            validateParcelGeometryGeoJson(
                """{"type":"MultiPolygon","coordinates":[[[[-3.50,37.70],[-3.40,37.70],[-3.40,37.80],[-3.50,37.70]]],[[[-3.30,37.60],[-3.20,37.60],[-3.20,37.70],[-3.30,37.60]]]]}""",
            ),
        )
    }

    @Test fun anotherGeometryTypeIsRejectedAsNotAParcelPolygon() {
        assertEquals(
            polygonRequired(),
            validateParcelGeometryGeoJson("""{"type":"Point","coordinates":[-3.5,37.7]}"""),
        )
    }

    @Test fun malformedJsonContainingPolygonTextIsRejected() {
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson("""{"type":"Polygon","coordinates":["""),
        )
    }

    @Test fun coordinatesAreRequired() {
        assertEquals(invalid(), validateParcelGeometryGeoJson("""{"type":"Polygon"}"""))
    }

    @Test fun ringNeedsAtLeastFourPositions() {
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[[-3.5,37.7],[-3.4,37.7],[-3.5,37.7]]]}""",
            ),
        )
    }

    @Test fun ringMustBeClosed() {
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[[-3.5,37.7],[-3.4,37.7],[-3.4,37.8],[-3.6,37.7]]]}""",
            ),
        )
    }

    @Test fun longitudeAndLatitudeMustBeFiniteAndInRange() {
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[[181,37.7],[-3.4,37.7],[-3.4,37.8],[181,37.7]]]}""",
            ),
        )
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[[-3.5,91],[-3.4,37.7],[-3.4,37.8],[-3.5,91]]]}""",
            ),
        )
        assertEquals(
            invalid(),
            validateParcelGeometryGeoJson(
                """{"type":"Polygon","coordinates":[[["NaN",37.7],[-3.4,37.7],[-3.4,37.8],["NaN",37.7]]]}""",
            ),
        )
    }

    private fun invalid() = AppResult.Failure(AppError.Validation("geometry", "invalid_geojson"))
    private fun polygonRequired() = AppResult.Failure(AppError.Validation("geometry", "polygon_required"))
}
