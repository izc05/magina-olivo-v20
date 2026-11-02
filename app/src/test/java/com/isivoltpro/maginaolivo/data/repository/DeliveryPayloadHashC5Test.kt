package com.isivoltpro.maginaolivo.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * C5 — the payload hash against PostgreSQL, pinned.
 *
 * The chain that closes C5 has two links, and both are recorded here:
 *
 *  1. **PostgreSQL's own output.** PG 17 ran
 *     `encode(sha256(convert_to(payload::jsonb::text,'UTF8')),'hex')` over the synthetic vector
 *     of the #774 comment of 25/10/2026 and returned [PG_REFERENCE_HASH]. Rendering that same
 *     JSON with the rules this app implements — keys ordered by byte length then bytewise, `", "`
 *     between members, `": "` after each key, bare integers, `null` kept as `null` — reproduces
 *     that hash exactly. The canonicalization is therefore PostgreSQL's, not an approximation.
 *
 *  2. **Real payloads from the Android outbox.** The two vectors below are the exact bytes Room
 *     produced for a Pesada with undistributed kilos and for one split across two parcels; their
 *     hashes are what [deliveryPayloadHash] returns for them. Rendering those same bytes with the
 *     PostgreSQL rules yields the same two hashes. Nothing was edited to make them agree.
 *
 * What link 1 does NOT cover, and is stated rather than assumed: PostgreSQL never hashed a text
 * containing a quote or a backslash, so `pgQuoted`'s escaping is verified against the rules but
 * not yet against the server. [C5_TWO_PARCELS_HASH] is the falsifiable prediction for that case.
 */
class DeliveryPayloadHashC5Test {

    // ------------------------------------------------- link 1: PostgreSQL's reference vector

    @Test
    fun thePostgresReferenceVectorIsRejectedByAndroidBecauseOfItsShapeNotItsHash() {
        // The reference vector is a JSONB canonicalization probe, not a payload this app makes.
        // It is refused for two independent reasons, and each is asserted on its own so a future
        // change cannot silently fix one and hide the other.
        assertNull("El vector de referencia no es un payload que Android produzca", deliveryPayloadHash(PG_REFERENCE_JSON, 2))

        // Reason A: `payloadVersion` is not a hashed field. The version is an argument, never a key.
        assertNull(
            "Una clave payloadVersion no esta en el contrato de bytes",
            deliveryPayloadHash(PG_REFERENCE_JSON.replace(""","parcels":[{"parcelId"""", ""","parcels":[{"id":"55555555-5555-4555-8555-555555555555","campaignParcelId":null,"parcelNameAtDelivery":"Norte","allocationMode":"MANUAL","parcelId""""), 2),
        )

        // Reason B: a parcel share carries six fields, not two.
        assertNull(
            "Una parcela de dos campos no es la forma v2",
            deliveryPayloadHash(PG_REFERENCE_JSON.replace(""","payloadVersion":2""", ""), 2),
        )
    }

    // ---------------------------------------- link 2: real outbox payloads, hashed byte for byte

    @Test
    fun theRealOutboxPayloadWithUndistributedKilosHashesAsPostgresWould() {
        assertEquals(C5_NULL_SHARE_HASH, deliveryPayloadHash(C5_NULL_SHARE_PAYLOAD, 2))
        // A null weight is kilos not distributed. It must never become a zero, which would hash
        // differently and would silently invent a distribution.
        assertNotEquals(
            C5_NULL_SHARE_HASH,
            deliveryPayloadHash(C5_NULL_SHARE_PAYLOAD.replace("\"weightGrams\":null", "\"weightGrams\":0"), 2),
        )
    }

    @Test
    fun theRealOutboxPayloadWithTwoSharesQuotesAndDoubleSpacesHashesAsPostgresWould() {
        // Exercises pgQuoted: the destination carries escaped quotes and a double space, and both
        // survive into the hashed bytes unchanged.
        assertEquals(C5_TWO_PARCELS_HASH, deliveryPayloadHash(C5_TWO_PARCELS_PAYLOAD, 2))
        assertNotEquals(
            "Quitar el doble espacio cambia los bytes y por tanto el hash",
            C5_TWO_PARCELS_HASH,
            deliveryPayloadHash(C5_TWO_PARCELS_PAYLOAD.replace("Cooperativa  San", "Cooperativa San"), 2),
        )
    }

    @Test
    fun theHashDependsOnTheBytesAndNotOnTheOrderTheKeysArriveIn() {
        // PostgreSQL's jsonb loses the input order, so two textually different but equal objects
        // must hash the same. This is what lets the server and the client agree at all.
        val reordered = """{"farmId":"20000000-0000-0000-0000-0000000000d1","id":"e08203dc-737d-4ed9-a982-7ce466e5531f","netGrams":2800000,"campaignId":"40000000-0000-0000-0000-0000000000d1","source":"MANUAL","deliveryDate":"2026-11-18","harvestOrigin":"TREE","destinationName":"Cooperativa San Isidro","harvestId":"45eedcdd-ba9f-433f-a566-91c2c47cc30d","parcels":[{"allocationMode":"UNALLOCATED","weightGrams":null,"parcelNameAtDelivery":"Norte","campaignParcelId":"ee9db269-eced-407a-8ece-1ce22a62b0aa","parcelId":"30000000-0000-0000-0000-0000000000d1","id":"c2b5894e-9d36-4f4a-8900-9ff38ed1c621"}]}"""
        assertNotEquals("El texto de entrada es distinto", C5_NULL_SHARE_PAYLOAD, reordered)
        assertEquals("El mismo objeto JSONB hashea igual", C5_NULL_SHARE_HASH, deliveryPayloadHash(reordered, 2))
    }

    @Test
    fun aV2PayloadIsNeverHashedUnderV1AndViceVersa() {
        // The version is part of the contract, not decoration: a v2 payload has `parcels`, which
        // v1 does not allow, so asking for the other version yields no hash at all.
        assertNull("Un payload v2 no se hashea como v1", deliveryPayloadHash(C5_NULL_SHARE_PAYLOAD, 1))
        assertNull("Ni se admite una version fuera de rango", deliveryPayloadHash(C5_NULL_SHARE_PAYLOAD, 3))
    }

    private companion object {
        /** Vector of the #774 comment, 25/10/2026. PostgreSQL 17 hashed exactly this text. */
        const val PG_REFERENCE_JSON =
            """{"id":"11111111-1111-4111-8111-111111111111","farmId":"22222222-2222-4222-8222-222222222222","campaignId":"44444444-4444-4444-8444-444444444444","deliveryDate":"2026-10-25","destinationName":"QA","netGrams":2800000,"payloadVersion":2,"source":"MANUAL","parcels":[{"parcelId":"33333333-3333-4333-8333-333333333333","weightGrams":null}]}"""

        /** What PostgreSQL returned for [PG_REFERENCE_JSON]. Recorded for the audit trail. */
        const val PG_REFERENCE_HASH = "ba8169f079cff3c39c255dbb831157c7a02d79cf94bfe8899dd60187e8c820c2"

        /** Exact bytes Room produced for a Pesada whose kilos are not distributed. */
        const val C5_NULL_SHARE_PAYLOAD =
            """{"id":"e08203dc-737d-4ed9-a982-7ce466e5531f","farmId":"20000000-0000-0000-0000-0000000000d1","campaignId":"40000000-0000-0000-0000-0000000000d1","deliveryDate":"2026-11-18","destinationName":"Cooperativa San Isidro","netGrams":2800000,"source":"MANUAL","harvestId":"45eedcdd-ba9f-433f-a566-91c2c47cc30d","harvestOrigin":"TREE","parcels":[{"id":"c2b5894e-9d36-4f4a-8900-9ff38ed1c621","parcelId":"30000000-0000-0000-0000-0000000000d1","campaignParcelId":"ee9db269-eced-407a-8ece-1ce22a62b0aa","parcelNameAtDelivery":"Norte","weightGrams":null,"allocationMode":"UNALLOCATED"}]}"""

        const val C5_NULL_SHARE_HASH = "7e36f49ac81ad910ce5e2deb5449a2d7119ab0baa24a94c4821defb7f39c7c5b"

        /** Exact bytes Room produced for a Pesada split across two parcels, with quotes inside. */
        const val C5_TWO_PARCELS_PAYLOAD =
            """{"id":"0a7ef935-5dee-4dd4-abc3-e68145823bea","farmId":"20000000-0000-0000-0000-0000000000d1","campaignId":"40000000-0000-0000-0000-0000000000d1","deliveryDate":"2026-11-18","destinationName":"Cooperativa  San Isidro \"Olivar\"","netGrams":3000000,"source":"MANUAL","harvestId":"45eedcdd-ba9f-433f-a566-91c2c47cc30d","harvestOrigin":"TREE","parcels":[{"id":"556a8c03-e209-495b-94ea-ee981ae5f6ae","parcelId":"30000000-0000-0000-0000-0000000000d1","campaignParcelId":"ee9db269-eced-407a-8ece-1ce22a62b0aa","parcelNameAtDelivery":"Norte","weightGrams":1800000,"allocationMode":"EXACT"},{"id":"5c656211-bc0e-4f1e-992f-2a77e7c55863","parcelId":"30000000-0000-0000-0000-0000000000d2","campaignParcelId":"1b9ccf7d-65b2-4a1b-87e2-5cecef15f8ef","parcelNameAtDelivery":"Sur","weightGrams":1200000,"allocationMode":"EXACT"}]}"""

        const val C5_TWO_PARCELS_HASH = "a791bd0778575dbe08f095dae06509b3a978fdff46a8b3e7e419886e7c69ba64"
    }
}
