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
        val mapSources = JsonParser.parseString(parcelStyle(MapBase.MAP, cadastreLines = false)).asJsonObject.getAsJsonObject("sources")
        val aerialSources = JsonParser.parseString(parcelStyle(MapBase.AERIAL, cadastreLines = false)).asJsonObject.getAsJsonObject("sources")
        val cadastreSources = JsonParser.parseString(parcelStyle(MapBase.MAP, cadastreLines = true)).asJsonObject.getAsJsonObject("sources")
        assertEquals(256, mapSources.getAsJsonObject("base")["tileSize"].asInt)
        assertEquals(256, aerialSources.getAsJsonObject("base")["tileSize"].asInt)
        assertEquals(512, cadastreSources.getAsJsonObject("cadastre")["tileSize"].asInt)
    }

    @Test fun theRadarIsDrawnOverTheBaseAndUnderTheParcels() {
        val tiles = "https://tilecache.rainviewer.com/v2/radar/5415fe0e827c/256/{z}/{x}/{y}/2/1_1.png"
        val style = JsonParser.parseString(parcelStyle(MapBase.MAP, cadastreLines = false, overlayTiles = tiles)).asJsonObject
        assertEquals(
            listOf("background", "base", "overlay", "parcels-fill", "parcels-line"),
            style.getAsJsonArray("layers").map { it.asJsonObject.get("id").asString },
        )
        val overlay = style.getAsJsonObject("sources").getAsJsonObject("overlay")
        assertEquals(tiles, overlay.getAsJsonArray("tiles")[0].asString)
        assertEquals(256, overlay.get("tileSize").asInt)
        // Without a radar the style is exactly the one the farm map always used.
        assertEquals(parcelStyle(MapBase.MAP, cadastreLines = false), parcelStyle(MapBase.MAP, cadastreLines = false, overlayTiles = null))
    }

    @Test fun theNumberOnTheMapIsTheCatastroParcelNumber() {
        assertEquals("120", parcelNumber("23044A00400120"))
        assertEquals("21", parcelNumber("23044A00400021"))
        assertEquals("Pol. 4 · Parc. 120", defaultParcelName("23044A00400120"))
    }
}
