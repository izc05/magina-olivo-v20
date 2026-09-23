package com.isivoltpro.maginaolivo.feature.catastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CadastreClientTest {
    @Test fun parsesOfficialHuelmaParcelAndSwapsAxisForGeoJson() {
        val xml = requireNotNull(javaClass.getResourceAsStream("/catastro/real-huelma-parcel.gml")).use { it.readBytes() }
        val candidate = parseCadastralGml(xml, "23044A00400021")
        assertEquals("23044A00400021", candidate.reference)
        assertEquals(148096.0, candidate.areaM2!!, 0.01)
        assertEquals(1, candidate.polygons.size)
        assertEquals(74, candidate.polygons[0][0].size)
        assertTrue(candidate.polygons[0][0][0].first in -4.0..-3.0)
        assertTrue(candidate.polygons[0][0][0].second in 37.0..38.0)
        assertTrue(candidate.geometryGeoJson.startsWith("{\"type\":\"Polygon\",\"coordinates\":[[["))
    }

    @Test fun rejectsAnotherReferenceEvenWhenResponseContainsAParcel() {
        val xml = requireNotNull(javaClass.getResourceAsStream("/catastro/real-huelma-parcel.gml")).use { it.readBytes() }
        try {
            parseCadastralGml(xml, "23044A00400022")
            fail("Must not import a neighbouring parcel")
        } catch (error: CadastreException) {
            assertEquals(CadastreError.NOT_FOUND, error.kind)
        }
    }

    @Test fun preservesMultipartAndInteriorRings() {
        val xml = """<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0"
            xmlns:cp="http://inspire.ec.europa.eu/schemas/cp/4.0"
            xmlns:gml="http://www.opengis.net/gml/3.2"><wfs:member><cp:CadastralParcel>
            <cp:nationalCadastralReference>23044A00400021</cp:nationalCadastralReference>
            <cp:geometry><gml:MultiSurface><gml:surfaceMember><gml:Surface srsName="EPSG::4326">
            <gml:patches><gml:PolygonPatch>
            <gml:exterior><gml:LinearRing><gml:posList>37 -3 37 -2.9 37.1 -2.9 37 -3</gml:posList></gml:LinearRing></gml:exterior>
            <gml:interior><gml:LinearRing><gml:posList>37.01 -2.99 37.02 -2.99 37.02 -2.98 37.01 -2.99</gml:posList></gml:LinearRing></gml:interior>
            </gml:PolygonPatch></gml:patches></gml:Surface></gml:surfaceMember>
            <gml:surfaceMember><gml:Surface srsName="EPSG::4326"><gml:patches><gml:PolygonPatch>
            <gml:exterior><gml:LinearRing><gml:posList>37.2 -3 37.2 -2.9 37.3 -2.9 37.2 -3</gml:posList></gml:LinearRing></gml:exterior>
            </gml:PolygonPatch></gml:patches></gml:Surface></gml:surfaceMember></gml:MultiSurface></cp:geometry>
            </cp:CadastralParcel></wfs:member></wfs:FeatureCollection>""".toByteArray()
        val candidate = parseCadastralGml(xml, "23044A00400021")
        assertEquals(2, candidate.polygons.size)
        assertEquals(2, candidate.polygons[0].size)
        assertTrue(candidate.geometryGeoJson.contains("\"MultiPolygon\""))
    }

    @Test fun acceptsCanaryIslandsParcelsAndRejectsSwappedAxes() {
        fun gml(posList: String) = """<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0"
            xmlns:cp="http://inspire.ec.europa.eu/schemas/cp/4.0"
            xmlns:gml="http://www.opengis.net/gml/3.2"><wfs:member><cp:CadastralParcel>
            <cp:nationalCadastralReference>38001A00100001</cp:nationalCadastralReference>
            <cp:geometry><gml:MultiSurface><gml:surfaceMember><gml:Surface srsName="EPSG::4326">
            <gml:patches><gml:PolygonPatch>
            <gml:exterior><gml:LinearRing><gml:posList>$posList</gml:posList></gml:LinearRing></gml:exterior>
            </gml:PolygonPatch></gml:patches></gml:Surface></gml:surfaceMember></gml:MultiSurface></cp:geometry>
            </cp:CadastralParcel></wfs:member></wfs:FeatureCollection>""".toByteArray()

        // Tenerife, latitude first as Catastro serves EPSG::4326.
        val canary = parseCadastralGml(gml("28.3 -16.5 28.3 -16.49 28.31 -16.49 28.3 -16.5"), "38001A00100001")
        assertEquals(-16.5, canary.polygons[0][0][0].first, 0.0)
        assertEquals(28.3, canary.polygons[0][0][0].second, 0.0)

        try {
            parseCadastralGml(gml("-16.5 28.3 -16.49 28.3 -16.49 28.31 -16.5 28.3"), "38001A00100001")
            fail("Longitude-first coordinates must not be stored as a parcel")
        } catch (error: CadastreException) {
            assertEquals(CadastreError.INVALID_GEOMETRY, error.kind)
        }
    }
}
