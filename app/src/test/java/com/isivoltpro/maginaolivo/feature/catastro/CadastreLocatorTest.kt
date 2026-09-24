package com.isivoltpro.maginaolivo.feature.catastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadastreLocatorTest {

    @Test fun queryUsesCatastroNamesAndPlainNumbers() {
        assertEquals(
            "Provincia=JAEN&Municipio=HUELMA&Poligono=4&Parcela=21",
            polygonParcelQuery(" Jaén ", "Huelma", "004", "00021"),
        )
    }

    @Test fun queryKeepsTheEnye() {
        val query = polygonParcelQuery("Jaén", "Baños de la Encina", "7", "12")!!
        assertTrue(query, query.contains("Municipio=BA%C3%91OS+DE+LA+ENCINA"))
    }

    @Test fun queryRefusesMissingOrNonNumericData() {
        assertNull(polygonParcelQuery("", "Huelma", "4", "21"))
        assertNull(polygonParcelQuery("Jaén", "Huelma", "4a", "21"))
        assertNull(polygonParcelQuery("Jaén", "Huelma", "0", "21"))
        assertNull(polygonParcelQuery("Jaén", "Huelma&x=1", "4", "21"))
    }

    @Test fun readsTheReferenceOfASingleAnswer() {
        val xml = """<consulta_dnp xmlns="http://www.catastro.meh.es/"><control><cudnp>1</cudnp></control>
            <bico><bi><idbi><cn>RU</cn><rc><pc1>23044A0</pc1><pc2>0400021</pc2><car>0000</car><cc1>T</cc1><cc2>Q</cc2></rc></idbi></bi></bico>
            </consulta_dnp>""".toByteArray()
        assertEquals(listOf("23044A00400021"), parseDnpppReferences(xml))
    }

    @Test fun readsEveryReferenceOfAListAnswer() {
        val xml = """<consulta_dnp xmlns="http://www.catastro.meh.es/"><control><cudnp>2</cudnp></control><lrcdnp>
            <rcdnp><rc><pc1>23044A0</pc1><pc2>0400021</pc2></rc></rcdnp>
            <rcdnp><rc><pc1>23044A0</pc1><pc2>0400022</pc2></rc></rcdnp>
            </lrcdnp></consulta_dnp>""".toByteArray()
        assertEquals(listOf("23044A00400021", "23044A00400022"), parseDnpppReferences(xml))
    }

    @Test fun anErrorAnswerIsNotFoundNotAGuess() {
        val xml = """<consulta_dnp xmlns="http://www.catastro.meh.es/"><control><cuerr>1</cuerr></control>
            <lerr><err><cod>13</cod><des>LA PARCELA NO EXISTE</des></err></lerr></consulta_dnp>""".toByteArray()
        assertTrue(parseDnpppReferences(xml).isEmpty())
    }

    @Test(expected = CadastreException::class)
    fun aDoctypeIsRefusedBeforeParsing() {
        parseDnpppReferences("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><consulta_dnp>&e;</consulta_dnp>""".toByteArray())
    }
}
