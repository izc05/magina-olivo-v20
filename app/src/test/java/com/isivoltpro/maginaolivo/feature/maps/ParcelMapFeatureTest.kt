package com.isivoltpro.maginaolivo.feature.maps

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelMapFeatureTest {
    @Test fun savedPolygonBecomesSelectableFeatureWithoutChangingItsGeometry() {
        val geometry = """{"type":"Polygon","coordinates":[[[-3.0,37.0],[-2.9,37.0],[-2.9,37.1],[-3.0,37.0]]]}"""

        val collection = JsonParser.parseString(mapFeatureCollection(listOf(MapParcel("parcel-1", "Norte", geometry)), "parcel-1")).asJsonObject
        val feature = collection.getAsJsonArray("features")[0].asJsonObject

        assertEquals("FeatureCollection", collection["type"].asString)
        assertEquals("parcel-1", feature.getAsJsonObject("properties")["id"].asString)
        assertTrue(feature.getAsJsonObject("properties")["selected"].asBoolean)
        assertEquals(JsonParser.parseString(geometry), feature["geometry"])
    }

    @Test fun malformedStoredGeometryIsSkippedInsteadOfCrashingTheFarmMap() {
        val collection = JsonParser.parseString(mapFeatureCollection(listOf(MapParcel("bad", "Sin límite", "not-json")), null)).asJsonObject

        assertEquals(0, collection.getAsJsonArray("features").size())
        assertFalse(collection.has("not-json"))
    }
}
