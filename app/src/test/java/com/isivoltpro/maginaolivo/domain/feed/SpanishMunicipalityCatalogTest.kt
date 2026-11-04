package com.isivoltpro.maginaolivo.domain.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selector 1.0 — the bundled official INE 2026 municipality directory, searched offline. */
class SpanishMunicipalityCatalogTest {

    @Test
    fun coversAll50ProvincesCeutaAndMelillaWithFiveDigitCodes() {
        assertEquals(8132, SpanishMunicipalityCatalog.size)
        assertEquals(52, SpanishMunicipalityCatalog.provinces.size)
        SpanishMunicipalityCatalog.provinces.forEach { province ->
            assertTrue("${province.name} has municipalities", SpanishMunicipalityCatalog.municipalitiesInProvince(province.code).isNotEmpty())
        }
        assertEquals("Ceuta", SpanishMunicipalityCatalog.findByCode("51001")?.displayName)
        assertEquals("Melilla", SpanishMunicipalityCatalog.findByCode("52001")?.displayName)
        SpanishMunicipalityCatalog.search("", limit = 10_000).forEach {
            assertTrue(it.ineCode.matches(Regex("\\d{5}")))
            assertTrue(it.ineCode.startsWith(it.provinceCode))
        }
    }

    @Test
    fun searchIgnoresAccentsCaseAndArticles() {
        assertEquals("23024", SpanishMunicipalityCatalog.search("la carolina").first().ineCode)
        assertEquals("23024", SpanishMunicipalityCatalog.search("CAROLINA, LA").first().ineCode)
        assertEquals("La Carolina", SpanishMunicipalityCatalog.findByCode("23024")?.displayName)
        assertEquals("04902", SpanishMunicipalityCatalog.search("el ejido").first().ineCode)
        assertEquals("23092", SpanishMunicipalityCatalog.search("ubeda", provinceCode = "23").first().ineCode)
        assertEquals("23092", SpanishMunicipalityCatalog.search("ÚBEDA").first().ineCode)
        assertEquals("23902", SpanishMunicipalityCatalog.search("bedmar").first().ineCode)
    }

    @Test
    fun resolveNeverGuessesBetweenHomonymsAcrossProvinces() {
        // «Cabra» exists only once exactly (Córdoba); «Cabra del Camp» is another town.
        assertEquals("14013", SpanishMunicipalityCatalog.resolve("Cabra", "Córdoba")?.ineCode)
        // A name shared by several provinces is ambiguous without a province…
        val moyaCandidates = SpanishMunicipalityCatalog.search("moya").filter { SpanishMunicipalityCatalog.normalize(it.displayName) == "moya" }
        assertTrue("Moya exists in more than one province", moyaCandidates.map { it.provinceCode }.distinct().size > 1)
        assertNull(SpanishMunicipalityCatalog.resolve("Moya"))
        // …and resolved once the province is given.
        moyaCandidates.forEach { candidate ->
            assertEquals(candidate.ineCode, SpanishMunicipalityCatalog.resolve("Moya", candidate.provinceName)?.ineCode)
        }
    }

    @Test
    fun resolvesCommonFarmSpellingsToTheOfficialCode() {
        assertEquals("23902", SpanishMunicipalityCatalog.resolve("Bedmar", "Jaén")?.ineCode)
        assertEquals("23902", SpanishMunicipalityCatalog.resolve(" bedmar ", "Jaen")?.ineCode)
        assertEquals("23024", SpanishMunicipalityCatalog.resolve("La Carolina", "Jaén")?.ineCode)
        assertEquals("23", SpanishMunicipalityCatalog.resolveProvince("jaen")?.code)
        assertEquals("01", SpanishMunicipalityCatalog.resolveProvince("Álava")?.code)
        assertEquals("15", SpanishMunicipalityCatalog.resolveProvince("La Coruña")?.code)
    }

    @Test
    fun nonExistentOrMalformedPlacesAreRejected() {
        assertNull(SpanishMunicipalityCatalog.resolve("Villainventada de Arriba", "Jaén"))
        assertNull(SpanishMunicipalityCatalog.resolve("Bedmar", "Provincia Falsa"))
        assertNull(SpanishMunicipalityCatalog.resolve("Bedmar", "Córdoba"))
        assertNull(SpanishMunicipalityCatalog.findByCode("99999"))
        assertNull(SpanishMunicipalityCatalog.findByCode("2390"))
        assertNull(SpanishMunicipalityCatalog.findByCode("abcde"))
        assertNull(SpanishMunicipalityCatalog.resolve("  "))
    }

    @Test
    fun cacheKeysOfHomonymsInDifferentProvincesNeverCollide() {
        val moya = SpanishMunicipalityCatalog.search("moya").filter { SpanishMunicipalityCatalog.normalize(it.displayName) == "moya" }
        val keys = moya.map { it.toFeedLocation().key }
        assertEquals(keys.size, keys.toSet().size)
        // A legacy farm location without code keeps its historical key (cache rows stay readable).
        assertEquals("bedmar|jaen", FeedLocation("Bedmar", "Jaén").key)
        val enriched = FeedLocation("Bedmar", "Jaén").withValidatedIneCode()
        assertEquals("23902", enriched.ineCode)
        assertEquals("Bedmar", enriched.municipality)
        assertNotNull(enriched.key)
        assertTrue(enriched.key.startsWith("23902|"))
    }
}

