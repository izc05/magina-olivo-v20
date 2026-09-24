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

    @Test fun eachBaseLayerIsLightByDefaultAndCatastroLinesOnlyWhenAsked() {
        fun layers(style: String) = JsonParser.parseString(style).asJsonObject.getAsJsonArray("layers").map { it.asJsonObject.get("id").asString }
        assertEquals(listOf("background", "base", "parcels-fill", "parcels-line"), layers(parcelStyle(MapBase.MAP, cadastreLines = false)))
        assertEquals(listOf("background", "base", "cadastre", "parcels-fill", "parcels-line"), layers(parcelStyle(MapBase.AERIAL, cadastreLines = true)))
        // Offline-safe: only the saved boundaries, never a remote source.
        val none = parcelStyle(MapBase.NONE, cadastreLines = true)
        assertEquals(listOf("background", "parcels-fill", "parcels-line"), layers(none))
        assertFalse(none.contains("https://"))
        // Fewer, larger tiles keep the phone fluid.
        assertTrue(parcelStyle(MapBase.AERIAL, cadastreLines = false).contains("\"tileSize\":512"))
    }

    @Test fun theNumberOnTheMapIsTheCatastroParcelNumber() {
        assertEquals("120", parcelNumber("23044A00400120"))
        assertEquals("21", parcelNumber("23044A00400021"))
        assertEquals("Pol. 4 · Parc. 120", defaultParcelName("23044A00400120"))
    }
}
