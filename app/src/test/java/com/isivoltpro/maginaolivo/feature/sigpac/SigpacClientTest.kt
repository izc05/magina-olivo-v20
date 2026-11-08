package com.isivoltpro.maginaolivo.feature.sigpac

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SigpacClientTest {
    private val albanchez = SigpacParcelCode(23, 1, 0, 0, 3, 4)
    private val square = "POLYGON((-3.45 37.74,-3.43 37.74,-3.43 37.77,-3.45 37.77,-3.45 37.74))"
    private val away = "POLYGON((-3.10 37.10,-3.09 37.10,-3.09 37.11,-3.10 37.11,-3.10 37.10))"

    private fun item(
        recinto: Int = 1, superficie: String = "179.0965", wkt: String = square, poligono: Int = 3,
        coef: String = "0", incidencias: String = "null",
    ) = """{"provincia":23,"municipio":1,"agregado":0,"zona":0,"poligono":$poligono,"parcela":4,"recinto":$recinto,
        "superficie":$superficie,"pendiente_media":64.2,"coef_regadio":$coef,"admisibilidad":65,"incidencias":$incidencias,
        "uso_sigpac":"PR","region":15,"altitud":1342,"srid":4258,"wkt":"$wkt"}"""

    private val parcelGeometry =
        """{"type":"Polygon","coordinates":[[[-3.46,37.73],[-3.42,37.73],[-3.42,37.78],[-3.46,37.78],[-3.46,37.73]]]}"""

    @Test fun readsTheCodeOfARuralReference() =
        assertEquals(albanchez, sigpacCodeFromCadastralReference("23001a00300004"))

    @Test fun refusesReferencesItCannotTranslate() {
        assertNull(sigpacCodeFromCadastralReference("1234567VH1234N"))
        assertNull(sigpacCodeFromCadastralReference(null))
        assertNull(sigpacCodeFromCadastralReference("99001A00300004"))
    }

    @Test fun mapsTheOfficialFieldsWithoutChangingThem() {
        val recinto = parseSigpacRecintos("[${item()}]", albanchez).single()
        assertEquals(179.0965, recinto.areaHa!!, 1e-9)
        assertEquals(64.2, recinto.slope!!, 1e-9)
        assertEquals(65.0, recinto.admissibilityPercent!!, 1e-9)
        assertEquals("PR", recinto.useCode)
        assertEquals("15", recinto.region)
        assertNull(recinto.incidents)
    }

    @Test fun zeroAndMissingStayDifferent() {
        assertEquals(0.0, parseSigpacRecintos("[${item(coef = "0")}]", albanchez).single().irrigationCoefficient!!, 0.0)
        assertNull(parseSigpacRecintos("[${item(coef = "null")}]", albanchez).single().irrigationCoefficient)
    }

    @Test fun anEmptyAnswerIsNoRecintos() = assertTrue(parseSigpacRecintos("[]", albanchez).isEmpty())

    @Test fun rejectsAnAnswerAboutAnotherParcel() {
        val failure = assertThrows(SigpacException::class.java) { parseSigpacRecintos("[${item(poligono = 9)}]", albanchez) }
        assertEquals(SigpacError.RESPONSE, failure.kind)
    }

    @Test fun rejectsRepeatedRecintosAndUnexpectedBodies() {
        assertThrows(SigpacException::class.java) { parseSigpacRecintos("[${item()},${item()}]", albanchez) }
        assertThrows(SigpacException::class.java) { parseSigpacRecintos("<html>", albanchez) }
        assertThrows(SigpacException::class.java) { parseSigpacRecintos("[${item(superficie = "-1")}]", albanchez) }
    }

    @Test fun sumsEachRecintoOnce() {
        val recintos = parseSigpacRecintos("[${item(1, "1.5")},${item(2, "2.25")}]", albanchez)
        assertEquals(3.75, sigpacAreaSum(recintos + recintos.first())!!, 1e-9)
    }

    @Test fun aMissingSurfaceNeverGivesAPartialSum() =
        assertNull(sigpacAreaSum(parseSigpacRecintos("[${item(1, "1.5")},${item(2, "null")}]", albanchez)))

    @Test fun aRecintoOutsideTheContourIsNotCounted() {
        val recintos = parseSigpacRecintos("[${item(1, "1.5", square)},${item(2, "9.0", away)}]", albanchez)
        val assessment = assessSigpacRecintos(recintos, parcelGeometry)
        assertEquals(listOf(1), assessment.matched.map { it.recinto })
        assertEquals(listOf(2), assessment.outside.map { it.recinto })
        assertEquals(1.5, assessment.totalAreaHa!!, 1e-9)
        assertFalse(assessment.unverified)
    }

    @Test fun withoutAContourTheResultIsMarkedUnverified() {
        val assessment = assessSigpacRecintos(parseSigpacRecintos("[${item()}]", albanchez), null)
        assertTrue(assessment.unverified)
    }

    @Test fun readsMultiPolygons() {
        val polygons = parseWktPolygons("MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((2 2,3 2,3 3,2 2),(2.1 2.1,2.2 2.1,2.2 2.2,2.1 2.1)))")!!
        assertEquals(2, polygons.size)
        assertEquals(2, polygons[1].size)
    }

    @Test fun decodesTheGzipTheServiceAlwaysSends() = runBlocking {
        val gzipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("[${item()}]".toByteArray()) } }.toByteArray()
        var requested = ""
        val recintos = OfficialSigpacClient { url -> requested = url; gzipped }.recintos(albanchez)
        assertEquals("https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/23/1/0/0/3/4.json", requested)
        assertEquals(1, recintos.size)
    }
}

/** Real public answers saved on 08/11/2026 for Albanchez de Mágina, 23001A00300004. */
class SigpacAlbanchezFixtureTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResource("/sigpac/$name")).readText()

    @Test fun theOfficialRecintoMatchesTheCatastroContour() {
        val code = requireNotNull(sigpacCodeFromCadastralReference("23001A00300004"))
        val assessment = assessSigpacRecintos(parseSigpacRecintos(resource("albanchez-recinfoparc.json"), code), resource("albanchez-catastro-parcel.geojson"))
        assertFalse(assessment.unverified)
        assertEquals(listOf(1), assessment.matched.map { it.recinto })
        assertTrue(assessment.outside.isEmpty())
        assertEquals(179.0965, assessment.totalAreaHa!!, 1e-9)
        assertEquals(listOf("PR"), assessment.useCodes)
    }
}
