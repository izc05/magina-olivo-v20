package com.isivoltpro.maginaolivo.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.repository.sync.BundleRequest
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryHeadKind
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestBundle
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveParser
import com.isivoltpro.maginaolivo.data.repository.sync.HarvestReceiveVerifier
import com.isivoltpro.maginaolivo.data.repository.sync.PesadaVerdict
import com.isivoltpro.maginaolivo.data.repository.sync.ReceiveHoldCauses
import com.isivoltpro.maginaolivo.data.repository.sync.ReceivedDelivery
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 R3-b on the device (API 35, real org.json): the Pesada receipts of REAL server bytes rebuild byte for byte with
 * the sender's own builders (v3 CREATE, v2 CREATE, v2 UPDATE with base = version - 1), and whole-bundle verdicts. The
 * fixture is the same real capture the JVM tests use. Pure verification: no Room write, no network, no outbox.
 */
@RunWith(AndroidJUnit4::class)
class HarvestReceiveVerificationDeviceTest {
    private val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResource("r3b/receive-fixture.json")!!.readText()).jsonObject
    private val req = fixture.getValue("request").jsonObject
    private val ids = fixture.getValue("ids").jsonObject
    private fun id(key: String) = UUID.fromString(ids.getValue(key).jsonPrimitive.content)
    private fun bundle(key: String) = HarvestReceiveParser.parseBundle(
        fixture.getValue("bundles").jsonObject.getValue(key).jsonPrimitive.content,
        BundleRequest(req.getValue("issuer").jsonPrimitive.content, UUID.fromString(req.getValue("account").jsonPrimitive.content), id(key),
            UUID.fromString(req.getValue("workspace").jsonPrimitive.content), UUID.fromString(req.getValue("farm").jsonPrimitive.content),
            UUID.fromString(req.getValue("campaign").jsonPrimitive.content)))
    private fun HarvestBundle.pesada(key: String) = deliveries.single { it.id == id(key) }
    private fun HarvestBundle.with(key: String, change: (ReceivedDelivery) -> ReceivedDelivery) =
        copy(deliveries = deliveries.map { if (it.id == id(key)) change(it) else it })
    private fun holds(b: HarvestBundle) = HarvestReceiveVerifier.verify(b).holds.map { it.first }

    @Test fun everyProtocolWriteRebuildsByteForByteFromTheServerRow() {
        val e = bundle("E")
        for (key in listOf("v3c", "v2c", "v2u", "gone")) assertTrue(key, HarvestReceiveVerifier.verifyPesada(e.pesada(key)) is PesadaVerdict.Verified)
        assertEquals(listOf(3, 2, 2), listOf("v3c", "v2c", "v2u").map { e.pesada(it).head!!.payloadVersion })
        assertTrue(HarvestReceiveVerifier.verifyPesada(bundle("N").pesada("manualP")) is PesadaVerdict.Verified)
    }

    @Test fun wholeBundleVerdictsHoldOnlyWhatCannotBeVerified() {
        // E: only the out-of-protocol Pesada is unverified; kilos and split follow the verified Pesadas.
        val e = HarvestReceiveVerifier.verify(bundle("E"))
        assertEquals(listOf(ReceiveHoldCauses.UNVERIFIED), e.holds.map { it.first })
        assertTrue(e.holds.single().second.contains(id("touched").toString()))
        // N (MANUAL): everything verifies, nothing is held.
        assertTrue(HarvestReceiveVerifier.verify(bundle("N")).verified)
    }

    @Test fun anyTamperingWithAWriteIsUnverified() {
        val e = bundle("E")
        fun verdict(key: String, change: (ReceivedDelivery) -> ReceivedDelivery) = HarvestReceiveVerifier.verifyPesada(change(e.pesada(key)))
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v3c") { it.copy(head = it.head!!.copy(payloadHash = "0".repeat(64))) })
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v3c") { it.copy(ticketNumber = "T-2") })
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v2c") { it.copy(notes = "otra") })
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v2u") { it.copy(netGrams = it.netGrams + 1) })
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v2u") { it.copy(parcels = it.parcels.map { p -> p.copy(parcelNameAtDelivery = "Otra") }) })
        // A v2 head presented as v3 (or the reverse) never verifies.
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v2c") { it.copy(head = it.head!!.copy(payloadVersion = 3), originCreatedAtExact = "2026-10-25T07:00:00Z") })
        assertEquals(PesadaVerdict.Unverified("HASH_MISMATCH"), verdict("v3c") { it.copy(head = it.head!!.copy(payloadVersion = 2)) })
        assertEquals(PesadaVerdict.Unverified("KIND_MISMATCH"), verdict("v2c") { it.copy(head = it.head!!.copy(kind = DeliveryHeadKind.DELETE)) })
    }

    @Test fun jornadaTamperingAndForeignRecordersAreHeld() {
        val n = bundle("N")
        assertEquals(listOf(ReceiveHoldCauses.UNVERIFIED), holds(n.copy(head = n.head.copy(ownFingerprint = "0".repeat(64)))))
        assertEquals(listOf(ReceiveHoldCauses.UNVERIFIED), holds(n.copy(harvest = n.harvest.copy(notes = "cambiada"))))
        assertEquals(listOf(ReceiveHoldCauses.UNVERIFIED),
            holds(n.copy(head = n.head.copy(headOperationId = null, headPayloadVersion = null, headPayloadHash = null))))
        assertEquals(listOf(ReceiveHoldCauses.FOREIGN_RECORDER), holds(n.with("manualP") { it.copy(recordedBySelf = false) }))
        assertEquals(listOf(ReceiveHoldCauses.FOREIGN_RECORDER), holds(n.copy(harvest = n.harvest.copy(recordedByUserId = UUID.randomUUID()))))
        // AUTO_DAY: a derived fingerprint, a server split or server kilos that do not follow the Pesadas is WEIGHT_DIVERGENT.
        val e = bundle("E")
        assertFalse(holds(e).contains(ReceiveHoldCauses.WEIGHT_DIVERGENT))
        assertTrue(holds(e.copy(head = e.head.copy(derivedFingerprint = "0".repeat(64)))).contains(ReceiveHoldCauses.WEIGHT_DIVERGENT))
        assertTrue(holds(e.copy(serverParcels = e.serverParcels.map { it.copy(weightGrams = null, allocationMode = "UNALLOCATED") }))
            .contains(ReceiveHoldCauses.WEIGHT_DIVERGENT))
        // Dropping a live Pesada the server counted (here the unverified one) makes the kilos diverge too.
        assertTrue(holds(e.copy(deliveries = e.deliveries.filter { it.id != id("touched") })).contains(ReceiveHoldCauses.WEIGHT_DIVERGENT))
    }
}
