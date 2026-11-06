package com.isivoltpro.maginaolivo.data.repository

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryPayloadHashTest {
    @Test fun matchesTheExactVectorsAlsoExecutedByNativePostgres() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/g2-delivery-payload-hash-vectors.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val vectors = Json.parseToJsonElement(fixture).jsonArray
        assertEquals(9, vectors.size)
        for (entry in vectors) {
            val row = entry.jsonObject
            val name = row.getValue("name").jsonPrimitive.content
            val expected = row.getValue("sha256").jsonPrimitive.content
            assertEquals(name, expected, sha(row.getValue("postgresText").jsonPrimitive.content))
            assertEquals(name, expected, deliveryPayloadHash(row.getValue("payload").jsonPrimitive.content,
                row.getValue("payloadVersion").jsonPrimitive.content.toInt()))
        }
    }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun postgresTextOrderingAndSpacesDifferFromRawSnapshot() {
        val raw = """{"netGrams":2500000,"notes":"Mágina","id":"x"}"""
        val expected = """{"id": "x", "notes": "Mágina", "netGrams": 2500000}"""
        assertEquals(sha(expected), deliveryPayloadHash(raw))
        assertNotEquals(sha(raw), deliveryPayloadHash(raw))
        assertEquals(deliveryPayloadHash(raw), deliveryPayloadHash("""{ "id":"x", "notes":"M\u00e1gina", "netGrams":2500000 }"""))
    }

    @Test fun sameLengthKeysUseByteOrderAndLongNeverPassesThroughDouble() {
        val raw = """{"tareGrams":0,"grossGrams":9223372036854775807,"netGrams":9223372036854775807,"source":"MANUAL"}"""
        val expected = """{"source": "MANUAL", "netGrams": 9223372036854775807, "tareGrams": 0, "grossGrams": 9223372036854775807}"""
        assertEquals(sha(expected), deliveryPayloadHash(raw))
        assertEquals(deliveryPayloadHash("""{"netGrams":0}"""), deliveryPayloadHash("""{"netGrams":-0}"""))
    }

    @Test fun escapedControlsAndSupplementaryUnicodeUsePostgresBytes() {
        val raw = """{"notes":"Línea\n\t\b\f\r\u0001\"\\/🌿"}"""
        val expected = """{"notes": "Línea\n\t\b\f\r\u0001\"\\/🌿"}"""
        assertEquals(sha(expected), deliveryPayloadHash(raw))
    }

    @Test fun rejectsUnsupportedV2ShapesAndCrossVersionPayloads() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/g2-delivery-payload-hash-vectors.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val vector = Json.parseToJsonElement(fixture).jsonArray[6].jsonObject
        val payload = vector.getValue("payload").jsonPrimitive.content
        assertNull(deliveryPayloadHash(payload, 1))
        assertNull(deliveryPayloadHash(payload, 3))
        assertNull(deliveryPayloadHash("""{"parcels":[]}""", 2))
        assertNull(deliveryPayloadHash("""{"parcels":[{}]}""", 2))
        assertNull(deliveryPayloadHash("""{"parcels":null}""", 2))
        assertNull(deliveryPayloadHash("""{"notes":"x"}""", 2))
    }

    @Test fun rejectsAnythingOutsideWriterContract() {
        for (raw in listOf("bad", "[]", "{}", """{"other":1}""", """{"netGrams":1.0}""",
            """{"netGrams":1e2}""", """{"netGrams":9223372036854775808}""",
            """{"netGrams":"1"}""", """{"notes":true}""", """{"notes":null}""",
            """{"notes":[]}""", """{"notes":{}}""", """{"notes":"\u0000"}""",
            """{"notes":"\ud800"}""", """{"notes":"\udc00"}""", " ".repeat(65_537))) {
            assertNull(raw.take(100), deliveryPayloadHash(raw))
        }
    }
}
