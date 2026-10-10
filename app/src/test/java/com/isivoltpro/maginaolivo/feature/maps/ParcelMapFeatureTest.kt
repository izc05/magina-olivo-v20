package com.isivoltpro.maginaolivo.feature.maps

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelMapFeatureTest {
    @Test fun appearanceChangesTheOfflineCanvasWithoutRecoloringSourceTiles() {
        for (base in MapBase.entries) {
            val light = JsonParser.parseString(parcelStyle(base, cadastreLines = true)).asJsonObject
            val dark = JsonParser.parseString(parcelStyle(base, cadastreLines = true, dark = true)).asJsonObject
            assertEquals(light.getAsJsonObject("sources"), dark.getAsJsonObject("sources"))
            val lightLayers = light.getAsJsonArray("layers").map { it.asJsonObject }
            val darkLayers = dark.getAsJsonArray("layers").map { it.asJsonObject }
            assertEquals("#171914", darkLayers.first().getAsJsonObject("paint")["background-color"].asString)
            assertEquals(lightLayers.filter { it["type"].asString == "raster" }, darkLayers.filter { it["type"].asString == "raster" })
            if (base == MapBase.NONE) {
                assertFalse(dark.toString().contains("https://"))
                assertTrue(dark.toString().contains("#B6D39E"))
            }
        }
    }

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
        // #361: IGN/PNOA tiles are 256 px and are drawn at their own size, never stretched.
        assertTrue(parcelStyle(MapBase.AERIAL, cadastreLines = false).contains("\"base\":{\"type\":\"raster\",\"tileSize\":256"))
        assertTrue(parcelStyle(MapBase.MAP, cadastreLines = false).contains("\"base\":{\"type\":\"raster\",\"tileSize\":256"))
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

    /** #361: «Mi ubicación» is a blue dot above the parcels, only when there is a position. */
    @Test fun myLocationIsDrawnAboveEverythingOnlyWhenKnown() {
        val ids = { style: String -> JsonParser.parseString(style).asJsonObject.getAsJsonArray("layers").map { it.asJsonObject.get("id").asString } }
        assertEquals(
            listOf("background", "base", "parcels-fill", "parcels-line", "my-location-halo", "my-location"),
            ids(parcelStyle(MapBase.MAP, cadastreLines = false, myLocation = true)),
        )
        assertFalse(parcelStyle(MapBase.MAP, cadastreLines = false).contains("my-location"))
        val feature = JsonParser.parseString(myLocationFeature(GeoPoint(37.73, -3.45))).asJsonObject
        val coordinates = feature.getAsJsonObject("geometry").getAsJsonArray("coordinates")
        // GeoJSON is longitude first.
        assertEquals(-3.45, coordinates[0].asDouble, 1e-9)
        assertEquals(37.73, coordinates[1].asDouble, 1e-9)
    }

    @Test fun theNumberOnTheMapIsTheCatastroParcelNumber() {
        assertEquals("120", parcelNumber("23044A00400120"))
        assertEquals("21", parcelNumber("23044A00400021"))
        assertEquals("Pol. 4 · Parc. 120", defaultParcelName("23044A00400120"))
    }
}
