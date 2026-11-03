package com.isivoltpro.maginaolivo.data.repository.sync

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #808 R3-b (JVM): the parts of the verification that need no Android JSON builder, against REAL server bytes: the
 * canonical delete hash, both Jornada fingerprints recomputed with the SERVER rule (owner decision B1), the derived rule,
 * and every refusal decided before rebuilding a payload. The v2/v3 payload rebuilds (org.json, a stub on the JVM) are
 * proven on the device in `HarvestReceiveVerificationDeviceTest`.
 */
class HarvestReceiveVerifierTest {
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

    @Test fun theDeleteReceiptIsTheCanonicalHashAndAnOutOfProtocolWriteHasNoHead() {
        val e = bundle("E")
        assertTrue(HarvestReceiveVerifier.verifyPesada(e.pesada("gone")) is PesadaVerdict.Verified)
        assertEquals(deliveryDeleteHash(id("gone"), 1), e.pesada("gone").head!!.payloadHash)
        assertEquals(PesadaVerdict.Unverified("NO_HEAD"), HarvestReceiveVerifier.verifyPesada(e.pesada("touched")))
    }

    @Test fun bothJornadaFingerprintsAreRecomputedWithTheServerRule() {
        for (key in listOf("E", "N")) {
            val b = bundle(key)
            assertEquals(key, b.head.ownFingerprint, harvestOwnFingerprint(HarvestReceiveVerifier.harvestEntity(b.harvest)))
        }
        val e = bundle("E")
        val derived = HarvestReceiveVerifier.serverDerived(e.deliveries.filter { it.deletedAt == null })
        assertEquals(e.head.derivedFingerprint, HarvestReceiveVerifier.derivedFingerprint(derived))
        assertEquals(e.serverWeightGrams, derived.weightGrams)
        assertEquals(e.serverParcels.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) },
            derived.parcels.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) })
    }

    @Test fun refusalsDecidedBeforeAnyRebuild() {
        val e = bundle("E")
        fun verdict(key: String, change: (ReceivedDelivery) -> ReceivedDelivery) = HarvestReceiveVerifier.verifyPesada(change(e.pesada(key)))
        // A lying kind: a live row labelled DELETE, or a deleted row labelled WRITE.
        assertEquals(PesadaVerdict.Unverified("KIND_MISMATCH"), verdict("v2c") { it.copy(head = it.head!!.copy(kind = DeliveryHeadKind.DELETE)) })
        assertEquals(PesadaVerdict.Unverified("KIND_MISMATCH"), verdict("gone") { it.copy(head = it.head!!.copy(kind = DeliveryHeadKind.WRITE)) })
        // A write hash can never pass as a delete: the delete hash is checked exactly.
        assertEquals(PesadaVerdict.Unverified("DELETE_HASH"), verdict("gone") { it.copy(head = it.head!!.copy(payloadHash = e.pesada("v3c").head!!.payloadHash)) })
        assertEquals(PesadaVerdict.Unverified("DELETE_CONTRACT"), verdict("gone") { it.copy(head = it.head!!.copy(payloadVersion = 2)) })
        assertEquals(PesadaVerdict.Unverified("UNSUPPORTED_PAYLOAD_VERSION"), verdict("v2c") { it.copy(head = it.head!!.copy(payloadVersion = 1)) })
        // B3: an origin text the sender would not produce cannot be rebuilt: never confirmed.
        assertEquals(PesadaVerdict.Unverified("ORIGIN_TEXT"), verdict("v3c") { it.copy(originCreatedAtExact = "2026-10-25T07:00:01.25Z") })
        assertEquals(PesadaVerdict.Unverified("NO_ORIGIN"), verdict("v3c") { it.copy(originCreatedAtExact = null) })
        assertEquals(PesadaVerdict.Unverified("V3_NOT_CREATE"), verdict("v3c") { it.copy(version = 2, head = it.head!!.copy(version = 2)) })
        assertEquals(PesadaVerdict.Unverified("HEAD_VERSION"), verdict("v2u") { it.copy(head = it.head!!.copy(version = 1)) })
    }

    @Test fun theDerivedRuleCoversExactUnallocatedAndPesadasWithoutParcels() {
        val e = bundle("E")
        val v3c = e.pesada("v3c"); val v2c = e.pesada("v2c")
        val noParcels = v3c.copy(id = UUID.randomUUID(), parcels = emptyList(), netGrams = 50)
        val derived = HarvestReceiveVerifier.serverDerived(listOf(v3c, v2c, noParcels))
        // A Pesada without parcels adds kilos but no parcel; any UNALLOCATED share makes its parcel UNALLOCATED.
        assertEquals(v3c.netGrams + v2c.netGrams + 50, derived.weightGrams)
        assertEquals((v3c.parcels + v2c.parcels).map { it.parcelId }.toSet(), derived.parcels.map { it.parcelId }.toSet())
        assertTrue(derived.parcels.any { it.allocationMode == "UNALLOCATED" && it.weightGrams == null })
        assertTrue(derived.parcels.any { it.allocationMode == "EXACT" && it.weightGrams != null })
        assertEquals(sha("{\"parcels\": [], \"weightGrams\": 0}"), HarvestReceiveVerifier.derivedFingerprint(HarvestReceiveVerifier.Derived(0, emptyList())))
    }

    private fun sha(text: String) = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
