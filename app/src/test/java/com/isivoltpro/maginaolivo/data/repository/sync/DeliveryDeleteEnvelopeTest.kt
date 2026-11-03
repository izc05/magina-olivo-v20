package com.isivoltpro.maginaolivo.data.repository.sync

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #808 R1-a: the Pesada DELETE envelope and the server's canonical DELETE hash, without Room or network. */
class DeliveryDeleteEnvelopeTest {
    private val id = UUID.fromString("6f1c2b3a-0000-4000-8000-000000000808")

    @Test fun theDeleteHashIsTheServersCanonicalJsonbText() {
        // sha256 of {"kind": "DELETE", "deliveryId": "<id>", "expectedVersion": 3}: keys by length then bytes, the
        // exact text the R1-a SQL suite verifies against PostgreSQL (g26-delivery-delete, deleteHash).
        assertEquals("a2efed3ac854def7fee2f78c99e98db9736ca28e28c56ec354f5e15f6c0b0206", deliveryDeleteHash(id, 3))
        assertNotEquals(deliveryDeleteHash(id, 3), deliveryDeleteHash(id, 4))
        assertNotEquals(deliveryDeleteHash(id, 3), deliveryDeleteHash(UUID.randomUUID(), 3))
    }

    @Test fun theEnvelopeRoundTripsExactlyAndRejectsAnythingElse() {
        val envelope = DeliveryDeleteEnvelope("https://issuer.example/auth", 7, id, 3)
        val parsed = parseDeliveryDeleteEnvelope(envelope.json())
        assertEquals(envelope, parsed)
        assertEquals(envelope.json(), parsed!!.json())
        assertEquals(deliveryDeleteHash(id, 3), parsed.hash)
        // A write payload, a wrong kind, an extra or missing key, or a version below 1 is never a delete envelope.
        assertNull(parseDeliveryDeleteEnvelope("""{"id":"$id","netGrams":1}"""))
        assertNull(parseDeliveryDeleteEnvelope(envelope.json().replace("\"DELETE\"", "\"UPDATE\"")))
        assertNull(parseDeliveryDeleteEnvelope(envelope.json().replace("}", ",\"extra\":1}")))
        assertNull(parseDeliveryDeleteEnvelope(envelope.json().replace(",\"expectedVersion\":3", "")))
        assertNull(parseDeliveryDeleteEnvelope(envelope.json().replace("\"expectedVersion\":3", "\"expectedVersion\":0")))
    }
}
