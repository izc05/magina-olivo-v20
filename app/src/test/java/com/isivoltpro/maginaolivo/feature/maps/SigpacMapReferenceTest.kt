package com.isivoltpro.maginaolivo.feature.maps

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigpacMapReferenceTest {
    @Test fun optedInRecintosUseTheOfficialTransparentSourceBelowOwnedParcels() {
        val style = JsonParser.parseString(parcelStyle(MapBase.MAP, false, sigpacLines = true)).asJsonObject
        val sources = style.getAsJsonObject("sources")
        assertTrue("Explicit SIGPAC reference must have its own source", sources.has("sigpac"))
        val source = sources.getAsJsonObject("sigpac")
        assertEquals(512, source["tileSize"].asInt)
        val url = source.getAsJsonArray("tiles")[0].asString
        assertTrue(url.startsWith("https://sigpac-hubcloud.es/wms/ows?"))
        assertTrue("Published SIGPAC service requires the verified WMS1.3.0 request", url.contains("VERSION=1.3.0"))
        assertTrue(url.contains("LAYERS=AU.Sigpac:recinto"))
        assertTrue(url.contains("TRANSPARENT=TRUE"))
        assertTrue(url.contains("CRS=EPSG:3857"))
        assertTrue(url.contains("BBOX={bbox-epsg-3857}"))
        val layers = style.getAsJsonArray("layers").map { it.asJsonObject }
        val ids = layers.map { it["id"].asString }
        assertTrue(ids.indexOf("sigpac") < ids.indexOf("parcels-fill"))
        assertEquals(15, layers.first { it["id"].asString == "sigpac" }["minzoom"].asInt)
    }

    @Test fun defaultAndOfflineDoNotRequestAnyOnlineReference() {
        val initial = JsonParser.parseString(parcelStyle(MapBase.MAP, false)).asJsonObject
        assertFalse(initial.getAsJsonObject("sources").has("sigpac"))
        val offline = parcelStyle(MapBase.NONE, true, overlayTiles = "https://example.invalid/radar/{z}/{x}/{y}.png", sigpacLines = true)
        assertFalse("Solo parcelas must never depend on a remote tile source", offline.contains("https://"))
    }

    @Test fun enablingTheReferencePreservesOwnedFeaturesAndOriginalRasterQuality() {
        for (base in listOf(MapBase.MAP, MapBase.AERIAL)) {
            val before = JsonParser.parseString(parcelStyle(base, false, dark = true)).asJsonObject
            val after = JsonParser.parseString(parcelStyle(base, false, dark = true, sigpacLines = true)).asJsonObject
            assertEquals(before.getAsJsonObject("sources")["saved-parcels"], after.getAsJsonObject("sources")["saved-parcels"])
            assertEquals(before.getAsJsonObject("sources")["base"], after.getAsJsonObject("sources")["base"])
            assertEquals(256, after.getAsJsonObject("sources").getAsJsonObject("base")["tileSize"].asInt)
            fun ownedLayers(style: com.google.gson.JsonObject) = style.getAsJsonArray("layers")
                .map { it.asJsonObject }.filter { it["source"]?.asString == "saved-parcels" }
            assertEquals(ownedLayers(before), ownedLayers(after))
        }
    }
}
